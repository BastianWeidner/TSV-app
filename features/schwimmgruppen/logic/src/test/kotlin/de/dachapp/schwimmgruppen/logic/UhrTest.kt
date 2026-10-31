package de.dachapp.schwimmgruppen.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UhrTest {
    private val s = Serie(anzahl = 4, abstandMs = 90_000, serien = 2, pauseMs = 60_000)

    @Test
    fun abgaengeMitPause() {
        val a = Abgangsuhr.abgaenge(s).map { it.first / 1000 }
        // Serie 1: 0, 90, 180, 270; Pause bis 270+90+60 = 420; Serie 2: 420, 510, 600, 690.
        assertEquals(listOf(0L, 90, 180, 270, 420, 510, 600, 690), a)
    }

    @Test
    fun standWaehrendDerSerie() {
        val st = Abgangsuhr.stand(s, 100_000)
        assertEquals(1, st.serie)
        assertEquals(2, st.abgang)
        assertEquals(80_000L, st.bisNaechsterMs)
        assertFalse(st.inPause)
    }

    @Test
    fun pauseZwischenDenSerien() {
        val st = Abgangsuhr.stand(s, 380_000)
        assertTrue(st.inPause)
        assertEquals(40_000L, st.bisNaechsterMs)
        assertEquals(2, Abgangsuhr.stand(s, 420_000).serie)
    }

    @Test
    fun fertigNachDemLetztenAbstand() {
        assertEquals(10_000L, Abgangsuhr.stand(s, 770_000).bisNaechsterMs)
        val st = Abgangsuhr.stand(s, 780_000)
        assertTrue(st.fertig)
        assertNull(st.bisNaechsterMs)
    }

    @Test
    fun textUndHundertstel() {
        assertEquals("1:45", Abgangsuhr.text(105_000))
        assertEquals("0:01", Abgangsuhr.text(1))
        assertEquals(3852, Stoppuhr.hundertstel(38_529))
        assertEquals(listOf(30_000L, 32_000L), Stoppuhr.teilzeiten(Bahn(1, zwischen = listOf(30_000, 62_000))))
    }
}
