package de.dachapp.schwimmgruppen.logic

import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Beispieldateien aus der DSV-Beschreibung (Format 7 und 8), Zeilen wie im PDF umbrochen. */
class WettkampfTest {
    private fun datei(name: String) = javaClass.getResource("/dsv/$name")!!.readText()

    @Test
    fun ausschreibungFormat8() {
        val a = Dsv.ausschreibung(datei("wk8.dsv8"))
        assertEquals(8, a.version)
        assertEquals("EDV-Testwettkampf des SV NRW", a.veranstaltung)
        assertEquals("50", a.bahn)
        assertEquals("01.03.2002 18:00 Uhr", a.meldeschluss)
        assertEquals(2, a.abschnitte.size)
        assertEquals(LocalDate.of(2002, 3, 10), a.letzterTag)
        assertEquals(6, a.wettkaempfe.size)
        assertEquals("100 m Freistil", a.wettkaempfe.first().streckenText)
        assertEquals("4×100 m Brust", a.wettkaempfe.first { it.nr == 4 }.streckenText)
        assertEquals(250L, a.einzelMeldegeldCent)
        // Umbrochene Zeile (VERANSTALTUNGSORT … GER;…) wird zusammengesetzt, das Element danach bleibt eigenständig.
        assertEquals(7, a.wertungen.size)
    }

    @Test
    fun ausschreibungFormat7() {
        val a = Dsv.ausschreibung(datei("wk7.dsv7"))
        assertEquals(7, a.version)
        assertEquals(6, a.wettkaempfe.size)
    }

    @Test
    fun passendeWettkaempfeUndPflichtzeit() {
        val a = Dsv.ausschreibung(datei("wk8.dsv8"))
        // Frau, Jahrgang 1990: WK 1 (100 F W, V), 3 (200 S W), 5 (100 F W); WK 2 ist für Männer, 4 ist Staffel, 101 Finale.
        assertEquals(listOf(1, 3, 5), a.passendFuer("W", 1990).map { it.nr })
        assertEquals(listOf(2), a.passendFuer("M", 1990).map { it.nr })
        assertEquals(5600, a.pflichtzeit(a.wettkaempfe.first { it.nr == 1 }, "W", 1991))
        assertEquals(5500, a.pflichtzeit(a.wettkaempfe.first { it.nr == 1 }, "W", 1985))
        assertEquals(null, a.pflichtzeit(a.wettkaempfe.first { it.nr == 3 }, "W", 1990))
    }

    private val bestand = Bestand(
        mitglieder = listOf(
            Mitglied("m1", "Simone", "Keller", LocalDate.of(1990, 5, 1), geschlecht = "w", dsvId = "123456"),
            Mitglied("m2", "Lena", "Öztürk", LocalDate.of(2014, 2, 3), geschlecht = "w"),
        ),
    )

    @Test
    fun meldelisteSchreibenUndWiederLesen() {
        val a = Dsv.ausschreibung(datei("wk8.dsv8"))
        val wer = Meldender("TSV Fischach", "1234", 2, "Weidner, Bastian", "trainer@example.org")
        val text = Dsv.meldeliste(a, wer, bestand, listOf(Meldung("m1", mapOf(1 to 6102, 3 to 15810)), Meldung("m2", mapOf(5 to 0))))
        val el = Dsv.elemente(text)
        assertEquals("FORMAT", el.first().name)
        assertEquals("DATEIENDE", el.last().name)
        assertEquals(listOf("Vereinsmeldeliste", "8"), el.first().werte)
        assertEquals(listOf(1, 3, 5), el.filter { it.name == "WETTKAMPF" }.map { it[0].toInt() })
        // Vereinsmeldeliste ohne Bestenlisten-Zuordnung: 10 Attribute.
        assertEquals(10, el.first { it.name == "WETTKAMPF" }.werte.size)
        assertEquals(listOf("TSV Fischach", "1234", "2", "GER", "N"), el.first { it.name == "VEREIN" }.werte)
        val pn = el.filter { it.name == "PNMELDUNG" }
        assertEquals("Keller, Simone", pn[0][0])
        assertEquals("123456", pn[0][1])
        assertEquals("W", pn[0][3])
        assertEquals("1990", pn[0][4])
        assertEquals(10, pn[0].werte.size)
        assertEquals("0", pn[1][1])
        assertEquals(listOf("1", "1", "00:01:01,02"), el.first { it.name == "STARTPN" }.werte)
        assertEquals("00:00:00,00", el.last { it.name == "STARTPN" }[2])
        assertTrue(text.endsWith("DATEIENDE\r\n"))
        assertEquals("2002-03-10-Duisburg-TSVFischach-Me.DSV8", Dsv.meldeDateiname(a, "TSV Fischach"))
        // Format 7: VEREIN ohne Lastschrift-Attribut.
        val v7 = Dsv.elemente(Dsv.meldeliste(a.copy(version = 7), wer, bestand, listOf(Meldung("m1", mapOf(1 to 6102)))))
        assertEquals(4, v7.first { it.name == "VEREIN" }.werte.size)
    }

    @Test
    fun wettkampfergebnisliste() {
        val l = Dsv.ergebnisse(datei("pr8.dsv8"))
        assertEquals("EDV-Testwettkampf des SV NRW", l.veranstaltung)
        val simone = l.ergebnisse.filter { it.nachname == "Keller" }
        assertEquals(2, simone.size)
        assertEquals(6082, simone.first().hundertstel)
        assertEquals("100 m Freistil", simone.first().strecke)
        assertEquals(LocalDate.of(2002, 3, 9), simone.first().datum)
        // Staffeln werden übergangen.
        assertFalse(l.ergebnisse.any { it.strecke.contains("×") })
    }

    @Test
    fun vereinsergebnislisteUndUebernahme() {
        val l = Dsv.ergebnisse(datei("vpr8.dsv8"))
        assertEquals(2, l.ergebnisse.size)
        val t = Wettkampfabgleich.zuordnen(l, bestand, "1234", "")
        // Zwei Wertungen für denselben Start: eine Zeile mit dem besten Platz.
        assertEquals(1, t.size)
        assertEquals("m1", t.single().mitgliedId)
        assertEquals(1, t.single().ergebnis.platz)
        var n = 0
        val neu = Wettkampfabgleich.uebernehmen(bestand, t, l) { "id${n++}" }
        val m = neu.mitglied("m1")!!
        assertEquals(6082, m.zeiten.single().hundertstel)
        assertTrue(m.zeiten.single().wettkampf)
        assertEquals("1. Platz 100 m Freistil (Jahrgang 1990)", m.erfolge.single().text)
        // Noch einmal einlesen: schon da, nichts doppelt.
        val t2 = Wettkampfabgleich.zuordnen(l, neu, "1234", "")
        assertTrue(t2.single().schonDa)
    }

    @Test
    fun zuordnungUeberNamenUndUmlaute() {
        val e = Ergebnis("Lena Oeztuerk", "Lena", "Oeztuerk", "0", 2014, "W", "TSV Fischach", "", "50 m Brust", 5512, 3, "", "", LocalDate.of(2026, 11, 14))
        val t = Wettkampfabgleich.zuordnen(Ergebnisliste("Test", "Augsburg", "25", listOf(e)), bestand, "", "TSV Fischach Abteilung Schwimmen")
        assertEquals("m2", t.single().mitgliedId)
    }

    @Test
    fun gepackteDsv8z() {
        val roh = datei("wk8.dsv8").toByteArray()
        val zip = ByteArrayOutputStream().also { o ->
            ZipOutputStream(o).use { z -> z.putNextEntry(ZipEntry("2002-03-10-Duisburg-Wk.DSV8")); z.write(roh); z.closeEntry() }
        }.toByteArray()
        assertEquals(6, Dsv.ausschreibung(Dsv.text(zip)).wettkaempfe.size)
    }

    private val lenex = """<?xml version="1.0" encoding="UTF-8"?>
<LENEX version="3.0"><MEETS><MEET name="Herbstschwimmfest" city="Augsburg" course="SCM" deadline="2026-11-01">
<SESSIONS><SESSION number="1" date="2026-11-14" daytime="09:00"><EVENTS>
<EVENT eventid="11" number="1" gender="F" round="TIM"><SWIMSTYLE distance="50" stroke="BREAST" relaycount="1"/>
<AGEGROUPS><AGEGROUP agegroupid="1" agemin="11" agemax="12" name="Jahrgang 2014/15"><RANKINGS><RANKING place="3" resultid="501"/></RANKINGS></AGEGROUP></AGEGROUPS></EVENT>
<EVENT eventid="12" number="2" gender="M" round="TIM"><SWIMSTYLE distance="100" stroke="FREE" relaycount="1"/></EVENT>
</EVENTS></SESSION></SESSIONS>
<CLUBS><CLUB name="TSV Fischach" code="4321"><ATHLETES>
<ATHLETE firstname="Lena" lastname="Öztürk" birthdate="2014-02-03" gender="F" license="234567"><RESULTS>
<RESULT resultid="501" eventid="11" swimtime="00:00:55.12"/></RESULTS></ATHLETE>
</ATHLETES></CLUB></CLUBS></MEET></MEETS></LENEX>"""

    @Test
    fun lenexEinladungUndErgebnis() {
        val bytes = lenex.toByteArray()
        assertTrue(Lenex.istLenex(bytes))
        val a = Lenex.ausschreibung(bytes)
        assertEquals("25", a.bahn)
        assertEquals(listOf(1), a.passendFuer("W", 2014).map { it.nr })
        assertEquals(listOf<Int>(), a.passendFuer("W", 2010).map { it.nr })
        val l = Lenex.ergebnisse(bytes)
        val e = l.ergebnisse.single()
        assertEquals("50 m Brust", e.strecke)
        assertEquals(5512, e.hundertstel)
        assertEquals(3, e.platz)
        assertEquals("Jahrgang 2014/15", e.wertung)
        assertEquals("m2", Wettkampfabgleich.zuordnen(l, bestand, "4321", "").single().mitgliedId)
    }

    @Test
    fun dateiartErkennen() {
        val a = WettkampfDatei.lesen(datei("wk8.dsv8").encodeToByteArray())
        assertTrue(a is WettkampfDatei.Einladung)
        val e = WettkampfDatei.lesen(datei("pr8.dsv8").encodeToByteArray())
        assertTrue(e is WettkampfDatei.Ergebnisse && e.liste.ergebnisse.isNotEmpty())
        val v = WettkampfDatei.lesen(datei("vpr8.dsv8").encodeToByteArray())
        assertTrue(v is WettkampfDatei.Ergebnisse)
    }
}
