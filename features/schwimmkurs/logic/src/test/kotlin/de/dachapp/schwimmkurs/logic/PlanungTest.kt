package de.dachapp.schwimmkurs.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlanungTest {

    private val start = LocalDate.of(2026, 11, 7) // ein Samstag
    private val heute = LocalDate.of(2026, 10, 20)

    private fun termine(von: Int, bis: Int, ab: LocalDate = start, anzahl: Int = 6) =
        Terminserie.woechentlich(ab, DayOfWeek.SATURDAY, anzahl, LocalTime.of(von, 0), LocalTime.of(bis, 0))

    private fun kind(
        id: String, alter: Int, mail: String = "$id@x.de", ort: String = "", abz: Set<String> = emptySet(),
        anmeldung: LocalDate? = null, vorname: String = id.replaceFirstChar { it.uppercase() },
    ) = Kind(id, vorname, "N$id", start.minusYears(alter.toLong()).minusDays(10), email = mail, ort = ort, abzeichen = abz, anmeldedatum = anmeldung)

    private val seepferdchen = Kurs("see", "Seepferdchen", termine(9, 10), plaetze = 3, minAlter = 5, maxAlter = 7, ziel = "Seepferdchen", preisCent = 9000)
    private val bronze = Kurs("bronze", "Bronze", termine(10, 11), plaetze = 3, minAlter = 6, voraussetzung = "Seepferdchen", ziel = "Bronze", preisCent = 9000)

    @Test
    fun alterUndAbzeichenEntscheiden() {
        val e = Einstellungen()
        assertEquals(null, Kursplaner.grund(kind("a", 5), seepferdchen, e))
        assertEquals("zu jung (4)", Kursplaner.grund(kind("a", 4), seepferdchen, e))
        assertEquals("zu alt (8)", Kursplaner.grund(kind("a", 8), seepferdchen, e))
        assertEquals("hat Seepferdchen schon", Kursplaner.grund(kind("a", 6, abz = setOf("Seepferdchen")), seepferdchen, e))
        assertEquals("Seepferdchen fehlt", Kursplaner.grund(kind("a", 8), bronze, e))
        assertEquals("hat Bronze schon", Kursplaner.grund(kind("a", 8, abz = setOf("Silber")), bronze, e))
        assertEquals("Geburtsdatum fehlt", Kursplaner.grund(Kind("x", "X", "Y"), seepferdchen, e))
    }

    @Test
    fun wohnortVorrangUndAnmeldedatumBestimmenReihenfolge() {
        val kinder = listOf(
            kind("a", 5, ort = "Nachbarort", anmeldung = LocalDate.of(2026, 1, 1)),
            kind("b", 5, ort = "Heimatstadt", anmeldung = LocalDate.of(2026, 5, 1)),
            kind("c", 5, ort = "Weit weg", anmeldung = LocalDate.of(2025, 1, 1)),
            kind("d", 5, ort = "Heimatstadt", anmeldung = LocalDate.of(2026, 3, 1)),
        )
        val b = Bestand(kinder = kinder, kurse = listOf(seepferdchen), einstellungen = Einstellungen(wohnortVorrang = listOf("Heimatstadt", "Nachbarort")))
        val v = Kursplaner.vorschlagen(b, heute)
        assertEquals(listOf("d", "b", "a"), v.zuteilung["see"])
        assertEquals(listOf("c" to "alle passenden Kurse voll"), v.warteliste)
    }

    @Test
    fun wohnortAusBemerkungZaehltOhneEingetragenenOrt() {
        val k = Kind("1", "A", "B", notiz = "hat nachgefragt, KiGa Willmatshofen")
        assertEquals(1, Kursplaner.wohnortRang(k, listOf("Fischach", "Willmatshofen")))
        assertEquals("Willmatshofen", Kursplaner.wohnort(k, listOf("Fischach", "Willmatshofen")))
        assertEquals(2, Kursplaner.wohnortRang(k.copy(notiz = "Fischacher Straße"), listOf("Fischach", "Willmatshofen")))
        assertEquals(3, Kursplaner.wohnortRang(k.copy(ort = "Augsburg"), listOf("Fischach", "Willmatshofen")))
    }

    @Test
    fun einzugsgebietMitOrtsteilenUndAuswaertigenZuletzt() {
        val gebiet = Vorlagen.EINZUGSGEBIET
        fun rang(notiz: String, ort: String = "") = Kursplaner.wohnortRang(Kind("1", "A", "B", ort = ort, notiz = notiz), gebiet, Vorlagen.AUSSERHALB)
        assertEquals(0, rang("KiGa Willmatshofen"))
        assertEquals(0, rang("Fischach Schläulestr"))
        assertEquals(1, rang("hat bereits nachgefragt. Aus Langenneufnach"))
        assertEquals(2, rang("hat bereits nachgefragt - Gesserthausen"))
        assertEquals(2, rang("Margertshausen"))
        assertEquals(4, rang("Mickhausen"))
        assertEquals(5, rang("Dinkelscherben"))
        assertEquals(6, rang("Kutzenhausen"))
        assertEquals(9, rang("Rommelsried"))
        assertEquals(10, rang("hat bereits nachgefragt!")) // unbekannt = neutral
        assertEquals(11, rang("Ziemetshausen"))
        assertEquals(11, rang("", ort = "Königsbrunn"))
        assertEquals(0, rang("", ort = "Reitenbuch"))
        assertEquals("Willmatshofen (Fischach)", Kursplaner.wohnort(Kind("1", "A", "B", notiz = "gemeinsam mit Heidi, Willmatshofen"), gebiet))
        assertEquals("Walkertshofen", Kursplaner.wohnort(Kind("1", "A", "B", notiz = "Walkertshofen"), gebiet, Vorlagen.AUSSERHALB))
    }

    @Test
    fun gemeindeDannAlterDannEinschulung() {
        fun k(id: String, alter: Int, notiz: String, einschulung: String = "") =
            kind(id, alter).copy(notiz = notiz, einschulung = einschulung, listenplatz = id.last().code)
        val kinder = listOf(
            k("a", 7, "Ziemetshausen"),
            k("b", 5, "Fischach"),
            k("c", 6, "Langenneufnach", einschulung = "2027"),
            k("d", 6, "Langenneufnach", einschulung = "2026 evtl. 2027"),
            k("e", 7, "hat nachgefragt"),
            k("f", 6, "Willmatshofen"),
        )
        val kurs = Kurs("k", "Kurs", termine(9, 10), plaetze = 5, ziel = "Seepferdchen")
        val b = Bestand(kinder = kinder, kurse = listOf(kurs), einstellungen = Einstellungen(maxAltersspanne = 0))
        val v = Kursplaner.vorschlagen(b, heute)
        assertEquals(listOf("f", "b", "d", "c", "e"), v.zuteilung["k"])
        assertEquals("a", v.warteliste.single().first)
    }

    @Test
    fun alteStandardlisteWirdErgaenzt() {
        val alt = Vorlagen.EINZUGSGEBIET.take(6)
        val e = Vorlagen.aktualisieren(Einstellungen(wohnortVorrang = alt, orteAusserhalb = listOf("Mittelneufnach", "Walkertshofen", "Scherstetten", "Kutzenhausen", "Rommelsried", "Ziemetshausen", "Augsburg", "Neusäß", "Zusmarshausen", "Bobingen", "Schwabmünchen", "Stadtbergen", "Diedorf")))
        assertEquals(Vorlagen.EINZUGSGEBIET, e.wohnortVorrang)
        assertEquals(Vorlagen.AUSSERHALB, e.orteAusserhalb)
    }

    @Test
    fun geaenderteListeBekommtNachbarorteEinmalig() {
        val eigene = Einstellungen(
            wohnortVorrang = listOf("Fischach: Willmatshofen", "Gessertshausen", "Walkertshofen: Neuschwand"),
            orteAusserhalb = listOf("Mittelneufnach", "Ziemetshausen", " Kutzenhausen"),
        )
        val e = Vorlagen.aktualisieren(eigene)
        assertEquals(listOf("Fischach: Willmatshofen", "Gessertshausen", "Walkertshofen: Neuschwand", "Kutzenhausen", "Mittelneufnach", "Rommelsried"), e.wohnortVorrang)
        assertEquals(listOf("Ziemetshausen"), e.orteAusserhalb)
        // Danach bleibt eine bewusst gekürzte Liste so, wie sie ist, auch nach Sichern und Laden.
        val gekuerzt = e.copy(wohnortVorrang = listOf("Fischach"))
        assertEquals(listOf("Fischach"), BestandCodec.decode(BestandCodec.encode(Bestand(einstellungen = gekuerzt))).einstellungen.wohnortVorrang)
    }

    @Test
    fun einschulungAusFeldOderGeschaetzt() {
        assertEquals(2026, Kursplaner.einschulungsjahr(Kind("1", "A", "B", einschulung = "2026 evtl. 2027")))
        assertEquals(2027, Kursplaner.einschulungsjahr(Kind("1", "A", "B", geburtsdatum = LocalDate.of(2021, 9, 30))))
        assertEquals(2028, Kursplaner.einschulungsjahr(Kind("1", "A", "B", geburtsdatum = LocalDate.of(2021, 10, 1))))
    }

    @Test
    fun geschwisterGetrenntKommenInVerschiedeneKurse() {
        val zweiter = seepferdchen.copy(id = "see2", name = "Seepferdchen 2")
        val kinder = listOf(kind("a", 5, mail = "f@x.de").copy(geschwisterGetrennt = true), kind("b", 6, mail = "f@x.de"))
        val v = Kursplaner.vorschlagen(Bestand(kinder = kinder, kurse = listOf(seepferdchen, zweiter)), heute)
        assertEquals(2, v.zuteilung.size)
        assertEquals(1, v.zuteilung.values.first().size)
    }

    @Test
    fun altersgruppenWerdenNichtGemischt() {
        val ohneGrenzen = listOf(
            Kurs("k1", "Kurs 1", termine(9, 10), plaetze = 4, ziel = "Seepferdchen"),
            Kurs("k2", "Kurs 2", termine(10, 11), plaetze = 4, ziel = "Seepferdchen"),
        )
        // Reihenfolge der Anmeldeliste wechselt zwischen jung und alt; ein 3-Jähriger ist zu jung.
        val kinder = listOf(3, 8, 5, 8, 6, 7, 5, 6).mapIndexed { i, a -> kind("k$i", a).copy(listenplatz = i) }
        val v = Kursplaner.vorschlagen(Bestand(kinder = kinder, kurse = ohneGrenzen), heute)
        val alterJeKurs = v.zuteilung.mapValues { (_, ids) -> ids.map { id -> kinder.single { it.id == id }.alterAm(start)!! } }
        alterJeKurs.values.forEach { a -> assertTrue(a.max() - a.min() <= 2, "Kurs mit Altern $a") }
        assertEquals(7, v.anzahl)
        assertTrue(v.warteliste.single().second.startsWith("kein passender Kurs: zu jung (3)"))
    }

    @Test
    fun anmeldelisteBestimmtReihenfolgeBeiGleichemVorrang() {
        val kinder = listOf(kind("z", 6).copy(nachname = "A", listenplatz = 2), kind("y", 6).copy(nachname = "B", listenplatz = 1))
        val v = Kursplaner.vorschlagen(Bestand(kinder = kinder, kurse = listOf(seepferdchen.copy(plaetze = 1))), heute)
        assertEquals(listOf("y"), v.zuteilung["see"])
    }

    @Test
    fun plzMitSternPasst() {
        val k = Kind("1", "A", "B", plz = "12345")
        assertEquals(1, Kursplaner.wohnortRang(k, listOf("99999", "123*")))
        assertEquals(2, Kursplaner.wohnortRang(k, listOf("99999", "124*")))
    }

    @Test
    fun geschwisterKommenZusammenOderAnDieselbenTage() {
        val zweiter = seepferdchen.copy(id = "see2", name = "Seepferdchen 2", termine = termine(9, 10, start.plusWeeks(8)), plaetze = 5)
        val kinder = listOf(
            kind("a", 5, mail = "fam@x.de"),
            kind("b", 6, mail = "fam@x.de"),
            kind("c", 7, mail = "fam2@x.de", abz = setOf("Seepferdchen")),
            kind("d", 5, mail = "fam2@x.de"),
        )
        val b = Bestand(kinder = kinder, kurse = listOf(seepferdchen, zweiter, bronze))
        val v = Kursplaner.vorschlagen(b, heute)
        // a und b passen beide in Seepferdchen; in "see2" sind mehr Plätze frei, aber beide Kurse passen gleich gut.
        val ab = v.zuteilung.filterValues { "a" in it }.keys.single()
        assertTrue("b" in v.zuteilung.getValue(ab))
        // c muss in Bronze (Samstage ab 7.11.), d soll deshalb ins Seepferdchen an denselben Tagen statt in see2.
        assertEquals(listOf("c"), v.zuteilung["bronze"])
        assertTrue("d" in v.zuteilung.getValue("see"))
    }

    @Test
    fun geschwisterWerdenGetrenntWennKeinGemeinsamerKursReicht() {
        val klein = seepferdchen.copy(plaetze = 1)
        val kinder = listOf(kind("a", 5, mail = "f@x.de"), kind("b", 6, mail = "f@x.de"))
        val v = Kursplaner.vorschlagen(Bestand(kinder = kinder, kurse = listOf(klein)), heute)
        assertEquals(1, v.anzahl)
        assertEquals(1, v.warteliste.size)
    }

    @Test
    fun bestehendeZuteilungUndPausierteBleiben() {
        val kinder = listOf(kind("a", 5), kind("b", 5).copy(pausiert = true), kind("c", 5), kind("alt", 9))
        val b = Bestand(kinder = kinder, kurse = listOf(seepferdchen.copy(teilnehmer = listOf("a"))))
        val v = Kursplaner.vorschlagen(b, heute)
        assertEquals(mapOf("see" to listOf("c")), v.zuteilung)
        assertEquals("alt", v.warteliste.single().first)
        assertTrue("zu alt" in v.warteliste.single().second)
        val neu = Kursplaner.uebernehmen(b, v)
        assertEquals(listOf("a", "c"), neu.kurs("see")!!.teilnehmer)
    }

    @Test
    fun einladungProFamilieMitRabattUndPlatzhaltern() {
        val kinder = listOf(
            kind("ben", 6, mail = "Mama@x.de; papa@x.de").copy(nachname = "Müller", eltern = "Sabine Müller"),
            kind("anna", 9, mail = "mama@x.de", abz = setOf("Seepferdchen")).copy(nachname = "Müller"),
            kind("solo", 5).copy(nachname = "Albers"),
            kind("ohne", 5).copy(nachname = "Zander"),
        )
        val e = Einstellungen(iban = "DE00 1234", geschwisterRabattCent = 1000)
        val b = Bestand(
            kinder = kinder,
            kurse = listOf(
                seepferdchen.copy(teilnehmer = listOf("ben", "solo"), kuerzel = "SK04 2526"),
                bronze.copy(teilnehmer = listOf("anna"), kuerzel = "BR01", zahlungsfrist = LocalDate.of(2026, 10, 31)),
            ),
            einstellungen = e,
        )
        val liste = Einladungen.erstellen(b)
        assertEquals(listOf("Solo", "Anna und Ben"), liste.map { it.kinderNamen })
        val m = liste[1]
        assertEquals(listOf("mama@x.de", "papa@x.de"), m.empfaenger)
        assertEquals(17000, m.betragCent)
        assertEquals("Seepferdchen-Schwimmkurs beim TSV Fischach – Ihr Kursplatz für Anna und Ben", m.betreff)
        assertTrue(m.text.startsWith("Liebe Familie Müller,"))
        assertTrue("dass wir Anna und Ben aufgrund unserer Warteliste jeweils einen Platz" in m.text)
        assertTrue("## Kurstermine und Ort\nSeepferdchen – für Ben\n6 Termine, jeweils Samstag 09:00–10:00 Uhr:\n• Sa. 07.11.2026\n" in m.text, m.text)
        assertTrue("Bronze – für Anna\n6 Termine" in m.text)
        assertTrue("• Ben: 90,00 €" in m.text)
        assertTrue("• Geschwisterrabatt: −10,00 €\n• Gesamtbetrag: 170,00 €" in m.text)
        assertTrue("Betrag: 170,00 €" in m.text)
        assertTrue("Verwendungszweck: SK04 2526/BR01 - Müller Anna, Müller Ben" in m.text)
        assertTrue("bis spätestens Samstag, 31.10.2026 (Zahlungseingang)" in m.text)
        assertTrue("Der erste Kurstag ist Samstag, 07.11.2026, 09:00 Uhr." in m.text)
        assertTrue("{" !in m.text)
        assertTrue("## " !in m.textOhneMarken)
        assertTrue("Anna Müller, geb. " in m.haftungText)
        assertTrue(liste[0].text.startsWith("Liebe Familie Albers,"))
        assertTrue("dass wir Solo aufgrund unserer Warteliste einen Platz" in liste[0].text)
        assertTrue("innerhalb von 14 Tagen (Zahlungseingang)" in liste[0].text)
    }

    @Test
    fun preisAusTerminenUndPausenWieImVerein() {
        // 10 Samstage wie im Kurs SK04: ohne 31.01. und 21.02.
        val tage = listOf("2026-01-10", "2026-01-17", "2026-01-24", "2026-02-07", "2026-02-14", "2026-02-28", "2026-03-07", "2026-03-14", "2026-03-21", "2026-03-28")
        val kurs = Kurs(
            "sk04", "Seepferdchen Samstag 11 Uhr", tage.map { Termin(LocalDate.parse(it), LocalTime.of(11, 0), LocalTime.of(11, 45)) },
            ort = "Hallenbad Fischach", kuerzel = "SK04 2526", teilnehmer = listOf("a"), hinweis = "Am 28.03. dürfen Eltern mit ins Wasser.",
        )
        val k = Kind("a", "Anna", "Huber", LocalDate.of(2020, 5, 1), email = "h@x.de")
        val m = Einladungen.erstellen(Bestand(kinder = listOf(k), kurse = listOf(kurs))).single()
        assertEquals(12500, m.betragCent)
        assertTrue("10 Termine, jeweils Samstag 11:00–11:45 Uhr, Hallenbad Fischach:" in m.text)
        assertTrue("Achtung: Am 31.01. und 21.02. ist kein Schwimmkurs.\nAm 28.03. dürfen Eltern mit ins Wasser." in m.text)
        assertTrue("• Kursgebühr: 10 × 12,00 € à 45 Minuten Unterricht\n• Eintrittspauschale: 10 × 0,50 €\n• Gesamtbetrag: 125,00 €" in m.text)
        assertTrue("Verwendungszweck: SK04 2526 - Huber Anna" in m.text)
        assertTrue("IBAN: DE03 7206 9220 0502 5149 31" in m.text)
    }

    @Test
    fun alteStandardtexteWerdenBeimLadenErsetzt() {
        val alt = Einstellungen(
            mailText = Vorlagen.MAIL.replace(
                "unsere Haftungserklärung. Mit der Überweisung des Kursbeitrags erklären Sie sich mit den Bedingungen der Haftungserklärung einverstanden. Der erste Kurstag ist {ErsterTermin}.",
                "die Haftungserklärung. Bitte bringen Sie die Haftungserklärung unterschrieben zum ersten Kurstag ({ErsterTermin}) mit.",
            ),
            haftungText = Vorlagen.HAFTUNG.substringBefore("\n\nMit der Überweisung"),
        )
        assertTrue("unterschrieben" in alt.mailText)
        val neu = BestandCodec.decode(BestandCodec.encode(Bestand(einstellungen = alt))).einstellungen
        assertEquals(Vorlagen.MAIL, neu.mailText)
        assertEquals(Vorlagen.HAFTUNG, neu.haftungText)
    }

    @Test
    fun unbekannterPlatzhalterBleibtStehen() {
        assertEquals("Hallo X {Unbekannt}", Einladungen.einsetzen("Hallo {Eltern} {Unbekannt}", mapOf("Eltern" to "X")))
    }

    @Test
    fun sicherungHinUndZurueck() {
        val b = Bestand(
            kinder = listOf(kind("a", 5, abz = setOf("Seepferdchen"))),
            kurse = listOf(seepferdchen.copy(teilnehmer = listOf("a"))),
            einstellungen = Einstellungen(iban = "DE1", vorrangStand = 2),
            versendet = mapOf("e:a@x.de" to "2026-10-20T10:00"),
        )
        assertEquals(b, BestandCodec.decode(BestandCodec.encode(b)))
        assertFailsWith<IllegalArgumentException> { BestandCodec.decode("{\"version\": 99}") }
        assertFailsWith<IllegalArgumentException> { BestandCodec.decode("Hallo") }
    }

    @Test
    fun kurslisteAlsCsv() {
        val b = Bestand(kinder = listOf(kind("a", 5).copy(notiz = "kann; tauchen")), kurse = listOf(seepferdchen.copy(teilnehmer = listOf("a"))))
        val csv = Kursliste.csv(b.kurse[0], b)
        assertTrue(csv.startsWith("﻿Vorname;Nachname;"))
        assertTrue("A;Na;28.10.2021;5;;;;a@x.de;\"kann; tauchen\"" in csv)
    }

    @Test
    fun terminserieBeginntAmWochentag() {
        val t = Terminserie.woechentlich(LocalDate.of(2026, 11, 4), DayOfWeek.SATURDAY, 3, LocalTime.of(9, 0), LocalTime.of(9, 45))
        assertEquals(listOf(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 21)), t.map { it.datum })
    }
}
