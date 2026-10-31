package de.dachapp.abgleich

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AbgleichTest {
    private fun w(vararg p: Pair<String, String>): Map<String, Map<String, JsonElement>> =
        mapOf("x" to p.associate { (id, t) -> id to JsonPrimitive(t) })

    /** Ein Gerät: hält seinen gemeinsamen Stand und tauscht über eine „Datei“ (String) aus. */
    private class Geraet(var daten: Map<String, Map<String, JsonElement>>) {
        var vorher = Abgleich.Stand()
        fun abgleichen(datei: StringBuilder, jetzt: Long) {
            val r = Abgleich.abgleich("test", daten, vorher, Abgleich.decode(datei.toString(), "test"), jetzt)
            if (r.schreiben) datei.setLength(0).also { datei.append(Abgleich.encode(r.stand)) }
            vorher = r.stand
            daten = r.stand.werte()
        }
    }

    @Test
    fun zweiGeraeteFuehrenAenderungenUndLoeschungenZusammen() {
        val datei = StringBuilder()
        val a = Geraet(w("1" to "Anna", "2" to "Ben"))
        a.abgleichen(datei, 1000)
        val b = Geraet(w())
        b.abgleichen(datei, 2000)
        assertEquals(a.daten, b.daten)

        // A ändert 1, B löscht 2 und legt 3 an.
        a.daten = w("1" to "Anna Neu", "2" to "Ben")
        b.daten = w("1" to "Anna", "3" to "Carla")
        a.abgleichen(datei, 3000)
        b.abgleichen(datei, 4000)
        a.abgleichen(datei, 5000)
        assertEquals(w("1" to "Anna Neu", "3" to "Carla"), a.daten)
        assertEquals(a.daten, b.daten)
    }

    @Test
    fun neuesGeraetUeberschreibtDieDateiNichtMitVorgaben() {
        val datei = StringBuilder()
        val a = Geraet(w("g1" to "Gruppe umbenannt"))
        a.abgleichen(datei, 1000)
        val b = Geraet(w("g1" to "Gruppe Vorgabe", "g9" to "Eigene"))
        b.abgleichen(datei, 9000)
        assertEquals(w("g1" to "Gruppe umbenannt", "g9" to "Eigene"), b.daten)
        a.abgleichen(datei, 9500)
        assertEquals(b.daten, a.daten)
    }

    @Test
    fun gleichstandEntscheidetAufAllenGeraetenGleich() {
        val x = Abgleich.Eintrag("1", 5, false, JsonPrimitive("a"))
        val y = Abgleich.Eintrag("1", 5, true, null)
        assertEquals(y, Zusammenfuehren.neuer(x, y))
        assertEquals(y, Zusammenfuehren.neuer(y, x))
    }

    @Test
    fun fremdeOderLeereDatei() {
        assertNull(Abgleich.decode("  ", "test"))
        assertFailsWith<IllegalArgumentException> { Abgleich.decode("""{"format":"dach-abgleich","app":"cafe"}""", "test") }
        assertFailsWith<IllegalArgumentException> { Abgleich.decode("""{"a":1}""", "test") }
    }

    @Test
    fun ohneAenderungWirdNichtGeschrieben() {
        val datei = StringBuilder()
        val a = Geraet(w("1" to "x"))
        a.abgleichen(datei, 1000)
        val r = Abgleich.abgleich("test", a.daten, a.vorher, Abgleich.decode(datei.toString(), "test"), 2000)
        assertFalse(r.schreiben)
        assertFalse(r.uebernehmen)
        assertTrue(Abgleich.decode(datei.toString(), "test")!!.arten.isNotEmpty())
    }
}
