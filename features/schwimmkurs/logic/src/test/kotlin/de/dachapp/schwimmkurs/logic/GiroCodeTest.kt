package de.dachapp.schwimmkurs.logic

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GiroCodeTest {

    @Test
    fun ibanPruefziffer() {
        assertTrue(GiroCode.ibanGueltig(Vorlagen.IBAN))
        assertTrue(GiroCode.ibanGueltig("DE89 3704 0044 0532 0130 00"))
        assertFalse(GiroCode.ibanGueltig("DE89 3704 0044 0532 0130 01"))
        assertFalse(GiroCode.ibanGueltig(""))
    }

    @Test
    fun inhaltNachEpcStandard() {
        val t = GiroCode.inhalt("TSV Fischach - Abteilung Schwimmen", "DE89 3704 0044 0532 0130 00", "", 9650, "SK04 2526 - Muster Max,  Muster Mia")!!
        assertEquals(
            listOf("BCD", "002", "1", "SCT", "", "TSV Fischach - Abteilung Schwimmen", "DE89370400440532013000", "EUR96.50", "", "", "SK04 2526 - Muster Max, Muster Mia"),
            t.split("\n"),
        )
        assertEquals("EUR0.05", GiroCode.inhalt("A", "DE89370400440532013000", "", 5, "x")!!.split("\n")[7])
    }

    @Test
    fun keinCodeBeiFehlendenDaten() {
        assertNull(GiroCode.inhalt("Verein", "", "", 1000, "x"))
        assertNull(GiroCode.inhalt("Verein", "DE89370400440532013001", "", 1000, "x"))
        assertNull(GiroCode.inhalt("Verein", "DE89370400440532013000", "", 0, "x"))
        assertNull(GiroCode.inhalt("", "DE89370400440532013000", "", 1000, "x"))
    }

    @Test
    fun qrLaesstSichZurueckLesen() {
        val inhalt = GiroCode.inhalt("TSV Fischach - Abteilung Schwimmen", Vorlagen.IBAN, "", 12_550, "SK04 2526 - Größer Jürgen")!!
        val m = GiroCode.matrix(inhalt)
        // Mit 4 Modulen Ruhezone und 4 Pixeln je Modul wie ein Foto aufbereiten.
        val px = 4
        val rand = 4
        val n = (m.size + 2 * rand) * px
        val bild = ByteArray(n * n) { i ->
            val x = i % n / px - rand
            val y = i / n / px - rand
            if (y in m.indices && x in m.indices && m[y][x]) 0 else -1
        }
        val quelle = object : LuminanceSource(n, n) {
            override fun getRow(y: Int, row: ByteArray?): ByteArray = bild.copyOfRange(y * n, (y + 1) * n)
            override fun getMatrix(): ByteArray = bild
        }
        val gelesen = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(quelle)), mapOf(DecodeHintType.CHARACTER_SET to "UTF-8"))
        assertEquals(inhalt, gelesen.text)
    }

    @Test
    fun einladungBringtGiroCodeMit() {
        val kurs = Kurs(id = "k1", name = "Seepferdchen 1", kuerzel = "SK01 2526", preisCent = 9650)
        val kind = Kind(id = "a", vorname = "Max", nachname = "Muster", email = "m@x.de")
        val b = Bestand(kinder = listOf(kind), kurse = listOf(kurs.copy(teilnehmer = listOf("a"))))
        val e = Einladungen.erstellen(b).single()
        val zeilen = e.giroCode!!.split("\n")
        assertEquals(Vorlagen.IBAN.replace(" ", ""), zeilen[6])
        assertEquals("EUR96.50", zeilen[7])
        assertEquals(e.verwendungszweck, zeilen[10])
    }
}
