package de.dachapp.module

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SucheTest {
    @Test
    fun leerPasstImmer() = assertTrue(Suche.passt("  ", "Luftfilter"))

    @Test
    fun alleWoerterInIrgendeinemFeld() {
        assertTrue(Suche.passt("luft 261", "Luftfilter", "Stihl MS 261"))
        assertFalse(Suche.passt("luft 362", "Luftfilter", "Stihl MS 261"))
    }

    @Test
    fun umlauteUndAkzente() {
        assertTrue(Suche.passt("ruckwarts", "Rückwärts"))
        assertTrue(Suche.passt("Schaerfen", "Kette schärfen"))
        assertTrue(Suche.passt("cafe", "Café"))
    }
}
