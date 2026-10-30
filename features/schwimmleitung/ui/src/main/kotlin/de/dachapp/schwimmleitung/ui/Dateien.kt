package de.dachapp.schwimmleitung.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import de.dachapp.schwimmleitung.logic.Beleg
import de.dachapp.schwimmleitung.logic.Bericht
import de.dachapp.schwimmleitung.logic.Format
import de.dachapp.schwimmleitung.logic.Protokoll
import java.io.ByteArrayOutputStream
import java.io.File

/** PDF und CSV erzeugen und über Android teilen (Mail, Drive, WhatsApp …). Kein eigener Netzzugriff. */
object Dateien {

    private fun ordner(context: Context) = File(context.cacheDir, "schwimmleitung").apply { mkdirs() }

    private fun uri(context: Context, datei: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".schwimmleitung.dateien", datei)

    fun dateiname(text: String) = text.replace(Regex("[^A-Za-z0-9ÄÖÜäöüß_-]+"), "_").trim('_').take(60).ifEmpty { "datei" }

    /** Leere Datei für ein Kamerafoto; die Kamera-App schreibt über die zurückgegebene Adresse hinein. */
    fun fotoZiel(context: Context): Uri {
        val f = File(ordner(context), "foto.jpg")
        f.delete()
        f.createNewFile()
        return uri(context, f)
    }

    fun teilen(context: Context, datei: File, typ: String, betreff: String) {
        val u = uri(context, datei)
        val senden = Intent(Intent.ACTION_SEND).apply {
            type = typ
            putExtra(Intent.EXTRA_SUBJECT, betreff)
            putExtra(Intent.EXTRA_STREAM, u)
            clipData = ClipData.newRawUri(datei.name, u)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        starten(context, Intent.createChooser(senden, "Teilen"))
    }

    fun csvTeilen(context: Context, name: String, inhalt: String) {
        val datei = File(ordner(context), dateiname(name) + ".csv")
        datei.writeText(inhalt)
        teilen(context, datei, "text/csv", name)
    }

    fun berichtTeilen(context: Context, b: Bericht) {
        val datei = File(ordner(context), dateiname(b.titel) + ".pdf")
        berichtPdf(datei, b)
        teilen(context, datei, "application/pdf", b.titel)
    }

    fun protokollTeilen(context: Context, verein: String, titel: String, abschnitte: List<Protokoll.Abschnitt>, protokollant: String) {
        val datei = File(ordner(context), dateiname(titel) + ".pdf")
        protokollPdf(datei, verein, titel, abschnitte, protokollant)
        teilen(context, datei, "application/pdf", titel)
    }

    fun belegeTeilen(context: Context, verein: String, belege: List<Beleg>) {
        val titel = "Belege " + Format.datum(java.time.LocalDate.now())
        val datei = File(ordner(context), dateiname(titel) + ".pdf")
        belegePdf(datei, verein, titel, belege)
        teilen(context, datei, "application/pdf", titel)
    }

    /** Link aus der Ablage öffnen (Drive, Browser, PDF-Anzeige). */
    fun oeffnen(context: Context, link: String) {
        val l = link.trim().let { if (it.contains("://")) it else "https://$it" }
        starten(context, Intent(Intent.ACTION_VIEW, l.toUri()))
    }

    private fun starten(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Keine passende App gefunden.", Toast.LENGTH_LONG).show()
        false
    }

    // ---------- Fotos ----------

    private const val FOTO_KANTE = 960

    /**
     * Foto verkleinern (längste Kante 960 Pixel), richtig drehen und als JPEG in Base64 umwandeln. So passt es in die
     * geteilte Datei (etwa 60–120 KB je Foto). null, wenn das Bild nicht lesbar ist.
     */
    fun fotoLesen(context: Context, quelle: Uri): String? = runCatching {
        val r = context.contentResolver
        val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        r.openInputStream(quelle)?.use { BitmapFactory.decodeStream(it, null, opt) }
        if (opt.outWidth <= 0) return null
        var stufe = 1
        while (maxOf(opt.outWidth, opt.outHeight) / (stufe * 2) >= FOTO_KANTE) stufe *= 2
        val roh = r.openInputStream(quelle)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = stufe }) }
            ?: return null
        val drehung = r.openInputStream(quelle)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val faktor = minOf(1f, FOTO_KANTE.toFloat() / maxOf(roh.width, roh.height))
        val m = Matrix().apply { postScale(faktor, faktor); postRotate(drehung) }
        val bild = Bitmap.createBitmap(roh, 0, 0, roh.width, roh.height, m, true)
        val out = ByteArrayOutputStream()
        bild.compress(Bitmap.CompressFormat.JPEG, 70, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    fun fotoBitmap(base64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    // ---------- PDF ----------

    private const val BREITE = 595 // A4 in Punkt
    private const val HOEHE = 842
    private const val RAND = 50f
    private const val TEXT_BREITE = BREITE - 2 * RAND
    private const val AKZENT = 0xFF1565C0.toInt()
    private const val GRAU = 0xFF5F6368.toInt()
    private const val HELL = 0xFFF1F4F9.toInt()

    private fun stift(groesse: Float, fett: Boolean = false, farbe: Int = 0xFF202124.toInt()) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = groesse
            color = farbe
            if (fett) typeface = Typeface.DEFAULT_BOLD
        }

    /** Seitenweise Schreiben von oben nach unten, mit automatischem Seitenwechsel. */
    private class Seiten(private val doc: PdfDocument, private val fusszeile: String) {
        private var nr = 0
        private var seite: PdfDocument.Page? = null
        var y = RAND
        val c: Canvas get() = seite!!.canvas

        fun neu() {
            beenden()
            nr++
            seite = doc.startPage(PdfDocument.PageInfo.Builder(BREITE, HOEHE, nr).create())
            y = RAND
        }

        /** Beginnt eine neue Seite, wenn [hoehe] nicht mehr passt. */
        fun platz(hoehe: Float) {
            if (seite == null || y + hoehe > HOEHE - RAND - 20f) neu()
        }

        fun beenden() {
            val s = seite ?: return
            s.canvas.drawText("$fusszeile · Seite $nr", RAND, HOEHE - RAND / 2, stift(8f, farbe = GRAU))
            doc.finishPage(s)
            seite = null
        }

        /** Umbrochener Text ab [x] mit Breite [breite]; lange Texte laufen über mehrere Seiten. */
        fun text(t: String, paint: TextPaint, abstandDanach: Float = 6f, x: Float = RAND, breite: Float = TEXT_BREITE) {
            if (t.isBlank()) return
            val layout = StaticLayout.Builder.obtain(t, 0, t.length, paint, breite.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(1.5f, 1.1f)
                .build()
            var zeile = 0
            while (zeile < layout.lineCount) {
                platz(layout.getLineBottom(zeile) - layout.getLineTop(zeile).toFloat())
                val oben = layout.getLineTop(zeile).toFloat()
                var ende = zeile
                while (ende < layout.lineCount && layout.getLineBottom(ende) - oben <= HOEHE - RAND - 20f - y) ende++
                if (ende == zeile) ende++
                val unten = layout.getLineBottom(ende - 1).toFloat()
                c.save()
                c.translate(x, y - oben)
                c.clipRect(0f, oben, breite, unten)
                layout.draw(c)
                c.restore()
                y += unten - oben
                zeile = ende
            }
            y += abstandDanach
        }
    }

    private fun kopf(s: Seiten, verein: String, titel: String, untertitel: String?) {
        if (verein.isNotBlank()) {
            s.c.drawText(verein, RAND, s.y + 9f, stift(9f, farbe = GRAU))
            s.y += 16f
        }
        s.c.drawRect(RAND, s.y, RAND + 40f, s.y + 3f, Paint().apply { color = AKZENT })
        s.y += 26f
        s.text(titel, stift(20f, fett = true), 2f)
        if (untertitel != null) s.text(untertitel, stift(11f, farbe = GRAU), 0f)
        s.y += 14f
    }

    private fun ueberschrift(s: Seiten, t: String) {
        s.platz(60f)
        s.y += 6f
        s.text(t, stift(13f, fett = true, farbe = AKZENT), 4f)
    }

    /** Zweispaltige Kennzahlen-Tabelle mit Vorjahr; lange Bezeichnungen werden umbrochen. */
    private fun kennzahlen(s: Seiten, zeilen: List<de.dachapp.schwimmleitung.logic.Zeile>, mitVorjahr: Boolean) {
        val wertX = RAND + TEXT_BREITE * 0.58f
        val vorX = RAND + TEXT_BREITE * 0.82f
        val linie = Paint().apply { color = 0xFFDADCE0.toInt(); strokeWidth = 0.7f }
        if (mitVorjahr) {
            s.platz(16f)
            s.c.drawText("Zeitraum", wertX + 4f, s.y + 10f, stift(8.5f, fett = true, farbe = GRAU))
            s.c.drawText("Vorjahr", vorX + 4f, s.y + 10f, stift(8.5f, fett = true, farbe = GRAU))
            s.y += 14f
        }
        zeilen.forEachIndexed { i, z ->
            val paint = stift(10f)
            val layout = StaticLayout.Builder.obtain(z.text, 0, z.text.length, paint, (wertX - RAND - 8f).toInt()).build()
            val h = maxOf(18f, layout.height + 6f)
            s.platz(h)
            if (i % 2 == 1) s.c.drawRect(RAND, s.y, BREITE - RAND, s.y + h, Paint().apply { color = HELL })
            s.c.save()
            s.c.translate(RAND + 4f, s.y + 3f)
            layout.draw(s.c)
            s.c.restore()
            s.c.drawText(z.wert, wertX + 4f, s.y + 13f, stift(10f, fett = true))
            if (mitVorjahr) s.c.drawText(z.vorjahr, vorX + 4f, s.y + 13f, stift(10f, farbe = GRAU))
            s.y += h
            s.c.drawLine(RAND, s.y, BREITE - RAND, s.y, linie)
        }
        s.y += 6f
    }

    private fun balken(s: Seiten, titel: String, werte: List<de.dachapp.schwimmleitung.logic.Balken>) {
        if (werte.isEmpty()) return
        val hoehe = 90f
        s.platz(hoehe + 40f)
        s.text(titel, stift(9.5f, fett = true, farbe = GRAU), 4f)
        val breite = TEXT_BREITE / werte.size
        val boden = s.y + hoehe
        val fuell = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AKZENT }
        werte.forEachIndexed { i, b ->
            val x = RAND + i * breite
            val h = (hoehe - 14f) * b.anteil.toFloat()
            s.c.drawRoundRect(RectF(x + breite * 0.2f, boden - h, x + breite * 0.8f, boden), 2f, 2f, fuell)
            val w = stift(7.5f)
            s.c.drawText(b.wert, x + breite / 2 - w.measureText(b.wert) / 2, boden - h - 3f, w)
            val l = stift(7.5f, farbe = GRAU)
            s.c.drawText(b.beschriftung, x + breite / 2 - l.measureText(b.beschriftung) / 2, boden + 11f, l)
        }
        s.y = boden + 22f
    }

    fun berichtPdf(datei: File, b: Bericht) {
        val doc = PdfDocument()
        try {
            val s = Seiten(doc, b.verein.ifBlank { "Schwimmleitung" })
            s.neu()
            kopf(s, b.verein, b.titel, b.untertitel)
            for (t in b.teile) {
                ueberschrift(s, t.titel)
                kennzahlen(s, t.zeilen, t.zeilen.any { it.vorjahr.isNotEmpty() })
                balken(s, t.balkenTitel, t.balken)
                if (t.hinweis.isNotBlank()) s.text(t.hinweis, stift(9.5f, farbe = GRAU))
            }
            s.y += 8f
            s.text("Erstellt am ${Format.datum(b.erstellt)}. " + b.quellen.joinToString(" · "), stift(8.5f, farbe = GRAU))
            s.beenden()
            datei.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    fun protokollPdf(datei: File, verein: String, titel: String, abschnitte: List<Protokoll.Abschnitt>, protokollant: String) {
        val doc = PdfDocument()
        try {
            val s = Seiten(doc, verein.ifBlank { "Protokoll" })
            s.neu()
            kopf(s, verein, titel, null)
            for (a in abschnitte) {
                ueberschrift(s, a.titel)
                s.text(a.text, stift(10.5f), 8f)
            }
            if (protokollant.isNotBlank()) {
                s.y += 12f
                s.text("Protokoll: $protokollant", stift(10f, farbe = GRAU))
            }
            s.beenden()
            datei.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    /** Belegliste mit Summen, danach jedes Belegfoto groß, damit der Kassenwart alles in einer Datei hat. */
    fun belegePdf(datei: File, verein: String, titel: String, belege: List<Beleg>) {
        val doc = PdfDocument()
        try {
            val s = Seiten(doc, verein.ifBlank { "Belege" })
            s.neu()
            val aus = belege.filter { !it.einnahme }.sumOf { it.betragCent }
            val ein = belege.filter { it.einnahme }.sumOf { it.betragCent }
            kopf(s, verein, titel, "${belege.size} Belege · Ausgaben ${Format.euro(aus)} · Einnahmen ${Format.euro(ein)}")
            val xs = listOf(RAND, RAND + 70f, RAND + 150f, RAND + 360f)
            val linie = Paint().apply { color = 0xFFDADCE0.toInt(); strokeWidth = 0.7f }
            s.platz(18f)
            listOf("Datum", "Betrag", "Zweck", "Von").forEachIndexed { i, k -> s.c.drawText(k, xs[i] + 4f, s.y + 12f, stift(9f, fett = true, farbe = GRAU)) }
            s.y += 18f
            belege.sortedBy { it.datum }.forEachIndexed { i, b ->
                val zweck = StaticLayout.Builder.obtain(b.zweck, 0, b.zweck.length, stift(10f), 200).build()
                val h = maxOf(18f, zweck.height + 6f)
                s.platz(h)
                if (i % 2 == 1) s.c.drawRect(RAND, s.y, BREITE - RAND, s.y + h, Paint().apply { color = HELL })
                s.c.drawText(Format.datum(b.datum), xs[0] + 4f, s.y + 13f, stift(10f))
                s.c.drawText((if (b.einnahme) "+" else "−") + Format.euro(b.betragCent), xs[1] + 4f, s.y + 13f, stift(10f, fett = true))
                s.c.save(); s.c.translate(xs[2] + 4f, s.y + 3f); zweck.draw(s.c); s.c.restore()
                s.c.drawText(b.von.take(24) + if (b.foto != null) " (Foto)" else "", xs[3] + 4f, s.y + 13f, stift(10f))
                s.y += h
                s.c.drawLine(RAND, s.y, BREITE - RAND, s.y, linie)
            }
            for (b in belege.sortedBy { it.datum }) {
                val bild = b.foto?.let(::fotoBitmap) ?: continue
                s.neu()
                s.text("${Format.datum(b.datum)} · ${Format.euro(b.betragCent)} · ${b.zweck}", stift(11f, fett = true), 8f)
                val maxH = HOEHE - RAND - 30f - s.y
                val f = minOf(TEXT_BREITE / bild.width, maxH / bild.height)
                val ziel = RectF(RAND, s.y, RAND + bild.width * f, s.y + bild.height * f)
                s.c.drawBitmap(bild, null, ziel, Paint(Paint.FILTER_BITMAP_FLAG))
                s.y = ziel.bottom + 8f
            }
            s.beenden()
            datei.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }
}
