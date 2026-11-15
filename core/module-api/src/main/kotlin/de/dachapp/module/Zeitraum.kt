package de.dachapp.module

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

/** Länge eines Rückblicks. Die Texte passen in Sätze wie „mehr als [vorher]“ oder „[ganz] ohne Alkohol“. */
enum class Rueckblickart(
    val titel: String,
    /** „Wochenrückblick“ … */
    val rueckblick: String,
    /** „Vorwoche“ … (kurz, für Kennzahlen). */
    val vorKurz: String,
    /** „letzte Woche“ … (im Satz, z. B. „mehr als letzte Woche“). */
    val vorher: String,
    /** „diese Woche“ … */
    val diese: String,
    /** „die ganze Woche“ … */
    val ganz: String,
    /** „der Vorwoche“ … (nach „gegenüber“). */
    val gegenueber: String,
) {
    WOCHE("Woche", "Wochenrückblick", "Vorwoche", "letzte Woche", "diese Woche", "die ganze Woche", "der Vorwoche"),
    MONAT("Monat", "Monatsrückblick", "Vormonat", "im Vormonat", "diesen Monat", "den ganzen Monat", "dem Vormonat"),
    QUARTAL("Quartal", "Quartalsrückblick", "Vorquartal", "im Vorquartal", "dieses Quartal", "das ganze Quartal", "dem Vorquartal"),
    HALBJAHR("Halbjahr", "Halbjahresrückblick", "Vorhalbjahr", "im Halbjahr davor", "dieses Halbjahr", "das ganze Halbjahr", "dem Halbjahr davor"),
    JAHR("Jahr", "Jahresrückblick", "Vorjahr", "im Vorjahr", "dieses Jahr", "das ganze Jahr", "dem Vorjahr"),
}

/** Ein Rückblick-Zeitraum vom Tag [von] (einschließlich) bis [bis] (ausschließlich). */
data class Zeitraum(val art: Rueckblickart, val von: LocalDate) {
    val bis: LocalDate = when (art) {
        Rueckblickart.WOCHE -> von.plusWeeks(1)
        Rueckblickart.MONAT -> von.plusMonths(1)
        Rueckblickart.QUARTAL -> von.plusMonths(3)
        Rueckblickart.HALBJAHR -> von.plusMonths(6)
        Rueckblickart.JAHR -> von.plusYears(1)
    }

    /** Ganze Länge in Tagen. */
    val laenge: Int get() = ChronoUnit.DAYS.between(von, bis).toInt()

    /** Letzter Tag des Zeitraums. */
    val letzterTag: LocalDate get() = bis.minusDays(1)

    fun vorher(): Zeitraum = um(-1)
    fun danach(): Zeitraum = um(1)

    fun um(n: Int): Zeitraum = when (art) {
        Rueckblickart.WOCHE -> Zeitraum(art, von.plusWeeks(n.toLong()))
        Rueckblickart.MONAT -> Zeitraum(art, von.plusMonths(n.toLong()))
        Rueckblickart.QUARTAL -> Zeitraum(art, von.plusMonths(3L * n))
        Rueckblickart.HALBJAHR -> Zeitraum(art, von.plusMonths(6L * n))
        Rueckblickart.JAHR -> Zeitraum(art, von.plusYears(n.toLong()))
    }

    fun msVon(zone: ZoneId = ZoneId.systemDefault()): Long = Wochenrueckblick.ms(von, zone)

    /** Ende in ms, höchstens [jetzt] (ein laufender Zeitraum zählt bis jetzt). */
    fun msBis(jetzt: Long, zone: ZoneId = ZoneId.systemDefault()): Long = minOf(Wochenrueckblick.ms(bis, zone), jetzt)

    /** Begonnene Tage (1 bis [laenge]). */
    fun tage(jetzt: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val ende = maxOf(jetzt, msVon(zone) + 1)
        var n = 0
        while (n < laenge && Wochenrueckblick.ms(von.plusDays(n.toLong()), zone) < ende) n++
        return n.coerceIn(1, laenge)
    }

    /** Beginn jedes begonnenen Tages in ms und als letztes das Ende (für tageweise Rechnungen). */
    fun tagesgrenzen(jetzt: Long, zone: ZoneId = ZoneId.systemDefault()): List<Long> =
        (0 until tage(jetzt, zone)).map { Wochenrueckblick.ms(von.plusDays(it.toLong()), zone) } + msBis(jetzt, zone)

    /** Begonnene Wochen als Kommazahl (mindestens 1), um Wochenziele auf längere Zeiträume umzurechnen. */
    fun wochen(jetzt: Long, zone: ZoneId = ZoneId.systemDefault()): Double = (tage(jetzt, zone) / 7.0).coerceAtLeast(1.0)

    /** „KW 46 · 9.–15. Nov.“, „November 2026“, „Q4 2026 · Okt.–Dez.“, „2. Halbjahr 2026“, „2026“. */
    val titel: String
        get() = when (art) {
            Rueckblickart.WOCHE -> Wochenrueckblick.titel(von)
            Rueckblickart.MONAT -> "${MONATE_LANG[von.monthValue - 1]} ${von.year}"
            Rueckblickart.QUARTAL -> "Q${(von.monthValue - 1) / 3 + 1} ${von.year} · ${MONATE[von.monthValue - 1]}–${MONATE[letzterTag.monthValue - 1]}"
            Rueckblickart.HALBJAHR -> "${if (von.monthValue <= 6) 1 else 2}. Halbjahr ${von.year}"
            Rueckblickart.JAHR -> "${von.year}"
        }

    /** Ein Wert, der je Zeitraum gleich bleibt (für Sprüche). */
    val nummer: Long get() = von.toEpochDay() / 7 + art.ordinal * 7919L

    companion object {
        private val MONATE = listOf("Jan.", "Feb.", "März", "Apr.", "Mai", "Juni", "Juli", "Aug.", "Sep.", "Okt.", "Nov.", "Dez.")
        private val MONATE_LANG = listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")

        /** Zeitraum der Art [art], in dem [tag] liegt. */
        fun um(art: Rueckblickart, tag: LocalDate): Zeitraum = Zeitraum(
            art,
            when (art) {
                Rueckblickart.WOCHE -> tag.with(DayOfWeek.MONDAY)
                Rueckblickart.MONAT -> tag.withDayOfMonth(1)
                Rueckblickart.QUARTAL -> LocalDate.of(tag.year, (tag.get(IsoFields.QUARTER_OF_YEAR) - 1) * 3 + 1, 1)
                Rueckblickart.HALBJAHR -> LocalDate.of(tag.year, if (tag.monthValue <= 6) 1 else 7, 1)
                Rueckblickart.JAHR -> LocalDate.of(tag.year, 1, 1)
            },
        )

        /** Zeiträume, die an [tag] enden (letzter Tag), größte zuerst. */
        fun endenAm(tag: LocalDate): List<Rueckblickart> =
            Rueckblickart.entries.filter { um(it, tag).letzterTag == tag }.sortedByDescending { it.ordinal }

        /** Zeiträume, die an [tag] beginnen, größte zuerst. */
        fun beginnenAm(tag: LocalDate): List<Rueckblickart> =
            Rueckblickart.entries.filter { um(it, tag).von == tag }.sortedByDescending { it.ordinal }
    }
}
