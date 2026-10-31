package de.dachapp.module

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

class SicherungsarchivTest {
    private fun ordner(): File = Files.createTempDirectory("sich").toFile()

    @Test
    fun sichernUndEinspielen() {
        val quelle = ordner()
        val dateien = File(quelle, "files").apply { mkdirs() }
        val prefs = File(quelle, "shared_prefs").apply { mkdirs() }
        File(dateien, "camper.json").writeText("{\"a\":1}")
        File(dateien, "camper-fotos").mkdirs()
        File(dateien, "camper-fotos/x.jpg").writeBytes(byteArrayOf(1, 2, 3))
        File(dateien, "produkte-db.bin").writeText("gross")
        File(dateien, "karten.json.tmp").writeText("halb")
        File(dateien, "einkauf-defekt-123.json").writeText("kaputt")
        File(prefs, "werwolf.xml").writeText("<map/>")
        File(prefs, "dach_sicherung.xml").writeText("<map/>")

        val q = Sicherungsarchiv.quellen(dateien, prefs)
        assertEquals(listOf("dateien/camper-fotos/x.jpg", "dateien/camper.json", "prefs/werwolf.xml"), q.map { it.zipName })

        val out = ByteArrayOutputStream()
        assertEquals(3, Sicherungsarchiv.schreiben(out, q, mapOf("zeit" to "2026-10-31")))
        val bytes = out.toByteArray()

        val inhalt = Sicherungsarchiv.inhalt(ByteArrayInputStream(bytes))
        assertTrue(inhalt.gueltig)
        assertEquals("2026-10-31", inhalt.kopf["zeit"])
        assertEquals(3, inhalt.eintraege.size)

        val ziel = ordner()
        val n = Sicherungsarchiv.auspacken(ByteArrayInputStream(bytes)) { name ->
            if (name.startsWith("prefs/")) null else File(ziel, name.removePrefix(Sicherungsarchiv.DATEIEN))
        }
        assertEquals(2, n)
        assertEquals("{\"a\":1}", File(ziel, "camper.json").readText())
        assertEquals(3, File(ziel, "camper-fotos/x.jpg").readBytes().size)
        assertFalse(File(ziel, "werwolf.xml").exists())
    }

    @Test
    fun fremdeDateiIstUngueltig() {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { it.putNextEntry(java.util.zip.ZipEntry("a.txt")); it.write(1); it.closeEntry() }
        assertFalse(Sicherungsarchiv.inhalt(ByteArrayInputStream(out.toByteArray())).gueltig)
    }
}
