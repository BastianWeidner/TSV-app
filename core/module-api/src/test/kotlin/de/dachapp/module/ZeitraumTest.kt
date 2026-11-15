package de.dachapp.module

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class ZeitraumTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val tag = LocalDate.of(2026, 11, 15) // Sonntag

    @Test
    fun grenzenJeArt() {
        assertEquals(LocalDate.of(2026, 11, 9), Zeitraum.um(Rueckblickart.WOCHE, tag).von)
        assertEquals(LocalDate.of(2026, 11, 1), Zeitraum.um(Rueckblickart.MONAT, tag).von)
        assertEquals(LocalDate.of(2026, 10, 1), Zeitraum.um(Rueckblickart.QUARTAL, tag).von)
        assertEquals(LocalDate.of(2026, 7, 1), Zeitraum.um(Rueckblickart.HALBJAHR, tag).von)
        assertEquals(LocalDate.of(2026, 1, 1), Zeitraum.um(Rueckblickart.JAHR, tag).von)
        assertEquals(LocalDate.of(2027, 1, 1), Zeitraum.um(Rueckblickart.QUARTAL, tag).bis)
        assertEquals(30, Zeitraum.um(Rueckblickart.MONAT, tag).laenge)
        assertEquals(LocalDate.of(2026, 10, 1), Zeitraum.um(Rueckblickart.MONAT, tag).vorher().von)
        assertEquals(LocalDate.of(2026, 1, 1), Zeitraum.um(Rueckblickart.HALBJAHR, tag).vorher().von)
    }

    @Test
    fun titel() {
        assertEquals("November 2026", Zeitraum.um(Rueckblickart.MONAT, tag).titel)
        assertEquals("Q4 2026 · Okt.–Dez.", Zeitraum.um(Rueckblickart.QUARTAL, tag).titel)
        assertEquals("2. Halbjahr 2026", Zeitraum.um(Rueckblickart.HALBJAHR, tag).titel)
        assertEquals("2026", Zeitraum.um(Rueckblickart.JAHR, tag).titel)
        assertEquals("KW 46 · 9.–15. Nov.", Zeitraum.um(Rueckblickart.WOCHE, tag).titel)
    }

    @Test
    fun endenAmGroessteZuerst() {
        // 31.12.2026 ist ein Donnerstag: Jahr, Halbjahr, Quartal, Monat
        assertEquals(
            listOf(Rueckblickart.JAHR, Rueckblickart.HALBJAHR, Rueckblickart.QUARTAL, Rueckblickart.MONAT),
            Zeitraum.endenAm(LocalDate.of(2026, 12, 31)),
        )
        assertEquals(listOf(Rueckblickart.WOCHE), Zeitraum.endenAm(tag))
        assertEquals(listOf(Rueckblickart.MONAT, Rueckblickart.WOCHE), Zeitraum.endenAm(LocalDate.of(2026, 5, 31)))
        assertEquals(emptyList(), Zeitraum.endenAm(LocalDate.of(2026, 11, 18)))
        assertEquals(listOf(Rueckblickart.MONAT), Zeitraum.beginnenAm(LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun laufenderZeitraumZaehltBisJetzt() {
        val z = Zeitraum.um(Rueckblickart.MONAT, tag)
        val jetzt = tag.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(15, z.tage(jetzt, zone))
        val g = z.tagesgrenzen(jetzt, zone)
        assertEquals(16, g.size)
        assertEquals(jetzt, g.last())
        assertEquals(15 / 7.0, z.wochen(jetzt, zone), 1e-9)
    }
}
