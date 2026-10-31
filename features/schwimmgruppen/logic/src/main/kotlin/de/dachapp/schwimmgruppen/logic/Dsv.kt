package de.dachapp.schwimmgruppen.logic

import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

class WettkampfFehler(message: String) : Exception(message)

/**
 * DSV-Standard „Format 7“ (gültig bis 31.12.2026) und „Format 8“ (ab 01.08.2026): Textdateien mit einem Element je
 * Zeile, "NAME:Attribut;Attribut;…;". Gelesen werden Wettkampfdefinitionsliste (Ausschreibung) sowie Wettkampf- und
 * Vereinsergebnisliste, geschrieben wird die Vereinsmeldeliste. Beschreibung: dsv.de, Formulare Schwimmen.
 */
object Dsv {
    data class Element(val name: String, val werte: List<String>) {
        operator fun get(i: Int): String = werte.getOrNull(i)?.trim().orEmpty()
    }

    private val DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val elementAnfang = Regex("^([A-Z]+):(.*)$")

    /** Bytes einer .DSV7/.DSV8-Datei oder des gepackten .DSV8z-Archivs als Text. */
    fun text(bytes: ByteArray): String {
        if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (!e.isDirectory) return TabellenLeser.text(zip.readBytes())
                }
            }
            throw WettkampfFehler("Das ZIP-Archiv enthält keine DSV-Datei.")
        }
        return TabellenLeser.text(bytes)
    }

    fun istDsv(text: String): Boolean = text.trimStart().uppercase().startsWith("FORMAT:")

    /** Elemente der Datei; Kommentare (* … *) fallen weg, umbrochene Zeilen werden zusammengesetzt. */
    fun elemente(text: String): List<Element> {
        val zeilen = mutableListOf<String>()
        for (roh in text.lines()) {
            val z = roh.replace(Regex("\\(\\*.*?\\*\\)"), "").trim()
            if (z.isEmpty()) continue
            if (elementAnfang.matches(z) || z == "DATEIENDE" || zeilen.isEmpty()) zeilen += z else zeilen[zeilen.lastIndex] = zeilen.last() + z
        }
        return zeilen.mapNotNull { z ->
            if (z == "DATEIENDE") return@mapNotNull Element("DATEIENDE", emptyList())
            val m = elementAnfang.find(z) ?: return@mapNotNull null
            // Das letzte Semikolon schließt das Element ab und ergibt kein eigenes Attribut.
            Element(m.groupValues[1], m.groupValues[2].removeSuffix(";").split(';'))
        }
    }

    private fun format(el: List<Element>): Pair<String, Int> {
        val f = el.firstOrNull { it.name == "FORMAT" } ?: throw WettkampfFehler("Keine DSV-Datei: das FORMAT-Element fehlt.")
        return f[0].lowercase() to (f[1].toIntOrNull() ?: 7)
    }

    fun zeit(t: String): Int? {
        val m = Regex("^(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,2})$").find(t.trim()) ?: return null
        val (h, min, s, hs) = m.destructured
        return ((h.toInt() * 60 + min.toInt()) * 60 + s.toInt()) * 100 + (if (hs.length == 1) hs.toInt() * 10 else hs.toInt())
    }

    fun zeitText(hundertstel: Int): String {
        val h = hundertstel / 360_000
        val m = hundertstel / 6000 % 60
        val s = hundertstel / 100 % 60
        return "%02d:%02d:%02d,%02d".format(h, m, s, hundertstel % 100)
    }

    private fun datum(t: String): LocalDate? = runCatching { LocalDate.parse(t.trim(), DATUM) }.getOrNull()
    private fun uhrzeit(t: String): LocalTime? = runCatching { LocalTime.parse(t.trim()) }.getOrNull()

    private fun wk(e: Element, mitBestenliste: Boolean) = WkDef(
        nr = e[0].toIntOrNull() ?: 0, art = e[1], abschnitt = e[2].toIntOrNull() ?: 0, starter = e[3].toIntOrNull() ?: 1,
        strecke = e[4].toIntOrNull() ?: 0, technik = e[5], ausuebung = e[6], geschlecht = e[7],
        bestenliste = if (mitBestenliste) e[8] else "", qualiNr = if (mitBestenliste) e[9] else e[8], qualiArt = if (mitBestenliste) e[10] else e[9],
    )

    fun ausschreibung(text: String): Ausschreibung {
        val el = elemente(text)
        val (art, version) = format(el)
        if (art != "wettkampfdefinitionsliste") throw WettkampfFehler("Das ist keine Ausschreibung (Wettkampfdefinitionsliste), sondern: ${el.first { it.name == "FORMAT" }[0]}.")
        val v = el.first { it.name == "VERANSTALTUNG" }
        val schluss = el.firstOrNull { it.name == "MELDESCHLUSS" }
        val adresse = el.firstOrNull { it.name == "MELDEADRESSE" }
        return Ausschreibung(
            version = version,
            veranstaltung = v[0], ort = v[1], bahn = v[2], zeitmessung = v[3],
            meldeschluss = schluss?.let { "${it[0]} ${it[1]} Uhr" }.orEmpty(),
            meldeadresse = adresse?.let { listOf(it[0], it[7]).filter { s -> s.isNotBlank() }.joinToString(", ") }.orEmpty(),
            abschnitte = el.filter { it.name == "ABSCHNITT" }.map { Abschnitt(it[0].toIntOrNull() ?: 0, datum(it[1]), uhrzeit(it[4])) },
            wettkaempfe = el.filter { it.name == "WETTKAMPF" }.map { wk(it, true) },
            wertungen = el.filter { it.name == "WERTUNG" }.map {
                WertungDef(it[0].toIntOrNull() ?: 0, it[1], it[2].toIntOrNull() ?: 0, it[3], it[4], it[5], it[6], it[7])
            },
            pflichtzeiten = el.filter { it.name == "PFLICHTZEIT" }.mapNotNull {
                Pflichtzeit(it[0].toIntOrNull() ?: 0, it[1], it[2], it[3], it[4], zeit(it[5]) ?: return@mapNotNull null, it[6])
            },
            einzelMeldegeldCent = el.firstOrNull { it.name == "MELDEGELD" && it[0].uppercase() == "EINZELMELDEGELD" }
                ?.let { it[1].replace(".", "").replace(',', '.').toBigDecimalOrNull()?.movePointRight(2)?.toLong() },
        )
    }

    /** Wettkampf- oder Vereinsergebnisliste. */
    fun ergebnisse(text: String): Ergebnisliste {
        val el = elemente(text)
        val (art, _) = format(el)
        val v = el.firstOrNull { it.name == "VERANSTALTUNG" } ?: throw WettkampfFehler("VERANSTALTUNG fehlt.")
        val tage = el.filter { it.name == "ABSCHNITT" }.associate { (it[0].toIntOrNull() ?: 0) to datum(it[1]) }
        val wks = el.filter { it.name == "WETTKAMPF" }.map { wk(it, true) }.associateBy { it.nr to it.art }
        val wertungen = el.filter { it.name == "WERTUNG" }.associate { (it[2].toIntOrNull() ?: 0) to it[7] }
        fun strecke(nr: Int, a: String) = wks[nr to a] ?: wks.entries.firstOrNull { it.key.first == nr }?.value
        val liste = when (art) {
            "wettkampfergebnisliste" -> el.filter { it.name == "PNERGEBNIS" }.mapNotNull { e ->
                val w = strecke(e[0].toIntOrNull() ?: return@mapNotNull null, e[1]) ?: return@mapNotNull null
                ergebnis(e[5], e[6], e[9], e[8], e[11], e[12], w, e[13], e[3], e[2], e[4], tage[w.abschnitt], wertungen)
            }
            "vereinsergebnisliste" -> {
                val verein = el.firstOrNull { it.name == "VEREIN" }
                val personen = el.filter { it.name == "PERSON" }.associateBy { it[2] }
                el.filter { it.name == "PERSONENERGEBNIS" }.mapNotNull { e ->
                    val p = personen[e[0]] ?: return@mapNotNull null
                    val w = strecke(e[1].toIntOrNull() ?: return@mapNotNull null, e[2]) ?: return@mapNotNull null
                    ergebnis(p[0], p[1], p[4], p[3], verein?.get(0).orEmpty(), verein?.get(1).orEmpty(), w, e[5], e[4], e[3], e[6], tage[w.abschnitt], wertungen)
                }
            }
            else -> throw WettkampfFehler("Das ist keine Ergebnisliste, sondern: ${el.first { it.name == "FORMAT" }[0]}.")
        }
        return Ergebnisliste(v[0], v[1], v[2], liste)
    }

    private fun ergebnis(
        name: String, dsvId: String, jahrgang: String, geschlecht: String, verein: String, vereinsnummer: String, w: WkDef,
        zeit: String, platz: String, wertungsId: String, grund: String, datum: LocalDate?, wertungen: Map<Int, String>,
    ): Ergebnis? {
        if (w.staffel) return null
        val (nach, vor) = name.split(',', limit = 2).map { it.trim() }.let { it[0] to it.getOrElse(1) { "" } }
        return Ergebnis(
            name = "$vor $nach".trim(), vorname = vor, nachname = nach, dsvId = dsvId, jahrgang = jahrgang.toIntOrNull(),
            geschlecht = geschlecht, verein = verein, vereinsnummer = vereinsnummer, strecke = w.streckenText,
            hundertstel = zeit(zeit) ?: 0, platz = platz.toIntOrNull() ?: 0,
            wertung = wertungen[wertungsId.toIntOrNull()].orEmpty().lowercase().replaceFirstChar { it.uppercase() },
            grund = grund, datum = datum,
        )
    }

    /** Dateiname nach Standard, z. B. "2026-11-15-Augsburg-TSVFischach-Me.DSV8". */
    fun meldeDateiname(a: Ausschreibung, verein: String): String {
        fun kurz(s: String, n: Int) = s.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("Ä", "Ae")
            .replace("Ö", "Oe").replace("Ü", "Ue").replace("ß", "ss").replace(Regex("[^A-Za-z0-9]"), "").take(n)
        val tag = a.letzterTag ?: LocalDate.now()
        return "$tag-${kurz(a.ort, 8)}-${kurz(verein, 16)}-Me.DSV${a.version}"
    }

    /**
     * Vereinsmeldeliste im Format der Ausschreibung (7 oder 8). Veranstaltungs-IDs der Schwimmer sind fortlaufend ab 1;
     * Meldezeit 0 schreibt 00:00:00,00 („ohne Zeit“).
     */
    fun meldeliste(a: Ausschreibung, wer: Meldender, b: Bestand, meldungen: List<Meldung>): String {
        val v8 = a.version >= 8
        fun f(s: String) = s.replace(";", ",").replace("\n", " ").trim()
        val z = mutableListOf<String>()
        z += "FORMAT:Vereinsmeldeliste;${a.version};"
        z += "ERZEUGER:Dach-App Schwimmgruppen;1.0;keine-antwort@dach-app.invalid;"
        z += "VERANSTALTUNG:${f(a.veranstaltung)};${f(a.ort)};${a.bahn};${a.zeitmessung};"
        val genutzt = meldungen.flatMap { it.starts.keys }.toSet()
        val wks = a.wettkaempfe.filter { it.nr in genutzt && it.art in setOf("V", "E") }
        val abschnitte = wks.map { it.abschnitt }.toSet()
        a.abschnitte.filter { it.nr in abschnitte }.forEach {
            z += "ABSCHNITT:${it.nr};${it.datum?.format(DATUM).orEmpty()};${it.beginn?.toString().orEmpty()};;"
        }
        wks.forEach { w ->
            z += "WETTKAMPF:${w.nr};${w.art};${w.abschnitt};${if (w.starter > 1) w.starter else ""};${w.strecke};${w.technik};${w.ausuebung};${w.geschlecht};${w.qualiNr};${w.qualiArt};"
        }
        z += "VEREIN:${f(wer.verein)};${wer.vereinsnummer.ifBlank { "0" }};${wer.lsv};GER;" + if (v8) "N;" else ""
        z += "ANSPRECHPARTNER:${f(wer.name)};;;;GER;${f(wer.telefon)};;${f(wer.email)};"
        meldungen.forEachIndexed { i, m ->
            val p = b.mitglied(m.mitgliedId) ?: return@forEachIndexed
            val id = i + 1
            z += "PNMELDUNG:${f(p.nachname)}, ${f(p.vorname)};${p.dsvId.filter(Char::isDigit).ifEmpty { "0" }};$id;${geschlecht(p.geschlecht)};${p.jahrgang ?: ""};;;GER;;;"
            m.starts.toSortedMap().forEach { (wk, zeit) -> z += "STARTPN:$id;$wk;${zeitText(zeit)};" }
        }
        z += "DATEIENDE"
        return z.joinToString("\r\n", postfix = "\r\n")
    }

    fun geschlecht(g: String): String = when (g.trim().lowercase()) { "w", "weiblich" -> "W"; "m", "männlich", "maennlich" -> "M"; "d", "divers" -> "D"; else -> "" }
}
