package de.dachapp.schwimmgruppen.logic

import de.dachapp.abgleich.Abgleich
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class TeilenTest {
    @Test
    fun zweiTrainerErfassenAnwesenheitGetrennt() {
        val anna = Mitglied("m1", "Anna", "A", gruppen = setOf("g"))
        val ben = Mitglied("m2", "Ben", "B", gruppen = setOf("g"))
        val start = Bestand(mitglieder = listOf(anna, ben), gruppen = listOf(Gruppe("g", "Gruppe")))
        var datei: Abgleich.Stand? = null

        fun abgleich(b: Bestand, vorher: Abgleich.Stand, jetzt: Long): Pair<Bestand, Abgleich.Stand> {
            val r = Abgleich.abgleich(Teilen.APP, Teilen.zerlegen(b), vorher, datei, jetzt, Teilen.nurVorgaben(b))
            if (r.schreiben) datei = Abgleich.decode(Abgleich.encode(r.stand), Teilen.APP)
            return Teilen.zusammensetzen(b, r.stand.werte()) to r.stand
        }

        var (geraet1, v1) = abgleich(start, Abgleich.Stand(), 1000)
        var (geraet2, v2) = abgleich(Bestand(), Abgleich.Stand(), 2000)
        assertEquals(listOf("m1", "m2"), geraet2.mitglieder.map { it.id })
        assertEquals(listOf("g"), geraet2.gruppen.map { it.id })

        val mo = LocalDate.of(2026, 11, 2)
        val mi = LocalDate.of(2026, 11, 4)
        geraet1 = geraet1.einheitSetzen(Einheit("g", mo, LocalTime.of(17, 0), LocalTime.of(18, 0), anwesend = setOf("m1")))
        geraet2 = geraet2.einheitSetzen(Einheit("g", mi, LocalTime.of(17, 0), LocalTime.of(18, 0), anwesend = setOf("m2")))
        abgleich(geraet1, v1, 3000).also { geraet1 = it.first; v1 = it.second }
        abgleich(geraet2, v2, 4000).also { geraet2 = it.first; v2 = it.second }
        abgleich(geraet1, v1, 5000).also { geraet1 = it.first; v1 = it.second }

        assertEquals(2, geraet1.einheiten.size)
        assertEquals(geraet1.einheiten.toSet(), geraet2.einheiten.toSet())
        assertEquals(geraet1.mitglieder, geraet2.mitglieder)
    }
}
