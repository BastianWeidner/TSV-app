package de.dachapp.schwimmleitung.logic

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchwimmleitungTest {

    private val heute = LocalDate.of(2026, 10, 21)

    /** Ausschnitt einer echten Schwimmkurs-Sicherung (Feldnamen wie dort, unbekannte Felder werden übergangen). */
    private val kursSicherung = """
        {
          "version": 1,
          "kinder": [
            {"id": "k1", "vorname": "Anna", "nachname": "A", "geburtsdatum": "2020-03-01", "ort": "Fischach", "abzeichen": ["Seepferdchen"], "anmeldedatum": "2026-06-01"},
            {"id": "k2", "vorname": "Ben", "nachname": "B", "geburtsdatum": "2020-12-01", "notiz": "KiGa Willmatshofen", "anmeldedatum": "2026-07-01"},
            {"id": "k3", "vorname": "Cem", "nachname": "C", "geburtsdatum": "2019-05-01", "ort": "Augsburg", "anmeldedatum": "2025-10-10"},
            {"id": "k4", "vorname": "Dora", "nachname": "D", "geburtsdatum": "2021-01-01", "ort": "Kutzenhausen", "anmeldedatum": "2026-09-15"},
            {"id": "k5", "vorname": "Emil", "nachname": "E", "geburtsdatum": "2022-02-01", "anmeldedatum": "2026-10-01"},
            {"id": "k6", "vorname": "Fea", "nachname": "F", "geburtsdatum": "2020-01-01", "pausiert": true}
          ],
          "kurse": [
            {"id": "sk1", "name": "SK01 2026", "plaetze": 4, "ziel": "Seepferdchen", "teilnehmer": ["k1", "k2"],
             "termine": [{"datum": "2026-09-19", "beginn": "11:00", "ende": "11:45"}, {"datum": "2026-09-26", "beginn": "11:00", "ende": "11:45"}]},
            {"id": "sk0", "name": "SK05 2025", "plaetze": 4, "preisCent": 9000, "teilnehmer": ["k3"],
             "termine": [{"datum": "2025-11-08", "beginn": "11:00", "ende": "11:45"}]}
          ],
          "einstellungen": {
            "gebuehrProTerminCent": 1200, "eintrittProTerminCent": 50, "mindestalter": 5,
            "wohnortVorrang": ["Fischach: Willmatshofen, Aretsried", "Langenneufnach", "Kutzenhausen"],
            "orteAusserhalb": ["Augsburg"]
          }
        }
    """.trimIndent()

    /** Ausschnitt einer Schwimmgruppen-Sicherung. Erstes erfasstes Training: Di 06.10.2026. */
    private val gruppenSicherung = """
        {
          "version": 2,
          "mitglieder": [
            {"id": "m1", "vorname": "Lisa", "nachname": "Lang", "geburtsdatum": "1990-01-01", "gruppen": ["kader"], "interesse": "JA",
             "erfolge": [{"id": "e1", "text": "Bronze", "art": "ABZEICHEN", "datum": "2026-10-10"}, {"id": "e2", "text": "1. Platz", "art": "WETTKAMPF", "datum": "2025-10-10"}],
             "zeiten": [
               {"id": "z1", "strecke": "50 m Freistil", "hundertstel": 4000, "datum": "2026-06-01"},
               {"id": "z2", "strecke": "50 m Freistil", "hundertstel": 3900, "datum": "2026-10-13"},
               {"id": "z3", "strecke": "50 m Freistil", "hundertstel": 3950, "datum": "2026-10-14"}
             ]},
            {"id": "m2", "vorname": "Tom", "nachname": "Tief", "geburtsdatum": "2010-01-01", "gruppen": ["kader"]},
            {"id": "m3", "vorname": "Ola", "nachname": "Ohne", "gruppen": []},
            {"id": "m4", "vorname": "Alt", "nachname": "Weg", "gruppen": ["kader"], "aktiv": false}
          ],
          "gruppen": [
            {"id": "kader", "name": "Kader", "art": "VEREIN", "plaetze": 12, "trainerIds": ["t1"],
             "zeiten": [{"wochentag": 2, "beginn": "18:15", "ende": "19:30"}]},
            {"id": "wassergymnastik", "name": "Wassergymnastik", "art": "EXTERN", "nurZaehlen": true,
             "zeiten": [{"wochentag": 4, "beginn": "16:30", "ende": "17:00"}]}
          ],
          "einheiten": [
            {"gruppeId": "kader", "datum": "2026-10-06", "beginn": "18:15", "ende": "19:30", "anwesend": ["m1", "m2"], "weitere": 1},
            {"gruppeId": "kader", "datum": "2026-10-13", "beginn": "18:15", "ende": "19:30", "anwesend": ["m1"], "vertretungen": {"t1": "t2"}},
            {"gruppeId": "kader", "datum": "2026-10-20", "beginn": "18:15", "ende": "19:30", "ausgefallen": true},
            {"gruppeId": "wassergymnastik", "datum": "2026-10-08", "beginn": "16:30", "ende": "17:00", "weitere": 9},
            {"gruppeId": "wassergymnastik", "datum": "2026-10-15", "beginn": "16:30", "ende": "17:00", "weitere": 11}
          ],
          "trainer": [
            {"id": "t1", "mitgliedId": "m1", "rang": "TRAINER", "schein": "Trainer C"},
            {"id": "t2", "mitgliedId": "m2", "rang": "JUNGTRAINER"}
          ]
        }
    """.trimIndent()

    private fun kurs() = (Import.lesen(kursSicherung, 1000) as Import.Ergebnis.Kurs).auszug
    private fun gruppen() = (Import.lesen(gruppenSicherung, 1000) as Import.Ergebnis.Gruppen).auszug

    @Test
    fun abgleichNeuererGewinntUndLoeschenKommtAn() {
        val a = LeitungDaten().setzen(Aufgabe("a1", "Leinen tauschen"), 100).setzen(Aufgabe("a2", "Plan drucken"), 100)
        val b = a.setzen(Aufgabe("a1", "Leinen tauschen bis Freitag"), 200).aufgabeLoeschen("a2", 300)
        val c = a.setzen(Aufgabe("a3", "Neu"), 150)
        val r = Abgleich.abgleich(c, b)
        assertTrue(r.schreiben)
        assertEquals("Leinen tauschen bis Freitag", r.daten.aufgabe("a1")?.titel)
        assertNull(r.daten.aufgabe("a2"))
        assertNotNull(r.daten.aufgabe("a3"))
        // Zweiter Abgleich mit dem Ergebnis: nichts mehr zu schreiben.
        assertFalse(Abgleich.abgleich(r.daten, r.daten).schreiben)
    }

    @Test
    fun aenderungGewinntAuchBeiNachgehenderUhr() {
        val a = LeitungDaten().setzen(Material("m", "Bretter"), 5_000)
        val b = a.setzen(a.materialStueck("m")!!.copy(anzahl = 20), 1_000)
        assertEquals(20, Abgleich.zusammenfuehren(a, b).materialStueck("m")?.anzahl)
    }

    @Test
    fun codecPruefFormat() {
        val d = LeitungDaten().setzen(Person("p", "Bastian"), 1)
        assertEquals(d, LeitungCodec.decode(LeitungCodec.encode(d)))
        assertNull(LeitungCodec.decode("   "))
        assertFailsWith<IllegalArgumentException> { LeitungCodec.decode(kursSicherung) }
    }

    @Test
    fun importErkenntDateiart() {
        assertTrue(Import.lesen(kursSicherung, 1) is Import.Ergebnis.Kurs)
        assertTrue(Import.lesen(gruppenSicherung, 1) is Import.Ergebnis.Gruppen)
        assertFailsWith<IllegalArgumentException> { Import.lesen("""{"bohnen": []}""", 1) }
        assertFailsWith<IllegalArgumentException> { Import.lesen(LeitungCodec.encode(LeitungDaten()), 1) }
        assertFailsWith<IllegalArgumentException> { Import.lesen("kein json", 1) }
    }

    @Test
    fun kursAuszugOhneNamenMitHerkunft() {
        val a = kurs()
        val k = a.kinder.associateBy { it.id }
        assertEquals(Herkunft.EINZUGSGEBIET, k["k1"]?.herkunft)
        assertEquals("Fischach", k["k2"]?.gemeinde) // aus der Bemerkung
        assertEquals(Herkunft.AUSWAERTS, k["k3"]?.herkunft)
        assertEquals(Herkunft.NACHBARORTE, k["k4"]?.herkunft)
        assertEquals(Herkunft.OHNE_ORT, k["k5"]?.herkunft)
        assertEquals(2 * 1250L, a.kurse.first { it.id == "sk1" }.preisProKindCent)
        assertEquals(9000L, a.kurse.first { it.id == "sk0" }.preisProKindCent)
        assertFalse(LeitungCodec.encode(LeitungDaten().setzen(a, 1)).contains("Anna"))
    }

    @Test
    fun kursKennzahlenJeSaison() {
        val saison = Zeitraum.saison(heute)
        val z = Kennzahlen.kurse(kurs(), saison)
        assertEquals(1, z.kurse)
        assertEquals(50, z.auslastung)
        assertEquals(5000L, z.gebuehrenCent) // 2 Termine × 12,50 € × 2 Kinder
        assertEquals(mapOf("Seepferdchen" to 1), z.abzeichen)
        assertEquals(mapOf(6 to 1, 5 to 1), z.alter)
        // Anmeldungen 01.06. und 01.07. bis 19.09.: 15,7 und 11,4 Wochen, Median 13,6.
        assertEquals("13,6", Format.zahl(z.wartezeitWochen!!))
        assertEquals(2, z.neueAnmeldungen) // 15.09. und 01.10.
        val v = Kennzahlen.kurse(kurs(), saison.vorjahr())
        assertEquals(1, v.kurse)
        assertEquals(mapOf(Herkunft.AUSWAERTS to 1), v.herkunft)
        val w = Kennzahlen.warteliste(kurs(), heute)
        assertEquals(Warteliste(gesamt = 2, imMindestalter = 1, pausiert = 1), w)
    }

    @Test
    fun gruppenKennzahlen() {
        val a = gruppen()
        assertEquals("Lisa Lang", a.trainer("t1")?.name)
        val g = Kennzahlen.gruppen(a, Zeitraum.saison(heute), heute)
        val kader = g.gruppen.first { it.name == "Kader" }
        assertEquals(2, kader.aktive)
        assertEquals(2, kader.gehalten)
        assertEquals(1, kader.ausgefallen)
        assertEquals(0, kader.offen)
        assertEquals("2,0", Format.zahl(kader.durchschnitt))
        val wg = g.gruppen.first { it.extern }
        assertEquals(20, wg.teilnahmen)
        // Training am 13.10. leitete die Vertretung Tom; am 06.10. Lisa. Ausfall zählt nicht.
        assertEquals(listOf("Lisa Lang" to 1, "Tom Tief" to 1), g.trainer)
        assertEquals(1, g.vertretungen)
        assertEquals(1, g.ohneVolljaehrige) // 13.10.: nur der 16-jährige Tom
        assertEquals(mapOf("Abzeichen" to 1), g.erfolge)
        assertEquals(1, g.verbesserungen) // 39,00 unterbietet 40,00; 39,50 nicht
        assertEquals(1, g.wettkampfInteresse)
        assertEquals(1, g.ohneGruppe)
        assertEquals(2, g.aktiveSchwimmer)
    }

    @Test
    fun planZaehltNichtErfassteTermineErstAbBeginn() {
        val a = gruppen()
        // Do 22.10. ist noch nicht vorbei; Di 27.10. Zukunft. Bis 21.10. ist nichts offen.
        val z = Zeitraum(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31), "x")
        val t = Kennzahlen.trainings(a, z)
        assertTrue(t.none { it.datum.isBefore(LocalDate.of(2026, 10, 6)) })
        val g = Kennzahlen.gruppen(a, z, LocalDate.of(2026, 10, 30))
        // Do 22.10. und 29.10. Wassergymnastik und Di 27.10. Kader ohne Eintrag.
        assertEquals(3, g.gruppen.sumOf { it.offen })
    }

    @Test
    fun defektWirdAufgabeUndErledigtSichMit() {
        var d = LeitungDaten().setzen(Material("leine", "Bahnleine 3"), 1)
        d = d.melden(Meldung("me", MeldungArt.DEFEKT, "Schwimmer gerissen", materialId = "leine", von = "Tablet", zeit = 10), heute, 10, "auf")
        assertEquals(Zustand.DEFEKT, d.materialStueck("leine")?.zustand)
        val a = d.aufgabe("auf")!!
        assertEquals("Defekt: Bahnleine 3", a.titel)
        assertEquals("me", a.meldungId)
        d = d.aufgabeErledigt("auf", heute, 20)
        assertTrue(d.meldung("me")!!.erledigt)
        assertEquals(Zustand.IN_ORDNUNG, d.materialStueck("leine")?.zustand)
        assertEquals(heute, d.aufgabe("auf")?.erledigtAm)
    }

    @Test
    fun vorkommnisOhneAufgabe() {
        val d = LeitungDaten().melden(Meldung("v", MeldungArt.VORKOMMNIS, "Kind ausgerutscht, Pflaster", zeit = 1), heute, 1, "x")
        assertNull(d.aufgabe("x"))
        assertEquals(1, d.sichtbareMeldungen.size)
    }

    @Test
    fun pin() {
        val e = Pin.setzen(Einstellungen(), "4711", salz = "abc")
        assertTrue(Pin.pruefen(e, "4711"))
        assertFalse(Pin.pruefen(e, "4712"))
        assertFalse(Pin.pruefen(Einstellungen(), "4711"))
        assertFalse(e.pinHash.contains("4711"))
        assertFalse(Pin.gueltig("12a4"))
        assertTrue(Pin.gueltig("1234"))
    }

    @Test
    fun zeitraeume() {
        val s = Zeitraum.saison(heute)
        assertEquals(LocalDate.of(2026, 9, 1), s.von)
        assertEquals(LocalDate.of(2027, 8, 31), s.bis)
        assertEquals("Saison 2026/27", s.name)
        assertEquals(LocalDate.of(2026, 8, 31), Zeitraum.saison(LocalDate.of(2026, 8, 31)).bis)
        assertEquals(LocalDate.of(2025, 9, 1), s.vorjahr().von)
        assertEquals("4. Quartal 2026", Zeitraum.quartal(heute).name)
        assertEquals(LocalDate.of(2026, 10, 31), Zeitraum.monat(heute).bis)
    }

    @Test
    fun betraege() {
        assertEquals(1250L, Format.cent("12,50"))
        assertEquals(1250L, Format.cent("12.50"))
        assertEquals(123450L, Format.cent("1.234,5"))
        assertEquals(123400L, Format.cent("1.234"))
        assertEquals(500L, Format.cent("5 €"))
        assertNull(Format.cent("abc"))
        assertNull(Format.cent("-3"))
        assertEquals("1.234,50 €", Format.euro(123450))
        assertEquals("−0,05 €", Format.euro(-5))
    }

    @Test
    fun nachweiseWarnen() {
        var d = LeitungDaten().setzen(Person("p", "Lisa"), 1)
        d = d.setzen(Nachweis("n1", "p", NachweisArt.ERSTE_HILFE, gueltigBis = heute.plusDays(30)), 1)
        d = d.setzen(Nachweis("n2", "p", NachweisArt.FUEHRUNGSZEUGNIS, gueltigBis = heute.minusDays(1)), 1)
        d = d.setzen(Nachweis("n3", "p", NachweisArt.KAMPFRICHTER), 1)
        val l = Kennzahlen.leitung(d, Zeitraum.saison(heute), heute)
        assertEquals(listOf("n1"), l.nachweiseBald.map { it.second.id })
        assertEquals(listOf("n2"), l.nachweiseAbgelaufen.map { it.second.id })
        // Person gelöscht: ihre Nachweise gehen mit.
        assertTrue(d.personLoeschen("p", 2).nachweiseVon("p").isEmpty())
    }

    @Test
    fun belegeSummen() {
        var d = LeitungDaten()
        d = d.setzen(Beleg("b1", heute, 1250, zweck = "Pflaster"), 1)
        d = d.setzen(Beleg("b2", heute, 3000, einnahme = true, zweck = "Kuchenverkauf"), 1)
        d = d.setzen(Beleg("b3", heute.minusYears(2), 999, zweck = "alt", uebergeben = true), 1)
        val l = Kennzahlen.leitung(d, Zeitraum.saison(heute), heute)
        assertEquals(1250L, l.ausgabenCent)
        assertEquals(3000L, l.einnahmenCent)
        assertEquals(2, l.nichtUebergeben)
        assertEquals(1750L, l.nichtUebergebenCent)
        d = d.uebergeben(listOf("b1", "b2"), heute, 2)
        assertEquals(0, Kennzahlen.leitung(d, Zeitraum.saison(heute), heute).nichtUebergeben)
        assertTrue(Listen.belege(d.sichtbareBelege).contains("-12,50;Pflaster"))
    }

    @Test
    fun trainerWerdenPersonenOhneDoppelte() {
        val (d, neu) = LeitungDaten().trainerUebernehmen(gruppen(), 1) { "p" + System.nanoTime() }
        assertEquals(2, neu)
        assertEquals(listOf("Lisa Lang", "Tom Tief"), d.aktivePersonen.map { it.name })
        assertEquals("Jungtrainer", d.aktivePersonen.last().rolle)
        val (d2, neu2) = d.trainerUebernehmen(gruppen(), 2) { "x" }
        assertEquals(0, neu2)
        assertEquals(2, d2.aktivePersonen.size)
    }

    @Test
    fun berichtUndProtokoll() {
        var d = LeitungDaten().setzen(kurs(), 1).setzen(gruppen(), 1)
        d = d.setzen(Person("p1", "Bastian"), 1).setzen(Person("p2", "Lisa"), 1)
        val b = Berichte.erstellen(d, Zeitraum.saison(heute), heute)
        assertEquals(listOf("Schwimmkurse", "Trainingsgruppen", "Angebote für Externe", "Trainereinsätze", "Erfolge und Zeiten", "Leitung"), b.teile.map { it.titel })
        val text = Berichte.text(b)
        assertTrue(text.contains("• Auslastung: 50 % (Vorjahr 25 %)"), text)
        assertTrue(text.contains("Wassergymnastik: Teilnahmen: 20 (Vorjahr 0)"), text)

        val s = Sitzung("s1", datum = heute, anwesend = listOf("p2", "p1"), entschuldigt = listOf("p9"), gaeste = "Kassenwart", leitungsbericht = "Hallenzeiten bleiben.", kennzahlen = text, offeneRunde = "Weihnachtsfeier")
        d = d.setzen(s, 1).setzen(Aufgabe("a", "Saal reservieren", personId = "p2", faellig = heute.plusDays(7), sitzungId = "s1"), 1)
        val teile = Protokoll.abschnitte(d, s)
        assertEquals(listOf("Datum", "Anwesenheiten", "Leitungsbericht", "Offene Runde", "Aufgaben aus der Sitzung"), teile.map { it.titel })
        assertEquals("Anwesend (3): Bastian, Lisa, Kassenwart", teile[1].text)
        assertTrue(teile[4].text.contains("Saal reservieren – Lisa – bis 28.10.2026"))
    }
}
