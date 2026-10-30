package de.dachapp.schwimmgruppen.logic

object Zeiten {

    /**
     * Liest eine Schwimmzeit: "38,52", "38.5", "1:05,32", "1:05" oder "1.05,32" (Minuten mit Punkt).
     * Ergebnis in Hundertstelsekunden, null wenn nicht lesbar.
     */
    fun lesen(roh: String): Int? {
        var t = roh.trim().lowercase().removeSuffix("sek").removeSuffix("s").trim().replace(" ", "")
        // "1.05,32": Punkt trennt Minuten, Komma die Hundertstel.
        if ('.' in t && ',' in t && t.indexOf('.') < t.indexOf(',')) t = t.replaceFirst('.', ':')
        val m = Regex("^(?:(\\d{1,3}):)?(\\d{1,4})(?:[,.](\\d{1,2}))?$").find(t) ?: return null
        val min = m.groupValues[1].toIntOrNull() ?: 0
        val sek = m.groupValues[2].toInt()
        // Mit Minuten müssen die Sekunden unter 60 liegen; ohne Minuten sind auch "65,3" Sekunden erlaubt.
        if (m.groupValues[1].isNotEmpty() && sek >= 60) return null
        return (min * 60 + sek) * 100 + bruch(m.groupValues[3])
    }

    private fun bruch(s: String): Int = when (s.length) {
        0 -> 0
        1 -> s.toInt() * 10
        else -> s.toInt()
    }

    /** 3852 → "38,52"; 6532 → "1:05,32". */
    fun text(hundertstel: Int): String {
        val min = hundertstel / 6000
        val sek = hundertstel / 100 % 60
        val h = hundertstel % 100
        return if (min > 0) "%d:%02d,%02d".format(min, sek, h) else "%d,%02d".format(sek, h)
    }

    /** Beste Zeit je Strecke, Strecken in der Reihenfolge von [reihenfolge], unbekannte danach alphabetisch. */
    fun bestzeiten(m: Mitglied, reihenfolge: List<String>): List<Zeit> =
        m.zeiten.groupBy { it.strecke }.map { (_, l) -> l.minWith(compareBy({ it.hundertstel }, { it.datum })) }
            .sortedWith(compareBy({ reihenfolge.indexOf(it.strecke).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.strecke }))
}
