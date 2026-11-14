package de.dachapp.module

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields

/**
 * Wochenrückblick: Jede Unter-App, die etwas über die Woche zu sagen hat, liefert über [SubApp.woche] einen
 * [Wochenbeitrag]. Die Dach-App sammelt die Beiträge, zeigt sie gemeinsam an und meldet sie sonntags. Unter-Apps
 * lesen dafür nur ihre eigenen Daten; sie kennen einander weiterhin nicht.
 */
data class Wochenbeitrag(
    /** Überschrift des Abschnitts, z. B. „Training“. */
    val titel: String,
    /** Emoji vor der Überschrift. */
    val symbol: String,
    /** Ganz kurz für die Benachrichtigung, z. B. „3 Trainings“ oder „Ø 2.950 kcal“. */
    val kurz: String,
    val kennzahlen: List<Kennzahl> = emptyList(),
    /** Was gut lief: ein Satz je Erfolg, gern mit Emoji vorn. Zählt für die Überschrift des Rückblicks. */
    val lob: List<String> = emptyList(),
    /** Sanfte Hinweise für nächste Woche, ohne erhobenen Zeigefinger. */
    val tipps: List<String> = emptyList(),
)

/**
 * Eine Zahl im Rückblick. [unter]: Zusatz wie „Ziel 180 g“ oder „Vorwoche 3“. [anteil]: Fortschritt 0..1 für einen
 * Balken (null = keiner). [trend]: +1 besser als die Vorwoche, -1 schlechter, 0 gleich oder kein Vergleich.
 */
data class Kennzahl(
    val name: String,
    val wert: String,
    val unter: String? = null,
    val anteil: Float? = null,
    val trend: Int = 0,
)

object Wochenrueckblick {
    /** Montag der Woche, in der [tag] liegt. */
    fun montag(tag: LocalDate): LocalDate = tag.with(DayOfWeek.MONDAY)

    /** Beginn eines Tages in ms (lokale Zeit). */
    fun ms(tag: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long = tag.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Ende des Rückblicks: Ende der Woche, höchstens aber [jetzt] (eine laufende Woche zählt bis jetzt). */
    fun bis(montag: LocalDate, jetzt: Long, zone: ZoneId = ZoneId.systemDefault()): Long = minOf(ms(montag.plusWeeks(1), zone), jetzt)

    /** Wie viele Tage der Woche schon begonnen haben (1 bis 7). */
    fun tage(montag: LocalDate, jetzt: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val von = ms(montag, zone)
        var n = 0
        while (n < 7 && ms(montag.plusDays(n.toLong()), zone) < maxOf(jetzt, von + 1)) n++
        return n.coerceIn(1, 7)
    }

    private val MONATE = listOf("Jan.", "Feb.", "März", "Apr.", "Mai", "Juni", "Juli", "Aug.", "Sep.", "Okt.", "Nov.", "Dez.")

    /** „KW 46 · 9.–15. Nov.“ */
    fun titel(montag: LocalDate): String {
        val so = montag.plusDays(6)
        val kw = montag.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val von = if (montag.month == so.month) "${montag.dayOfMonth}." else "${montag.dayOfMonth}. ${MONATE[montag.monthValue - 1]}"
        return "KW $kw · $von–${so.dayOfMonth}. ${MONATE[so.monthValue - 1]}"
    }

    /** Überschrift nach der Zahl der Erfolge aller Beiträge. */
    fun ueberschrift(beitraege: List<Wochenbeitrag>): String {
        val n = beitraege.sumOf { it.lob.size }
        return when {
            beitraege.isEmpty() -> "Noch nichts eingetragen"
            n >= 5 -> "Was für eine Woche! 🔥"
            n >= 3 -> "Starke Woche! 💪"
            n >= 1 -> "Gute Woche – es geht voran 🌱"
            else -> "Neue Woche, neue Chance ✨"
        }
    }

    private val SPRUECHE = listOf(
        "Dranbleiben schlägt Perfektion.",
        "Kleine Schritte jede Woche ergeben große Sprünge im Jahr.",
        "Erfolg ist die Summe kleiner Anstrengungen, Tag für Tag wiederholt.",
        "Du musst nicht jede Woche perfekt sein – nur dabei.",
        "Gewohnheiten machen den Unterschied, nicht einzelne Tage.",
        "Stark wird man nicht an einem Tag, sondern Woche für Woche.",
        "Was du diese Woche säst, erntest du in ein paar Monaten.",
        "Gönn dir den Sonntag – nächste Woche geht's weiter.",
    )

    /** Ein Spruch je Woche (gleiche Woche, gleicher Spruch). */
    fun spruch(montag: LocalDate): String = SPRUECHE[Math.floorMod(montag.toEpochDay() / 7, SPRUECHE.size.toLong()).toInt()]

    /** Kurzer Text für die Benachrichtigung. */
    fun meldungText(beitraege: List<Wochenbeitrag>): String = beitraege.joinToString(" · ") { it.kurz }

    /** Aufgeklappter Text der Benachrichtigung: Erfolge, dann der Spruch. */
    fun meldungLangtext(beitraege: List<Wochenbeitrag>, montag: LocalDate): String = buildString {
        append(meldungText(beitraege))
        val lob = beitraege.flatMap { it.lob }
        if (lob.isNotEmpty()) append("\n\n").append(lob.take(6).joinToString("\n"))
        append("\n\n").append(spruch(montag))
    }

    /** Trend gegenüber der Vorwoche: +1 wenn [jetzt] spürbar (mehr als [schwelle] relativ) höher, -1 wenn niedriger. */
    fun trend(jetzt: Double, vorher: Double?, schwelle: Double = 0.05, mehrIstBesser: Boolean = true): Int {
        if (vorher == null || vorher <= 0 || jetzt <= 0) return 0
        val r = jetzt / vorher - 1
        val t = when {
            r > schwelle -> 1
            r < -schwelle -> -1
            else -> 0
        }
        return if (mehrIstBesser) t else -t
    }
}
