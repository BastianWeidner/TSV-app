package de.dachapp.schwimmgruppen.logic

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

enum class MitgliedFeld(val anzeige: String) {
    MITGLIEDSNUMMER("Mitgliedsnummer"),
    VORNAME("Vorname"),
    NACHNAME("Nachname"),
    NAME("Name (Vor- und Nachname)"),
    GEBURTSDATUM("Geburtsdatum"),
    GESCHLECHT("Geschlecht"),
    EMAIL("E-Mail"),
    TELEFON("Telefon"),
    STRASSE("Straße"),
    PLZ("PLZ"),
    ORT("Wohnort"),
    PLZ_ORT("PLZ und Ort"),
    EINTRITT("Eintritt / EJ"),
    FOTORECHTE("Fotorechte"),
    GESUNDHEIT("Gesundheitserklärung"),
    AUSTRITT("Austritt"),
    ABTEILUNG("Abteilung / Sparte"),
    GRUPPE("Trainingsgruppe"),
    NOTIZ("Bemerkung"),
}

/** Wohin eine Spalte beim Import geht; null = nicht übernehmen. */
typealias Zuordnung = MitgliedFeld?

data class ImportErgebnis(
    /** Gesamte neue Mitgliederliste (bestehende + neue, aktualisierte ersetzt). */
    val mitglieder: List<Mitglied>,
    val neu: Int,
    val aktualisiert: Int,
    /** Zeilen anderer Abteilungen. */
    val andereAbteilung: Int,
    /** Aktive Mitglieder der App, die in der Liste nicht mehr vorkommen. */
    val nichtInListe: List<Mitglied>,
    val hinweise: List<String>,
)

object MitgliederImport {

    fun norm(s: String) = s.lowercase()
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .filter { it.isLetterOrDigit() }

    /** Schlägt für jede Spalte eine Zuordnung anhand der Überschrift vor. Jedes Feld nur einmal, außer Straße und Bemerkung. */
    fun vorschlagen(kopf: List<String>): List<Zuordnung> {
        val vergeben = mutableSetOf<MitgliedFeld>()
        val v = kopf.map { titel -> raten(norm(titel))?.takeIf { it in MEHRFACH || vergeben.add(it) } }
        // "Name" ist der Nachname, wenn es zusätzlich eine Vornamen-Spalte gibt.
        return if (MitgliedFeld.VORNAME in v && MitgliedFeld.NACHNAME !in v) {
            v.map { if (it == MitgliedFeld.NAME) MitgliedFeld.NACHNAME else it }
        } else v
    }

    private val MEHRFACH = setOf(MitgliedFeld.STRASSE, MitgliedFeld.NOTIZ)

    private fun raten(t: String): MitgliedFeld? = when {
        t.isEmpty() -> null
        "mail" in t -> MitgliedFeld.EMAIL
        listOf("telefon", "handy", "mobil", "rufnummer").any { it in t } || t == "tel" -> MitgliedFeld.TELEFON
        "geburt" in t || t.startsWith("geb") || t == "geboren" -> MitgliedFeld.GEBURTSDATUM
        "geschlecht" in t || t == "anrede" || t == "mw" || t == "mwd" || t == "sex" -> MitgliedFeld.GESCHLECHT
        listOf("mitgliedsnr", "mitglnr", "mitgliedsnummer", "mitgliednr", "mitgliednummer", "mglnr", "mitgliedsid").any { it in t } ||
            t == "nr" || t == "id" || t == "nummer" -> MitgliedFeld.MITGLIEDSNUMMER
        "austritt" in t || "kuendig" in t -> MitgliedFeld.AUSTRITT
        "eintritt" in t || "mitgliedseit" in t || "beitritt" in t || t == "ej" -> MitgliedFeld.EINTRITT
        "foto" in t || "bildrecht" in t -> MitgliedFeld.FOTORECHTE
        "gesundheit" in t || "attest" in t -> MitgliedFeld.GESUNDHEIT
        "plz" in t && "ort" in t || "postleitzahl" in t && "ort" in t -> MitgliedFeld.PLZ_ORT
        "plz" in t || "postleitzahl" in t -> MitgliedFeld.PLZ
        "vorname" in t || "rufname" in t -> MitgliedFeld.VORNAME
        "nachname" in t || "familienname" in t || "zuname" in t -> MitgliedFeld.NACHNAME
        listOf("abteilung", "sparte", "sportart").any { it in t } -> MitgliedFeld.ABTEILUNG
        "gruppe" in t || "trainingsgr" in t || "kader" in t -> MitgliedFeld.GRUPPE
        listOf("strasse", "anschrift", "adresse", "hausnummer", "hausnr").any { it in t } || t == "str" -> MitgliedFeld.STRASSE
        "wohnort" in t || t == "ort" || t == "stadt" || t == "gemeinde" -> MitgliedFeld.ORT
        listOf("bemerk", "notiz", "anmerk", "kommentar", "hinweis", "sonstig", "info").any { it in t } -> MitgliedFeld.NOTIZ
        "name" in t -> MitgliedFeld.NAME
        else -> null
    }

    /**
     * Übernimmt die Zeilen der Mitgliederliste. Wer schon da ist (gleiche Mitgliedsnummer, sonst gleicher Name
     * und Geburtsdatum), wird mit den Stammdaten der Liste aktualisiert; Gruppen, Erfolge und Zeiten bleiben.
     * Eine Gruppen-Spalte teilt nur Mitglieder ein, die noch in keiner Gruppe sind.
     */
    fun importieren(
        tabelle: Tabelle,
        zuordnung: List<Zuordnung>,
        bestand: Bestand,
        heute: LocalDate = LocalDate.now(),
        neueId: () -> String = { UUID.randomUUID().toString() },
    ): ImportErgebnis {
        val filter = bestand.einstellungen.abteilungFilter.trim()
        val mitAbteilung = MitgliedFeld.ABTEILUNG in zuordnung
        val liste = bestand.mitglieder.toMutableList()
        val hinweise = mutableListOf<String>()
        val gesehen = mutableSetOf<String>()
        var neu = 0
        var aktualisiert = 0
        var andere = 0
        val unbekannteGruppen = sortedSetOf<String>()

        tabelle.zeilen.forEachIndexed { i, zeile ->
            val nr = i + 2 // Zeile in der Datei, die Kopfzeile ist 1.
            val w = mutableMapOf<MitgliedFeld, String>()
            zuordnung.forEachIndexed { s, f ->
                val v = zeile.getOrNull(s).orEmpty().trim()
                if (f != null && v.isNotEmpty()) w.merge(f, v) { a, b -> "$a $b" }
            }
            if (mitAbteilung && filter.isNotEmpty() && !w[MitgliedFeld.ABTEILUNG].orEmpty().contains(filter, ignoreCase = true)) {
                andere++
                return@forEachIndexed
            }
            var vorname = w[MitgliedFeld.VORNAME].orEmpty()
            var nachname = w[MitgliedFeld.NACHNAME].orEmpty()
            w[MitgliedFeld.NAME]?.let { voll ->
                if (vorname.isEmpty() && nachname.isEmpty()) {
                    val (v, n) = nameTeilen(voll)
                    vorname = v; nachname = n
                }
            }
            if (vorname.isEmpty() && nachname.isEmpty()) {
                if (w.isNotEmpty()) hinweise += "Zeile $nr: kein Name, übersprungen."
                return@forEachIndexed
            }
            val anzeige = "$vorname $nachname".trim()
            val gebText = w[MitgliedFeld.GEBURTSDATUM]
            val geburt = gebText?.let { datum(it, heute) }
            if (gebText != null && geburt == null) hinweise += "Zeile $nr ($anzeige): Geburtsdatum „$gebText“ nicht erkannt."
            var plz = w[MitgliedFeld.PLZ].orEmpty()
            var ort = w[MitgliedFeld.ORT].orEmpty()
            w[MitgliedFeld.PLZ_ORT]?.let { po ->
                val m = Regex("^(\\d{4,5})\\s*(.*)$").find(po)
                if (m != null) {
                    if (plz.isEmpty()) plz = m.groupValues[1]
                    if (ort.isEmpty()) ort = m.groupValues[2]
                } else if (ort.isEmpty()) ort = po
            }
            val austritt = w[MitgliedFeld.AUSTRITT]?.let { datum(it, heute) }
            val ausgetreten = austritt != null && !austritt.isAfter(heute)
            val gruppeId = w[MitgliedFeld.GRUPPE]?.let { g ->
                gruppeFinden(bestand.gruppen, g).also { if (it == null) unbekannteGruppen += g }
            }
            val nummer = w[MitgliedFeld.MITGLIEDSNUMMER].orEmpty()
            val gelesen = Mitglied(
                id = "",
                vorname = vorname,
                nachname = nachname,
                geburtsdatum = geburt,
                geschlecht = geschlecht(w[MitgliedFeld.GESCHLECHT].orEmpty()),
                mitgliedsnummer = nummer,
                email = w[MitgliedFeld.EMAIL].orEmpty(),
                telefon = w[MitgliedFeld.TELEFON].orEmpty(),
                strasse = w[MitgliedFeld.STRASSE].orEmpty(),
                plz = plz,
                ort = ort,
                eintritt = w[MitgliedFeld.EINTRITT].orEmpty(),
                fotorechte = w[MitgliedFeld.FOTORECHTE]?.let(::janein),
                gesundheitserklaerung = w[MitgliedFeld.GESUNDHEIT]?.let(::janein),
                notiz = w[MitgliedFeld.NOTIZ].orEmpty(),
            )

            val idx = liste.indexOfFirst { gleich(it, gelesen) }
            if (idx >= 0) {
                val alt = liste[idx]
                if (alt.id in gesehen) hinweise += "Zeile $nr ($anzeige): steht mehrfach in der Liste."
                fun neuOder(n: String, a: String) = n.ifEmpty { a }
                liste[idx] = alt.copy(
                    vorname = neuOder(gelesen.vorname, alt.vorname),
                    nachname = neuOder(gelesen.nachname, alt.nachname),
                    geburtsdatum = gelesen.geburtsdatum ?: alt.geburtsdatum,
                    geschlecht = neuOder(gelesen.geschlecht, alt.geschlecht),
                    mitgliedsnummer = neuOder(gelesen.mitgliedsnummer, alt.mitgliedsnummer),
                    email = neuOder(gelesen.email, alt.email),
                    telefon = neuOder(gelesen.telefon, alt.telefon),
                    strasse = neuOder(gelesen.strasse, alt.strasse),
                    plz = neuOder(gelesen.plz, alt.plz),
                    ort = neuOder(gelesen.ort, alt.ort),
                    eintritt = neuOder(gelesen.eintritt, alt.eintritt),
                    fotorechte = gelesen.fotorechte ?: alt.fotorechte,
                    gesundheitserklaerung = gelesen.gesundheitserklaerung ?: alt.gesundheitserklaerung,
                    notiz = if (gelesen.notiz.isEmpty() || alt.notiz.contains(gelesen.notiz)) alt.notiz
                        else listOf(alt.notiz, gelesen.notiz).filter { it.isNotEmpty() }.joinToString("\n"),
                    gruppen = if (alt.gruppen.isEmpty() && gruppeId != null) setOf(gruppeId) else alt.gruppen,
                    aktiv = if (ausgetreten) false else alt.aktiv,
                )
                gesehen += alt.id
                aktualisiert++
            } else {
                val m = gelesen.copy(id = neueId(), gruppen = setOfNotNull(gruppeId), aktiv = !ausgetreten)
                liste += m
                gesehen += m.id
                neu++
            }
            if (ausgetreten) hinweise += "Zeile $nr ($anzeige): ausgetreten, als inaktiv übernommen."
        }

        if (unbekannteGruppen.isNotEmpty()) {
            hinweise += "Gruppen nicht gefunden, bitte von Hand einteilen: " + unbekannteGruppen.joinToString(", ") { "„$it“" }
        }
        val fehlend = bestand.mitglieder.filter { it.aktiv && it.id !in gesehen }
        return ImportErgebnis(liste, neu, aktualisiert, andere, fehlend, hinweise)
    }

    private fun gleich(a: Mitglied, b: Mitglied): Boolean {
        if (a.mitgliedsnummer.isNotEmpty() && b.mitgliedsnummer.isNotEmpty()) return a.mitgliedsnummer == b.mitgliedsnummer
        if (norm(a.vorname) != norm(b.vorname) || norm(a.nachname) != norm(b.nachname)) return false
        return a.geburtsdatum == null || b.geburtsdatum == null || a.geburtsdatum == b.geburtsdatum
    }

    /** "Müller, Anna" → Anna Müller; sonst ist das letzte Wort der Nachname. */
    fun nameTeilen(voll: String): Pair<String, String> {
        val t = voll.trim().replace(Regex("\\s+"), " ")
        if (',' in t) {
            val (n, v) = t.split(',', limit = 2)
            return v.trim() to n.trim()
        }
        val teile = t.split(' ')
        if (teile.size == 1) return "" to t
        // Namenszusätze wie "von", "van der" gehören zum Nachnamen.
        var start = teile.size - 1
        while (start > 1 && teile[start - 1].lowercase() in ZUSAETZE) start--
        return teile.take(start).joinToString(" ") to teile.drop(start).joinToString(" ")
    }

    private val ZUSAETZE = setOf("von", "van", "de", "der", "den", "zu", "vom", "zum", "di", "da", "le")

    /** "ja", "x", "liegt vor" → true; "nein", "-", "0" → false; sonst unbekannt. */
    fun janein(roh: String): Boolean? {
        val t = roh.trim().lowercase()
        return when {
            t.isEmpty() -> null
            t.startsWith("nein") || t.startsWith("nicht") || t in setOf("n", "no", "0", "-", "false", "keine") -> false
            t.startsWith("ja") || t in setOf("j", "x", "yes", "1", "true", "✓", "✔") || "liegt vor" in t -> true
            else -> null
        }
    }

    fun geschlecht(roh: String): String = when (norm(roh)) {
        "w", "weiblich", "f", "female", "frau", "fr", "maedchen" -> "w"
        "m", "maennlich", "male", "herr", "hr", "junge" -> "m"
        "d", "divers", "x" -> "d"
        else -> ""
    }

    /** Ordnet einen Gruppennamen aus der Liste einer Gruppe zu: "Gruppe 2", "II", "Gr. II", "Wassergym." … */
    fun gruppeFinden(gruppen: List<Gruppe>, roh: String): String? {
        fun schluessel(s: String): String {
            val t = norm(s).removePrefix("gruppe").removePrefix("gr")
            return when (t) { "1" -> "i"; "2" -> "ii"; "3" -> "iii"; "4" -> "iv"; "5" -> "v"; else -> t }
        }
        val k = schluessel(roh)
        if (k.isEmpty()) return null
        gruppen.firstOrNull { schluessel(it.name) == k }?.let { return it.id }
        return gruppen.firstOrNull { g -> k.length >= 4 && schluessel(g.name).startsWith(k) }?.id
    }

    private val formate = listOf("d.M.uuuu", "d.M.uu", "uuuu-M-d", "d/M/uuuu", "d-M-uuuu", "uuuu/M/d").map { DateTimeFormatter.ofPattern(it) }

    /** Datum aus Text oder Excel-Tageszahl; zweistellige Jahre in der Zukunft gehören ins vorige Jahrhundert. */
    fun datum(roh: String, heute: LocalDate = LocalDate.now()): LocalDate? {
        val t = roh.trim().split(Regex("[ T]"), limit = 2).first().trimEnd(',')
        if (Regex("\\d+([.,]\\d+)?").matches(t)) {
            // Excel zählt Tage ab 30.12.1899. Nur 1903–2099 als Datum werten, kleinere Zahlen sind keine Daten.
            val n = t.replace(',', '.').toDouble()
            return if (n in 1100.0..73000.0) LocalDate.of(1899, 12, 30).plusDays(n.toLong()) else null
        }
        for (f in formate) {
            val d = try { LocalDate.parse(t, f) } catch (e: DateTimeParseException) { continue }
            val zweistellig = Regex("\\d{1,2}[./-]\\d{1,2}[./-]\\d{2}$").matches(t)
            return if (zweistellig && d.isAfter(heute)) d.minusYears(100) else d
        }
        return null
    }
}
