package de.dachapp.schwimmkurs.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.time.LocalDate

/** Anhang anmeldung.csv aus den Wartelisten-Mails der Website (TSV-relaunch, api/formular.php). */
class WebsiteAnmeldungTest {
    private val kopf = "Anmeldedatum;Name des Kindes;Geburtsdatum;Einschulung;PLZ und Ort;Elternteil;E-Mail;Telefon;Bemerkung Kurswunsch;Bemerkung"

    private fun anhang(zeile: String) = TabellenLeser.lesen(("﻿$kopf\r\n$zeile\r\n").encodeToByteArray())

    @Test
    fun spaltenWerdenOhneZutunErkannt() {
        val z = KinderImport.vorschlagen(kopf.split(";"), emptyList())
        assertEquals(
            listOf(
                KindFeld.ANMELDEDATUM, KindFeld.NAME, KindFeld.GEBURTSDATUM, KindFeld.EINSCHULUNG, KindFeld.PLZ_ORT,
                KindFeld.ELTERN, KindFeld.EMAIL, KindFeld.TELEFON, KindFeld.NOTIZ, KindFeld.NOTIZ,
            ).map { Zuordnung.Feld(it) },
            z,
        )
    }

    @Test
    fun mehrereAnhaengeAufEinmalOhneDoppelte() {
        val a = anhang("31.10.2026;Mia Muster;2020-05-14;2026;86850 Fischach;Sabine Muster;sabine@example.org;0821 123;Kurswunsch: Seepferdchenkurs;Geschwister im selben Kurs")
        val b = anhang("01.11.2026;Ben Beispiel;2019-09-02;2025;86868 Mittelneufnach;Tom Beispiel;tom@example.org;;Kurswunsch: Seepferdchenkurs;")
        // Dieselbe Anmeldung zweimal abgeschickt.
        val t = zusammenfuehren(listOf(a, b, a))
        assertEquals(3, t.zeilen.size)
        val e = KinderImport.importieren(t, KinderImport.vorschlagen(t.kopf, emptyList()), emptyList(), emptyList())
        assertEquals(2, e.neu)
        val mia = e.kinder.first { it.vorname == "Mia" }
        assertEquals("Muster", mia.nachname)
        assertEquals(LocalDate.of(2020, 5, 14), mia.geburtsdatum)
        assertEquals("86850", mia.plz)
        assertEquals("Fischach", mia.ort)
        assertEquals("Sabine Muster", mia.eltern)
        assertEquals("2026", mia.einschulung)
        assertEquals(LocalDate.of(2026, 10, 31), mia.anmeldedatum)
        assertEquals("Kurswunsch: Seepferdchenkurs Geschwister im selben Kurs", mia.notiz)
    }

    /** So, wie formular.php den Anhang wirklich erzeugt (im Testmodus erzeugt, erfundene Daten). */
    @Test
    fun echterAnhangDerWebsite() {
        val bytes = javaClass.getResourceAsStream("/website/anmeldung.csv")!!.readBytes()
        val t = TabellenLeser.lesen(bytes)
        assertEquals(kopf.split(";"), t.kopf)
        val e = KinderImport.importieren(t, KinderImport.vorschlagen(t.kopf, emptyList()), emptyList(), emptyList())
        val mia = e.kinder.single()
        assertEquals("Mia Muster", mia.name)
        assertEquals("+49 821 123", mia.telefon)
        assertEquals("Fischach", mia.ort)
        assertEquals("Kurswunsch: Seepferdchenkurs Geschwister; gern zusammen =SUMME(1)", mia.notiz)
    }

    @Test
    fun unterschiedlicheDateienWerdenAbgelehnt() {
        val andere = TabellenLeser.lesen("Name;Geburtsdatum\nMax;1.1.2020\n".encodeToByteArray())
        assertFailsWith<TabellenFehler> { zusammenfuehren(listOf(anhang("31.10.2026;Mia Muster;2020-05-14;2026;86850 Fischach;S;s@example.org;;;"), andere)) }
    }
}
