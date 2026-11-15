package de.dachapp.module

import kotlin.test.Test
import kotlin.test.assertEquals

class HolzbuchTest {
    @Test
    fun alteUndNeueZeilen() {
        val text = "100;3\n" + Holzbuch.zeile(Holzbuch.Eintrag(200, 0, 75.0, fremd = true)) + "kaputt\n" + Holzbuch.zeile(Holzbuch.Eintrag(150, 2))
        val l = Holzbuch.lesen(text)
        assertEquals(listOf(100L, 150L, 200L), l.map { it.zeit })
        assertEquals(Holzbuch.Eintrag(100, 3), l[0])
        assertEquals(Holzbuch.Eintrag(150, 2), l[1])
        assertEquals(Holzbuch.Eintrag(200, 0, 75.0, true), l[2])
        assertEquals("150;2\n", Holzbuch.zeile(Holzbuch.Eintrag(150, 2)))
    }
}
