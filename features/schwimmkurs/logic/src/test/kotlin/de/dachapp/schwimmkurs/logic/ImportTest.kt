package de.dachapp.schwimmkurs.logic

import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportTest {

    private val stufen = Einstellungen.STANDARD_ABZEICHEN

    @Test
    fun csvMitSemikolonUndAnfuehrungszeichen() {
        val text = "Vorname;Nachname;Bemerkung\r\nAnna;Müller;\"sagt \"\"hallo\"\"; mag Wasser\"\r\n\r\nBen;Schulz;\"zwei\nZeilen\"\r\n"
        val t = TabellenLeser.lesen(text.toByteArray())
        assertEquals(listOf("Vorname", "Nachname", "Bemerkung"), t.kopf)
        assertEquals(2, t.zeilen.size)
        assertEquals("sagt \"hallo\"; mag Wasser", t.zelle(0, 2))
        assertEquals("zwei\nZeilen", t.zelle(1, 2))
    }

    @Test
    fun csvMitKommaUndWindowsZeichensatz() {
        val bytes = "Name,Straße\nJörg Weiß,Hauptstraße 1\n".toByteArray(charset("windows-1252"))
        val t = TabellenLeser.lesen(bytes)
        assertEquals(listOf("Name", "Straße"), t.kopf)
        assertEquals("Jörg Weiß", t.zelle(0, 0))
    }

    @Test
    fun csvMitBom() {
        val t = TabellenLeser.lesen(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Vorname\nAnna\n".toByteArray())
        assertEquals("Vorname", t.kopf[0])
    }

    @Test
    fun alteExcelDateiGibtVerstaendlichenFehler() {
        val f = assertFailsWith<TabellenFehler> { TabellenLeser.lesen(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0, 0, 0, 0)) }
        assertTrue(".xlsx" in f.message!!)
    }

    @Test
    fun xlsxMitGeteiltenTextenZahlenUndLuecken() {
        val t = TabellenLeser.lesen(xlsx())
        assertEquals(listOf("Vorname", "Nachname", "Geburtsdatum", "PLZ"), t.kopf)
        assertEquals(listOf("Anna", "Müller", "43831", "12345"), t.zeilen[0])
        // Zelle B3 fehlt in der Datei, D3 ist eine Inline-Zeichenkette.
        assertEquals(listOf("Ben", "", "2019-05-01", "54321"), t.zeilen[1])
    }

    @Test
    fun spaltenWerdenErkannt() {
        val kopf = listOf(
            "Zeitstempel", "Vorname des Kindes", "Name", "Geb.-Datum", "Name Erziehungsberechtigte/r",
            "E-Mail-Adresse", "Handynummer", "Straße", "Hausnr.", "PLZ / Ort", "Seepferdchen", "Sonstiges", "Lieblingsfarbe",
        )
        val z = KinderImport.vorschlagen(kopf, stufen)
        fun f(feld: KindFeld) = Zuordnung.Feld(feld)
        assertEquals(
            listOf(
                f(KindFeld.ANMELDEDATUM), f(KindFeld.VORNAME), f(KindFeld.NACHNAME), f(KindFeld.GEBURTSDATUM), f(KindFeld.ELTERN),
                f(KindFeld.EMAIL), f(KindFeld.TELEFON), f(KindFeld.STRASSE), f(KindFeld.STRASSE), f(KindFeld.PLZ_ORT),
                Zuordnung.AbzeichenSpalte("Seepferdchen"), f(KindFeld.NOTIZ), Zuordnung.Ignorieren,
            ),
            z,
        )
    }

    @Test
    fun importLegtKinderAnUndFuehrtDoppelteZusammen() {
        val t = Tabelle(
            listOf("Name", "Geburtsdatum", "E-Mail", "PLZ Ort", "Abzeichen", "Seepferdchen", "Straße", "Nr"),
            listOf(
                listOf("Müller, Anna", "03.04.2018", "mama@example.de", "12345 Musterstadt", "", "x", "Hauptstr.", "5"),
                listOf("Ben Müller", "43831", "mama@example.de", "12345 Musterstadt", "Freischwimmer", "", "Hauptstr.", "5"),
                listOf("", "", "", "", "", "", "", ""),
                listOf("", "01.01.2019", "", "", "", "", "", ""),
                listOf("Clara Schulz", "31.02.2019", "keine-mail", "", "Seepferdchen, DLRG Silber", "nein", "", ""),
            ),
        )
        val bestehend = listOf(Kind("alt", "Anna", "Müller", LocalDate.of(2018, 4, 3), notiz = "alt", abzeichen = setOf("Bronze")))
        var n = 0
        val e = KinderImport.importieren(t, KinderImport.vorschlagen(t.kopf, stufen), bestehend, stufen) { "id${n++}" }

        assertEquals(2, e.neu)
        assertEquals(1, e.aktualisiert)
        val anna = e.kinder.single { it.vorname == "Anna" }
        assertEquals("alt", anna.id)
        assertEquals(setOf("Bronze", "Seepferdchen"), anna.abzeichen)
        assertEquals("12345", anna.plz)
        assertEquals("Musterstadt", anna.ort)
        assertEquals("Hauptstr. 5", anna.strasse)
        assertEquals("alt", anna.notiz)
        val ben = e.kinder.single { it.vorname == "Ben" }
        assertEquals(LocalDate.of(2020, 1, 1), ben.geburtsdatum)
        assertEquals(setOf("Bronze"), ben.abzeichen)
        assertEquals(anna.familienSchluessel, ben.familienSchluessel)
        val clara = e.kinder.single { it.vorname == "Clara" }
        assertNull(clara.geburtsdatum)
        assertEquals(setOf("Seepferdchen", "Silber"), clara.abzeichen)
        assertTrue(e.hinweise.any { "Zeile 5" in it && "kein Name" in it })
        assertTrue(e.hinweise.any { "31.02.2019" in it })
        assertTrue(e.hinweise.any { "keine-mail" in it })
    }

    @Test
    fun datumsformate() {
        val heute = LocalDate.of(2026, 10, 20)
        assertEquals(LocalDate.of(2018, 4, 3), KinderImport.datum("3.4.2018", heute))
        assertEquals(LocalDate.of(2018, 4, 3), KinderImport.datum("2018-04-03", heute))
        assertEquals(LocalDate.of(2026, 3, 12), KinderImport.datum("12.03.2026 18:04:11", heute))
        assertEquals(LocalDate.of(2019, 5, 1), KinderImport.datum("01.05.19", heute))
        assertEquals(LocalDate.of(1998, 5, 1), KinderImport.datum("01.05.98", heute))
        assertEquals(LocalDate.of(2020, 1, 1), KinderImport.datum("43831", heute))
        assertEquals(LocalDate.of(2020, 1, 1), KinderImport.datum("43831.5", heute))
        assertNull(KinderImport.datum("12.03", heute))
        assertNull(KinderImport.datum("unbekannt", heute))
    }

    @Test
    fun abzeichenErkennung() {
        val a = Abzeichen(stufen)
        assertEquals(setOf("Seepferdchen", "Gold"), a.ausZelle("Seepferdchen und Jugendschwimmer"))
        assertEquals(emptySet(), a.ausZelle("keine"))
        assertEquals(setOf("Tauchring"), a.ausZelle("Tauchring"))
        val k = Kind("1", "A", "B", abzeichen = setOf("Silber"))
        assertTrue(a.hat(k, "Seepferdchen"))
        assertTrue(a.hat(k, "Silber"))
        assertTrue(!a.hat(k, "Gold"))
    }

    @Test
    fun gemischteNamensreihenfolgeWirdErkannt() {
        val t = Tabelle(
            listOf("Name", "Geburtsdatum", "E-Mail", "Einschulung", "Kommentar"),
            listOf(
                listOf("Wagner Linus", "21.11.2021", "fam.w@x.de", "2028", "KiGa Willmatshofen"),
                listOf("Wagner Felix", "30.05.2024", "fam.w@x.de", "2030", ""),
                listOf("Hampf Thea", "30.10.2019", "tom.hampf@x.de", "", ""),
                listOf("Gütinger Gabriel", "12.10.2020", "ursula.guetinger@x.de", "", ""),
                listOf("Spengler Toni", "09.02.2021", "melli.blank@x.de", "", ""),
                listOf("Anna Maria Huber", "01.01.2020", "a@x.de", "", ""),
                listOf("Ramona Jakob", "25.09.2019", "jakob@x.de", "", "Beide Jakobgeschwister nicht in die selbe Gruppe."),
                listOf("", "22.09.2016", "leer@x.de", "", "Fischach"),
            ),
        )
        val e = KinderImport.importieren(t, KinderImport.vorschlagen(t.kopf, stufen), emptyList(), stufen)
        val namen = e.kinder.map { it.vorname to it.nachname }
        assertEquals(
            listOf(
                "Linus" to "Wagner", "Felix" to "Wagner", "Thea" to "Hampf", "Gabriel" to "Gütinger",
                "Toni" to "Spengler", "Anna Maria" to "Huber", "Ramona" to "Jakob",
            ),
            namen,
        )
        assertEquals("2028", e.kinder[0].einschulung)
        assertTrue(e.kinder.single { it.vorname == "Ramona" }.geschwisterGetrennt)
        assertTrue(e.hinweise.any { "kein Name" in it && "leer@x.de" in it })
    }

    /** Kleinste gültige Excel-Datei mit geteilten Texten, Zahlen, fehlender Zelle und Inline-Text. */
    private fun xlsx(): ByteArray {
        val dateien = mapOf(
            "[Content_Types].xml" to "<Types/>",
            "xl/workbook.xml" to """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Anmeldungen" sheetId="1" r:id="rId7"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId7" Type="worksheet" Target="worksheets/sheet2.xml"/></Relationships>""",
            "xl/sharedStrings.xml" to """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>Vorname</t></si><si><t>Nachname</t></si><si><r><t>Geburts</t></r><r><t>datum</t></r></si><si><t>PLZ</t></si><si><t>Anna</t></si><si><t>Müller</t></si><si><t>Ben</t></si></sst>""",
            "xl/worksheets/sheet1.xml" to """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="s"><v>0</v></c></row></sheetData></worksheet>""",
            "xl/worksheets/sheet2.xml" to """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c><c r="D1" t="s"><v>3</v></c></row>
                <row r="2"><c r="A2" t="s"><v>4</v></c><c r="B2" t="s"><v>5</v></c><c r="C2" s="1"><v>43831</v></c><c r="D2"><v>12345.0</v></c></row>
                <row r="4"><c r="A4" t="s"><v>6</v></c><c r="C4" t="str"><v>2019-05-01</v></c><c r="D4" t="inlineStr"><is><t>54321</t></is></c></row>
                </sheetData></worksheet>""",
        )
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, inhalt) in dateien) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(inhalt.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
