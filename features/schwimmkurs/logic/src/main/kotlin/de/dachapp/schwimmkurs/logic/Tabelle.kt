package de.dachapp.schwimmkurs.logic

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Eine eingelesene Tabelle: Kopfzeile und Datenzeilen, alle Zellen als Text. */
data class Tabelle(val kopf: List<String>, val zeilen: List<List<String>>) {
    fun zelle(zeile: Int, spalte: Int): String = zeilen.getOrNull(zeile)?.getOrNull(spalte).orEmpty()
}

class TabellenFehler(message: String) : Exception(message)

/** Liest CSV- und Excel-Dateien (.xlsx) ohne Fremdbibliothek. */
object TabellenLeser {

    fun lesen(bytes: ByteArray): Tabelle {
        val roh = when {
            bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> xlsx(bytes)
            bytes.size >= 8 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() ->
                throw TabellenFehler("Alte Excel-Dateien (.xls) gehen nicht. Bitte in Excel als .xlsx oder .csv speichern.")
            else -> csv(text(bytes))
        }
        val zeilen = roh.map { z -> z.map { it.trim() } }.filter { z -> z.any { it.isNotEmpty() } }
        if (zeilen.isEmpty()) throw TabellenFehler("Die Datei enthält keine Daten.")
        val breite = zeilen.maxOf { it.size }
        val voll = zeilen.map { it + List(breite - it.size) { "" } }
        // Rechts komplett leere Spalten weglassen.
        val genutzt = (0 until breite).filter { s -> voll.any { it[s].isNotEmpty() } }
        val tab = voll.map { z -> genutzt.map { z[it] } }
        return Tabelle(tab.first(), tab.drop(1))
    }

    /** UTF-8 (mit oder ohne BOM), sonst Windows-1252, wie es Excel beim CSV-Speichern oft nimmt. */
    fun text(bytes: ByteArray): String {
        val ohneBom = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            bytes.copyOfRange(3, bytes.size)
        } else bytes
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(ohneBom)).toString()
        } catch (e: CharacterCodingException) {
            String(ohneBom, Charset.forName("windows-1252"))
        }
    }

    /** CSV mit automatisch erkanntem Trennzeichen (; , Tab) und Anführungszeichen nach RFC 4180. */
    fun csv(text: String): List<List<String>> {
        val trenner = trennzeichen(text)
        val zeilen = mutableListOf<List<String>>()
        var zeile = mutableListOf<String>()
        val feld = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { feld.append('"'); i++ } else inQuotes = false
                } else feld.append(c)
            } else when (c) {
                '"' -> inQuotes = true
                trenner -> { zeile.add(feld.toString()); feld.clear() }
                '\r' -> {}
                '\n' -> { zeile.add(feld.toString()); feld.clear(); zeilen.add(zeile); zeile = mutableListOf() }
                else -> feld.append(c)
            }
            i++
        }
        if (feld.isNotEmpty() || zeile.isNotEmpty()) { zeile.add(feld.toString()); zeilen.add(zeile) }
        return zeilen
    }

    private fun trennzeichen(text: String): Char {
        val ersteZeilen = text.lineSequence().filter { it.isNotBlank() }.take(5).toList()
        return listOf(';', '\t', ',').maxBy { t -> ersteZeilen.sumOf { z -> ausserhalbQuotes(z).count { it == t } } }
    }

    private fun ausserhalbQuotes(z: String): String {
        val sb = StringBuilder()
        var q = false
        for (c in z) if (c == '"') q = !q else if (!q) sb.append(c)
        return sb.toString()
    }

    // ---------- Excel ----------

    private fun xlsx(bytes: ByteArray): List<List<String>> {
        val dateien = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val n = e.name.trimStart('/')
                if (n.startsWith("xl/") && n.endsWith(".xml") || n.endsWith(".rels")) dateien[n] = zip.readBytes()
            }
        }
        val blatt = erstesBlatt(dateien) ?: throw TabellenFehler("Keine Tabelle in der Excel-Datei gefunden.")
        val texte = dateien["xl/sharedStrings.xml"]?.let { geteilteTexte(it) }.orEmpty()
        return blattLesen(dateien.getValue(blatt), texte)
    }

    private fun xml(bytes: ByteArray): Element {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { f.isExpandEntityReferences = false }
        return f.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
    }

    private fun Node.kinder(name: String): List<Element> {
        val out = mutableListOf<Element>()
        val l = childNodes
        for (i in 0 until l.length) {
            val n = l.item(i)
            if (n is Element && (n.localName ?: n.nodeName) == name) out.add(n)
        }
        return out
    }

    private fun Element.alle(name: String): List<Element> {
        val l = getElementsByTagNameNS("*", name)
        return (0 until l.length).map { l.item(it) as Element }
    }

    /** Erstes Blatt in der Reihenfolge der Arbeitsmappe. */
    private fun erstesBlatt(dateien: Map<String, ByteArray>): String? {
        val ausMappe = runCatching {
            val mappe = xml(dateien.getValue("xl/workbook.xml"))
            val rid = mappe.alle("sheet").first().let { s ->
                s.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
            }
            val rels = xml(dateien.getValue("xl/_rels/workbook.xml.rels"))
            val ziel = rels.alle("Relationship").first { it.getAttribute("Id") == rid }.getAttribute("Target")
            if (ziel.startsWith("/")) ziel.trimStart('/') else "xl/$ziel"
        }.getOrNull()
        if (ausMappe != null && ausMappe in dateien) return ausMappe
        return dateien.keys.filter { it.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
            .minByOrNull { it.filter(Char::isDigit).toInt() }
    }

    private fun geteilteTexte(bytes: ByteArray): List<String> =
        xml(bytes).kinder("si").map { si ->
            // Text direkt oder aus formatierten Abschnitten; Lautschrift (rPh) gehört nicht dazu.
            val direkt = si.kinder("t")
            if (direkt.isNotEmpty()) direkt.joinToString("") { it.textContent }
            else si.kinder("r").joinToString("") { r -> r.kinder("t").joinToString("") { it.textContent } }
        }

    private fun blattLesen(bytes: ByteArray, texte: List<String>): List<List<String>> {
        val daten = xml(bytes).alle("sheetData").firstOrNull() ?: return emptyList()
        val zeilen = sortedMapOf<Int, MutableMap<Int, String>>()
        var naechsteZeile = 1
        for (row in daten.kinder("row")) {
            val nr = row.getAttribute("r").toIntOrNull() ?: naechsteZeile
            naechsteZeile = nr + 1
            val zellen = zeilen.getOrPut(nr) { sortedMapOf() }
            var naechsteSpalte = 0
            for (c in row.kinder("c")) {
                val spalte = spaltenIndex(c.getAttribute("r")) ?: naechsteSpalte
                naechsteSpalte = spalte + 1
                val v = c.kinder("v").firstOrNull()?.textContent
                val wert = when (c.getAttribute("t")) {
                    "s" -> v?.toIntOrNull()?.let { texte.getOrNull(it) }
                    "inlineStr" -> c.kinder("is").firstOrNull()?.alle("t")?.joinToString("") { it.textContent }
                    "b" -> if (v == "1") "ja" else "nein"
                    "n", "" -> v?.let(::zahl)
                    else -> v
                }.orEmpty()
                if (wert.isNotEmpty()) zellen[spalte] = wert
            }
        }
        if (zeilen.isEmpty()) return emptyList()
        return zeilen.values.map { z ->
            val breite = (z.keys.maxOrNull() ?: -1) + 1
            List(breite) { z[it].orEmpty() }
        }
    }

    /** "12345.0" → "12345"; Excel speichert ganze Zahlen manchmal mit Nachkommastelle. */
    private fun zahl(v: String): String = v.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: v

    private fun spaltenIndex(ref: String): Int? {
        val buchstaben = ref.takeWhile { it.isLetter() }.uppercase()
        if (buchstaben.isEmpty()) return null
        return buchstaben.fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) } - 1
    }
}
