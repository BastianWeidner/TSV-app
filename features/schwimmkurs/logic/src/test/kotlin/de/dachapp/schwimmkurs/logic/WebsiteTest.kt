package de.dachapp.schwimmkurs.logic

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WebsiteTest {
    private val heute = LocalDate.of(2026, 10, 31)

    private fun samstage(start: LocalDate, n: Int) =
        (0 until n).map { Termin(start.plusWeeks(it.toLong()), LocalTime.of(11, 0), LocalTime.of(11, 45)) }

    @Test
    fun kursartWieAufDerWebsite() {
        assertEquals("Seepferdchenkurs", Website.kursart("Seepferdchen 2"))
        assertEquals("Aufbaukurs", Website.kursart("Aufbaukurs 1"))
        assertEquals("Robby-Kurs", Website.kursart("Robby-Kurs"))
    }

    @Test
    fun kurslisteInDenSechsSpalten() {
        val b = Bestand(
            kinder = listOf(Kind(id = "c1", vorname = "Mia", nachname = "Muster")),
            kurse = listOf(
                Kurs("k1", "Seepferdchen 1", samstage(LocalDate.of(2026, 10, 17), 10), plaetze = 8, teilnehmer = listOf("c1")),
                Kurs("k2", "Seepferdchen 2", samstage(LocalDate.of(2026, 11, 7), 10), plaetze = 1, teilnehmer = listOf("c1")),
                Kurs("k0", "Seepferdchen 0", samstage(LocalDate.of(2026, 6, 6), 10), plaetze = 8),
            ),
        )
        val zeilen = Website.kurseCsv(b, heute).trimEnd().lines()
        assertEquals(
            listOf(
                Website.KOPF,
                "Seepferdchenkurs,Sa 11:00–11:45,läuft,7,offen,10 Termine à 45 Minuten",
                "Seepferdchenkurs,Sa 11:00–11:45,07.11.2026,0,warteliste,10 Termine à 45 Minuten",
            ),
            zeilen,
        )
        val text = zeilen.joinToString("\n")
        assertFalse("Mia" in text || "Muster" in text)
    }
}
