package de.dachapp.schwimmgruppen.logic

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.LocalTime
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * LENEX 3 (XML, .lef bzw. gepackt .lxf), wie es Meet Manager, Splash und viele Ausrichter liefern.
 * Gelesen werden Einladungen (Wettkämpfe) und Ergebnisse.
 */
object Lenex {

    fun istLenex(bytes: ByteArray): Boolean {
        if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) return entpacken(bytes)?.let { istLenex(it) } == true
        return TabellenLeser.text(bytes.copyOfRange(0, minOf(bytes.size, 2000))).contains("<LENEX", ignoreCase = true)
    }

    private fun entpacken(bytes: ByteArray): ByteArray? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory && e.name.lowercase().endsWith(".lef")) return zip.readBytes()
                e = zip.nextEntry
            }
        }
        return null
    }

    private fun wurzel(bytes: ByteArray): Element {
        val roh = if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            entpacken(bytes) ?: throw WettkampfFehler("Im .lxf-Archiv ist keine .lef-Datei.")
        } else bytes
        val f = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isExpandEntityReferences = false
        }
        return f.newDocumentBuilder().parse(ByteArrayInputStream(roh)).documentElement
    }

    private fun Element.kinder(name: String): List<Element> {
        val out = mutableListOf<Element>()
        var n: Node? = firstChild
        while (n != null) {
            if (n is Element && n.tagName.equals(name, true)) out += n
            n = n.nextSibling
        }
        return out
    }

    private fun Element.pfad(vararg namen: String): List<Element> =
        namen.fold(listOf(this)) { acc, n -> acc.flatMap { it.kinder(n) } }

    private fun Element.a(name: String): String = getAttribute(name).trim()

    private val TECHNIK = mapOf("FREE" to "F", "BACK" to "R", "BREAST" to "B", "FLY" to "S", "MEDLEY" to "L", "IMRELAY" to "L")

    private fun bahn(course: String) = when (course.uppercase()) { "LCM" -> "50"; "SCM" -> "25"; "OPEN" -> "FW"; else -> "X" }

    private fun geschlecht(g: String) = when (g.uppercase()) { "M" -> "M"; "F" -> "W"; "X" -> "X"; else -> "X" }

    /** "00:01:05.32" → Hundertstel; "NT" oder leer → null. */
    fun zeit(t: String): Int? = Dsv.zeit(t.replace('.', ','))

    private fun wks(meet: Element): List<Pair<WkDef, LocalDate?>> = meet.pfad("SESSIONS", "SESSION").flatMap { s ->
        val tag = runCatching { LocalDate.parse(s.a("date")) }.getOrNull()
        s.pfad("EVENTS", "EVENT").mapNotNull { e ->
            val st = e.kinder("SWIMSTYLE").firstOrNull() ?: return@mapNotNull null
            val runde = when (e.a("round").uppercase()) { "PRE" -> "V"; "SEM" -> "Z"; "FIN" -> "F"; else -> "E" }
            WkDef(
                nr = e.a("number").toIntOrNull() ?: return@mapNotNull null, art = runde, abschnitt = s.a("number").toIntOrNull() ?: 0,
                strecke = st.a("distance").toIntOrNull() ?: 0, technik = TECHNIK[st.a("stroke").uppercase()] ?: "X",
                geschlecht = geschlecht(e.a("gender")), starter = st.a("relaycount").toIntOrNull() ?: 1,
            ) to tag
        }
    }

    /** Einladung (Wettkämpfe, Jahrgänge, Meldeschluss) als [Ausschreibung]; die Meldung geht danach als DSV-Datei. */
    fun ausschreibung(bytes: ByteArray): Ausschreibung {
        val meet = wurzel(bytes).pfad("MEETS", "MEET").firstOrNull() ?: throw WettkampfFehler("Keine Veranstaltung in der LENEX-Datei.")
        val sessions = meet.pfad("SESSIONS", "SESSION")
        val events = sessions.flatMap { s -> s.pfad("EVENTS", "EVENT").map { s to it } }
        val liste = wks(meet).map { it.first }
        val wertungen = events.flatMap { (_, e) ->
            val nr = e.a("number").toIntOrNull() ?: return@flatMap emptyList()
            val art = liste.firstOrNull { it.nr == nr }?.art ?: "E"
            e.pfad("AGEGROUPS", "AGEGROUP").mapIndexed { i, g ->
                // LENEX: Alter von/bis (−1 = offen) bezogen auf das Jahr der Veranstaltung; daraus Jahrgänge.
                val jahr = sessions.firstOrNull()?.a("date")?.take(4)?.toIntOrNull() ?: LocalDate.now().year
                val max = g.a("agemax").toIntOrNull() ?: -1
                val min = g.a("agemin").toIntOrNull() ?: -1
                WertungDef(nr, art, nr * 100 + i, "JG", if (max < 0) "0" else "${jahr - max}", if (min < 0) "0" else "${jahr - min}", "", g.a("name"))
            }
        }
        val frist = meet.a("deadline").let { d -> runCatching { LocalDate.parse(d) }.getOrNull()?.let { "%02d.%02d.%d".format(it.dayOfMonth, it.monthValue, it.year) } ?: d }
        return Ausschreibung(
            version = 8,
            veranstaltung = meet.a("name"), ort = meet.a("city"), bahn = bahn(meet.a("course")),
            zeitmessung = if (meet.a("timing").uppercase().startsWith("MANUAL")) "HANDZEIT" else "AUTOMATISCH",
            meldeschluss = frist + meet.a("deadlinetime").let { if (it.isBlank()) "" else " $it Uhr" },
            abschnitte = sessions.map { s ->
                Abschnitt(s.a("number").toIntOrNull() ?: 0, runCatching { LocalDate.parse(s.a("date")) }.getOrNull(), runCatching { LocalTime.parse(s.a("daytime")) }.getOrNull())
            },
            wettkaempfe = liste,
            wertungen = wertungen,
        )
    }

    fun ergebnisse(bytes: ByteArray): Ergebnisliste {
        val meet = wurzel(bytes).pfad("MEETS", "MEET").firstOrNull() ?: throw WettkampfFehler("Keine Veranstaltung in der LENEX-Datei.")
        val eventInfo = mutableMapOf<String, Pair<WkDef, LocalDate?>>()
        val plaetze = mutableMapOf<String, Pair<Int, String>>()
        meet.pfad("SESSIONS", "SESSION").forEach { s ->
            val tag = runCatching { LocalDate.parse(s.a("date")) }.getOrNull()
            s.pfad("EVENTS", "EVENT").forEach { e ->
                val st = e.kinder("SWIMSTYLE").firstOrNull()
                if (st != null) {
                    eventInfo[e.a("eventid")] = WkDef(
                        e.a("number").toIntOrNull() ?: 0, "E", 0, st.a("distance").toIntOrNull() ?: 0,
                        TECHNIK[st.a("stroke").uppercase()] ?: "X", geschlecht = geschlecht(e.a("gender")), starter = st.a("relaycount").toIntOrNull() ?: 1,
                    ) to tag
                }
                e.pfad("AGEGROUPS", "AGEGROUP").forEach { g ->
                    g.pfad("RANKINGS", "RANKING").forEach { r ->
                        val platz = r.a("place").toIntOrNull() ?: 0
                        val alt = plaetze[r.a("resultid")]
                        if (platz > 0 && (alt == null || platz < alt.first)) plaetze[r.a("resultid")] = platz to g.a("name")
                    }
                }
            }
        }
        val ergebnisse = meet.pfad("CLUBS", "CLUB").flatMap { c ->
            c.pfad("ATHLETES", "ATHLETE").flatMap { a ->
                a.pfad("RESULTS", "RESULT").mapNotNull { r ->
                    val (w, tag) = eventInfo[r.a("eventid")] ?: return@mapNotNull null
                    if (w.staffel) return@mapNotNull null
                    val status = r.a("status").uppercase()
                    val (platz, wertung) = plaetze[r.a("resultid")] ?: (0 to "")
                    Ergebnis(
                        name = "${a.a("firstname")} ${a.a("lastname")}".trim(), vorname = a.a("firstname"), nachname = a.a("lastname"),
                        dsvId = a.a("license"), jahrgang = a.a("birthdate").take(4).toIntOrNull(), geschlecht = geschlecht(a.a("gender")),
                        verein = c.a("name"), vereinsnummer = c.a("code"), strecke = w.streckenText, hundertstel = zeit(r.a("swimtime")) ?: 0,
                        platz = if (status.isEmpty()) platz else 0, wertung = wertung,
                        grund = when (status) { "" -> ""; "DSQ" -> "DS"; "DNS" -> "NA"; "DNF" -> "AU"; else -> status }, datum = tag,
                    )
                }
            }
        }
        return Ergebnisliste(meet.a("name"), meet.a("city"), bahn(meet.a("course")), ergebnisse)
    }
}
