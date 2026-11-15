package de.dachapp.vereinsmeisterschaft.logic

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Ein Schwimmer auf dem Zeitzettel: je Lage sein Lauf und seine Bahn (fehlt = nicht eingeteilt). */
data class ZettelZeile(val teilnehmer: Teilnehmer, val plaetze: Map<Lage, Pair<Int, Int>>) {
    /** Erster Lauf, für die Reihenfolge und die Trennlinien zwischen den Läufen. */
    val ersterLauf: Int get() = plaetze.values.minOfOrNull { it.first } ?: Int.MAX_VALUE
}

/** Alle Schwimmer einer Stufe mit den Lagen nebeneinander. */
data class ZettelGruppe(val titel: String, val strecken: List<Strecke>, val zeilen: List<ZettelZeile>)

/**
 * Zeiten-Datei zum Abtippen: eine Excel-Tabelle (.xlsx), eine Zeile je Schwimmer, die Lagen nebeneinander
 * (Bastian 2026-11-15: kompakt, wie der Zeitzettel). Gleiche Reihenfolge wie der Zeitzettel, damit man von oben nach
 * unten abtippen kann. Die Lagen-Spalten sind als Text formatiert, damit Excel „1:05,30“ nicht in eine Uhrzeit
 * verwandelt. Schon erfasste Zeiten stehen drin. Zurück geht die Datei als .xlsx oder CSV über „Zeiten importieren“
 * (Name + Lagen). Ohne Fremdbibliothek geschrieben.
 */
object Zeitdatei {
    /** Schwimmer je Stufe, in Laufreihenfolge (Stufen nach ihrem ersten Lauf, darin nach erstem Lauf und Bahn). */
    fun gruppen(v: Veranstaltung): List<ZettelGruppe> {
        val plaetze = HashMap<Long, MutableMap<Lage, Pair<Int, Int>>>()
        v.laeufe.forEach { l -> l.belegung.forEach { b -> plaetze.getOrPut(b.teilnehmer) { linkedMapOf() }[l.strecke.lage] = l.nr to b.bahn } }
        val zeilen = v.teilnehmer.filter { it.id in plaetze }.map { ZettelZeile(it, plaetze.getValue(it.id)) }
        return zeilen.groupBy { v.stufe(it.teilnehmer)?.id }.map { (id, z) ->
            val stufe = v.regeln.stufe(id)
            val lagen = z.flatMap { it.plaetze.keys }.toSet()
            val strecken = (stufe?.strecken.orEmpty().filter { it.lage in lagen } +
                v.laeufe.map { it.strecke }.filter { s -> s.lage in lagen && stufe?.strecke(s.lage) == null }.distinctBy { it.lage })
                .sortedBy { v.regeln.lagenFolge.indexOf(it.lage).let { i -> if (i < 0) 99 else i } }
            ZettelGruppe(
                stufe?.let { "${it.name} · ${it.jahrgaenge}" } ?: "ohne Stufe",
                strecken,
                z.sortedWith(compareBy({ it.ersterLauf }, { r -> r.plaetze.values.minByOrNull { it.first }?.second ?: 0 })),
            )
        }.sortedBy { g -> g.zeilen.minOf { it.ersterLauf } }
    }

    /** Spalten der Datei: Nachname, Vorname, Jahrgang, Stufe, alle vorkommenden Lagen, Bemerkung. */
    fun lagen(v: Veranstaltung): List<Lage> {
        val da = v.laeufe.map { it.strecke.lage }.toSet()
        return Lage.entries.filter { it in da }.sortedBy { v.regeln.lagenFolge.indexOf(it).let { i -> if (i < 0) 99 else i } }
    }

    fun kopf(v: Veranstaltung) = listOf("Nachname", "Vorname", "Jahrgang", "Stufe") + lagen(v).map { it.text } + "Bemerkung"

    /** Zeilen der Datei, in der Reihenfolge des Zeitzettels. Nicht geschwommene Lagen bleiben leer. */
    fun zeilen(v: Veranstaltung): List<List<String>> {
        val lagen = lagen(v)
        return gruppen(v).flatMap { g ->
            g.zeilen.map { z ->
                val t = z.teilnehmer
                listOf(t.nachname, t.vorname, t.jahrgang.toString(), v.stufe(t)?.name.orEmpty()) + lagen.map { l ->
                    val e = v.ergebnis(t.id, l)
                    if (e == null) "" else if (e.gueltig) Zeit.text(e.zeit) else e.status.kurz
                } + ""
            }
        }
    }

    fun csv(v: Veranstaltung): String = "\uFEFF" + (listOf(kopf(v)) + zeilen(v)).joinToString("\r\n") { z -> z.joinToString(";") } + "\r\n"

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun spalte(i: Int) = ('A' + i).toString()

    fun xlsx(v: Veranstaltung): ByteArray {
        val kopf = kopf(v)
        val alle = listOf(kopf) + zeilen(v)
        val zahlSpalten = setOf(2)
        val zeitSpalten = 4 until kopf.size
        val blatt = StringBuilder()
        blatt.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        blatt.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        blatt.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        blatt.append("<cols>")
        (listOf(16, 14, 9, 10) + List(kopf.size - 5) { 11 } + 22).forEachIndexed { i, w -> blatt.append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
        blatt.append("</cols><sheetData>")
        alle.forEachIndexed { r, z ->
            blatt.append("""<row r="${r + 1}">""")
            z.forEachIndexed { c, wert ->
                val ref = spalte(c) + (r + 1)
                // Stil 1 = fett (Kopf), 2 = Text-Format (Zeiten, Bemerkung), 0 = normal.
                val stil = when { r == 0 -> 1; c in zeitSpalten -> 2; else -> 0 }
                when {
                    r > 0 && c in zahlSpalten && wert.toIntOrNull() != null -> blatt.append("""<c r="$ref"><v>$wert</v></c>""")
                    wert.isEmpty() -> blatt.append("""<c r="$ref" s="$stil"/>""")
                    else -> blatt.append("""<c r="$ref" t="inlineStr" s="$stil"><is><t>${esc(wert)}</t></is></c>""")
                }
            }
            blatt.append("</row>")
        }
        blatt.append("</sheetData></worksheet>")

        val dateien = linkedMapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Zeiten" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""",
            "xl/styles.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="49" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs></styleSheet>""",
            "xl/worksheets/sheet1.xml" to blatt.toString(),
        )
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            dateien.forEach { (name, inhalt) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(inhalt.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
