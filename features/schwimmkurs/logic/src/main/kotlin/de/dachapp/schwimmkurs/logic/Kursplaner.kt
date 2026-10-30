package de.dachapp.schwimmkurs.logic

import java.time.LocalDate

/** Ergebnis des automatischen Befüllens. Wird erst mit [Kursplaner.uebernehmen] gespeichert. */
data class Vorschlag(
    /** Kurs-ID → neu zugeteilte Kind-IDs. Bereits zugeteilte Kinder bleiben unverändert. */
    val zuteilung: Map<String, List<String>>,
    /** Kinder ohne Platz, mit Grund, in Vorrangreihenfolge. */
    val warteliste: List<Pair<String, String>>,
) {
    val anzahl: Int get() = zuteilung.values.sumOf { it.size }
}

object Kursplaner {

    /** null, wenn das Kind in den Kurs passt, sonst der Grund in Worten. */
    fun grund(kind: Kind, kurs: Kurs, einstellungen: Einstellungen, heute: LocalDate = LocalDate.now()): String? {
        val abz = Abzeichen(einstellungen.abzeichen)
        val min = kurs.minAlter ?: einstellungen.mindestalter.takeIf { it > 0 }
        if (min != null || kurs.maxAlter != null) {
            val alter = kind.alterAm(kurs.ersterTag ?: heute) ?: return "Geburtsdatum fehlt"
            if (min != null && alter < min) return "zu jung ($alter)"
            if (kurs.maxAlter != null && alter > kurs.maxAlter) return "zu alt ($alter)"
        }
        if (kurs.voraussetzung != null && !abz.hat(kind, kurs.voraussetzung)) return "${kurs.voraussetzung} fehlt"
        if (kurs.ziel != null && abz.hat(kind, kurs.ziel)) return "hat ${kurs.ziel} schon"
        return null
    }

    /** Eine Vorrang-Zeile "Gemeinde: Ortsteil, Ortsteil" in ihre Namen zerlegt; der erste ist die Gemeinde. */
    private fun namen(zeile: String): List<String> =
        zeile.split(':', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }

    private fun n(s: String) = s.trim().lowercase()

    private fun passt(name: String, kind: Kind): Boolean {
        val e = n(name)
        return e == n(kind.ort) || e == n(kind.plz) || (e.endsWith("*") && e.length > 1 && n(kind.plz).startsWith(e.dropLast(1)))
    }

    private fun inBemerkung(name: String, notiz: String): Boolean =
        name.length >= 3 && !name.endsWith("*") &&
            Regex("(?<![\\p{L}])" + Regex.escape(name) + "(?![\\p{L}])", RegexOption.IGNORE_CASE).containsMatchIn(notiz)

    /** Gefundene Zeile der Vorrangliste und der passende Name daraus. */
    private fun treffer(kind: Kind, vorrang: List<String>): Pair<Int, String>? {
        val listen = vorrang.map(::namen)
        listen.forEachIndexed { i, l -> l.firstOrNull { passt(it, kind) }?.let { return i to it } }
        if (kind.ort.isBlank()) {
            listen.forEachIndexed { i, l -> l.firstOrNull { inBemerkung(it, kind.notiz) }?.let { return i to it } }
        }
        return null
    }

    /**
     * Rang des Wohnorts (0 = bester). Die Zeilen der Vorrangliste zählen in ihrer Reihenfolge, Ortsteile wie ihre Gemeinde.
     * Ohne eingetragenen Ort wird in der Bemerkung gesucht ("KiGa Willmatshofen").
     * Unbekannter Wohnort = vorrang.size (neutral), bekannter Ort außerhalb = vorrang.size + 1 (zuletzt).
     */
    fun wohnortRang(kind: Kind, vorrang: List<String>, ausserhalb: List<String> = emptyList()): Int {
        treffer(kind, vorrang)?.let { return it.first }
        val draussen = kind.ort.isNotBlank() || ausserhalb.any { passt(it, kind) || inBemerkung(it.trim(), kind.notiz) }
        return if (draussen) vorrang.size + 1 else vorrang.size
    }

    /** Wohnort für die Anzeige: eingetragen oder aus der Bemerkung erkannt, Ortsteile mit Gemeinde ("Willmatshofen (Fischach)"). */
    fun wohnort(kind: Kind, vorrang: List<String>, ausserhalb: List<String> = emptyList()): String? {
        if (kind.ort.isNotBlank()) return kind.ort
        treffer(kind, vorrang)?.let { (i, name) ->
            val gemeinde = namen(vorrang[i]).first()
            return if (name.endsWith("*") || name.all { it.isDigit() }) kind.plz.ifBlank { gemeinde }
            else if (n(name) == n(gemeinde)) gemeinde else "$name ($gemeinde)"
        }
        ausserhalb.firstOrNull { inBemerkung(it.trim(), kind.notiz) }?.let { return it.trim() }
        return kind.plz.ifBlank { null }
    }

    /**
     * Jahr der Einschulung aus dem Feld (erste Jahreszahl, "2026 evtl. 2027" = 2026). Leer = geschätzt aus dem
     * Geburtsdatum nach bayerischem Stichtag: wer bis 30.09. sechs wird, kommt in dem Jahr in die Schule.
     */
    fun einschulungsjahr(kind: Kind): Int? {
        Regex("(?<!\\d)(19|20)\\d\\d(?!\\d)").find(kind.einschulung)?.let { return it.value.toInt() }
        val g = kind.geburtsdatum ?: return null
        return g.year + if (g.monthValue <= 9) 6 else 7
    }

    /**
     * Füllt freie Plätze mit passenden Kindern, die noch in keinem Kurs sitzen.
     *
     * Reihenfolge der Familien: Einzugsgebiet in der Reihenfolge der Vorrangliste, dann Kinder ohne bekannten Wohnort,
     * zuletzt Orte außerhalb. Innerhalb davon ältere Kinder zuerst, dann frühere Einschulung, dann Platz in der Anmeldeliste.
     * Altersgruppen: In einem Kurs liegen höchstens [Einstellungen.maxAltersspanne] Jahre zwischen dem jüngsten und
     * dem ältesten Kind; ein Kind kommt bevorzugt in den Kurs, dessen Kinder ihm im Alter am nächsten sind.
     * Geschwister kommen zusammen in einen Kurs, wenn sie alle hineinpassen und genug Plätze frei sind.
     * Sonst bekommt jedes Kind einzeln einen Platz, bevorzugt in einem Kurs an denselben Tagen wie seine
     * Geschwister. Ist bei einem Kind "Geschwister getrennt" gesetzt, kommen die Geschwister möglichst in
     * verschiedene Kurse. Unter mehreren passenden Kursen gewinnt der mit der engsten Altersspanne, dann der leerere.
     */
    fun vorschlagen(bestand: Bestand, heute: LocalDate = LocalDate.now()): Vorschlag {
        val e = bestand.einstellungen
        val frei = bestand.kurse.associate { it.id to it.frei }.toMutableMap()
        val neu = bestand.kurse.associate { it.id to mutableListOf<String>() }
        val tage = bestand.kurse.associate { k -> k.id to k.termine.map { it.datum }.toSet() }
        val warte = mutableListOf<Pair<String, String>>()

        val kandidaten = bestand.ohneKurs.filter { !it.pausiert }
        val passend = kandidaten.associate { k -> k.id to bestand.kurse.filter { grund(k, it, e, heute) == null } }

        // Alter (am ersten Kurstag) aller Kinder je Kurs, bereits eingeteilte eingeschlossen.
        fun alter(kind: Kind, kurs: Kurs) = kind.alterAm(kurs.ersterTag ?: heute)
        val alterImKurs = bestand.kurse.associate { k ->
            k.id to k.teilnehmer.mapNotNull { id -> bestand.kind(id)?.let { alter(it, k) } }.toMutableList()
        }
        fun spannePasst(kurs: Kurs, kinder: List<Kind>): Boolean {
            if (e.maxAltersspanne <= 0) return true
            val alle = alterImKurs.getValue(kurs.id) + kinder.mapNotNull { alter(it, kurs) }
            return alle.isEmpty() || alle.max() - alle.min() <= e.maxAltersspanne
        }
        /**
         * Abstand zum Durchschnittsalter im Kurs. Ein leerer Kurs zählt schlechter als jede Gruppe, in die das Kind
         * noch passt, damit angefangene Altersgruppen erst voll werden, bevor eine neue aufgemacht wird.
         */
        fun abstand(kurs: Kurs, kinder: List<Kind>): Double {
            val da = alterImKurs.getValue(kurs.id)
            val neuAlter = kinder.mapNotNull { alter(it, kurs) }
            if (neuAlter.isEmpty()) return e.maxAltersspanne.coerceAtLeast(1) / 2.0
            if (da.isEmpty()) return e.maxAltersspanne.coerceAtLeast(1) + 0.5
            return kotlin.math.abs(neuAlter.average() - da.average())
        }

        val familien = kandidaten.groupBy { it.familienSchluessel }.values.sortedWith(
            compareBy<List<Kind>>(
                { f -> f.minOf { wohnortRang(it, e.wohnortVorrang, e.orteAusserhalb) } },
                { f -> -(f.mapNotNull { it.alterAm(heute) }.maxOrNull() ?: -1) },
                { f -> f.mapNotNull { einschulungsjahr(it) }.minOrNull() ?: Int.MAX_VALUE },
                { f -> f.mapNotNull { it.anmeldedatum }.minOrNull() ?: LocalDate.MAX },
                { f -> f.mapNotNull { it.listenplatz }.minOrNull() ?: Int.MAX_VALUE },
                { f -> f.first().nachname.lowercase() },
                { f -> f.first().vorname.lowercase() },
            ),
        )
        // Kursbelegung aller Geschwister (alt und neu), um gleiche Tage zu bevorzugen.
        val familienKurse = bestand.kurse.flatMap { k -> k.teilnehmer.mapNotNull { bestand.kind(it) }.map { it.familienSchluessel to k.id } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.toMutableList() }.toMutableMap()

        fun rangfolge(kurse: List<Kurs>, familie: String, kinder: List<Kind>, getrennt: Boolean = false): List<Kurs> {
            val geschwisterKurse = familienKurse[familie].orEmpty()
            val geschwisterTage = geschwisterKurse.flatMap { tage.getValue(it) }.toSet()
            return kurse.sortedWith(
                compareBy<Kurs> { k -> if (getrennt && k.id in geschwisterKurse) 1 else 0 }
                    .thenByDescending { k -> tage.getValue(k.id).count { it in geschwisterTage } }
                    .thenBy { k -> abstand(k, kinder) }
                    .thenBy { k -> if (k.minAlter != null && k.maxAlter != null) k.maxAlter - k.minAlter else Int.MAX_VALUE }
                    .thenByDescending { k -> frei.getValue(k.id) }
                    .thenBy { k -> k.ersterTag ?: LocalDate.MAX }
                    .thenBy { k -> k.name },
            )
        }

        fun setzen(kind: Kind, kurs: Kurs) {
            neu.getValue(kurs.id).add(kind.id)
            frei[kurs.id] = frei.getValue(kurs.id) - 1
            alter(kind, kurs)?.let { alterImKurs.getValue(kurs.id).add(it) }
            familienKurse.getOrPut(kind.familienSchluessel) { mutableListOf() }.add(kurs.id)
        }

        for (familie in familien) {
            val schluessel = familie.first().familienSchluessel
            val mitKurs = familie.filter { passend.getValue(it.id).isNotEmpty() }
            for (k in familie - mitKurs.toSet()) warte += k.id to ablehnung(k, bestand, heute)

            val getrennt = familie.any { it.geschwisterGetrennt }
            val gemeinsam = if (mitKurs.size > 1 && !getrennt) {
                bestand.kurse.filter { kurs ->
                    frei.getValue(kurs.id) >= mitKurs.size && mitKurs.all { kurs in passend.getValue(it.id) } && spannePasst(kurs, mitKurs)
                }
            } else emptyList()
            if (gemeinsam.isNotEmpty()) {
                val kurs = rangfolge(gemeinsam, schluessel, mitKurs).first()
                mitKurs.forEach { setzen(it, kurs) }
                continue
            }
            // Wählerische Kinder (wenige passende Kurse) zuerst, damit Geschwister sich keine Plätze wegnehmen.
            for (k in mitKurs.sortedBy { passend.getValue(it.id).size }) {
                val mitPlatz = passend.getValue(k.id).filter { frei.getValue(it.id) > 0 }
                val offen = mitPlatz.filter { spannePasst(it, listOf(k)) }
                when {
                    mitPlatz.isEmpty() -> warte += k.id to "alle passenden Kurse voll"
                    offen.isEmpty() -> warte += k.id to "kein Platz in einer passenden Altersgruppe"
                    else -> setzen(k, rangfolge(offen, schluessel, listOf(k), getrennt).first())
                }
            }
        }
        return Vorschlag(neu.filterValues { it.isNotEmpty() }, warte)
    }

    private fun ablehnung(kind: Kind, bestand: Bestand, heute: LocalDate): String {
        if (bestand.kurse.isEmpty()) return "keine Kurse angelegt"
        val gruende = bestand.kurse.mapNotNull { grund(kind, it, bestand.einstellungen, heute) }.distinct()
        return "kein passender Kurs: " + gruende.joinToString(", ")
    }

    fun uebernehmen(bestand: Bestand, vorschlag: Vorschlag): Bestand = bestand.copy(
        kurse = bestand.kurse.map { k ->
            val dazu = vorschlag.zuteilung[k.id].orEmpty().filter { it !in k.teilnehmer && bestand.kursVon(it) == null }
            k.copy(teilnehmer = (k.teilnehmer + dazu).take(maxOf(k.plaetze, k.teilnehmer.size)))
        },
    )
}
