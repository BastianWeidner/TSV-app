package de.dachapp.module

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpdateVersionTest {
    @Test
    fun praefixJeVariante() {
        assertEquals("dach-app", UpdateVersion.praefix("de.dachapp"))
        assertEquals("schwimmen", UpdateVersion.praefix("de.dachapp.schwimmen"))
        assertEquals("gym", UpdateVersion.praefix("de.dachapp.gym"))
    }

    @Test
    fun namenLesen() {
        assertEquals(listOf(1, 27, 0), UpdateVersion.ausName("schwimmen-1.27.0.apk", "schwimmen"))
        assertEquals(listOf(1, 27, 0), UpdateVersion.ausName("Dach-App-1.27.0.APK", "dach-app"))
        assertNull(UpdateVersion.ausName("schwimmen-1.27.0.apk", "gym"))
        assertNull(UpdateVersion.ausName("schwimmen-1.27.0 (1).apk", "schwimmen"))
        assertNull(UpdateVersion.ausName("schwimmen-1.27.0.apk.zip", "schwimmen"))
        assertNull(UpdateVersion.ausName("version.json", "dach-app"))
    }

    @Test
    fun vergleichen() {
        assertEquals(0, UpdateVersion.vergleich(listOf(1, 27), listOf(1, 27, 0)))
        assertEquals(1, UpdateVersion.vergleich(listOf(1, 27, 0), listOf(1, 26, 9)))
        assertEquals(-1, UpdateVersion.vergleich(listOf(1, 9, 0), listOf(1, 10, 0)))
        assertEquals(listOf(1, 26, 1), UpdateVersion.zerlegen("1.26.1-debug"))
    }

    @Test
    fun neuesteEigeneVariante() {
        val namen = listOf("dach-app-1.26.1.apk", "dach-app-1.27.0.apk", "dach-app-1.10.0.apk", "gym-1.30.0.apk", "dach-app-1.27.0 (1).apk")
        assertEquals("dach-app-1.27.0.apk" to listOf(1, 27, 0), UpdateVersion.neueste(namen, { it }, "dach-app", "1.26.1"))
        assertNull(UpdateVersion.neueste(namen, { it }, "dach-app", "1.27.0"))
        assertNull(UpdateVersion.neueste(namen, { it }, "schwimmen", "1.0.0"))
    }
}
