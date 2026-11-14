package de.dachapp.vereinsmeisterschaft.logic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Verbindung zur Schwimmgruppen-Verwaltung, ohne Code-Abhängigkeit (Unter-Apps kennen einander nicht):
 * - hin: Mitglieder aus deren Speicherdatei (schwimmgruppen.json) lesen, mit Geburtsjahr, Geschlecht und Gruppe,
 * - zurück: eine DSV-Vereinsergebnisliste schreiben, die Schwimmgruppen unter Wettkampfmeldungen › „Ergebnisse
 *   übernehmen“ schon lesen kann (Zuordnung über DSV-ID oder Name + Jahrgang). Es sind die echten, nicht
 *   hochgerechneten Zeiten; die Strecken heißen 17 m / 33 m / 50 m, der Anlass trägt „16⅔-m-Becken“.
 */
object Querverbindung {

    @Serializable
    data class SgMitglied(
        val id: String = "",
        val vorname: String = "",
        val nachname: String = "",
        val geburtsdatum: String? = null,
        val geschlecht: String = "",
        val dsvId: String = "",
        val gruppen: Set<String> = emptySet(),
        val aktiv: Boolean = true,
    )

    @Serializable
    data class SgGruppe(val id: String = "", val name: String = "")

    @Serializable
    data class SgBestand(val mitglieder: List<SgMitglied> = emptyList(), val gruppen: List<SgGruppe> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** Ein Mitglied, wie es zur Auswahl angezeigt wird. [schonDa] = steht schon in der Teilnehmerliste. */
    data class Kandidat(
        val id: String,
        val vorname: String,
        val nachname: String,
        val jahrgang: Int?,
        val geschlecht: Geschlecht?,
        val gruppe: String,
        val dsvId: String,
        val schonDa: Boolean,
    ) {
        val name: String get() = "$vorname $nachname".trim()
    }

    /** Aktive Mitglieder aus dem Inhalt von schwimmgruppen.json. */
    fun mitglieder(text: String, v: Veranstaltung): List<Kandidat> {
        val b = json.decodeFromString(SgBestand.serializer(), text)
        val gruppen = b.gruppen.associate { it.id to it.name }
        return b.mitglieder.filter { it.aktiv && (it.vorname.isNotBlank() || it.nachname.isNotBlank()) }.map { m ->
            val jg = m.geburtsdatum?.take(4)?.toIntOrNull()
            val g = when (m.geschlecht.lowercase()) { "w" -> Geschlecht.W; "m" -> Geschlecht.M; else -> null }
            val schonDa = v.teilnehmer.any { t ->
                (t.mitgliedId.isNotEmpty() && t.mitgliedId == m.id) ||
                    (Import.norm(t.vorname) == Import.norm(m.vorname) && Import.norm(t.nachname) == Import.norm(m.nachname) && (jg == null || t.jahrgang == jg))
            }
            Kandidat(m.id, m.vorname.trim(), m.nachname.trim(), jg, g, m.gruppen.mapNotNull { gruppen[it] }.joinToString(", "), m.dsvId, schonDa)
        }.sortedWith(compareBy({ it.nachname.lowercase() }, { it.vorname.lowercase() }))
    }

    /** Gewählte Mitglieder übernehmen; ohne Geburtsjahr geht es nicht (sie werden übersprungen). */
    fun uebernehmen(v: Veranstaltung, auswahl: List<Kandidat>): ImportBericht {
        var id = v.naechsteId
        val hinweise = ArrayList<String>()
        val neu = auswahl.filter { !it.schonDa }.mapNotNull { k ->
            if (k.jahrgang == null) { hinweise += "${k.name}: Geburtsdatum fehlt in den Schwimmgruppen, übersprungen."; return@mapNotNull null }
            Teilnehmer(id++, k.vorname, k.nachname, k.jahrgang, k.geschlecht, gruppe = k.gruppe, mitgliedId = k.id, dsvId = k.dsvId)
        }
        val ohneG = neu.count { it.geschlecht == null }
        if (ohneG > 0) hinweise += "Bei $ohneG fehlt das Geschlecht, bitte nachtragen."
        return ImportBericht(v.copy(teilnehmer = v.teilnehmer + neu, naechsteId = id), neu.size, 0, hinweise)
    }

    private val TECHNIK = mapOf(Lage.BRUST to "B", Lage.RUECKEN to "R", Lage.KRAUL to "F", Lage.DELPHIN to "S")

    /** Meter einer Strecke, gerundet (1 Bahn = 17 m, 2 = 33 m, 3 = 50 m bei 16⅔ m). */
    fun meter(r: Regeln, s: Strecke): Int = Math.round(r.bahnLaenge * s.bahnen).toInt()

    fun beckenText(r: Regeln): String {
        val l = r.bahnLaenge
        return if (kotlin.math.abs(l - 50.0 / 3) < 0.01) "16⅔-m-Becken" else "${"%.1f".format(l).replace('.', ',').removeSuffix(",0")}-m-Becken"
    }

    private fun f(s: String) = s.replace(";", ",").replace("\n", " ").trim()

    private fun zeit(h: Int): String = "%02d:%02d:%02d,%02d".format(h / 360_000, h / 6000 % 60, h / 100 % 60, h % 100)

    /** Dateiname nach DSV-Muster, z. B. „2026-05-16-Fischach-Vereinsmeisterschaft-Pr.DSV8“. */
    fun dateiname(v: Veranstaltung): String {
        val tag = v.datum.ifBlank { LocalDate.now().toString() }
        val ort = v.ort.split(' ').lastOrNull().orEmpty().replace(Regex("[^A-Za-z0-9]"), "").take(8)
        return "$tag-$ort-Vereinsmeisterschaft-Pr.DSV8"
    }

    /**
     * Vereinsergebnisliste DSV 8. Ein Wettkampf je Strecke (gemischt), Wertungen je Jahrgangsgruppe und Geschlecht
     * mit den Plätzen der Jahrgangswertung. Nur Schwimmer mit Geschlecht und Ergebnis.
     */
    fun dsv(v: Veranstaltung): String {
        val r = v.regeln
        val wks = Laufeinteilung.wettkaempfe(v).mapIndexed { i, s -> s to i + 1 }.toMap()
        val wertung = Wertung.jahrgaenge(v)
        val datum = runCatching { LocalDate.parse(v.datum) }.getOrNull()
        val z = ArrayList<String>()
        z += "FORMAT:Vereinsergebnisliste;8;"
        z += "ERZEUGER:Dach-App Vereinsmeisterschaft;1.0;keine-antwort@dach-app.invalid;"
        z += "VERANSTALTUNG:${f(v.titel)} · ${beckenText(r)};${f(v.ort)};X;HANDZEIT;"
        z += "ABSCHNITT:1;${datum?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")).orEmpty()};;;"
        wks.forEach { (s, nr) -> z += "WETTKAMPF:$nr;E;1;;${meter(r, s)};${TECHNIK[s.lage]};GL;X;SW;;;" }
        // Wertungs-ID je Strecke und Gruppe.
        var wid = 0
        val ids = HashMap<Pair<Strecke, Gruppe>, Int>()
        for (gw in wertung) for (lw in gw.lagen) {
            val nr = wks[lw.strecke] ?: continue
            val id = ++wid
            ids[lw.strecke to gw.gruppe] = id
            val g = gw.gruppe
            val min = if (g.aelter <= 1900) 0 else g.aelter
            z += "WERTUNG:$nr;E;$id;JG;$min;${g.juenger};${g.geschlecht.name};${f(g.titel)};"
        }
        z += "VEREIN:${f(v.verein.substringBefore('·').trim())};${v.vereinsnummer.filter(Char::isDigit).ifEmpty { "0" }};0;GER;N;"
        var pid = 0
        for (gw in wertung) for (zeile in gw.gesamt) {
            val t = zeile.teilnehmer
            val erg = v.starts(t).mapNotNull { s -> v.ergebnis(t.id, s.lage)?.let { s to it } }
            if (erg.isEmpty()) continue
            val p = ++pid
            z += "PERSON:${f(t.nachname)}, ${f(t.vorname)};${t.dsvId.filter(Char::isDigit)};$p;${t.geschlecht!!.name};${t.jahrgang};;GER;;;"
            for ((s, e) in erg) {
                val nr = wks[s] ?: continue
                val platz = gw.lagen.firstOrNull { it.strecke == s }?.zeilen?.firstOrNull { it.teilnehmer.id == t.id }?.platz
                val grund = when (e.status) { Status.OK -> if (e.zeit == null) "NA" else ""; Status.NA -> "NA"; Status.DSQ -> "DS"; Status.AUFG -> "AB" }
                val zt = if (e.gueltig) zeit(e.zeit!!) else "00:00:00,00"
                z += "PERSONENERGEBNIS:$p;$nr;E;${ids[s to gw.gruppe] ?: 0};${platz ?: 0};$zt;$grund;;;"
            }
        }
        z += "DATEIENDE"
        return z.joinToString("\r\n") + "\r\n"
    }
}
