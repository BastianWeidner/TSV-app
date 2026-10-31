package de.dachapp.schwimmkurs.logic

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KontoauszugTest {

    private val kurs = Kurs(id = "k1", name = "Seepferdchen", kuerzel = "SK04 2526", preisCent = 9650)
    private val kinder = listOf(
        Kind(id = "a", vorname = "Max", nachname = "Müller", email = "mueller@x.de"),
        Kind(id = "b", vorname = "Mia", nachname = "Huber", email = "huber@x.de"),
        Kind(id = "c", vorname = "Ben", nachname = "Huber", email = "andere@x.de"),
    )
    private val bestand = Bestand(kinder = kinder, kurse = listOf(kurs.copy(teilnehmer = listOf("a", "b", "c"))))
    private val einladungen = Einladungen.erstellen(bestand)

    private fun lesen(csv: String) = Kontoauszug.buchungen(TabellenLeser.lesen(csv.toByteArray()))

    @Test
    fun betraegeLesen() {
        assertEquals(9650L, Kontoauszug.cent("96,50"))
        assertEquals(123456L, Kontoauszug.cent("1.234,56"))
        assertEquals(-9650L, Kontoauszug.cent("-96,50 EUR"))
        assertEquals(9650L, Kontoauszug.cent("96.50"))
        assertEquals(null, Kontoauszug.cent(""))
    }

    @Test
    fun sparkasseCsv() {
        val b = lesen(
            """
            "Auftragskonto";"Buchungstag";"Valutadatum";"Buchungstext";"Verwendungszweck";"Beguenstigter/Zahlungspflichtiger";"Kontonummer/IBAN";"BIC (SWIFT-Code)";"Betrag";"Waehrung";"Info"
            "DE03720692200502514931";"14.10.25";"14.10.25";"GUTSCHR. UEBERWEISUNG";"SK04 2526 - Mueller Max";"Anna Mueller";"DE11";"X";"96,50";"EUR";"Umsatz gebucht"
            "DE03720692200502514931";"15.10.25";"15.10.25";"FOLGELASTSCHRIFT";"Strom";"Stadtwerke";"DE22";"Y";"-40,00";"EUR";"Umsatz gebucht"
            """.trimIndent(),
        )
        assertEquals(1, b.size)
        assertEquals(LocalDate.of(2025, 10, 14), b[0].datum)
        assertEquals("Anna Mueller", b[0].name)
        assertEquals(9650L, b[0].betragCent)
    }

    @Test
    fun vrBankMitVorspannUndSollHaben() {
        val b = lesen(
            """
            Umsatzanzeige;;;
            Konto;DE03 7206 9220 0502 5149 31;;
            Buchungstag;Name Zahlungsbeteiligter;Verwendungszweck;Betrag;Soll/Haben
            16.10.2025;HUBER THOMAS;SK042526 HUBER MIA BEN;193,00;H
            16.10.2025;Verein;Rueckbuchung;10,00;S
            """.trimIndent(),
        )
        assertEquals(1, b.size)
        assertEquals("SK042526 HUBER MIA BEN", b[0].zweck)
    }

    @Test
    fun zuordnungUeberKuerzelNameUndBetrag() {
        val buchungen = listOf(
            Buchung(2, LocalDate.of(2025, 10, 14), "Anna Mueller", "SK04 2526 - Mueller Max", 9650),
            Buchung(3, LocalDate.of(2025, 10, 16), "HUBER THOMAS", "SK042526 HUBER MIA BEN", 18000),
            Buchung(4, LocalDate.of(2025, 10, 17), "Fremd", "Schwimmkurs fuer Lena", 9650),
            Buchung(5, LocalDate.of(2025, 10, 18), "Stadtwerke", "Gutschrift", 1200),
        )
        val a = Kontoauszug.abgleichen(einladungen, buchungen, emptySet())
        assertEquals(2, a.treffer.size)
        val mueller = a.treffer.first { it.einladung.nachname == "Müller" }
        assertTrue(mueller.sicher)
        assertEquals("", mueller.hinweis)
        // Familie Huber hat zwei Mailadressen, also zwei Einladungen; die Buchung geht an genau eine davon.
        val huber = a.treffer.first { it.einladung.nachname == "Huber" }
        assertTrue(huber.hinweis.startsWith("Betrag"))
        assertEquals(listOf(4), a.unklar.map { it.zeile })
    }

    @Test
    fun ohneNachnameKeineZuordnung() {
        val buchungen = listOf(Buchung(2, null, "Jemand", "SK04 2526", 9650))
        val a = Kontoauszug.abgleichen(einladungen, buchungen, emptySet())
        assertTrue(a.treffer.isEmpty())
        assertEquals(1, a.unklar.size)
    }

    @Test
    fun schonBezahlteBleibenDraussen() {
        val buchungen = listOf(Buchung(2, null, "Anna Mueller", "SK04 2526 - Mueller Max", 9650))
        val schluessel = einladungen.first { it.nachname == "Müller" }.familienSchluessel
        val a = Kontoauszug.abgleichen(einladungen, buchungen, setOf(schluessel))
        assertFalse(a.treffer.any { it.einladung.familienSchluessel == schluessel })
    }

    @Test
    fun zahlungenWerdenGesichert() {
        val b = bestand.copy(zahlungen = mapOf("e:x@y.de" to Zahlung(LocalDate.of(2025, 10, 1), 9650, "auszug")))
        assertEquals(b.zahlungen, BestandCodec.decode(BestandCodec.encode(b)).zahlungen)
    }
}
