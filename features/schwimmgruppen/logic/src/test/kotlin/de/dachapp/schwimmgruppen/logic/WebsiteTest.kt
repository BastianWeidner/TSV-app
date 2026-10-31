package de.dachapp.schwimmgruppen.logic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WebsiteTest {
    @Test
    fun vorgabenErgebenDieKennungenDerWebsite() {
        val ids = Vorgaben.gruppen().map(Website::webId).toSet()
        assertEquals(setOf("gruppe-1", "gruppe-2", "gruppe-3", "gruppe-4", "kader", "wassergymnastik", "erwachsenenschwimmen"), ids)
    }

    @Test
    fun eigeneGruppenBekommenLesbareKennung() {
        assertEquals("gruppe-5", Website.webId(Gruppe("7f3a9c12-0000-4000-8000-000000000000", "Gruppe V Montag")))
        assertEquals("aquafitness-ue60", Website.webId(Gruppe("7f3a9c12-0000-4000-8000-000000000001", "Aquafitness Ü60")))
    }

    @Test
    fun zeitenJsonWieAufDerWebsite() {
        val text = Website.zeitenJson(Bestand(), LocalDate.of(2026, 10, 31))
        val o = Json.parseToJsonElement(text).jsonObject
        assertEquals("2026-10-31", o["stand"]!!.jsonPrimitive.content)
        val termine = o["termine"]!!.jsonArray.map { it.jsonObject }
        // Wie zeiten.json der Website, nur ohne die Schwimmkurse (die kommen aus der Schwimmkurs-App).
        assertEquals(12, termine.size)
        val erster = termine.first()
        assertEquals("Dienstag", erster["tag"]!!.jsonPrimitive.content)
        assertEquals("wassergymnastik", erster["gruppe"]!!.jsonPrimitive.content)
        assertEquals("16:30", erster["von"]!!.jsonPrimitive.content)
        assertEquals("17:00", erster["bis"]!!.jsonPrimitive.content)
        val samstag = termine.filter { it["tag"]!!.jsonPrimitive.content == "Samstag" }.map { it["gruppe"]!!.jsonPrimitive.content }
        assertEquals(listOf("gruppe-1", "gruppe-2", "kader"), samstag)
    }

    @Test
    fun keinePersonendaten() {
        val b = Bestand(
            mitglieder = listOf(Mitglied("m1", "Muster", "Max", gruppen = setOf("kader"))),
            gruppen = listOf(Gruppe("kader", "Kader", zeiten = listOf(Trainingszeit(2, LocalTime.of(18, 15), LocalTime.of(19, 30))))),
        )
        val text = Website.zeitenJson(b, LocalDate.of(2026, 10, 31))
        assertFalse("Muster" in text || "Max" in text)
    }
}
