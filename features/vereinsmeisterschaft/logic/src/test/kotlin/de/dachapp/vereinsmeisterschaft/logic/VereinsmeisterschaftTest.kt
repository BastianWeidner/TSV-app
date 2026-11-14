package de.dachapp.vereinsmeisterschaft.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VereinsmeisterschaftTest {

    private fun veranstaltung(vararg t: Teilnehmer) = Vorgaben.veranstaltung(1, 2026).copy(teilnehmer = t.toList(), naechsteId = 100)

    private fun t(id: Long, vor: String, jg: Int, g: Geschlecht? = Geschlecht.W, zeiten: Map<Lage, Int> = emptyMap()) =
        Teilnehmer(id, vor, "Muster$id", jg, g, meldezeiten = zeiten)

    // ---------- Zeit ----------

    @Test
    fun zeitenLesen() {
        assertEquals(6530, Zeit.lesen("1:05,3"))
        assertEquals(6530, Zeit.lesen("1.05,30"))
        assertEquals(3540, Zeit.lesen("35,40"))
        assertEquals(3540, Zeit.lesen("35.4"))
        assertEquals(3500, Zeit.lesen("35"))
        assertEquals(3540, Zeit.lesen("3540"))
        assertEquals(4000, Zeit.lesen("40"))
        assertEquals(6530, Zeit.lesen("1'05.3"))
        assertEquals(6530, Zeit.lesen("10530"))
        assertNull(Zeit.lesen("1:75,00"))
        assertNull(Zeit.lesen("abc"))
        assertEquals("1:05,30", Zeit.text(6530))
        assertEquals("35,40", Zeit.text(3540))
        assertEquals(Status.NA, Zeit.status("n.a."))
        assertEquals(Status.DSQ, Zeit.status("Disq"))
    }

    // ---------- Stufen nach Ausschreibung 2026 ----------

    @Test
    fun stufenNachJahrgang2026() {
        val v = veranstaltung()
        fun stufe(jg: Int) = v.stufe(t(1, "x", jg))?.id
        assertEquals("b1", stufe(2020))
        assertEquals("b1", stufe(2019))
        assertEquals("b2", stufe(2018))
        assertEquals("b2", stufe(2017))
        assertEquals("b3", stufe(2016))
        assertEquals("b3", stufe(2009))
        assertEquals("master", stufe(2008))
        assertEquals("master", stufe(1975))
        assertEquals(listOf(Lage.BRUST, Lage.RUECKEN), v.starts(t(1, "x", 2018)).map { it.lage })
        val master = v.starts(t(1, "x", 1980))
        assertEquals(4, master.size)
        assertEquals(Strecke(Lage.DELPHIN, 2), master.last())
    }

    @Test
    fun folgejahrVerschiebtJahrgaenge() {
        val alt = veranstaltung(t(1, "Anna", 2018)).let {
            it.mitErgebnis(1, Lage.BRUST, Ergebnis(1, Lage.BRUST, 3000))
        }
        val neu = Vorgaben.folgejahr(alt, 2, 2027, teilnehmerMit = true)
        assertEquals("Vereinsmeisterschaft 2027", neu.titel)
        assertEquals(2020, neu.regeln.stufe("b1")!!.abJahrgang)
        assertEquals(2009, neu.regeln.stufe("master")!!.bisJahrgang)
        assertEquals(3000, neu.teilnehmer.single().meldezeiten[Lage.BRUST])
        assertTrue(neu.ergebnisse.isEmpty())
    }

    // ---------- Läufe ----------

    @Test
    fun laufgroessenGleichmaessig() {
        assertEquals(listOf(3, 3, 3), Laufeinteilung.groessen(9, 4))
        assertEquals(listOf(3, 4), Laufeinteilung.groessen(7, 4))
        assertEquals(listOf(4), Laufeinteilung.groessen(4, 4))
        assertEquals(listOf(2, 2, 3, 3), Laufeinteilung.groessen(10, 3))
        assertEquals(listOf(2, 3, 1, 4), Laufeinteilung.mitteZuerst(4))
    }

    @Test
    fun laeufeNachAlterUndZeit() {
        val v = veranstaltung(
            t(1, "A", 2016, zeiten = mapOf(Lage.BRUST to 4000)),
            t(2, "B", 2016, zeiten = mapOf(Lage.BRUST to 3000)),
            t(3, "C", 2014, Geschlecht.M),
            t(4, "D", 2015),
            t(5, "E", 2016),
            t(6, "F", 2019),
        )
        val laeufe = Laufeinteilung.einteilen(v)
        // Erst Brust 1 Bahn (F), dann Brust 3 Bahnen mit 5 Schwimmern → 2 + 3.
        assertEquals(Strecke(Lage.BRUST, 1), laeufe[0].strecke)
        val brust3 = laeufe.filter { it.strecke == Strecke(Lage.BRUST, 3) }
        assertEquals(listOf(2, 3), brust3.map { it.belegung.size })
        // Jüngste zuerst; im Jahrgang 2016 ohne Zeit (E) vor 40,00 (A) vor 30,00 (B).
        val reihe = Laufeinteilung.reihenfolge(v, Strecke(Lage.BRUST, 3)).map { it.vorname }
        assertEquals(listOf("E", "A", "B", "D", "C"), reihe)
        // Schnellster eines Laufs auf Bahn 2 (Mitte).
        assertEquals(1L, brust3[0].auf(2))
        // Läufe gemischt: Lauf 2 enthält Mädchen und Jungen.
        assertTrue(brust3[1].belegung.map { v.person(it.teilnehmer)!!.geschlecht }.toSet().size == 2)
        // Laufnummern fortlaufend, Brust vor Rücken vor Kraul.
        assertEquals((1..laeufe.size).toList(), laeufe.map { it.nr })
        assertEquals(listOf(Lage.BRUST, Lage.RUECKEN, Lage.KRAUL), laeufe.map { it.strecke.lage }.distinct())
    }

    @Test
    fun tauschenUndNachtragen() {
        val v = veranstaltung(t(1, "A", 2016), t(2, "B", 2016), t(3, "C", 2016), t(4, "D", 2016), t(5, "E", 2016))
        val l = Laufeinteilung.einteilen(v).filter { it.strecke.lage == Lage.BRUST }
        val a = l[0].auf(2)!!
        val getauscht = Laufeinteilung.tauschen(l, 1, 2, 2, 4)
        assertEquals(a, getauscht[1].auf(4))
        val mehr = Laufeinteilung.nachtragen(l, 99, Strecke(Lage.BRUST, 3), 4)
        assertEquals(l.size, mehr.size) // 2 + 3, im zweiten Lauf ist noch Platz
        assertTrue(mehr.any { lauf -> lauf.belegung.any { it.teilnehmer == 99L } })
        var voll = listOf(98L, 97L).fold(mehr) { acc, id -> Laufeinteilung.nachtragen(acc, id, Strecke(Lage.BRUST, 3), 4) }
        assertEquals(l.size, voll.size) // jetzt 4 + 4
        voll = Laufeinteilung.nachtragen(voll, 96, Strecke(Lage.BRUST, 3), 4)
        assertEquals(l.size + 1, voll.size)
        val weg = Laufeinteilung.entfernen(voll, 96)
        assertEquals(l.size, weg.size)
    }

    // ---------- Wertung ----------

    private fun mitZeiten(v: Veranstaltung, vararg z: Triple<Long, Lage, Int?>): Veranstaltung =
        z.fold(v) { acc, (id, l, zeit) -> acc.mitErgebnis(id, l, Ergebnis(id, l, zeit, if (zeit == null) Status.NA else Status.OK)) }

    @Test
    fun jahrgangswertungMitPlaetzenUndMedaillen() {
        var v = veranstaltung(
            t(1, "A", 2018), t(2, "B", 2018), t(3, "C", 2018), t(4, "D", 2018), t(5, "E", 2018, Geschlecht.M),
        )
        v = mitZeiten(
            v,
            Triple(1, Lage.BRUST, 4000), Triple(1, Lage.RUECKEN, 3800),
            Triple(2, Lage.BRUST, 3900), Triple(2, Lage.RUECKEN, 4100),
            Triple(3, Lage.BRUST, 4000), Triple(3, Lage.RUECKEN, 4500),
            Triple(4, Lage.BRUST, 3500), Triple(4, Lage.RUECKEN, null),
            Triple(5, Lage.BRUST, 3700), Triple(5, Lage.RUECKEN, 3700),
        )
        val w = Wertung.jahrgaenge(v)
        assertEquals(2, w.size) // 2018 w, 2018 m
        val wb = w.first { it.gruppe.geschlecht == Geschlecht.W }
        val brust = wb.lagen.first { it.strecke.lage == Lage.BRUST }
        assertEquals(listOf("D" to 1, "B" to 2, "A" to 3, "C" to 3), brust.zeilen.map { it.teilnehmer.vorname to it.platz })
        // Gesamt = Zeitsumme hochgerechnet; D ohne Rücken bekommt keinen Platz.
        assertEquals(listOf("A" to 1, "B" to 2, "C" to 3, "D" to null), wb.gesamt.map { it.teilnehmer.vorname to it.platz })
        assertEquals((7800 * 3 / 2), wb.gesamt[0].zeitsumme)
        assertEquals(setOf(1L, 2L, 3L, 5L), Wertung.medaillen(v, w))
    }

    @Test
    fun kinderpokalRechnetAufDreiBahnenHoch() {
        var v = veranstaltung(t(1, "Klein", 2019), t(2, "Gross", 2017))
        // 1 Bahn: 20 + 20 s → 60 + 60 = 120 s; 2 Bahnen: 35 + 35 s → 52,5 + 52,5 = 105 s.
        v = mitZeiten(v, Triple(1, Lage.BRUST, 2000), Triple(1, Lage.RUECKEN, 2000), Triple(2, Lage.BRUST, 3500), Triple(2, Lage.RUECKEN, 3500))
        val pokal = Wertung.pokale(v).single { it.pokal.name == "Kinderpokal" }
        assertEquals(listOf("Gross", "Klein"), pokal.zeilen.map { it.teilnehmer.vorname })
        assertEquals(10500, pokal.zeilen[0].zeitsumme)
        assertEquals("105,00", Wertung.wertText(v.regeln, pokal.zeilen[0]))
        val meister = Wertung.punkteurkunden(v)
        assertEquals(listOf("Gross"), meister.map { it.teilnehmer.vorname })
    }

    @Test
    fun pokaleGetrenntNachGeschlechtUndPunkteFormel() {
        var v = veranstaltung(t(1, "W", 2010), t(2, "M", 2010, Geschlecht.M))
        v = mitZeiten(v, Triple(1, Lage.BRUST, 3000), Triple(1, Lage.RUECKEN, 3000), Triple(1, Lage.KRAUL, 3000))
        v = mitZeiten(v, Triple(2, Lage.BRUST, 3000), Triple(2, Lage.RUECKEN, 3000), Triple(2, Lage.KRAUL, 3000))
        val jugend = Wertung.pokale(v).filter { it.pokal.name == "Jugendpokal" }
        assertEquals(2, jugend.size)
        assertTrue(jugend.all { it.zeilen.single().platz == 1 })
        val p = v.copy(regeln = v.regeln.copy(wertung = Wertungsart.PUNKTE))
        val z = Wertung.pokale(p).first().zeilen.single()
        // Brust W: 1000 × (28,37 / 30)³ ≈ 845,7
        assertEquals(845.7, z.einzeln[Strecke(Lage.BRUST, 3)]!!, 0.1)
    }

    @Test
    fun masterWerdenZusammenGewertetUndAusserKonkurrenz() {
        var v = veranstaltung(t(1, "Alt", 1970), t(2, "Jung", 2005), t(3, "Gast", 1990).copy(ausserKonkurrenz = true))
        v = mitZeiten(v, Triple(1, Lage.BRUST, 3000), Triple(2, Lage.BRUST, 3100), Triple(3, Lage.BRUST, 2000))
        val w = Wertung.jahrgaenge(v)
        assertEquals(1, w.size)
        assertEquals("Jahrgang 2008 und älter", w[0].gruppe.jahrgaenge)
        val brust = w[0].lagen.first { it.strecke.lage == Lage.BRUST }
        assertEquals(listOf(1, 2, null), brust.zeilen.map { it.platz })
    }

    @Test
    fun urkundeHatAlleZeitenUndPlaetze() {
        var v = veranstaltung(t(1, "A", 2018), t(2, "B", 2018))
        v = mitZeiten(v, Triple(1, Lage.BRUST, 4000), Triple(1, Lage.RUECKEN, 3800), Triple(2, Lage.BRUST, 3900), Triple(2, Lage.RUECKEN, 4100))
        val u = Wertung.urkunden(v).first { it.teilnehmer.vorname == "A" }
        assertEquals(listOf(2, 1), u.zeilen.map { it.platz })
        assertEquals(1, u.gesamtPlatz)
        assertTrue(u.medaille)
        assertEquals("Jahrgang 2018 · weiblich", u.gruppe.titel)
    }

    // ---------- Import ----------

    @Test
    fun meldelisteAusSchwimmgruppenFormat() {
        val tab = TabellenLeser.lesen(
            ("Name\tVorname\tStraße\tPLZ\tOrt\t\tTelefon\tGebDat\tE-Mail-Adresse\tEJ\n" +
                "Huber\tLena\tWeg 1\t86850\tFischach\t\t0821\t3/5/2017\tx@y.de\t2022\n" +
                "Maier\tTim\tWeg 2\t86850\tFischach\t\t0821\t12/1/2010\t\t2020\n").toByteArray(),
        )
        val b = Import.meldeliste(veranstaltung(), tab)
        assertEquals(2, b.neu)
        assertEquals(listOf(2017, 2010), b.veranstaltung.teilnehmer.map { it.jahrgang })
        assertTrue(b.hinweise.first().contains("Geschlecht"))
    }

    @Test
    fun meldelisteMitGeschlechtUndMeldezeiten() {
        val tab = TabellenLeser.lesen("Name;Jahrgang;m/w;Brust;Rücken\nLena Huber;2017;w;45,3;\nTim Maier;10;m;0:38,10;40\n".toByteArray())
        val v = Import.meldeliste(veranstaltung(), tab).veranstaltung
        val lena = v.teilnehmer.first { it.vorname == "Lena" }
        assertEquals("Huber", lena.nachname)
        assertEquals(Geschlecht.W, lena.geschlecht)
        assertEquals(4530, lena.meldezeiten[Lage.BRUST])
        val tim = v.teilnehmer.first { it.vorname == "Tim" }
        assertEquals(2010, tim.jahrgang)
        assertEquals(4000, tim.meldezeiten[Lage.RUECKEN])
        // Zweiter Import ändert statt zu verdoppeln.
        val nochmal = Import.meldeliste(v, tab)
        assertEquals(0, nochmal.neu)
        assertEquals(2, nochmal.veranstaltung.teilnehmer.size)
    }

    @Test
    fun ergebnisseInDreiFormen() {
        var v = veranstaltung(Teilnehmer(1, "Lena", "Huber", 2017, Geschlecht.W), Teilnehmer(2, "Tim", "Maier", 2010, Geschlecht.M))
        v = v.copy(laeufe = Laufeinteilung.einteilen(v))
        val breit = Import.ergebnisse(v, TabellenLeser.lesen("Nachname;Vorname;Brust;Rücken\nHuber;Lena;45,30;n.a.\n".toByteArray()))
        assertEquals(4530, breit.veranstaltung.ergebnis(1, Lage.BRUST)!!.zeit)
        assertEquals(Status.NA, breit.veranstaltung.ergebnis(1, Lage.RUECKEN)!!.status)
        val lang = Import.ergebnisse(v, TabellenLeser.lesen("Name;Lage;Zeit\nMaier, Tim;Kraul;38,1\nOtto Unbekannt;Kraul;30\n".toByteArray()))
        assertEquals(3810, lang.veranstaltung.ergebnis(2, Lage.KRAUL)!!.zeit)
        assertTrue(lang.hinweise.single().contains("Unbekannt"))
        val (l, bahn) = v.lauf(2, Lage.BRUST)!!
        val nachLauf = Import.ergebnisse(v, TabellenLeser.lesen("Lauf;Bahn;Zeit\n${l.nr};$bahn;41,00\n".toByteArray()))
        assertEquals(4100, nachLauf.veranstaltung.ergebnis(2, Lage.BRUST)!!.zeit)
    }

    @Test
    fun csvExport() {
        var v = veranstaltung(t(1, "A", 2018))
        v = mitZeiten(v, Triple(1, Lage.BRUST, 4000), Triple(1, Lage.RUECKEN, 3800))
        val csv = Import.ergebnisCsv(v).lines()
        assertTrue(csv[0].startsWith("Gruppe;Platz gesamt;Nachname"))
        assertTrue(csv[1].contains("40,00;1;38,00;1;117,00"))
    }

    // ---------- Scan ----------

    @Test
    fun scanLiestZeilen() {
        val zeilen = listOf(
            OcrZeile("Zeitzettel Bahn 2", 100, 20, 600, 60),
            OcrZeile("L01 B2", 100, 200, 220, 240),
            OcrZeile("Lena Huber", 260, 200, 500, 240),
            OcrZeile("4 5 , 3 0", 700, 195, 900, 250, 0.9f),
            OcrZeile("L02 B2", 100, 300, 220, 340),
            OcrZeile("Tim Maier", 260, 300, 500, 340),
            OcrZeile("1:O5,3", 700, 298, 900, 345, 0.8f),
            OcrZeile("L03 B2", 100, 400, 220, 440),
            OcrZeile("n.a.", 700, 400, 800, 440),
            OcrZeile("L04 B2", 100, 500, 220, 540),
            OcrZeile("Max Muster", 260, 500, 500, 540),
            OcrZeile("9", 700, 500, 750, 540, 0.3f),
            OcrZeile("L05 B2", 100, 600, 220, 640),
        )
        val t = Scan.auswerten(zeilen, { 2 })
        assertEquals(5, t.size)
        assertEquals(4530, t[0].zeit)
        assertTrue(t[0].sicher)
        assertEquals(6530, t[1].zeit) // „1:O5,3“ → 1:05,30 (Zehntel erkannt, O als 0)
        assertEquals(Status.NA, t[2].status)
        assertNull(t[3].zeit)
        assertFalse(t[3].sicher)
        assertNull(t[4].zeit)
        assertEquals("leer", t[4].grund)
    }

    @Test
    fun scanMehrdeutigNimmtMeldezeit() {
        // „305“: 3,05 s ist für 2 Bahnen unmöglich, 30,50 passt.
        assertEquals(listOf(3050), Scan.lesarten("305", 2))
        // „4530“ = 45,30, „45300“ wäre 7:33,00 und zu langsam.
        assertEquals(listOf(4530), Scan.lesarten("4530", 2))
        assertNotNull(Scan.kennung("L12 B3"))
        assertEquals(12 to 3, Scan.kennung("l12 83"))
        assertNull(Scan.kennung("Lena Huber"))
    }

    // ---------- Datei ----------

    @Test
    fun speichernUndLaden() {
        var v = veranstaltung(t(1, "A", 2018, zeiten = mapOf(Lage.BRUST to 4000)))
        v = v.copy(laeufe = Laufeinteilung.einteilen(v), staffeln = listOf(Staffel(1, "Familie Huber", "Oma, Lena")))
        v = mitZeiten(v, Triple(1, Lage.BRUST, 4000))
        val d = Daten(listOf(v), 1, null, 2)
        assertEquals(d, Datei.lesen(Datei.schreiben(d)))
    }

    // ---------- Schwimmgruppen ----------

    @Test
    fun mitgliederAusSchwimmgruppen() {
        val json = """{"version":5,"mitglieder":[
            {"id":"m1","vorname":"Lena","nachname":"Huber","geburtsdatum":"2017-05-03","geschlecht":"w","gruppen":["g1"],"dsvId":"123456"},
            {"id":"m2","vorname":"Tim","nachname":"Maier","geburtsdatum":"2010-01-12","geschlecht":"m"},
            {"id":"m3","vorname":"Alt","nachname":"Weg","geburtsdatum":"2000-01-01","aktiv":false},
            {"id":"m4","vorname":"Ohne","nachname":"Datum"}],
            "gruppen":[{"id":"g1","name":"Gruppe I Mittwoch","zeiten":[]}],"einstellungen":{"verein":"TSV"}}"""
        val v = veranstaltung(Teilnehmer(1, "Tim", "Maier", 2010, Geschlecht.M))
        val k = Querverbindung.mitglieder(json, v)
        assertEquals(listOf("Ohne Datum", "Lena Huber", "Tim Maier"), k.map { it.name })
        assertTrue(k.first { it.id == "m2" }.schonDa)
        val b = Querverbindung.uebernehmen(v, k)
        assertEquals(1, b.neu)
        val lena = b.veranstaltung.teilnehmer.first { it.vorname == "Lena" }
        assertEquals(Geschlecht.W, lena.geschlecht)
        assertEquals("m1", lena.mitgliedId)
        assertEquals("Gruppe I Mittwoch", lena.gruppe)
        assertTrue(b.hinweise.single().contains("Ohne Datum"))
    }

    @Test
    fun dsvErgebnisliste() {
        var v = veranstaltung(Teilnehmer(1, "Lena", "Huber", 2017, Geschlecht.W, dsvId = "123456")).copy(datum = "2026-05-16")
        v = mitZeiten(v, Triple(1, Lage.BRUST, 4530), Triple(1, Lage.RUECKEN, null))
        val d = Querverbindung.dsv(v)
        assertTrue(d.startsWith("FORMAT:Vereinsergebnisliste;8;"))
        assertTrue(d.contains("WETTKAMPF:1;E;1;;33;B;GL;X;SW;;;"))
        assertTrue(d.contains("PERSON:Huber, Lena;123456;1;W;2017;;GER;;;"))
        assertTrue(d.contains("PERSONENERGEBNIS:1;1;E;1;1;00:00:45,30;;;;"))
        assertTrue(d.contains(";00:00:00,00;NA;;;"))
        assertEquals("2026-05-16-Fischach-Vereinsmeisterschaft-Pr.DSV8", Querverbindung.dateiname(v))
    }
}
