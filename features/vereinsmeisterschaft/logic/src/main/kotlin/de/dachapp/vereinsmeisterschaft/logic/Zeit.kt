package de.dachapp.vereinsmeisterschaft.logic

/** Schwimmzeiten in Hundertstelsekunden lesen und schreiben. */
object Zeit {

    /** „1:05,30“ bzw. „35,40“. */
    fun text(h: Int?): String {
        if (h == null || h <= 0) return ""
        val min = h / 6000
        val sek = (h / 100) % 60
        val hu = h % 100
        return if (min > 0) "%d:%02d,%02d".format(min, sek, hu) else "%d,%02d".format(sek, hu)
    }

    /**
     * Liest „1:05,3“, „1.05,30“, „65,3“, „65.30“, „35“ (ganze Sekunden) und Ziffernfolgen ab drei Stellen wie aus der
     * Schnelleingabe („3540“ = 35,40; „10530“ = 1:05,30). null bei Unsinn.
     */
    fun lesen(text: String): Int? {
        var t = text.trim().replace(" ", "").lowercase().removeSuffix("sek").removeSuffix("s")
        if (t.isEmpty()) return null
        if (t.all { it.isDigit() }) return if (t.length <= 2) t.toInt() * 100 else ziffern(t)
        // Trenner vereinheitlichen: Komma = Dezimal; ohne Komma ist der letzte Punkt dezimal, ein Punkt davor Minuten.
        t = t.replace('\'', ':')
        if (!t.contains(',')) {
            val p = t.lastIndexOf('.')
            if (p >= 0) t = t.substring(0, p) + "," + t.substring(p + 1)
        }
        t = t.replace('.', ':')
        val m = Regex("""^(?:(\d{1,2}):)?(\d{1,3})(?:,(\d{1,2}))?$""").matchEntire(t) ?: return null
        val min = m.groupValues[1].toIntOrNull() ?: 0
        val sek = m.groupValues[2].toInt()
        if (m.groupValues[1].isNotEmpty() && sek >= 60) return null
        val bruch = m.groupValues[3]
        val hu = when (bruch.length) {
            0 -> 0
            1 -> bruch.toInt() * 10
            else -> bruch.toInt()
        }
        return (min * 60 + sek) * 100 + hu
    }

    /** Ziffern ohne Trenner: die letzten zwei sind Hundertstel, davor zwei Sekunden, der Rest Minuten. */
    fun ziffern(d: String): Int? {
        val s = d.trimStart('0').ifEmpty { return null }
        if (s.length > 6) return null
        val hu = s.takeLast(2).toInt()
        val rest = s.dropLast(2)
        val sek = rest.takeLast(2).ifEmpty { "0" }.toInt()
        val min = rest.dropLast(2).ifEmpty { "0" }.toInt()
        if (min > 0 && sek >= 60) return null
        return (min * 60 + sek) * 100 + hu
    }

    /** Status aus einem Eintrag wie „n.a.“ oder „disq“; null, wenn es kein Status ist. */
    fun status(text: String): Status? {
        val t = text.trim().lowercase().replace(".", "").replace(" ", "")
        return when (t) {
            "na", "nichtangetreten", "nichtgestartet", "ns", "dns", "fehlt", "-", "–" -> Status.NA
            "dq", "dsq", "disq", "disqualifiziert" -> Status.DSQ
            "aufg", "aufgegeben", "dnf" -> Status.AUFG
            else -> null
        }
    }
}
