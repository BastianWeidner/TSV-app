package de.dachapp.schwimmgruppen.logic

/**
 * Abgangsuhr für Serien: [anzahl] Abgänge im Abstand von [abstandMs], nach [serien] Serien jeweils [pauseMs] Pause.
 * Abgang 1 ist beim Start.
 */
data class Serie(val anzahl: Int = 8, val abstandMs: Long = 105_000, val serien: Int = 1, val pauseMs: Long = 60_000) {
    val abgaengeGesamt: Int get() = anzahl.coerceAtLeast(1) * serien.coerceAtLeast(1)
}

/** Wo die Uhr gerade steht. [abgang] zählt ab 1 (der zuletzt erfolgte Abgang), [bisNaechsterMs] null = fertig. */
data class UhrStand(
    val serie: Int,
    val abgang: Int,
    val inPause: Boolean,
    val bisNaechsterMs: Long?,
    /** Alle Abgänge und der letzte Abstand sind vorbei. */
    val fertig: Boolean,
)

object Abgangsuhr {

    /** Zeitpunkte aller Abgänge ab Start (ms), mit Serie (ab 1) und Nummer in der Serie (ab 1). */
    fun abgaenge(s: Serie): List<Triple<Long, Int, Int>> {
        val n = s.anzahl.coerceAtLeast(1)
        val out = mutableListOf<Triple<Long, Int, Int>>()
        var t = 0L
        for (serie in 1..s.serien.coerceAtLeast(1)) {
            for (nr in 1..n) {
                out += Triple(t, serie, nr)
                if (nr < n) t += s.abstandMs
            }
            // Die Pause beginnt, wenn der letzte Abgang seine Zeit hatte.
            t += s.abstandMs + s.pauseMs
        }
        return out
    }

    fun stand(s: Serie, vergangenMs: Long): UhrStand {
        val liste = abgaenge(s)
        val letzter = liste.lastOrNull { it.first <= vergangenMs } ?: liste.first()
        val naechster = liste.firstOrNull { it.first > vergangenMs }
        // Nach dem letzten Abgang läuft die Uhr noch einen Abstand lang (Ankunft), dann ist sie fertig.
        val ende = liste.last().first + s.abstandMs
        val fertig = naechster == null && vergangenMs >= ende
        val inPause = naechster != null && naechster.second != letzter.second && vergangenMs >= letzter.first + s.abstandMs
        return UhrStand(
            serie = letzter.second,
            abgang = letzter.third,
            inPause = inPause,
            bisNaechsterMs = when {
                fertig -> null
                naechster != null -> naechster.first - vergangenMs
                else -> ende - vergangenMs
            },
            fertig = fertig,
        )
    }

    /** Restzeit auf volle Sekunden aufgerundet, als "1:05" bzw. "0:09". */
    fun text(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 999) / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }
}

/** Gestoppte Zeit einer Bahn: Zwischenzeiten in ms seit Start, die letzte ist der Anschlag. */
data class Bahn(val nr: Int, val schwimmerId: String? = null, val strecke: String = "", val zwischen: List<Long> = emptyList(), val fertig: Boolean = false) {
    val endzeitMs: Long? get() = if (fertig) zwischen.lastOrNull() else null
}

object Stoppuhr {
    /** ms → Hundertstel, wie Schwimmzeiten gespeichert werden (abgeschnitten wie bei Handzeit). */
    fun hundertstel(ms: Long): Int = (ms / 10).toInt()

    /** Teilzeiten (Abstände zwischen den Zwischenzeiten). */
    fun teilzeiten(b: Bahn): List<Long> = b.zwischen.mapIndexed { i, t -> t - (b.zwischen.getOrNull(i - 1) ?: 0L) }
}
