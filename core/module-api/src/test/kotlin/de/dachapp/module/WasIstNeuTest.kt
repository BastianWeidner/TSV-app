package de.dachapp.module

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WasIstNeuTest {
    private val liste = listOf(
        WasIstNeu.Version(
            55, "1.27.0",
            listOf(
                WasIstNeu.Punkt("Feedback-Knopf"),
                WasIstNeu.Punkt("Direkt installieren", varianten = setOf("dach-app", "gym")),
                WasIstNeu.Punkt("Café neu", module = setOf("cafe")),
            ),
        ),
        WasIstNeu.Version(56, "1.28.0", listOf(WasIstNeu.Punkt("Nur Café", module = setOf("cafe")))),
        WasIstNeu.Version(57, "1.29.0", listOf(WasIstNeu.Punkt("Für alle"))),
    )

    private fun texte(v: List<WasIstNeu.Version>) = v.map { it.name to it.punkte.map { p -> p.text } }

    @Test
    fun frischInstalliertZeigtNichts() {
        assertTrue(WasIstNeu.zuZeigen(liste, 55, null, true, "gym", setOf("tracker")).isEmpty())
    }

    @Test
    fun updateOhneGespeichertenWertZeigtNurAktuelle() {
        val r = WasIstNeu.zuZeigen(liste, 55, null, false, "gym", setOf("tracker", "krafttraining"))
        assertEquals(listOf("1.27.0" to listOf("Feedback-Knopf", "Direkt installieren")), texte(r))
    }

    @Test
    fun varianteUndModuleFiltern() {
        val schwimmen = WasIstNeu.zuZeigen(liste, 55, 54, false, "schwimmen", setOf("schwimmkurs"))
        assertEquals(listOf("1.27.0" to listOf("Feedback-Knopf")), texte(schwimmen))
        val alle = WasIstNeu.zuZeigen(liste, 55, 54, false, "dach-app", setOf("cafe", "tracker"))
        assertEquals(listOf("1.27.0" to listOf("Feedback-Knopf", "Direkt installieren", "Café neu")), texte(alle))
    }

    @Test
    fun uebersprungeneVersionenNeuesteZuerstOhneLeere() {
        val r = WasIstNeu.zuZeigen(liste, 57, 54, false, "gym", setOf("tracker"))
        assertEquals(listOf("1.29.0", "1.27.0"), r.map { it.name })
    }

    @Test
    fun schonGesehenZeigtNichts() {
        assertTrue(WasIstNeu.zuZeigen(liste, 55, 55, false, "dach-app", setOf("cafe")).isEmpty())
        // Zurückgestuft (gesehen höher als installiert): auch nichts.
        assertTrue(WasIstNeu.zuZeigen(liste, 55, 57, false, "dach-app", setOf("cafe")).isEmpty())
    }

    @Test
    fun hoechstensDreiVersionen() {
        val viele = (60L..65L).map { WasIstNeu.Version(it, "1.$it.0", listOf(WasIstNeu.Punkt("x"))) }
        val r = WasIstNeu.zuZeigen(viele, 65, 50, false, "gym", emptySet())
        assertEquals(listOf(65L, 64L, 63L), r.map { it.versionCode })
    }
}
