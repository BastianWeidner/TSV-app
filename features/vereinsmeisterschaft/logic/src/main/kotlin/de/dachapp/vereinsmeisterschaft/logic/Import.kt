package de.dachapp.vereinsmeisterschaft.logic

/** Ergebnis eines Imports: neuer Stand und was dabei auffiel. */
data class ImportBericht(
    val veranstaltung: Veranstaltung,
    val neu: Int,
    val geaendert: Int,
    val hinweise: List<String>,
) {
    val text: String
        get() = buildString {
            append("$neu neu, $geaendert aktualisiert.")
            if (hinweise.isNotEmpty()) {
                append("\n\n")
                append(hinweise.take(15).joinToString("\n"))
                if (hinweise.size > 15) append("\n… und ${hinweise.size - 15} weitere")
            }
        }
}

/**
 * Meldelisten und Ergebnisse aus CSV/Excel. Spalten werden an der Kopfzeile erkannt, z. B. Name, Vorname, Jahrgang
 * (oder Geburtsdatum), Geschlecht, Gruppe sowie Brust/Rücken/Kraul/Delphin (Meldezeiten bzw. Zeiten).
 * Die Mitgliederliste der Schwimmgruppen (Name, Vorname, …, GebDat) geht direkt; das Geschlecht fehlt dort und
 * wird danach in der App nachgetragen.
 */
object Import {

    fun norm(s: String): String = s.trim().lowercase()
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun spalte(kopf: List<String>, vararg namen: String): Int? {
        val k = kopf.map { norm(it) }
        for (n in namen) k.indexOfFirst { it == n }.takeIf { it >= 0 }?.let { return it }
        return null
    }

    private fun spalteEnthaelt(kopf: List<String>, vararg teile: String): Int? {
        val k = kopf.map { norm(it) }
        return k.indexOfFirst { h -> teile.any { h.contains(it) } }.takeIf { it >= 0 }
    }

    private val lagenNamen = mapOf(
        Lage.BRUST to listOf("brust"),
        Lage.RUECKEN to listOf("ruecken", "rucken"),
        Lage.KRAUL to listOf("kraul", "freistil"),
        Lage.DELPHIN to listOf("delphin", "delfin", "schmetterling"),
    )

    private fun lagenSpalten(kopf: List<String>): Map<Lage, Int> =
        lagenNamen.mapNotNull { (l, n) -> spalteEnthaelt(kopf, *n.toTypedArray())?.let { l to it } }.toMap()

    fun lage(text: String): Lage? {
        val t = norm(text)
        return lagenNamen.entries.firstOrNull { (_, n) -> n.any { t.contains(it) } }?.key
    }

    fun geschlecht(text: String): Geschlecht? = when (norm(text)) {
        "w", "weiblich", "f", "female", "maedchen", "frau" -> Geschlecht.W
        "m", "maennlich", "male", "junge", "mann", "j" -> Geschlecht.M
        else -> null
    }

    /** Jahrgang aus „2017“, „17“, „03.05.2017“, „3/5/2017“, „2017-05-03“; zweistellig wird ins passende Jahrhundert gelegt. */
    fun jahrgang(text: String, jahr: Int): Int? {
        val t = text.trim()
        Regex("""(19|20)\d\d""").find(t)?.let { return it.value.toInt() }
        val zwei = Regex("""(?:^|\D)(\d{2})$""").find(t)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val j = 2000 + zwei
        return if (j > jahr) j - 100 else j
    }

    private data class Namensspalten(val vorname: Int?, val nachname: Int?, val voll: Int?)

    private fun namensspalten(kopf: List<String>): Namensspalten {
        val vor = spalte(kopf, "vorname", "rufname")
        val nach = spalte(kopf, "nachname", "familienname", "zuname") ?: if (vor != null) spalte(kopf, "name") else null
        val voll = if (vor == null) spalte(kopf, "name", "schwimmer", "teilnehmer", "kind", "name des kindes") else null
        return Namensspalten(vor, nach, voll)
    }

    /** Vor- und Nachname aus einer Zelle: „Nachname, Vorname“ oder „Vorname Nachname“ (letztes Wort = Nachname). */
    fun teilen(voll: String): Pair<String, String> {
        val t = voll.trim().replace(Regex("\\s+"), " ")
        if (t.contains(",")) return t.substringAfter(",").trim() to t.substringBefore(",").trim()
        val w = t.split(" ")
        return if (w.size == 1) "" to w[0] else w.dropLast(1).joinToString(" ") to w.last()
    }

    private fun namen(tab: Tabelle, i: Int, s: Namensspalten): Pair<String, String> = when {
        s.vorname != null -> tab.zelle(i, s.vorname).trim() to s.nachname?.let { tab.zelle(i, it).trim() }.orEmpty()
        s.voll != null -> teilen(tab.zelle(i, s.voll))
        else -> "" to ""
    }

    /** Meldeliste einlesen; vorhandene Schwimmer (gleicher Name und Jahrgang) werden ergänzt, nicht doppelt angelegt. */
    fun meldeliste(v: Veranstaltung, tab: Tabelle): ImportBericht {
        val ns = namensspalten(tab.kopf)
        if (ns.vorname == null && ns.voll == null) {
            throw TabellenFehler("Keine Namensspalte erkannt. Die erste Zeile braucht Überschriften, z. B. Name; Vorname; Jahrgang; Geschlecht.")
        }
        val jg = spalte(tab.kopf, "jahrgang", "jg", "geburtsjahr", "gebdat", "geburtsdatum", "geb datum", "geboren", "geb")
            ?: spalteEnthaelt(tab.kopf, "geburt", "jahrgang")
            ?: throw TabellenFehler("Keine Spalte für Jahrgang oder Geburtsdatum erkannt.")
        val gs = spalte(tab.kopf, "geschlecht", "g", "m w", "w m", "sex")
        val gr = spalte(tab.kopf, "gruppe", "trainingsgruppe")
        val lagen = lagenSpalten(tab.kopf)
        var liste = v.teilnehmer
        var id = v.naechsteId
        var neu = 0
        var geaendert = 0
        val hinweise = ArrayList<String>()
        for (i in tab.zeilen.indices) {
            val (vor, nach) = namen(tab, i, ns)
            if (vor.isBlank() && nach.isBlank()) continue
            val j = jahrgang(tab.zelle(i, jg), v.jahr)
            if (j == null) { hinweise += "Zeile ${i + 2}: $vor $nach ohne lesbaren Jahrgang, übersprungen."; continue }
            val g = gs?.let { geschlecht(tab.zelle(i, it)) }
            val zeiten = lagen.mapNotNull { (l, s) -> Zeit.lesen(tab.zelle(i, s))?.let { l to it } }.toMap()
            val gruppe = gr?.let { tab.zelle(i, it).trim() }.orEmpty()
            val vorhanden = liste.firstOrNull { norm(it.vorname) == norm(vor) && norm(it.nachname) == norm(nach) && it.jahrgang == j }
            if (vorhanden != null) {
                val neuT = vorhanden.copy(
                    geschlecht = g ?: vorhanden.geschlecht,
                    meldezeiten = vorhanden.meldezeiten + zeiten,
                    gruppe = gruppe.ifEmpty { vorhanden.gruppe },
                )
                if (neuT != vorhanden) { liste = liste.map { if (it.id == vorhanden.id) neuT else it }; geaendert++ }
            } else {
                liste = liste + Teilnehmer(id++, vor, nach, j, g, meldezeiten = zeiten, gruppe = gruppe)
                neu++
            }
        }
        val ohneG = liste.count { it.geschlecht == null }
        if (ohneG > 0) hinweise.add(0, "Bei $ohneG Schwimmern fehlt das Geschlecht. Bitte unter „Teilnehmer“ nachtragen.")
        return ImportBericht(v.copy(teilnehmer = liste, naechsteId = id), neu, geaendert, hinweise)
    }

    /** Schwimmer zu einem Namen (Groß/klein, Umlaute und Reihenfolge Vor-/Nachname egal); null wenn keiner oder mehrdeutig. */
    fun finden(v: Veranstaltung, vorname: String, nachname: String, jahrgang: Int? = null): Teilnehmer? {
        val a = norm("$vorname $nachname")
        val b = norm("$nachname $vorname")
        val treffer = v.teilnehmer.filter { t ->
            val n = norm(t.name)
            (n == a || n == b) && (jahrgang == null || t.jahrgang == jahrgang)
        }
        return treffer.singleOrNull()
    }

    /**
     * Ergebnisse nachträglich einlesen. Drei Formen werden erkannt:
     * - breit: Name (+ Vorname) und je Lage eine Spalte mit der Zeit,
     * - lang: Name, Lage (oder Disziplin), Zeit,
     * - nach Lauf: Lauf, Bahn, Zeit (passend zu den eingeteilten Läufen).
     * „n.a.“, „disq“ usw. werden als Status übernommen. Vorhandene Ergebnisse werden überschrieben.
     */
    fun ergebnisse(v: Veranstaltung, tab: Tabelle): ImportBericht {
        val ns = namensspalten(tab.kopf)
        val lauf = spalte(tab.kopf, "lauf", "lauf nr", "laufnr", "lauf nr ")
        val bahn = spalte(tab.kopf, "bahn")
        val zeit = spalte(tab.kopf, "zeit", "endzeit", "ergebnis")
        val lageSp = spalte(tab.kopf, "lage", "disziplin", "stil", "schwimmart")
        val jg = spalte(tab.kopf, "jahrgang", "jg")
        val lagen = lagenSpalten(tab.kopf).filterValues { it != lageSp }
        var neuV = v
        var neu = 0
        var geaendert = 0
        val hinweise = ArrayList<String>()

        fun setzen(t: Teilnehmer, l: Lage, roh: String, zeile: Int) {
            if (roh.isBlank()) return
            val st = Zeit.status(roh)
            val z = if (st == null) Zeit.lesen(roh) else null
            if (st == null && z == null) { hinweise += "Zeile $zeile: „$roh“ ist keine Zeit."; return }
            if (neuV.starts(t).none { it.lage == l }) hinweise += "Zeile $zeile: ${t.name} ist für ${l.text} nicht gemeldet, trotzdem übernommen."
            val alt = neuV.ergebnis(t.id, l)
            neuV = neuV.mitErgebnis(t.id, l, Ergebnis(t.id, l, z, st ?: Status.OK))
            if (alt == null) neu++ else geaendert++
        }

        when {
            lauf != null && bahn != null && zeit != null -> for (i in tab.zeilen.indices) {
                val ln = tab.zelle(i, lauf).filter { it.isDigit() }.toIntOrNull() ?: continue
                val bn = tab.zelle(i, bahn).filter { it.isDigit() }.toIntOrNull() ?: continue
                val l = v.laeufe.firstOrNull { it.nr == ln }
                val t = l?.auf(bn)?.let { v.person(it) }
                if (l == null || t == null) { hinweise += "Zeile ${i + 2}: Lauf $ln Bahn $bn ist nicht belegt."; continue }
                setzen(t, l.strecke.lage, tab.zelle(i, zeit), i + 2)
            }
            ns.vorname != null || ns.voll != null -> for (i in tab.zeilen.indices) {
                val (vor, nach) = namen(tab, i, ns)
                if (vor.isBlank() && nach.isBlank()) continue
                val j = jg?.let { jahrgang(tab.zelle(i, it), v.jahr) }
                val t = finden(v, vor, nach, j) ?: finden(v, vor, nach)
                if (t == null) { hinweise += "Zeile ${i + 2}: „$vor $nach“ nicht in der Teilnehmerliste."; continue }
                if (lageSp != null && zeit != null) {
                    val l = lage(tab.zelle(i, lageSp))
                    if (l == null) { hinweise += "Zeile ${i + 2}: Lage „${tab.zelle(i, lageSp)}“ unbekannt."; continue }
                    setzen(t, l, tab.zelle(i, zeit), i + 2)
                } else {
                    lagen.forEach { (l, s) -> setzen(t, l, tab.zelle(i, s), i + 2) }
                }
            }
            else -> throw TabellenFehler(
                "Spalten nicht erkannt. Möglich sind: Name; Vorname; Brust; Rücken; Kraul – oder Name; Lage; Zeit – oder Lauf; Bahn; Zeit.",
            )
        }
        return ImportBericht(neuV, neu, geaendert, hinweise)
    }

    /** Ergebnisliste als CSV (Semikolon, für Excel), eine Zeile je Schwimmer. */
    fun ergebnisCsv(v: Veranstaltung): String {
        val w = Wertung.jahrgaenge(v)
        val lagen = Lage.entries.filter { l -> v.teilnehmer.any { t -> v.starts(t).any { it.lage == l } } }
        val sb = StringBuilder()
        sb.append((listOf("Gruppe", "Platz gesamt", "Nachname", "Vorname", "Jahrgang", "Geschlecht") +
            lagen.flatMap { listOf(it.text, "Platz ${it.text}") } + "Zeitpunkte").joinToString(";")).append("\r\n")
        for (gw in w) for (z in gw.gesamt) {
            val t = z.teilnehmer
            val felder = mutableListOf(gw.gruppe.titel, z.platz?.toString().orEmpty(), t.nachname, t.vorname, t.jahrgang.toString(), t.geschlecht?.kurz.orEmpty())
            for (l in lagen) {
                val e = v.ergebnis(t.id, l)
                felder += if (e == null) "" else if (e.gueltig) Zeit.text(e.zeit) else e.status.kurz
                felder += gw.lagen.firstOrNull { it.strecke.lage == l }?.zeilen?.firstOrNull { it.teilnehmer.id == t.id }?.platz?.toString().orEmpty()
            }
            felder += Wertung.wertText(v.regeln, z)
            sb.append(felder.joinToString(";") { f -> if (f.contains(';') || f.contains('"')) "\"" + f.replace("\"", "\"\"") + "\"" else f }).append("\r\n")
        }
        return sb.toString()
    }
}
