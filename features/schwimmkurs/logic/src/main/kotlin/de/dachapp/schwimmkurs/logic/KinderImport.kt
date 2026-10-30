package de.dachapp.schwimmkurs.logic

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.UUID

/** Wohin eine Spalte der Tabelle beim Import geht. */
sealed interface Zuordnung {
    val anzeige: String

    data class Feld(val feld: KindFeld) : Zuordnung {
        override val anzeige get() = feld.anzeige
    }

    /** Spalte steht für ein einzelnes Abzeichen, z. B. "Seepferdchen" mit Einträgen wie "x" oder "ja". */
    data class AbzeichenSpalte(val abzeichen: String) : Zuordnung {
        override val anzeige get() = "Abzeichen: $abzeichen"
    }

    data object Ignorieren : Zuordnung {
        override val anzeige get() = "nicht übernehmen"
    }
}

enum class KindFeld(val anzeige: String) {
    VORNAME("Vorname"),
    NACHNAME("Nachname"),
    NAME("Name (Vor- und Nachname)"),
    GEBURTSDATUM("Geburtsdatum"),
    ELTERN("Elternteil"),
    EMAIL("E-Mail"),
    TELEFON("Telefon"),
    STRASSE("Straße"),
    PLZ("PLZ"),
    ORT("Wohnort"),
    PLZ_ORT("PLZ und Ort"),
    ABZEICHEN("Abzeichen / Leistungen"),
    ANMELDEDATUM("Anmeldedatum"),
    NOTIZ("Bemerkung"),
    EINSCHULUNG("Einschulung"),
}

data class ImportErgebnis(
    /** Gesamte, neue Kinderliste (bestehende + neue, aktualisierte ersetzt). */
    val kinder: List<Kind>,
    val neu: Int,
    val aktualisiert: Int,
    val hinweise: List<String>,
)

object KinderImport {

    private fun norm(s: String) = s.lowercase()
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .filter { it.isLetterOrDigit() }

    /** Schlägt für jede Spalte eine Zuordnung anhand der Überschrift vor. */
    fun vorschlagen(kopf: List<String>, abzeichen: List<String>): List<Zuordnung> {
        val vergeben = mutableSetOf<KindFeld>()
        val vorschlag = kopf.map { titel ->
            raten(norm(titel), abzeichen)
                ?.takeIf { z -> z !is Zuordnung.Feld || z.feld in MEHRFACH || vergeben.add(z.feld) }
                ?: Zuordnung.Ignorieren
        }
        // "Name" ist der Nachname, wenn es zusätzlich eine Vornamen-Spalte gibt.
        val hatVorname = vorschlag.any { it == Zuordnung.Feld(KindFeld.VORNAME) }
        return vorschlag.map {
            if (hatVorname && it == Zuordnung.Feld(KindFeld.NAME) && KindFeld.NACHNAME !in vergeben) Zuordnung.Feld(KindFeld.NACHNAME) else it
        }
    }

    /** Felder, die aus mehreren Spalten zusammengesetzt werden dürfen (z. B. Straße + Hausnummer). */
    private val MEHRFACH = setOf(KindFeld.ELTERN, KindFeld.STRASSE, KindFeld.NOTIZ)

    private fun raten(t: String, abzeichen: List<String>): Zuordnung? {
        fun f(feld: KindFeld) = Zuordnung.Feld(feld)
        if (t.isEmpty()) return null
        abzeichen.firstOrNull { norm(it) == t }?.let { return Zuordnung.AbzeichenSpalte(it) }
        return when {
            "mail" in t -> f(KindFeld.EMAIL)
            listOf("telefon", "handy", "mobil", "rufnummer").any { it in t } || t == "tel" -> f(KindFeld.TELEFON)
            "geburt" in t || t.startsWith("geb") || t == "geboren" -> f(KindFeld.GEBURTSDATUM)
            "plz" in t && "ort" in t || "postleitzahl" in t && "ort" in t -> f(KindFeld.PLZ_ORT)
            "plz" in t || "postleitzahl" in t -> f(KindFeld.PLZ)
            listOf("eltern", "erziehungsber", "sorgeber", "mutter", "vater", "ansprechpartner", "kontaktperson").any { it in t } ->
                f(KindFeld.ELTERN)
            "vorname" in t || "rufname" in t -> f(KindFeld.VORNAME)
            "nachname" in t || "familienname" in t || "zuname" in t -> f(KindFeld.NACHNAME)
            listOf("strasse", "anschrift", "adresse", "hausnummer", "hausnr").any { it in t } || t == "str" || t == "nr" ->
                f(KindFeld.STRASSE)
            "wohnort" in t || t == "ort" || t == "stadt" || t == "gemeinde" -> f(KindFeld.ORT)
            listOf("abzeichen", "leistung", "vorkenntnis", "schwimmstufe", "kenntnisse").any { it in t } -> f(KindFeld.ABZEICHEN)
            "einschul" in t || "schuleintritt" in t -> f(KindFeld.EINSCHULUNG)
            listOf("anmeld", "zeitstempel", "eingang", "timestamp").any { it in t } -> f(KindFeld.ANMELDEDATUM)
            listOf("bemerk", "notiz", "anmerk", "kommentar", "hinweis", "sonstig").any { it in t } -> f(KindFeld.NOTIZ)
            "name" in t -> f(KindFeld.NAME)
            else -> null
        }
    }

    /**
     * Übernimmt die Zeilen der Tabelle in die Kinderliste. Ein Kind, das es schon gibt (gleicher Vor- und
     * Nachname und gleiches Geburtsdatum, ohne Geburtsdatum gleiche E-Mail), wird ergänzt statt doppelt angelegt.
     */
    fun importieren(
        tabelle: Tabelle,
        zuordnung: List<Zuordnung>,
        bestehend: List<Kind>,
        stufen: List<String>,
        neueId: () -> String = { UUID.randomUUID().toString() },
    ): ImportErgebnis {
        val abz = Abzeichen(stufen)
        val kinder = bestehend.toMutableList()
        val hinweise = mutableListOf<String>()
        var neu = 0
        var aktualisiert = 0
        val imDurchlauf = mutableSetOf<String>()
        var naechsterPlatz = (bestehend.mapNotNull { it.listenplatz }.maxOrNull() ?: 0) + 1

        // 1. Zeilen lesen. Bei einer gemeinsamen Namensspalte bleibt der Name zunächst ungeteilt.
        val gelesen = mutableListOf<Pair<Int, Kind>>()
        val vollNamen = mutableMapOf<Int, String>()
        tabelle.zeilen.forEachIndexed { i, zeile ->
            val nr = i + 2 // Zeile in der Datei, die Kopfzeile ist 1.
            val werte = mutableMapOf<KindFeld, String>()
            val abzeichen = LinkedHashSet<String>()
            zuordnung.forEachIndexed { s, z ->
                val v = zeile.getOrNull(s).orEmpty().trim()
                if (v.isEmpty()) return@forEachIndexed
                when (z) {
                    is Zuordnung.Feld -> if (z.feld == KindFeld.ABZEICHEN) abzeichen += abz.ausZelle(v)
                        else werte.merge(z.feld, v) { a, b -> "$a $b" }
                    is Zuordnung.AbzeichenSpalte -> if (wahr(v)) abzeichen += z.abzeichen
                    Zuordnung.Ignorieren -> {}
                }
            }
            val voll = werte[KindFeld.NAME]?.trim()?.replace(Regex("\\s+"), " ")
            val vorname = werte[KindFeld.VORNAME].orEmpty()
            val nachname = werte[KindFeld.NACHNAME].orEmpty()
            if (vorname.isEmpty() && nachname.isEmpty() && voll.isNullOrEmpty()) {
                if (werte.isNotEmpty() || abzeichen.isNotEmpty()) {
                    hinweise += "Zeile $nr: kein Name, übersprungen" +
                        (werte[KindFeld.EMAIL]?.let { " ($it)" } ?: "") + "."
                }
                return@forEachIndexed
            }
            var plz = werte[KindFeld.PLZ].orEmpty()
            var ort = werte[KindFeld.ORT].orEmpty()
            werte[KindFeld.PLZ_ORT]?.let { po ->
                val m = Regex("^(\\d{4,5})\\s*(.*)$").find(po)
                if (m != null) {
                    if (plz.isEmpty()) plz = m.groupValues[1]
                    if (ort.isEmpty()) ort = m.groupValues[2]
                } else if (ort.isEmpty()) ort = po
            }
            val anzeige = listOf(vorname, nachname).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { voll.orEmpty() }
            val gebText = werte[KindFeld.GEBURTSDATUM]
            val geburt = gebText?.let { datum(it) }
            if (gebText != null && geburt == null) hinweise += "Zeile $nr ($anzeige): Geburtsdatum „$gebText“ nicht erkannt."
            if (gebText == null) hinweise += "Zeile $nr ($anzeige): kein Geburtsdatum."
            val email = werte[KindFeld.EMAIL].orEmpty()
            if (email.isNotEmpty() && Einladungen.adressen(email).isEmpty()) {
                hinweise += "Zeile $nr ($anzeige): E-Mail „$email“ sieht ungültig aus."
            }
            if (email.isEmpty()) hinweise += "Zeile $nr ($anzeige): keine E-Mail, bekommt keine Einladung."
            val notiz = werte[KindFeld.NOTIZ].orEmpty()
            val getrennt = Regex("nicht\\s+(in\\s+)?(die|den|der|dem)?\\s*(selbe|gleiche)", RegexOption.IGNORE_CASE).containsMatchIn(notiz)
            if (getrennt) hinweise += "Zeile $nr ($anzeige): „Geschwister getrennt“ gesetzt wegen Bemerkung."
            if (notiz.isNotEmpty()) {
                if (stufen.any { notiz.contains(it, ignoreCase = true) }) {
                    hinweise += "Zeile $nr ($anzeige): Bemerkung erwähnt ein Abzeichen, bitte prüfen: „$notiz“"
                }
                if (Regex("abgemeldet|nicht mehr benötigt|storniert|abgesagt", RegexOption.IGNORE_CASE).containsMatchIn(notiz)) {
                    hinweise += "Zeile $nr ($anzeige): Bemerkung klingt nach Abmeldung, bitte prüfen: „$notiz“"
                }
            }
            if (voll != null && vorname.isEmpty() && nachname.isEmpty()) vollNamen[gelesen.size] = voll
            gelesen += nr to Kind(
                id = "",
                vorname = vorname,
                nachname = nachname,
                geburtsdatum = geburt,
                eltern = werte[KindFeld.ELTERN].orEmpty(),
                email = email,
                telefon = werte[KindFeld.TELEFON].orEmpty(),
                strasse = werte[KindFeld.STRASSE].orEmpty(),
                plz = plz,
                ort = ort,
                abzeichen = abzeichen,
                anmeldedatum = werte[KindFeld.ANMELDEDATUM]?.let { datum(it) },
                notiz = notiz,
                einschulung = werte[KindFeld.EINSCHULUNG].orEmpty(),
                geschwisterGetrennt = getrennt,
            )
        }

        // 2. Gemeinsame Namensspalten teilen: Reihenfolge aus Geschwistern und E-Mail ableiten.
        val geteilt = namenTeilen(vollNamen.mapValues { (i, n) -> n to gelesen[i].second.email })
        for ((i, vn) in geteilt) {
            val (nr, k) = gelesen[i]
            gelesen[i] = nr to k.copy(vorname = vn.first, nachname = vn.second)
        }

        // Ähnliche Namen in einer Familie ("Najeeb Ahmad Qamar" / "Qamar Najeeb") sind vermutlich dasselbe Kind.
        gelesen.groupBy { it.second.familienSchluessel }.values.filter { it.size > 1 }.forEach { fam ->
            for (x in fam.indices) for (y in x + 1 until fam.size) {
                val (na, a) = fam[x]
                val (nb, b) = fam[y]
                val wa = a.name.split(' ', '-').map(::norm).filter { it.length > 1 }.toSet()
                val wb = b.name.split(' ', '-').map(::norm).filter { it.length > 1 }.toSet()
                if (norm(a.name) != norm(b.name) && wa.intersect(wb).size >= 2) {
                    hinweise += "Zeile $na und $nb: „${a.name}“ und „${b.name}“ sind vielleicht dasselbe Kind, bitte prüfen."
                }
            }
        }

        // 3. Mit vorhandenen Kindern zusammenführen.
        for ((nr, k) in gelesen) {
            val idx = kinder.indexOfFirst { gleich(it, k) }
            if (idx >= 0) {
                val alt = kinder[idx]
                if (alt.id in imDurchlauf) hinweise += "Zeile $nr: ${k.name} steht doppelt in der Datei, zusammengeführt."
                kinder[idx] = ergaenzen(alt, k).let { if (it.listenplatz == null) it.copy(listenplatz = naechsterPlatz++) else it }
                if (alt.id !in imDurchlauf) aktualisiert++
                imDurchlauf += alt.id
            } else {
                val neuesKind = k.copy(id = neueId(), listenplatz = naechsterPlatz++)
                kinder += neuesKind
                imDurchlauf += neuesKind.id
                neu++
            }
        }
        return ImportErgebnis(kinder, neu, aktualisiert, hinweise)
    }

    private fun gleich(a: Kind, b: Kind): Boolean {
        if (norm(a.vorname) != norm(b.vorname) || norm(a.nachname) != norm(b.nachname)) return false
        if (a.geburtsdatum != null && b.geburtsdatum != null) return a.geburtsdatum == b.geburtsdatum
        val ma = Einladungen.adressen(a.email).map { it.lowercase() }.toSet()
        val mb = Einladungen.adressen(b.email).map { it.lowercase() }.toSet()
        return ma.isNotEmpty() && ma.intersect(mb).isNotEmpty()
    }

    /** Neue, nicht leere Werte gewinnen; Abzeichen werden vereinigt. */
    private fun ergaenzen(alt: Kind, neu: Kind): Kind {
        fun s(a: String, b: String) = b.ifBlank { a }
        return alt.copy(
            geburtsdatum = neu.geburtsdatum ?: alt.geburtsdatum,
            eltern = s(alt.eltern, neu.eltern),
            email = s(alt.email, neu.email),
            telefon = s(alt.telefon, neu.telefon),
            strasse = s(alt.strasse, neu.strasse),
            plz = s(alt.plz, neu.plz),
            ort = s(alt.ort, neu.ort),
            abzeichen = alt.abzeichen + neu.abzeichen,
            anmeldedatum = alt.anmeldedatum ?: neu.anmeldedatum,
            notiz = s(alt.notiz, neu.notiz),
            einschulung = s(alt.einschulung, neu.einschulung),
            geschwisterGetrennt = alt.geschwisterGetrennt || neu.geschwisterGetrennt,
        )
    }

    /**
     * Teilt Namen aus einer gemeinsamen Spalte, in der mal "Vorname Nachname", mal "Nachname Vorname" steht.
     * Schlüssel ist ein beliebiger Index, Wert ist (Name, E-Mail). Entscheidung je Name:
     * 1. Geschwister (gleiche E-Mail) teilen sich ein Wort am Anfang oder am Ende: das ist der Nachname.
     * 2. Das erste oder letzte Wort steckt in der E-Mail-Adresse (z. B. tom.hampf@… bei „Hampf Thea“).
     * 3. Nur das letzte Wort ist ein bekannter Vorname („Spengler Toni“).
     * 4. Sonst "Vorname Nachname".
     */
    fun namenTeilen(namen: Map<Int, Pair<String, String>>): Map<Int, Pair<String, String>> {
        val gruppen = namen.entries.groupBy { Einladungen.adressen(it.value.second).firstOrNull()?.lowercase() }
        return namen.mapValues { (i, ne) ->
            val (name, email) = ne
            val t = name.split(' ').filter { it.isNotEmpty() }
            if (',' in name || t.size < 2) return@mapValues nameTeilen(name)
            val erstes = t.first()
            val letztes = t.last()
            fun erstesIstNachname() = t.drop(1).joinToString(" ") to erstes
            val mail = Einladungen.adressen(email).firstOrNull()?.lowercase()
            val geschwister = if (mail == null) emptyList() else gruppen[mail].orEmpty().filter { it.key != i }
                .map { it.value.first.split(' ').filter { w -> w.isNotEmpty() } }.filter { it.size >= 2 }
            fun gleich(a: String, b: String) = norm(a) == norm(b)
            when {
                geschwister.any { gleich(it.last(), letztes) } -> nameTeilen(name)
                geschwister.any { gleich(it.first(), erstes) } -> erstesIstNachname()
                mail != null && steckt(letztes, mail) -> nameTeilen(name)
                mail != null && steckt(erstes, mail) -> erstesIstNachname()
                Vornamen.bekannt(letztes) && !Vornamen.bekannt(erstes) -> erstesIstNachname()
                else -> nameTeilen(name)
            }
        }
    }

    /** Steckt das Wort (mind. 4 Buchstaben) im Teil vor dem @? Umlaute zählen als ae/oe/ue und als a/o/u. */
    private fun steckt(wort: String, mail: String): Boolean {
        val lokal = mail.substringBefore('@').filter { it.isLetter() }
        val w1 = norm(wort).filter { it.isLetter() }
        val w2 = wort.lowercase().replace("ä", "a").replace("ö", "o").replace("ü", "u").replace("ß", "ss").filter { it.isLetter() }
        return w1.length >= 4 && (w1 in lokal || w2 in lokal)
    }

    /** "Müller, Anna" → (Anna, Müller); "Anna Maria Müller" → (Anna Maria, Müller). */
    fun nameTeilen(voll: String): Pair<String, String> {
        val t = voll.trim().replace(Regex("\\s+"), " ")
        if (',' in t) {
            val (n, v) = t.split(',', limit = 2)
            return v.trim() to n.trim()
        }
        val i = t.lastIndexOf(' ')
        return if (i < 0) t to "" else t.substring(0, i) to t.substring(i + 1)
    }

    private fun wahr(v: String): Boolean {
        val k = v.trim().lowercase()
        if (k in setOf("nein", "no", "false", "falsch", "0", "-", "")) return false
        return true // "x", "ja", "✓", ein Datum, "2023" …
    }

    private val formate = listOf("d.M.uuuu", "d.M.uu", "uuuu-M-d", "d/M/uuuu", "d-M-uuuu", "uuuu/M/d")
        .map { DateTimeFormatter.ofPattern(it).withResolverStyle(ResolverStyle.STRICT) }

    /**
     * Erkennt deutsche und ISO-Daten, auch mit Uhrzeit dahinter ("12.03.2026 18:04:11" aus Google Formulare),
     * sowie Excel-Seriennummern. Zweistellige Jahre zählen ins letzte Jahrhundert, wenn sie sonst in der Zukunft lägen.
     */
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
