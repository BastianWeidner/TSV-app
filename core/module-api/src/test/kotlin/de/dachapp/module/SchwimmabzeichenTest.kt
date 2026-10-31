package de.dachapp.module

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchwimmabzeichenTest {

    @Test
    fun alteNamenUndVarianten() {
        assertEquals("Bronze", Schwimmabzeichen.zu("Freischwimmer")?.name)
        assertEquals("Silber", Schwimmabzeichen.zu("Deutsches Schwimmabzeichen Silber")?.name)
        assertEquals("Seepferdchen", Schwimmabzeichen.zu("seepferdchen ")?.name)
        assertNull(Schwimmabzeichen.zu("Junior-Retter"))
    }

    @Test
    fun ueberMehrereTermineBestanden() {
        val a = Schwimmabzeichen.zu("Seepferdchen")!!
        var stand = emptyMap<String, Map<String, String>>()
        stand = Schwimmabzeichen.setzen(stand, a.name, "regeln", LocalDate.of(2026, 10, 1))
        stand = Schwimmabzeichen.setzen(stand, a.name, "heraufholen", LocalDate.of(2026, 10, 15))
        assertFalse(Schwimmabzeichen.fertig(stand, a))
        assertNull(Schwimmabzeichen.bestandenAm(stand, a))
        stand = Schwimmabzeichen.setzen(stand, a.name, "sprung25", LocalDate.of(2026, 10, 8))
        assertTrue(Schwimmabzeichen.fertig(stand, a))
        assertEquals(LocalDate.of(2026, 10, 15), Schwimmabzeichen.bestandenAm(stand, a))
        stand = Schwimmabzeichen.setzen(stand, a.name, "heraufholen", null)
        assertFalse(Schwimmabzeichen.fertig(stand, a))
    }

    @Test
    fun bestelllisteZaehltNurFertige() {
        val see = Schwimmabzeichen.ALLE.first()
        val voll = see.teile.associate { it.id to "2026-10-20" }
        val text = bestellliste("Kurs 1", listOf(
            Pruefling("1", "Anna A", mapOf(see.name to voll), emptySet()),
            Pruefling("2", "Ben B", mapOf(see.name to mapOf("regeln" to "2026-10-20")), emptySet()),
        ))
        assertTrue("Seepferdchen: 1" in text)
        assertTrue("Anna A (20.10.2026)" in text)
        assertFalse("Ben B" in text)
    }
}

class TrainingsbuchTest {
    @Test
    fun hinUndZurueck() {
        val e = listOf(Trainingsbuch.Eintrag("b", 2000, 300, "Beine; schwer"), Trainingsbuch.Eintrag("a", 1000, 250, ""))
        val zurueck = Trainingsbuch.lesen(Trainingsbuch.text(e) + "kaputt\n")
        assertEquals(listOf("a", "b"), zurueck.map { it.id })
        assertEquals("Beine, schwer", zurueck[1].name)
        assertEquals(300, zurueck[1].kcal)
    }
}
