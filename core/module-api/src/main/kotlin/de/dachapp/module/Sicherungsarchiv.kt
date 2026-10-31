package de.dachapp.module

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Gesamtsicherung als ZIP, ohne Android: Dateien aus dem App-Speicher unter "dateien/…", Einstellungen unter
 * "prefs/…", dazu "inhalt.txt" mit Kopfzeilen (Schlüssel=Wert). Reine Logik, damit sie testbar ist.
 */
object Sicherungsarchiv {
    const val FORMAT = "dach-sicherung"
    const val DATEIEN = "dateien/"
    const val PREFS = "prefs/"
    private const val INHALT = "inhalt.txt"

    /** Was nicht in die Sicherung gehört: Zwischenstände, große neu einlesbare Datenbanken, defekt beiseitegelegte Dateien. */
    fun ausgeschlossen(name: String): Boolean {
        val n = name.substringAfterLast('/')
        return n.endsWith(".tmp") || n.endsWith(".new") || n.endsWith(".bak") || n.contains("-defekt-") ||
            n == "produkte-db.bin" || n == "naehrwerte-db.bin" || n == "dach_sicherung.xml"
    }

    data class Quelle(val zipName: String, val datei: File)

    /** Alle Dateien unter [dateien] (rekursiv) und alle Einstellungsdateien aus [prefs]. */
    fun quellen(dateien: File, prefs: File?): List<Quelle> {
        val r = ArrayList<Quelle>()
        dateien.walkTopDown().filter { it.isFile }.forEach { f ->
            val rel = f.relativeTo(dateien).invariantSeparatorsPath
            if (!ausgeschlossen(rel)) r += Quelle(DATEIEN + rel, f)
        }
        prefs?.listFiles()?.filter { it.isFile && it.name.endsWith(".xml") && !ausgeschlossen(it.name) }?.forEach { r += Quelle(PREFS + it.name, it) }
        return r.sortedBy { it.zipName }
    }

    /** Schreibt das Archiv; Rückgabe: Zahl der Dateien. */
    fun schreiben(out: OutputStream, quellen: List<Quelle>, kopf: Map<String, String>): Int {
        var n = 0
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(INHALT))
            val text = (mapOf("format" to FORMAT) + kopf).entries.joinToString("\n") { "${it.key}=${it.value.replace('\n', ' ')}" } +
                "\n" + quellen.joinToString("\n") { "eintrag=${it.zipName}" } + "\n"
            zip.write(text.encodeToByteArray())
            zip.closeEntry()
            for (q in quellen) {
                if (!q.datei.isFile) continue
                val e = ZipEntry(q.zipName).apply { time = q.datei.lastModified() }
                zip.putNextEntry(e)
                q.datei.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                n++
            }
        }
        return n
    }

    data class Inhalt(val kopf: Map<String, String>, val eintraege: List<String>) {
        val gueltig: Boolean get() = kopf["format"] == FORMAT
    }

    /** Nur das Inhaltsverzeichnis lesen. */
    fun inhalt(input: InputStream): Inhalt {
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name == INHALT) {
                    val zeilen = zip.readBytes().decodeToString().lines().filter { '=' in it }
                    val kopf = zeilen.filterNot { it.startsWith("eintrag=") }.associate { it.substringBefore('=') to it.substringAfter('=') }
                    return Inhalt(kopf, zeilen.filter { it.startsWith("eintrag=") }.map { it.substringAfter('=') })
                }
            }
        }
        return Inhalt(emptyMap(), emptyList())
    }

    /**
     * Packt die Einträge aus, für die [ziel] eine Datei liefert (null = überspringen). Schreibt erst in eine
     * Nachbardatei und benennt dann um. Einträge mit „..“ werden nie ausgepackt. Rückgabe: Zahl der Dateien.
     */
    fun auspacken(input: InputStream, ziel: (String) -> File?): Int {
        var n = 0
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory || e.name == INHALT || e.name.contains("..") || e.name.startsWith("/")) continue
                val f = ziel(e.name) ?: continue
                f.parentFile?.mkdirs()
                val tmp = File(f.parentFile, f.name + ".einspielen")
                tmp.outputStream().use { zip.copyTo(it) }
                if (!tmp.renameTo(f)) {
                    f.delete()
                    tmp.renameTo(f)
                }
                n++
            }
        }
        return n
    }
}
