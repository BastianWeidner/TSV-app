package de.dachapp.vereinsmeisterschaft.logic

/** Eine erkannte Textzeile mit Lage im Foto (aus der Texterkennung), [sicherheit] 0 bis 1. */
data class OcrZeile(
    val text: String,
    val links: Int,
    val oben: Int,
    val rechts: Int,
    val unten: Int,
    val sicherheit: Float = 1f,
) {
    val mitteY: Int get() = (oben + unten) / 2
    val hoehe: Int get() = unten - oben
}

/** Ein gelesener Eintrag vom Zeitzettel. [sicher] false = bitte in der Korrektur-Ansicht ansehen. */
data class ScanTreffer(
    val lauf: Int,
    val bahn: Int,
    val roh: String,
    val zeit: Int?,
    val status: Status?,
    val sicher: Boolean,
    val grund: String = "",
    /** Höhe der Zeile im Foto (für einen Bildausschnitt in der Korrektur-Ansicht). */
    val oben: Int = 0,
    val unten: Int = 0,
)

/**
 * Liest fotografierte Zeitzettel. Jede Zeile des Zettels beginnt mit einem gedruckten Kennzeichen „L03 B2“ (Lauf 3,
 * Bahn 2); die gedruckte Schrift erkennt die Texterkennung sehr zuverlässig. Rechts davon in derselben Höhe steht die
 * handgeschriebene Zeit in Kästchen (Minuten : Sekunden , Hundertstel). Handschrift wird offline nur mäßig erkannt,
 * darum wird jede Zeit auf Plausibilität geprüft und Zweifelsfälle markiert; die Korrektur macht der Mensch.
 */
object Scan {

    private val kennzeichen = Regex("""[LlI|]\s*0?(\d{1,3})\s*[-·.]?\s*[B8]\s*(\d)""")

    /** Häufige Verwechslungen bei handgeschriebenen Ziffern. */
    private val ersatz = mapOf('O' to '0', 'o' to '0', 'D' to '0', 'Q' to '0', 'l' to '1', 'I' to '1', 'i' to '1', '!' to '1',
        'Z' to '2', 'z' to '2', 'S' to '5', 's' to '5', 'G' to '6', 'b' to '6', 'B' to '8', 'g' to '9', 'q' to '9', 'T' to '7')

    fun kennung(text: String): Pair<Int, Int>? {
        val m = kennzeichen.find(text.trim()) ?: return null
        if (m.range.first > 2) return null
        return m.groupValues[1].toInt() to m.groupValues[2].toInt()
    }

    /** Ziffern aus handschriftlichem Text, mit Ersatz für typische Verwechslungen; null wenn es nicht nach Zahl aussieht. */
    fun ziffern(text: String): String? {
        val t = text.filterNot { it.isWhitespace() || it == ':' || it == ',' || it == '.' || it == '\'' || it == '_' || it == '-' }
        if (t.isEmpty()) return null
        val ersetzt = t.map { ersatz[it] ?: it }
        val anteil = ersetzt.count { it.isDigit() }.toDouble() / ersetzt.size
        if (anteil < 0.75) return null
        return ersetzt.filter { it.isDigit() }.joinToString("")
    }

    /** Mit Trennzeichen geschrieben („1:O5,3“): direkt als Zeit lesen, Verwechslungen ersetzt. */
    fun mitTrennern(text: String): Int? {
        val t = text.filterNot { it.isWhitespace() || it == '_' }.map { ersatz[it] ?: it }.joinToString("")
        if (!t.contains(':') && !t.contains(',') && !t.contains('.')) return null
        if (t.any { !it.isDigit() && it != ':' && it != ',' && it != '.' }) return null
        return Zeit.lesen(t)?.takeIf { t.count { it.isDigit() } >= 3 }
    }

    /** Sinnvolle Zeit für [bahnen] Bahnen (zwischen 5 und 120 Sekunden je Bahn). */
    fun plausibel(zeit: Int, bahnen: Int): Boolean = zeit in (500 * bahnen)..(12_000 * bahnen)

    /**
     * Mögliche Lesarten einer Ziffernfolge: normal (letzte zwei = Hundertstel) und mit Zehnteln (letzte Ziffer = Zehntel),
     * falls nur eine Nachkommastelle geschrieben wurde. Nur plausible.
     */
    fun lesarten(d: String, bahnen: Int): List<Int> {
        val l = ArrayList<Int>()
        Zeit.ziffern(d)?.let { l += it }
        if (d.length >= 2) Zeit.ziffern(d + "0")?.let { l += it }
        return l.distinct().filter { plausibel(it, bahnen) }
    }

    /**
     * Treffer aus den Textzeilen eines Fotos. [bahnenVon] liefert die Bahnenzahl der Strecke eines Laufs (für die
     * Plausibilität); [erwartet] optional die Meldezeit (näher dran = wahrscheinlicher).
     */
    fun auswerten(
        zeilen: List<OcrZeile>,
        bahnenVon: (lauf: Int) -> Int?,
        erwartet: (lauf: Int, bahn: Int) -> Int? = { _, _ -> null },
    ): List<ScanTreffer> {
        val anker = zeilen.mapNotNull { z -> kennung(z.text)?.let { z to it } }.sortedBy { it.first.mitteY }
        if (anker.isEmpty()) return emptyList()
        // Zeilenabstand des Zettels, daraus die Höhe eines Zeilenbands.
        val abstaende = anker.zipWithNext { a, b -> b.first.mitteY - a.first.mitteY }.filter { it > 0 }.sorted()
        val abstand = abstaende.getOrNull(abstaende.size / 2) ?: (anker.first().first.hoehe * 3)
        val band = maxOf(abstand * 0.42, anker.first().first.hoehe * 0.8)
        val ankerZeilen = anker.map { it.first }.toSet()

        return anker.map { (a, lb) ->
            val (lauf, bahn) = lb
            // Auf der Ankerzeile selbst kann schon die Zeit stehen, wenn die Erkennung alles zu einer Zeile zusammenzieht.
            val eigenerRest = a.text.substring((kennzeichen.find(a.text)?.range?.last ?: -1) + 1)
            val kandidaten = zeilen.filter { z ->
                z !in ankerZeilen && z.links > a.links && kotlin.math.abs(z.mitteY - a.mitteY) <= band
            }.sortedBy { it.links }
            val texte = kandidaten.map { it.text } + listOfNotNull(eigenerRest.takeIf { it.isNotBlank() })
            val status = texte.firstNotNullOfOrNull { Zeit.status(it) }
            val ziffernTeile = texte.mapNotNull { ziffern(it) }.filter { it.isNotEmpty() }
            val roh = kandidaten.joinToString(" ") { it.text }.ifEmpty { eigenerRest.trim() }
            val sicherheit = kandidaten.minOfOrNull { it.sicherheit } ?: 1f
            val bahnen = bahnenVon(lauf)
            when {
                bahnen == null -> ScanTreffer(lauf, bahn, roh, null, null, false, "Lauf $lauf gibt es nicht")
                status != null -> ScanTreffer(lauf, bahn, roh, null, status, true)
                ziffernTeile.isEmpty() -> ScanTreffer(lauf, bahn, roh, null, null, roh.isBlank(), if (roh.isBlank()) "leer" else "nicht lesbar")
                else -> {
                    val d = ziffernTeile.joinToString("")
                    val direkt = mitTrennern(kandidaten.filter { ziffern(it.text) != null }.joinToString("") { it.text })?.takeIf { plausibel(it, bahnen) }
                    val moeglich = if (direkt != null) listOf(direkt) else lesarten(d, bahnen)
                    val ziel = erwartet(lauf, bahn)
                    val wahl = if (ziel != null) moeglich.minByOrNull { kotlin.math.abs(it - ziel) } else moeglich.firstOrNull()
                    val grund = when {
                        moeglich.isEmpty() -> "„$d“ ergibt keine sinnvolle Zeit"
                        moeglich.size > 1 -> "mehrdeutig"
                        sicherheit < 0.6f -> "schlecht lesbar"
                        else -> ""
                    }
                    ScanTreffer(lauf, bahn, roh, wahl, null, grund.isEmpty(), grund)
                }
            }
        }.zip(anker).map { (t, a) ->
            t.copy(oben = (a.first.mitteY - band).toInt().coerceAtLeast(0), unten = (a.first.mitteY + band).toInt())
        }.distinctBy { it.lauf to it.bahn }
    }
}
