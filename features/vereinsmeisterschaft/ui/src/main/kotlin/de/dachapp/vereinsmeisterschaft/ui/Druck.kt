package de.dachapp.vereinsmeisterschaft.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import de.dachapp.vereinsmeisterschaft.logic.Geschlecht
import de.dachapp.vereinsmeisterschaft.logic.Punkteurkunde
import de.dachapp.vereinsmeisterschaft.logic.Staffel
import de.dachapp.vereinsmeisterschaft.logic.Status
import de.dachapp.vereinsmeisterschaft.logic.Urkunde
import de.dachapp.vereinsmeisterschaft.logic.Veranstaltung
import de.dachapp.vereinsmeisterschaft.logic.Wertung
import de.dachapp.vereinsmeisterschaft.logic.Zeit
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Urkunden, Listen und Zeitzettel als PDF (A4, 595 × 842 Punkte). Gezeichnet wird auf einen Canvas, damit sich jede
 * Seite auch als Bild prüfen lässt.
 */
object Druck {
    const val B = 595f
    const val H = 842f

    private val BLAU = Color.rgb(11, 79, 138)
    private val GOLD = Color.rgb(184, 134, 11)
    private val GRAU = Color.rgb(100, 100, 100)
    private val HELL = Color.rgb(190, 190, 190)
    private val SCHWARZ = Color.rgb(25, 25, 25)

    private val serif = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    private val serifFett = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val sans = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val sansFett = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    private fun stift(groesse: Float, schrift: Typeface = sans, farbe: Int = SCHWARZ, ausrichtung: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = groesse; typeface = schrift; color = farbe; textAlign = ausrichtung }

    private fun linie(farbe: Int, dicke: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = farbe; strokeWidth = dicke; style = Paint.Style.STROKE }

    /** Text mittig, bei Bedarf kleiner, damit er in [breite] passt. */
    private fun Canvas.mitte(text: String, y: Float, p: Paint, breite: Float = B - 120) {
        val q = Paint(p).apply { textAlign = Paint.Align.CENTER }
        while (q.measureText(text) > breite && q.textSize > 8) q.textSize -= 1
        drawText(text, B / 2, y, q)
    }

    private fun Canvas.kuerzen(text: String, x: Float, y: Float, p: Paint, breite: Float) {
        var t = text
        while (p.measureText(t) > breite && t.length > 2) t = t.dropLast(2) + "…"
        drawText(t, x, y, p)
    }

    fun datumText(iso: String): String = runCatching {
        LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMANY))
    }.getOrDefault(iso)

    private fun unterzeile(v: Veranstaltung) = listOf(datumText(v.datum), v.ort).filter { it.isNotBlank() }.joinToString(" · ")

    // ---------- Urkunden ----------

    private fun rahmen(c: Canvas, farbe: Int, akzent: Int) {
        c.drawColor(Color.WHITE)
        c.drawRect(RectF(22f, 22f, B - 22f, H - 22f), linie(farbe, 3.5f))
        c.drawRect(RectF(31f, 31f, B - 31f, H - 31f), linie(akzent, 1f))
        // Ecken
        val e = linie(akzent, 2f)
        for ((x, y) in listOf(31f to 31f, B - 31f to 31f, 31f to H - 31f, B - 31f to H - 31f)) c.drawCircle(x, y, 4f, e)
    }

    private fun kopf(c: Canvas, v: Veranstaltung, logo: Bitmap?, farbe: Int): Float {
        var y = 66f
        if (logo != null) {
            val maxH = 92f
            val maxB = 180f
            val f = minOf(maxH / logo.height, maxB / logo.width)
            val w = logo.width * f
            val h = logo.height * f
            c.drawBitmap(logo, null, RectF(B / 2 - w / 2, y, B / 2 + w / 2, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
            y += h + 26f
        } else y += 60f
        c.mitte(v.verein.uppercase(Locale.GERMANY), y, stift(11f, sansFett, GRAU).apply { letterSpacing = 0.12f })
        y += 62f
        c.mitte("Urkunde", y, stift(56f, serifFett, farbe))
        return y
    }

    private fun fuss(c: Canvas, v: Veranstaltung) {
        val y = H - 92f
        val l = linie(GRAU, 0.8f)
        c.drawLine(80f, y, 250f, y, l)
        c.drawLine(B - 250f, y, B - 80f, y, l)
        c.drawText("Ort, Datum", 165f, y + 15f, stift(10f, sans, GRAU, Paint.Align.CENTER))
        c.drawText(v.unterschrift, B - 165f, y + 15f, stift(10f, sans, GRAU, Paint.Align.CENTER))
    }

    private fun tabelle(c: Canvas, start: Float, kopf: List<String>, zeilen: List<List<String>>, spalten: List<Float>, fett: Set<Int> = emptySet()): Float {
        val links = (B - spalten.sum()) / 2
        var y = start
        val k = stift(10f, sansFett, GRAU)
        var x = links
        kopf.forEachIndexed { i, t ->
            c.drawText(t.uppercase(Locale.GERMANY), if (i == 0) x else x + spalten[i], y, Paint(k).apply { textAlign = if (i == 0) Paint.Align.LEFT else Paint.Align.RIGHT; letterSpacing = 0.08f })
            x += spalten[i]
        }
        y += 8f
        c.drawLine(links, y, links + spalten.sum(), y, linie(HELL, 0.8f))
        y += 24f
        for (z in zeilen) {
            x = links
            z.forEachIndexed { i, t ->
                val p = stift(15f, if (i in fett) serifFett else serif, SCHWARZ, if (i == 0) Paint.Align.LEFT else Paint.Align.RIGHT)
                c.drawText(t, if (i == 0) x else x + spalten[i], y, p)
                x += spalten[i]
            }
            y += 9f
            c.drawLine(links, y, links + spalten.sum(), y, linie(Color.rgb(230, 230, 230), 0.6f))
            y += 23f
        }
        return y
    }

    private fun platzText(p: Int?) = if (p == null) "–" else "$p."

    fun urkunde(c: Canvas, v: Veranstaltung, u: Urkunde, logo: Bitmap?) {
        rahmen(c, BLAU, GOLD)
        var y = kopf(c, v, logo, BLAU)
        y += 36f
        c.mitte(v.titel, y, stift(20f, serif, SCHWARZ))
        y += 22f
        c.mitte(unterzeile(v), y, stift(12f, sans, GRAU))
        y += 66f
        c.mitte(u.teilnehmer.name, y, stift(36f, serifFett, SCHWARZ))
        y += 28f
        c.mitte(u.gruppe.titel + if (u.teilnehmer.ausserKonkurrenz) " · außer Konkurrenz" else "", y, stift(13f, sans, GRAU))
        y += 50f
        val zeilen = u.zeilen.map { z ->
            val e = z.ergebnis
            val zeit = when {
                e == null -> "–"
                e.status != Status.OK -> e.status.text
                else -> Zeit.text(e.zeit) + " s"
            }
            listOf(z.strecke.text, zeit, platzText(z.platz))
        }
        y = tabelle(c, y, listOf("Strecke", "Zeit", "Platz"), zeilen, listOf(230f, 120f, 70f), setOf(2))
        if (u.gesamtPlatz != null) {
            y += 22f
            c.mitte("Gesamtwertung im ${u.gruppe.jahrgaenge}: ${u.gesamtPlatz}. Platz", y, stift(17f, serifFett, BLAU))
        }
        fuss(c, v)
    }

    fun punkteurkunde(c: Canvas, v: Veranstaltung, u: Punkteurkunde, logo: Bitmap?) {
        rahmen(c, GOLD, BLAU)
        var y = kopf(c, v, logo, GOLD)
        y += 36f
        c.mitte("${u.wertung.pokal.name} · ${v.titel}", y, stift(20f, serif, SCHWARZ))
        y += 22f
        c.mitte(unterzeile(v), y, stift(12f, sans, GRAU))
        y += 66f
        c.mitte(u.teilnehmer.name, y, stift(36f, serifFett, SCHWARZ))
        y += 34f
        val platz = u.zeile.platz ?: 0
        val text = if (platz == 1) {
            if (u.teilnehmer.geschlecht == Geschlecht.W) "Siegerin im ${u.wertung.pokal.name}" else "Sieger im ${u.wertung.pokal.name}"
        } else "$platz. Platz im ${u.wertung.pokal.name}"
        c.mitte(text, y, stift(20f, serifFett, GOLD))
        y += 22f
        c.mitte("Wertung ${u.wertung.geschlecht.text} · ${u.teilnehmer.jahrgang.let { "Jahrgang $it" }}", y, stift(12f, sans, GRAU))
        y += 44f
        val r = v.regeln
        val g = u.teilnehmer.geschlecht ?: Geschlecht.W
        val zeilen = v.starts(u.teilnehmer).map { s ->
            val e = v.ergebnis(u.teilnehmer.id, s.lage)
            listOf(s.text, if (e?.gueltig == true) Zeit.text(e.zeit) + " s" else (e?.status?.text ?: "–"), Wertung.einzelText(r, e, s, g))
        }
        val punkte = if (r.wertung == de.dachapp.vereinsmeisterschaft.logic.Wertungsart.ZEITSUMME) "auf ${r.normBahnen} Bahnen" else "Punkte"
        y = tabelle(c, y, listOf("Strecke", "Zeit", punkte), zeilen, listOf(210f, 110f, 110f), setOf(2))
        y += 22f
        c.mitte("Zeitpunkte gesamt: ${Wertung.wertText(r, u.zeile)}", y, stift(17f, serifFett, BLAU))
        fuss(c, v)
    }

    fun staffelurkunde(c: Canvas, v: Veranstaltung, s: Staffel, logo: Bitmap?) {
        rahmen(c, BLAU, GOLD)
        var y = kopf(c, v, logo, BLAU)
        y += 36f
        c.mitte(v.titel, y, stift(20f, serif, SCHWARZ))
        y += 22f
        c.mitte(unterzeile(v), y, stift(12f, sans, GRAU))
        y += 70f
        c.mitte(s.name, y, stift(32f, serifFett, SCHWARZ))
        y += 30f
        if (s.mitglieder.isNotBlank()) {
            umbrechen(s.mitglieder, stift(14f, serif, GRAU), B - 160).forEach { c.mitte(it, y, stift(14f, serif, GRAU)); y += 20f }
        }
        y += 40f
        c.mitte("hat bei der Familienstaffel", y, stift(18f, serif, SCHWARZ))
        y += 26f
        c.mitte("mit viel Spaß gemeinsam ihre Aufgabe gemeistert.", y, stift(18f, serif, SCHWARZ))
        fuss(c, v)
    }

    private fun umbrechen(text: String, p: Paint, breite: Float): List<String> {
        val zeilen = ArrayList<String>()
        var akt = ""
        for (w in text.split(' ')) {
            val probe = if (akt.isEmpty()) w else "$akt $w"
            if (p.measureText(probe) > breite && akt.isNotEmpty()) { zeilen += akt; akt = w } else akt = probe
        }
        if (akt.isNotEmpty()) zeilen += akt
        return zeilen
    }

    // ---------- Listen ----------

    /** Schreibt fortlaufende Listen über mehrere Seiten. */
    private class Liste(private val doc: PdfDocument, private val titel: String, private val unter: String) {
        var c: Canvas? = null
        private var seite: PdfDocument.Page? = null
        private var nr = 0
        var y = 0f

        fun neueSeite() {
            seite?.let { doc.finishPage(it) }
            nr++
            val s = doc.startPage(PdfDocument.PageInfo.Builder(B.toInt(), H.toInt(), nr).create())
            seite = s
            c = s.canvas
            val cv = s.canvas
            cv.drawText(titel, 40f, 50f, stift(16f, sansFett, BLAU))
            cv.drawText(unter, 40f, 66f, stift(10f, sans, GRAU))
            cv.drawText("Seite $nr", B - 40f, 50f, stift(10f, sans, GRAU, Paint.Align.RIGHT))
            cv.drawLine(40f, 74f, B - 40f, 74f, linie(HELL, 0.8f))
            y = 96f
        }

        fun platz(h: Float) { if (c == null || y + h > H - 40f) neueSeite() }

        fun ende() { seite?.let { doc.finishPage(it) }; seite = null }
    }

    fun ergebnisliste(datei: File, v: Veranstaltung) = PdfDocument().let { doc ->
        val l = Liste(doc, "Ergebnisliste · ${v.titel}", unterzeile(v))
        val r = v.regeln
        val normal = stift(10f)
        val fett = stift(10f, sansFett)
        val grau = stift(9f, sans, GRAU)
        val medaillen = Wertung.medaillen(v)
        for (gw in Wertung.jahrgaenge(v)) {
            l.platz(60f)
            l.c!!.drawText(gw.gruppe.titel, 40f, l.y, stift(13f, sansFett, BLAU)); l.y += 18f
            for (lw in gw.lagen) {
                l.platz(30f)
                l.c!!.drawText(lw.strecke.text, 48f, l.y, fett); l.y += 14f
                for (z in lw.zeilen) {
                    l.platz(14f)
                    val c = l.c!!
                    c.drawText(platzText(z.platz), 70f, l.y, Paint(normal).apply { textAlign = Paint.Align.RIGHT })
                    c.kuerzen(z.teilnehmer.name, 80f, l.y, normal, 230f)
                    c.drawText(z.teilnehmer.jahrgang.toString(), 330f, l.y, grau)
                    val e = z.ergebnis
                    c.drawText(if (e == null) "–" else if (e.gueltig) Zeit.text(e.zeit) else e.status.kurz, 440f, l.y, Paint(normal).apply { textAlign = Paint.Align.RIGHT })
                    l.y += 13f
                }
                l.y += 6f
            }
            l.platz(30f)
            l.c!!.drawText("Gesamt (Zeitpunkte, hochgerechnet auf ${r.normBahnen} Bahnen)", 48f, l.y, fett); l.y += 14f
            for (z in gw.gesamt) {
                l.platz(14f)
                val c = l.c!!
                c.drawText(platzText(z.platz), 70f, l.y, Paint(normal).apply { textAlign = Paint.Align.RIGHT })
                c.kuerzen(z.teilnehmer.name + if (z.teilnehmer.id in medaillen) "  ●" else "", 80f, l.y, normal, 230f)
                c.drawText(Wertung.wertText(r, z), 440f, l.y, Paint(normal).apply { textAlign = Paint.Align.RIGHT })
                l.y += 13f
            }
            l.y += 14f
        }
        for (pw in Wertung.pokale(v)) {
            l.platz(50f)
            l.c!!.drawText(pw.titel, 40f, l.y, stift(13f, sansFett, GOLD)); l.y += 16f
            for (z in pw.zeilen) {
                l.platz(14f)
                val c = l.c!!
                c.drawText(platzText(z.platz), 70f, l.y, Paint(normal).apply { textAlign = Paint.Align.RIGHT })
                c.kuerzen(z.teilnehmer.name, 80f, l.y, normal, 230f)
                c.drawText(z.teilnehmer.jahrgang.toString(), 330f, l.y, grau)
                c.drawText(Wertung.wertText(r, z), 440f, l.y, Paint(normal).apply { textAlign = Paint.Align.RIGHT })
                l.y += 13f
            }
            l.y += 14f
        }
        if (l.c == null) l.neueSeite()
        l.c!!.drawText("● = Medaille (Plätze 1–${r.medaillenPlaetze} je Jahrgang und Geschlecht, gesamt)", 40f, H - 30f, grau)
        l.ende()
        speichern(doc, datei)
    }

    fun laufliste(datei: File, v: Veranstaltung) = PdfDocument().let { doc ->
        val l = Liste(doc, "Laufliste · ${v.titel}", unterzeile(v))
        val normal = stift(11f)
        val grau = stift(9f, sans, GRAU)
        var strecke: de.dachapp.vereinsmeisterschaft.logic.Strecke? = null
        for (lauf in v.laeufe) {
            if (lauf.strecke != strecke) {
                strecke = lauf.strecke
                l.platz(80f)
                l.y += 6f
                l.c!!.drawText(lauf.strecke.text, 40f, l.y, stift(13f, sansFett, BLAU)); l.y += 18f
            }
            l.platz(20f + 15f * v.regeln.bahnenImBecken)
            l.c!!.drawText("Lauf ${lauf.nr}", 48f, l.y, stift(11f, sansFett)); l.y += 15f
            for (b in 1..v.regeln.bahnenImBecken) {
                val t = lauf.auf(b)?.let { v.person(it) }
                val c = l.c!!
                c.drawText("Bahn $b", 60f, l.y, grau)
                if (t != null) {
                    c.kuerzen(t.name, 110f, l.y, normal, 220f)
                    c.drawText("${t.jahrgang} ${t.geschlecht?.kurz.orEmpty()}", 340f, l.y, grau)
                    t.meldezeiten[lauf.strecke.lage]?.let { c.drawText(Zeit.text(it), 450f, l.y, Paint(grau).apply { textAlign = Paint.Align.RIGHT }) }
                    val e = v.ergebnis(t.id, lauf.strecke.lage)
                    if (e != null) c.drawText(if (e.gueltig) Zeit.text(e.zeit) else e.status.kurz, B - 40f, l.y, Paint(normal).apply { textAlign = Paint.Align.RIGHT })
                } else c.drawText("frei", 110f, l.y, grau)
                l.y += 15f
            }
            l.y += 6f
        }
        if (l.c == null) l.neueSeite()
        l.ende()
        speichern(doc, datei)
    }

    /**
     * Zeitzettel je Bahn: jede Zeile beginnt mit dem Kennzeichen „L03 B2“, das die Texterkennung sicher liest; rechts
     * Kästchen für Minuten : Sekunden , Hundertstel. Auf der Zeile stehen sonst keine Ziffern.
     */
    fun zeitzettel(c: Canvas, v: Veranstaltung, bahn: Int, laeufe: List<de.dachapp.vereinsmeisterschaft.logic.Lauf>, seite: Int, seiten: Int) {
        c.drawColor(Color.WHITE)
        c.drawText("Zeitzettel Bahn $bahn", 40f, 54f, stift(22f, sansFett, BLAU))
        c.drawText("${v.titel} · Blatt $seite/$seiten", B - 40f, 54f, stift(10f, sans, GRAU, Paint.Align.RIGHT))
        c.drawText("Zeit in die Kästchen: Minuten : Sekunden , Hundertstel – eine Ziffer je Kästchen, Minuten leer lassen, wenn unter 1 Minute.", 40f, 74f, stift(8.5f, sans, GRAU))
        c.drawText("Nicht angetreten: „na“, disqualifiziert: „dq“ rechts neben die Kästchen schreiben.", 40f, 86f, stift(8.5f, sans, GRAU))
        var y = 120f
        var strecke: de.dachapp.vereinsmeisterschaft.logic.Strecke? = null
        val code = stift(16f, mono)
        val name = stift(13f)
        // Sehr helle Kästchen, damit die Texterkennung die Ränder nicht als Ziffer „1“ liest.
        val kasten = linie(Color.rgb(205, 205, 205), 0.8f)
        for (lauf in laeufe) {
            if (lauf.strecke != strecke) {
                strecke = lauf.strecke
                y += 8f
                c.drawText(lauf.strecke.text + if (lauf != laeufe.first() || seite == 1) "" else " (Fortsetzung)", 40f, y, stift(11f, sansFett, GRAU))
                y += 26f
            }
            val t = lauf.auf(bahn)?.let { v.person(it) }
            c.drawText("L%02d B%d".format(lauf.nr, bahn), 40f, y, code)
            c.kuerzen(t?.name ?: "(frei)", 140f, y, if (t == null) stift(13f, sans, HELL) else name, 200f)
            // Kästchen: [m] : [s][s] , [h][h]
            var x = 350f
            val w = 24f
            fun box() { c.drawRoundRect(RectF(x, y - 22f, x + w, y + 8f), 3f, 3f, kasten); x += w + 4f }
            box(); c.drawText(":", x + 1f, y, stift(16f, sansFett, GRAU)); x += 10f
            box(); box(); c.drawText(",", x + 1f, y, stift(16f, sansFett, GRAU)); x += 10f
            box(); box()
            y += 46f
        }
    }

    /** Läufe einer Bahn, aufgeteilt auf Seiten (gleiche Höhen wie beim Zeichnen: Zeile 46, Streckenkopf 34). */
    fun zettelSeiten(v: Veranstaltung, bahn: Int): List<List<de.dachapp.vereinsmeisterschaft.logic.Lauf>> {
        val seiten = ArrayList<List<de.dachapp.vereinsmeisterschaft.logic.Lauf>>()
        var akt = ArrayList<de.dachapp.vereinsmeisterschaft.logic.Lauf>()
        var y = 120f
        var strecke: de.dachapp.vereinsmeisterschaft.logic.Strecke? = null
        for (lauf in v.laeufe.filter { it.auf(bahn) != null }) {
            val kopf = if (lauf.strecke != strecke || akt.isEmpty()) 34f else 0f
            if (akt.isNotEmpty() && y + kopf + 46f > H - 30f) {
                seiten += akt; akt = ArrayList(); y = 120f
            }
            y += (if (lauf.strecke != strecke || akt.isEmpty()) 34f else 0f) + 46f
            strecke = lauf.strecke
            akt += lauf
        }
        if (akt.isNotEmpty()) seiten += akt
        return seiten
    }

    fun zeitzettel(datei: File, v: Veranstaltung) = PdfDocument().let { doc ->
        var nr = 0
        for (bahn in 1..v.regeln.bahnenImBecken) {
            val seiten = zettelSeiten(v, bahn)
            seiten.forEachIndexed { i, laeufe ->
                val s = doc.startPage(PdfDocument.PageInfo.Builder(B.toInt(), H.toInt(), ++nr).create())
                zeitzettel(s.canvas, v, bahn, laeufe, i + 1, seiten.size)
                doc.finishPage(s)
            }
        }
        if (nr == 0) doc.startPage(PdfDocument.PageInfo.Builder(B.toInt(), H.toInt(), 1).create()).also { doc.finishPage(it) }
        speichern(doc, datei)
    }

    // ---------- Urkunden als Datei ----------

    fun urkunden(datei: File, v: Veranstaltung, urkunden: List<Urkunde>, punkte: List<Punkteurkunde>, staffeln: List<Staffel>, logo: Bitmap?) =
        PdfDocument().let { doc ->
            var nr = 0
            fun seite(zeichnen: (Canvas) -> Unit) {
                val s = doc.startPage(PdfDocument.PageInfo.Builder(B.toInt(), H.toInt(), ++nr).create())
                zeichnen(s.canvas)
                doc.finishPage(s)
            }
            urkunden.forEach { u -> seite { urkunde(it, v, u, logo) } }
            punkte.forEach { u -> seite { punkteurkunde(it, v, u, logo) } }
            staffeln.forEach { s -> seite { staffelurkunde(it, v, s, logo) } }
            if (nr == 0) seite { it.drawColor(Color.WHITE) }
            speichern(doc, datei)
        }

    private fun speichern(doc: PdfDocument, datei: File) {
        try {
            datei.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }
}
