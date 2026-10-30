package de.dachapp.schwimmleitung.logic

import java.time.LocalDate

/** Eine Kennzahl mit Vorjahreswert (leer, wenn es keinen sinnvollen gibt). */
data class Zeile(val text: String, val wert: String, val vorjahr: String = "")

/** Ein Balken einer kleinen Monatsgrafik. [anteil] 0..1 bezogen auf den größten Wert. */
data class Balken(val beschriftung: String, val anteil: Double, val wert: String)

data class Teil(
    val titel: String,
    val zeilen: List<Zeile> = emptyList(),
    val balkenTitel: String = "",
    val balken: List<Balken> = emptyList(),
    val hinweis: String = "",
)

/** Sitzungsbericht: Kennzahlen des Zeitraums mit Vorjahresvergleich, für PDF und Protokoll. */
data class Bericht(
    val verein: String,
    val zeitraum: Zeitraum,
    val vergleich: Zeitraum,
    val erstellt: LocalDate,
    val teile: List<Teil>,
    /** Woher die Zahlen stammen und wie aktuell sie sind. */
    val quellen: List<String>,
) {
    val titel: String get() = "Leitungsbericht ${zeitraum.name}"
    val untertitel: String get() = "${Format.datum(zeitraum.von)} bis ${Format.datum(zeitraum.bis)}, Vergleich ${Format.datum(vergleich.von)} bis ${Format.datum(vergleich.bis)}"
}

object Berichte {

    fun erstellen(d: LeitungDaten, z: Zeitraum, heute: LocalDate): Bericht {
        val v = z.vorjahr()
        val teile = mutableListOf<Teil>()
        val quellen = mutableListOf<String>()

        d.kurs?.let { a ->
            quellen += "Schwimmkurs: Stand ${Format.datum(Format.tag(a.importiert))}"
            val k = Kennzahlen.kurse(a, z)
            val kv = Kennzahlen.kurse(a, v)
            val w = Kennzahlen.warteliste(a, heute)
            val zeilen = mutableListOf(
                Zeile("Kurse", "${k.kurse}", "${kv.kurse}"),
                Zeile("Plätze / belegt", "${k.plaetze} / ${k.belegt}", "${kv.plaetze} / ${kv.belegt}"),
                Zeile("Auslastung", prozent(k.auslastung), prozent(kv.auslastung)),
                Zeile("Neue Anmeldungen", "${k.neueAnmeldungen}", "${kv.neueAnmeldungen}"),
                Zeile("Wartezeit bis zum Kursplatz (Median)", wochen(k.wartezeitWochen), wochen(kv.wartezeitWochen)),
                Zeile("Kursgebühren (erwartet)", Format.euro(k.gebuehrenCent), Format.euro(kv.gebuehrenCent)),
            )
            k.abzeichen.toSortedMap().forEach { (abz, n) -> zeilen += Zeile("Teilnehmer mit $abz", "$n", "${kv.abzeichen[abz] ?: 0}") }
            Herkunft.entries.forEach { h ->
                val n = k.herkunft[h] ?: 0
                val nv = kv.herkunft[h] ?: 0
                if (n > 0 || nv > 0) zeilen += Zeile("Herkunft: ${h.text}", "$n", "$nv")
            }
            zeilen += Zeile("Warteliste heute (davon im Mindestalter)", "${w.gesamt} (${w.imMindestalter})")
            if (w.pausiert > 0) zeilen += Zeile("Pausiert", "${w.pausiert}")
            val alter = k.alter.entries.joinToString(", ") { "${it.key} J.: ${it.value}" }
            val orte = k.gemeinden.take(6).joinToString(", ") { "${it.first} ${it.second}" }
            teile += Teil(
                "Schwimmkurse",
                zeilen,
                hinweis = listOfNotNull(
                    alter.ifBlank { null }?.let { "Alter bei Kursbeginn: $it" },
                    orte.ifBlank { null }?.let { "Wohnorte: $it" },
                    "Gezählt werden Kurse mit erstem Termin im Zeitraum.",
                ).joinToString("\n"),
            )
        }

        d.gruppen?.let { a ->
            quellen += "Schwimmgruppen: Stand ${Format.datum(Format.tag(a.importiert))}"
            val g = Kennzahlen.gruppen(a, z, heute)
            val gv = Kennzahlen.gruppen(a, v, heute)
            val zeilen = mutableListOf(
                Zeile("Aktive Schwimmer in Vereinsgruppen", "${g.aktiveSchwimmer}"),
                Zeile("Trainings gehalten (Verein)", "${g.verein.sumOf { it.gehalten }}", "${gv.verein.sumOf { it.gehalten }}"),
                Zeile("Ø Teilnehmer je Training (Verein)", schnitt(g.verein), schnitt(gv.verein)),
                Zeile("Ausgefallen", "${g.gruppen.sumOf { it.ausgefallen }}", "${gv.gruppen.sumOf { it.ausgefallen }}"),
                Zeile("Nicht erfasst", "${g.gruppen.sumOf { it.offen }}"),
            )
            if (g.ohneGruppe > 0) zeilen += Zeile("Aktive ohne Gruppe", "${g.ohneGruppe}")
            for (x in g.verein) {
                val xv = gv.verein.firstOrNull { it.name == x.name }
                val platz = x.plaetze?.let { " / $it Plätze" }.orEmpty()
                zeilen += Zeile("${x.name}: ${x.aktive} Schwimmer$platz, Ø", Format.zahl(x.durchschnitt), xv?.let { Format.zahl(it.durchschnitt) }.orEmpty())
            }
            teile += Teil(
                "Trainingsgruppen",
                zeilen,
                balkenTitel = "Ø Teilnehmer je Training und Monat",
                balken = balken(g.vereinMonate) { it.durchschnitt to Format.zahl(it.durchschnitt) },
            )

            if (g.extern.isNotEmpty()) {
                val ez = mutableListOf<Zeile>()
                for (x in g.extern) {
                    val xv = gv.extern.firstOrNull { it.name == x.name }
                    ez += Zeile("${x.name}: Teilnahmen", "${x.teilnahmen}", "${xv?.teilnahmen ?: 0}")
                    ez += Zeile("${x.name}: Ø je Termin", Format.zahl(x.durchschnitt), xv?.let { Format.zahl(it.durchschnitt) }.orEmpty())
                }
                teile += Teil(
                    "Angebote für Externe",
                    ez,
                    balkenTitel = "Teilnahmen je Monat",
                    balken = balken(g.externMonate) { it.teilnahmen.toDouble() to "${it.teilnahmen}" },
                )
            }

            val tz = mutableListOf<Zeile>()
            g.trainer.forEach { (name, n) -> tz += Zeile(name, "$n Stunden") }
            tz += Zeile("Vertretungen", "${g.vertretungen}", "${gv.vertretungen}")
            if (g.ohneVolljaehrige > 0) tz += Zeile("Stunden ohne volljährigen Trainer", "${g.ohneVolljaehrige}")
            if (g.ohneTrainer > 0) tz += Zeile("Stunden ohne eingetragenen Trainer", "${g.ohneTrainer}")
            teile += Teil("Trainereinsätze", tz)

            val ez = mutableListOf<Zeile>()
            (g.erfolge.keys + gv.erfolge.keys).sorted().forEach { art -> ez += Zeile(art, "${g.erfolge[art] ?: 0}", "${gv.erfolge[art] ?: 0}") }
            ez += Zeile("Verbesserte Zeiten", "${g.verbesserungen}", "${gv.verbesserungen}")
            ez += Zeile("Schwimmer mit Wettkampfinteresse", "${g.wettkampfInteresse}")
            teile += Teil("Erfolge und Zeiten", ez)
        }

        val l = Kennzahlen.leitung(d, z, heute)
        val lz = mutableListOf(
            Zeile("Aufgaben offen (davon überfällig)", "${l.aufgabenOffen} (${l.ueberfaellig})"),
            Zeile("Aufgaben neu / erledigt im Zeitraum", "${l.neu} / ${l.erledigt}"),
            Zeile("Material defekt", "${l.materialDefekt}"),
            Zeile("Offene Meldungen aus der Halle", "${l.meldungenOffen}"),
            Zeile("Vorkommnisse im Zeitraum", "${l.vorkommnisse}"),
        )
        if (l.belege > 0 || l.nichtUebergeben > 0) {
            lz += Zeile("Belege im Zeitraum", "${l.belege}: Ausgaben ${Format.euro(l.ausgabenCent)}, Einnahmen ${Format.euro(l.einnahmenCent)}")
            lz += Zeile("Noch nicht an Kassenwart übergeben", "${l.nichtUebergeben} (${Format.euro(l.nichtUebergebenCent)})")
        }
        val hinweise = mutableListOf<String>()
        l.pruefungFaellig.forEach { hinweise += "Prüfung fällig: ${it.name} bis ${Format.datum(it.pruefen)}" }
        l.nachweiseAbgelaufen.forEach { (p, n) -> hinweise += "Abgelaufen: ${p.name}, ${n.art.text} seit ${Format.datum(n.gueltigBis)}" }
        l.nachweiseBald.forEach { (p, n) -> hinweise += "Läuft ab: ${p.name}, ${n.art.text} am ${Format.datum(n.gueltigBis)}" }
        teile += Teil("Leitung", lz, hinweis = hinweise.joinToString("\n"))

        if (quellen.isEmpty()) quellen += "Noch keine Daten aus Schwimmkurs oder Schwimmgruppen übernommen."
        return Bericht(d.e.verein, z, v, heute, teile, quellen)
    }

    /** Als schlichter Text, z. B. für den Leitungsbericht im Protokoll. */
    fun text(b: Bericht): String = buildString {
        appendLine("${b.titel} (${Format.datum(b.zeitraum.von)} bis ${Format.datum(b.zeitraum.bis)})")
        for (t in b.teile) {
            appendLine()
            appendLine(t.titel)
            for (z in t.zeilen) {
                append("• ${z.text}: ${z.wert}")
                if (z.vorjahr.isNotEmpty() && z.vorjahr != z.wert) append(" (Vorjahr ${z.vorjahr})")
                appendLine()
            }
            if (t.hinweis.isNotBlank()) t.hinweis.lines().forEach { appendLine("  $it") }
        }
        appendLine()
        append(b.quellen.joinToString(" · "))
    }.trim()

    private fun schnitt(l: List<GruppeZahl>): String {
        val n = l.sumOf { it.gehalten }
        return if (n == 0) "–" else Format.zahl(l.sumOf { it.teilnahmen }.toDouble() / n)
    }

    private fun prozent(p: Int?) = p?.let { "$it %" } ?: "–"
    private fun wochen(w: Double?) = w?.let { "${Format.zahl(it)} Wochen" } ?: "–"

    private fun balken(werte: List<MonatWert>, f: (MonatWert) -> Pair<Double, String>): List<Balken> {
        val max = werte.maxOfOrNull { f(it).first }?.takeIf { it > 0 } ?: return emptyList()
        return werte.map { m -> f(m).let { (x, t) -> Balken(Format.monat(m.monat), x / max, t) } }
    }
}

/** Protokoll einer Sitzung als Text und als Abschnitte für das PDF. */
object Protokoll {

    data class Abschnitt(val titel: String, val text: String)

    fun abschnitte(d: LeitungDaten, s: Sitzung): List<Abschnitt> {
        fun namen(ids: List<String>) = ids.mapNotNull { d.person(it)?.name }.sortedWith(LeitungDaten.DEUTSCH)
        val anwesend = (namen(s.anwesend) + s.gaeste.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() })
        val aufgaben = d.sichtbareAufgaben.filter { it.sitzungId == s.id }.sortedBy { it.titel }
        return listOfNotNull(
            Abschnitt(
                "Datum",
                Format.datum(s.datum) + (s.beginn?.let { ", ${it.toString().take(5)} Uhr" } ?: "") + (s.ort.ifBlank { null }?.let { ", $it" } ?: ""),
            ),
            Abschnitt(
                "Anwesenheiten",
                buildString {
                    append("Anwesend (${anwesend.size}): ").append(anwesend.joinToString(", ").ifEmpty { "–" })
                    val ent = namen(s.entschuldigt)
                    if (ent.isNotEmpty()) append("\nEntschuldigt: ").append(ent.joinToString(", "))
                },
            ),
            Abschnitt("Leitungsbericht", listOf(s.leitungsbericht.trim(), s.kennzahlen.trim()).filter { it.isNotEmpty() }.joinToString("\n\n").ifEmpty { "–" }),
            Abschnitt("Offene Runde", s.offeneRunde.trim().ifEmpty { "–" }),
            if (aufgaben.isEmpty()) null else Abschnitt(
                "Aufgaben aus der Sitzung",
                aufgaben.joinToString("\n") { a ->
                    "• ${a.titel}" + d.verantwortlich(a).ifBlank { null }?.let { " – $it" }.orEmpty() +
                        (a.faellig?.let { " – bis ${Format.datum(it)}" } ?: "") + (if (!a.offen) " (erledigt)" else "")
                },
            ),
        )
    }

    fun titel(s: Sitzung) = "Protokoll ${s.art.text} ${Format.datum(s.datum)}"

    fun text(d: LeitungDaten, s: Sitzung): String = buildString {
        appendLine(titel(s))
        for (a in abschnitte(d, s)) {
            appendLine()
            appendLine(a.titel.uppercase())
            appendLine(a.text)
        }
        if (s.protokollant.isNotBlank()) {
            appendLine()
            append("Protokoll: ${s.protokollant}")
        }
    }.trim()
}

/** Listen als CSV mit Semikolon und BOM, öffnen sich in Excel direkt richtig. */
object Listen {
    private fun csv(zeilen: List<List<String>>): String {
        fun z(s: String) = if (s.any { it == ';' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        return "﻿" + zeilen.joinToString("\r\n") { it.joinToString(";") { s -> z(s) } } + "\r\n"
    }

    private fun betrag(cent: Long) = (cent / 100).toString() + "," + (cent % 100).toString().padStart(2, '0')

    fun belege(belege: List<Beleg>): String = csv(
        listOf(listOf("Datum", "Art", "Betrag", "Zweck", "Von", "Foto", "Übergeben am")) +
            belege.sortedBy { it.datum }.map { b ->
                listOf(
                    Format.datum(b.datum), if (b.einnahme) "Einnahme" else "Ausgabe",
                    (if (b.einnahme) "" else "-") + betrag(b.betragCent), b.zweck, b.von, if (b.foto != null) "ja" else "",
                    Format.datum(b.uebergebenAm),
                )
            },
    )

    fun aufgaben(d: LeitungDaten): String = csv(
        listOf(listOf("Aufgabe", "Verantwortlich", "Fällig", "Status", "Erstellt", "Erledigt am", "Sitzung", "Notiz")) +
            d.sichtbareAufgaben.sortedWith(compareBy({ !it.offen }, { it.faellig ?: LocalDate.MAX })).map { a ->
                listOf(
                    a.titel, d.verantwortlich(a), Format.datum(a.faellig), a.status.text, Format.datum(a.erstellt), Format.datum(a.erledigtAm),
                    d.sitzung(a.sitzungId)?.let { Protokoll.titel(it) }.orEmpty(), a.notiz,
                )
            },
    )

    fun nachweise(d: LeitungDaten): String = csv(
        listOf(listOf("Name", "Rolle", "Nachweis", "Bezeichnung", "Ausgestellt", "Gültig bis")) +
            d.allePersonen.flatMap { p ->
                d.nachweiseVon(p.id).map { n -> listOf(p.name, p.rolle, n.art.text, n.bezeichnung, Format.datum(n.ausgestellt), Format.datum(n.gueltigBis)) }
            },
    )

    fun material(d: LeitungDaten): String = csv(
        listOf(listOf("Material", "Ort", "Anzahl", "Zustand", "Nächste Prüfung", "Notiz")) +
            d.sichtbaresMaterial.map { m -> listOf(m.name, m.ort, m.anzahl.toString(), m.zustand.text, Format.datum(m.pruefen), m.notiz) },
    )
}
