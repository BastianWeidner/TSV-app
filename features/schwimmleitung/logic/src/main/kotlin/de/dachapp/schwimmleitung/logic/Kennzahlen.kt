package de.dachapp.schwimmleitung.logic

import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Auswertungszeitraum, beide Tage einschließlich. */
data class Zeitraum(val von: LocalDate, val bis: LocalDate, val name: String) {
    operator fun contains(d: LocalDate?) = d != null && !d.isBefore(von) && !d.isAfter(bis)

    /** Derselbe Zeitraum ein Jahr früher, zum Vergleich. */
    fun vorjahr(): Zeitraum = Zeitraum(von.minusYears(1), bis.minusYears(1), "Vorjahr")

    companion object {
        /** Saison von [beginnMonat] bis zum Monat davor im Folgejahr, z. B. September bis August. */
        fun saison(tag: LocalDate, beginnMonat: Int = 9): Zeitraum {
            val startJahr = if (tag.monthValue >= beginnMonat) tag.year else tag.year - 1
            val von = LocalDate.of(startJahr, beginnMonat, 1)
            val bis = von.plusYears(1).minusDays(1)
            val name = if (beginnMonat == 1) "Jahr $startJahr" else "Saison $startJahr/${(startJahr + 1) % 100}"
            return Zeitraum(von, bis, name)
        }

        fun monat(tag: LocalDate): Zeitraum {
            val m = YearMonth.from(tag)
            return Zeitraum(m.atDay(1), m.atEndOfMonth(), m.month.getDisplayName(TextStyle.FULL, Locale.GERMAN) + " " + m.year)
        }

        fun quartal(tag: LocalDate): Zeitraum {
            val q = (tag.monthValue - 1) / 3
            val von = LocalDate.of(tag.year, q * 3 + 1, 1)
            return Zeitraum(von, von.plusMonths(3).minusDays(1), "${q + 1}. Quartal ${tag.year}")
        }

        fun jahr(tag: LocalDate): Zeitraum = Zeitraum(LocalDate.of(tag.year, 1, 1), LocalDate.of(tag.year, 12, 31), "Jahr ${tag.year}")

        /** Seit der letzten Sitzung (Tag danach) bis heute. */
        fun seit(letzte: LocalDate, heute: LocalDate): Zeitraum =
            Zeitraum(letzte.plusDays(1), heute, "seit der Sitzung am ${Format.datum(letzte)}")
    }
}

data class KursZahlen(
    val kurse: Int,
    val plaetze: Int,
    val belegt: Int,
    val neueAnmeldungen: Int,
    /** Median in Wochen von der Anmeldung bis zum ersten Kurstag. */
    val wartezeitWochen: Double?,
    val herkunft: Map<Herkunft, Int>,
    /** Gemeinden der Teilnehmer, häufigste zuerst. */
    val gemeinden: List<Pair<String, Int>>,
    /** Alter bei Kursbeginn → Anzahl. */
    val alter: Map<Int, Int>,
    val gebuehrenCent: Long,
    /** Abzeichen (Kursziel) → Teilnehmer, die es laut Kursliste haben. */
    val abzeichen: Map<String, Int>,
) {
    val auslastung: Int? get() = if (plaetze == 0) null else (100.0 * belegt / plaetze).toInt()
}

/** Stand der Warteliste heute, unabhängig vom Zeitraum. */
data class Warteliste(val gesamt: Int, val imMindestalter: Int, val pausiert: Int)

data class GruppeZahl(
    val name: String,
    val extern: Boolean,
    val aktive: Int,
    val plaetze: Int?,
    val gehalten: Int,
    val ausgefallen: Int,
    /** Planmäßige Trainings ohne Eintrag (vergangen). */
    val offen: Int,
    val teilnahmen: Int,
    val hoechstens: Int,
) {
    val durchschnitt: Double get() = if (gehalten == 0) 0.0 else teilnahmen.toDouble() / gehalten
}

data class MonatWert(val monat: YearMonth, val trainings: Int, val teilnahmen: Int) {
    val durchschnitt: Double get() = if (trainings == 0) 0.0 else teilnahmen.toDouble() / trainings
}

data class GruppenZahlen(
    val gruppen: List<GruppeZahl>,
    val aktiveSchwimmer: Int,
    val ohneGruppe: Int,
    val vereinMonate: List<MonatWert>,
    val externMonate: List<MonatWert>,
    /** Trainername → geleitete Stunden. */
    val trainer: List<Pair<String, Int>>,
    val vertretungen: Int,
    val ohneVolljaehrige: Int,
    val ohneTrainer: Int,
    /** Art → Anzahl (Abzeichen, Wettkampf, Sonstiges). */
    val erfolge: Map<String, Int>,
    /** Zeiten, die eine frühere Zeit desselben Schwimmers auf derselben Strecke unterbieten. */
    val verbesserungen: Int,
    val wettkampfInteresse: Int,
) {
    val verein get() = gruppen.filter { !it.extern }
    val extern get() = gruppen.filter { it.extern }
}

data class LeitungZahlen(
    val aufgabenOffen: Int,
    val ueberfaellig: Int,
    val neu: Int,
    val erledigt: Int,
    val materialDefekt: Int,
    val meldungenOffen: Int,
    val vorkommnisse: Int,
    val pruefungFaellig: List<Material>,
    val nachweiseBald: List<Pair<Person, Nachweis>>,
    val nachweiseAbgelaufen: List<Pair<Person, Nachweis>>,
    val ausgabenCent: Long,
    val einnahmenCent: Long,
    val belege: Int,
    val nichtUebergeben: Int,
    val nichtUebergebenCent: Long,
)

object Kennzahlen {

    // ---------- Schwimmkurs ----------

    fun kurse(a: KursAuszug, z: Zeitraum): KursZahlen {
        val kurse = a.kurse.filter { it.ersterTag in z }
        val teilnahmen = kurse.flatMap { k -> k.teilnehmer.mapNotNull { id -> a.kind(id)?.let { k to it } } }
        val warten = teilnahmen.mapNotNull { (k, kind) ->
            val an = kind.anmeldedatum ?: return@mapNotNull null
            val start = k.ersterTag ?: return@mapNotNull null
            ChronoUnit.DAYS.between(an, start).takeIf { it >= 0 }?.let { it / 7.0 }
        }
        val abzeichen = mutableMapOf<String, Int>()
        for ((k, kind) in teilnahmen) {
            val ziel = k.ziel?.takeIf { it.isNotBlank() } ?: "Seepferdchen"
            if (kind.abzeichen.any { it.equals(ziel, ignoreCase = true) }) abzeichen[ziel] = (abzeichen[ziel] ?: 0) + 1
        }
        return KursZahlen(
            kurse = kurse.size,
            plaetze = kurse.sumOf { it.plaetze },
            belegt = kurse.sumOf { it.teilnehmer.size },
            neueAnmeldungen = a.kinder.count { it.anmeldedatum in z },
            wartezeitWochen = median(warten),
            herkunft = teilnahmen.groupingBy { it.second.herkunft }.eachCount(),
            gemeinden = teilnahmen.map { it.second.gemeinde.ifBlank { "ohne Ortsangabe" } }
                .groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { it.key to it.value },
            alter = teilnahmen.mapNotNull { (k, kind) ->
                val g = kind.geburtsdatum ?: return@mapNotNull null
                val start = k.ersterTag ?: return@mapNotNull null
                ChronoUnit.YEARS.between(g, start).toInt()
            }.groupingBy { it }.eachCount().toSortedMap(),
            gebuehrenCent = kurse.sumOf { it.preisProKindCent * it.teilnehmer.size },
            abzeichen = abzeichen,
        )
    }

    fun warteliste(a: KursAuszug, heute: LocalDate): Warteliste {
        val belegt = a.kurse.flatMap { it.teilnehmer }.toSet()
        val ohne = a.kinder.filter { it.id !in belegt }
        val wartend = ohne.filter { !it.pausiert }
        return Warteliste(
            gesamt = wartend.size,
            imMindestalter = wartend.count { k -> k.geburtsdatum?.let { ChronoUnit.YEARS.between(it, heute) >= a.mindestalter } == true },
            pausiert = ohne.count { it.pausiert },
        )
    }

    // ---------- Trainingsgruppen ----------

    /**
     * Trainings im Zeitraum: Termine aus dem Wochenplan, zusammengeführt mit den erfassten Einheiten. Der Plan zählt
     * erst ab dem ersten erfassten Training, damit die Zeit vor Einführung der App nicht als "nicht erfasst" erscheint.
     */
    fun trainings(a: GruppenAuszug, z: Zeitraum): List<EinheitA> {
        val gespeichert = a.einheiten.filter { it.datum in z && a.gruppe(it.gruppeId) != null }
            .associateBy { Triple(it.gruppeId, it.datum, it.beginn) }
        val start = a.einheiten.minOfOrNull { it.datum } ?: return gespeichert.values.toList()
        val liste = mutableMapOf<Triple<String, LocalDate, java.time.LocalTime>, EinheitA>()
        var d = maxOf(z.von, start)
        while (!d.isAfter(z.bis)) {
            for (g in a.gruppen) for (zp in g.zeiten) {
                if (zp.wochentag != d.dayOfWeek.value) continue
                val k = Triple(g.id, d, zp.beginn)
                liste[k] = gespeichert[k] ?: EinheitA(g.id, d, zp.beginn)
            }
            d = d.plusDays(1)
        }
        for ((k, e) in gespeichert) liste.putIfAbsent(k, e)
        return liste.values.sortedWith(compareBy({ it.datum }, { it.beginn }))
    }

    fun gruppen(a: GruppenAuszug, z: Zeitraum, heute: LocalDate): GruppenZahlen {
        val alle = trainings(a, z)
        val vergangen = alle.filter { !it.datum.isAfter(heute) }
        val aktiv = a.mitglieder.filter { it.aktiv }
        val gruppen = a.gruppen.map { g ->
            val eig = alle.filter { it.gruppeId == g.id }
            val erfasst = eig.filter { it.erfasst }
            GruppeZahl(
                name = g.name,
                extern = g.extern,
                aktive = aktiv.count { g.id in it.gruppen },
                plaetze = g.plaetze,
                gehalten = erfasst.size,
                ausgefallen = eig.count { it.ausgefallen },
                offen = eig.count { !it.ausgefallen && it.anzahl == 0 && it.datum.isBefore(heute) },
                teilnahmen = erfasst.sumOf { it.anzahl },
                hoechstens = erfasst.maxOfOrNull { it.anzahl } ?: 0,
            )
        }
        fun monate(extern: Boolean) = alle.filter { it.erfasst && a.gruppe(it.gruppeId)?.extern == extern }
            .groupBy { YearMonth.from(it.datum) }.toSortedMap()
            .map { (m, l) -> MonatWert(m, l.size, l.sumOf { it.anzahl }) }

        val stattgefunden = vergangen.filter { !it.ausgefallen }
        val einsaetze = stattgefunden.flatMap { e -> a.trainerVon(e).map { it.name } }.groupingBy { it }.eachCount()
        val ohneVolljaehrige = stattgefunden.count { e ->
            val t = a.trainerVon(e)
            t.isNotEmpty() && t.all { tr -> tr.geburtsdatum?.let { ChronoUnit.YEARS.between(it, e.datum) < 18 } == true }
        }
        val erfolge = a.mitglieder.flatMap { it.erfolge }.filter { it.datum in z }
            .groupingBy { ERFOLG_ARTEN[it.art] ?: it.art }.eachCount()
        return GruppenZahlen(
            gruppen = gruppen,
            aktiveSchwimmer = aktiv.count { m -> m.gruppen.any { id -> a.gruppe(id)?.extern == false } },
            ohneGruppe = aktiv.count { it.gruppen.none { id -> a.gruppe(id) != null } },
            vereinMonate = monate(false),
            externMonate = monate(true),
            trainer = einsaetze.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key to it.value },
            vertretungen = stattgefunden.sumOf { e -> e.vertretungen.size },
            ohneVolljaehrige = ohneVolljaehrige,
            ohneTrainer = stattgefunden.count { a.trainerVon(it).isEmpty() && it.erfasst },
            erfolge = erfolge,
            verbesserungen = a.mitglieder.sumOf { verbesserungen(it, z) },
            wettkampfInteresse = aktiv.count { it.wettkampf },
        )
    }

    private val ERFOLG_ARTEN = mapOf("ABZEICHEN" to "Abzeichen", "WETTKAMPF" to "Wettkampf-Platzierungen", "SONSTIGES" to "Sonstige Erfolge")

    /** Zeiten im Zeitraum, die eine frühere Zeit desselben Schwimmers auf derselben Strecke unterbieten. */
    fun verbesserungen(m: MitgliedA, z: Zeitraum): Int =
        m.zeiten.filter { it.datum != null }.groupBy { it.strecke.trim().lowercase() }.values.sumOf { liste ->
            var beste: Int? = null
            var zahl = 0
            for (t in liste.sortedBy { it.datum }) {
                val b = beste
                if (b != null && t.hundertstel < b && t.datum in z) zahl++
                if (b == null || t.hundertstel < b) beste = t.hundertstel
            }
            zahl
        }

    // ---------- Leitung ----------

    fun leitung(d: LeitungDaten, z: Zeitraum, heute: LocalDate): LeitungZahlen {
        val aufgaben = d.sichtbareAufgaben
        val nachweise = d.allePersonen.filter { it.aktiv }.flatMap { p -> d.nachweiseVon(p.id).map { p to it } }
        val grenze = heute.plusDays(d.e.warnTage.toLong())
        val belege = d.sichtbareBelege.filter { it.datum in z }
        val offen = d.sichtbareBelege.filter { !it.uebergeben }
        return LeitungZahlen(
            aufgabenOffen = aufgaben.count { it.offen },
            ueberfaellig = aufgaben.count { it.ueberfaellig(heute) },
            neu = aufgaben.count { it.erstellt in z },
            erledigt = aufgaben.count { it.status == AufgabeStatus.ERLEDIGT && it.erledigtAm in z },
            materialDefekt = d.sichtbaresMaterial.count { it.zustand == Zustand.DEFEKT },
            meldungenOffen = d.sichtbareMeldungen.count { !it.erledigt },
            vorkommnisse = d.sichtbareMeldungen.count { it.art == MeldungArt.VORKOMMNIS && Format.tag(it.zeit) in z },
            pruefungFaellig = d.sichtbaresMaterial.filter { it.zustand != Zustand.AUSGESONDERT && it.pruefen != null && !it.pruefen.isAfter(heute.plusDays(30)) }
                .sortedBy { it.pruefen },
            nachweiseBald = nachweise.filter { (_, n) -> n.gueltigBis != null && !n.gueltigBis.isBefore(heute) && !n.gueltigBis.isAfter(grenze) }
                .sortedBy { it.second.gueltigBis },
            nachweiseAbgelaufen = nachweise.filter { (_, n) -> n.gueltigBis != null && n.gueltigBis.isBefore(heute) }
                .sortedBy { it.second.gueltigBis },
            ausgabenCent = belege.filter { !it.einnahme }.sumOf { it.betragCent },
            einnahmenCent = belege.filter { it.einnahme }.sumOf { it.betragCent },
            belege = belege.size,
            nichtUebergeben = offen.size,
            nichtUebergebenCent = offen.sumOf { if (it.einnahme) it.betragCent else -it.betragCent },
        )
    }

    private fun median(werte: List<Double>): Double? {
        if (werte.isEmpty()) return null
        val s = werte.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }
}

/** Zahlen und Daten deutsch geschrieben. */
object Format {
    private val DATUM = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun datum(d: LocalDate?): String = d?.format(DATUM).orEmpty()

    /** Datum aus "21.10.2026", "21.10.26", "1.2.2027" oder "2026-10-21". null = leer oder ungültig. */
    fun datumLesen(text: String): LocalDate? {
        val t = text.trim()
        Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})").matchEntire(t)?.let { m ->
            return runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
        }
        val m = Regex("(\\d{1,2})[./](\\d{1,2})[./](\\d{2}|\\d{4})").matchEntire(t) ?: return null
        val j = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
        return runCatching { LocalDate.of(j, m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
    }

    fun tag(ms: Long): LocalDate = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

    fun euro(cent: Long): String {
        val neg = cent < 0
        val c = kotlin.math.abs(cent)
        val euro = (c / 100).toString().reversed().chunked(3).joinToString(".").reversed()
        return (if (neg) "−" else "") + euro + "," + (c % 100).toString().padStart(2, '0') + " €"
    }

    /** Betrag aus Eingabe wie "12,50", "12.50", "1.234,5" oder "12". null = ungültig. */
    fun cent(text: String): Long? {
        var t = text.replace("€", "").replace(" ", "").trim()
        if (t.isEmpty()) return null
        if (t.contains(',')) t = t.replace(".", "").replace(',', '.')
        else if (t.count { it == '.' } > 1 || Regex("\\.\\d{3}$").containsMatchIn(t)) t = t.replace(".", "")
        val v = t.toBigDecimalOrNull() ?: return null
        if (v.signum() < 0) return null
        return v.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }

    fun zahl(x: Double, stellen: Int = 1): String = "%.${stellen}f".format(Locale.GERMAN, x)

    fun monat(m: YearMonth): String = m.month.getDisplayName(TextStyle.SHORT, Locale.GERMAN).trimEnd('.') + " " + (m.year % 100).toString().padStart(2, '0')

    fun monatLang(m: Month): String = m.getDisplayName(TextStyle.FULL, Locale.GERMAN)
}
