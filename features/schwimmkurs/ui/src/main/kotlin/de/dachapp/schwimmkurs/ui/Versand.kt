package de.dachapp.schwimmkurs.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StyleSpan
import android.widget.Toast
import androidx.core.content.FileProvider
import de.dachapp.schwimmkurs.logic.Bestand
import de.dachapp.schwimmkurs.logic.Einladung
import de.dachapp.schwimmkurs.logic.Einladungen
import de.dachapp.schwimmkurs.logic.Einstellungen
import de.dachapp.schwimmkurs.logic.Kurs
import de.dachapp.schwimmkurs.logic.Kursliste
import java.io.File

/** Erzeugt Anhänge und übergibt Mails an Gmail, Outlook oder eine andere Mail-App. Kein eigener Netzzugriff. */
object Versand {

    private fun ordner(context: Context) = File(context.cacheDir, "schwimmkurs").apply { mkdirs() }

    private fun uri(context: Context, datei: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".schwimmkurs.dateien", datei)

    private fun dateiname(text: String) = text.replace(Regex("[^A-Za-z0-9ÄÖÜäöüß_-]+"), "_").trim('_').take(60).ifEmpty { "datei" }

    /**
     * Öffnet die fertige Mail mit Kursübersicht und Haftungserklärung als PDF. Die Auswahl zeigt nur Mail-Apps;
     * gesendet wird erst, wenn in der Mail-App auf Senden getippt wird.
     */
    fun einladungOeffnen(context: Context, e: Einladung, einstellungen: Einstellungen): Boolean {
        val pdf = File(ordner(context), dateiname("Schwimmkurs_" + e.kinderNamen + "_" + e.nachname) + ".pdf")
        einladungPdf(pdf, e, einstellungen)
        val anhang = uri(context, pdf)
        val mail = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, e.empfaenger.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, e.betreff)
            putExtra(Intent.EXTRA_TEXT, mailText(e.text))
            putExtra(Intent.EXTRA_STREAM, anhang)
            clipData = ClipData.newRawUri(pdf.name, anhang)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return starten(context, Intent.createChooser(mail, "Mail öffnen mit"))
    }

    /** Zeilen mit "## " werden fett (Gmail und Outlook übernehmen das); andere Apps bekommen schlichten Text. */
    fun mailText(text: String): Spanned {
        val sb = SpannableStringBuilder()
        text.lines().forEachIndexed { i, zeile ->
            if (i > 0) sb.append("\n")
            if (zeile.startsWith("## ")) {
                val start = sb.length
                sb.append(zeile.removePrefix("## "))
                sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                sb.append(zeile)
            }
        }
        return sb
    }

    /** Teilnehmerliste als CSV, z. B. für die Kursleitung oder zum Öffnen in Excel. */
    fun kurslisteTeilen(context: Context, kurs: Kurs, bestand: Bestand) {
        val datei = File(ordner(context), dateiname("Teilnehmer_" + kurs.name) + ".csv")
        datei.writeText(Kursliste.csv(kurs, bestand))
        val u = uri(context, datei)
        val senden = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Teilnehmerliste ${kurs.name}")
            putExtra(Intent.EXTRA_STREAM, u)
            clipData = ClipData.newRawUri(datei.name, u)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        starten(context, Intent.createChooser(senden, "Liste teilen"))
    }

    private fun starten(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Keine passende App gefunden. Bitte Gmail oder Outlook installieren.", Toast.LENGTH_LONG).show()
        false
    }

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

        /** Umbrochener Text; lange Texte laufen über mehrere Seiten. */
        fun text(t: String, paint: TextPaint, abstandDanach: Float = 6f) {
            if (t.isBlank()) return
            val layout = StaticLayout.Builder.obtain(t, 0, t.length, paint, TEXT_BREITE.toInt())
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
                c.translate(RAND, y - oben)
                c.clipRect(0f, oben, TEXT_BREITE, unten)
                layout.draw(c)
                c.restore()
                y += unten - oben
                zeile = ende
            }
            y += abstandDanach
        }
    }

    /** Seite 1: Kursübersicht mit Termintabelle und Überweisungsdaten. Danach die Haftungserklärung. */
    fun einladungPdf(datei: File, e: Einladung, st: Einstellungen) {
        val doc = PdfDocument()
        try {
            val s = Seiten(doc, st.verein.ifBlank { "Schwimmkurs" })
            s.neu()
            kopf(s, st.verein, "Kursübersicht", "für ${e.kinderNamen} ${e.nachname}".trim())

            for (b in e.bloecke) {
                s.platz(80f)
                s.text(b.kurs.name + if (e.plaetze.size > 1) " – für " + Einladungen.aufzaehlen(b.kinder.map { it.vorname }) else "", stift(13f, fett = true, farbe = AKZENT), 2f)
                val t = b.kurs.sortierteTermine
                if (t.isEmpty()) {
                    s.text("Die Termine geben wir noch bekannt.", stift(10f))
                } else {
                    s.text("${t.size} Termine" + (b.kurs.kursleitung.ifBlank { null }?.let { " · Kursleitung: $it" } ?: ""), stift(9.5f, farbe = GRAU), 4f)
                    tabelle(s, listOf("Datum", "Tag", "Uhrzeit", "Ort"), listOf(0.17f, 0.2f, 0.28f, 0.35f), t.map {
                        listOf(it.datum.format(DATUM), Einladungen.wochentag(it.datum), Einladungen.zeit(it.beginn, it.ende), b.kurs.ort)
                    })
                }
                b.pausen?.let { s.text(it, stift(10f, fett = true), 2f) }
                if (b.kurs.hinweis.isNotBlank()) s.text(b.kurs.hinweis, stift(10f))
                s.y += 8f
            }

            // Überweisung als hinterlegter Kasten.
            val zeilen = listOfNotNull(
                "Empfänger" to st.kontoinhaber.ifBlank { st.verein },
                "IBAN" to st.iban,
                st.bic.ifBlank { null }?.let { "BIC" to it },
                "Betrag" to Einladungen.euro(e.betragCent),
                "Verwendungszweck" to e.verwendungszweck,
                "Zahlungseingang" to e.zahlungsfrist,
            )
            val hoehe = 30f + zeilen.size * 18f
            s.platz(hoehe + 10f)
            s.c.drawRoundRect(RectF(RAND, s.y, BREITE - RAND, s.y + hoehe), 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HELL })
            s.c.drawText("Überweisung", RAND + 12f, s.y + 20f, stift(12f, fett = true, farbe = AKZENT))
            var zy = s.y + 40f
            for ((k, v) in zeilen) {
                s.c.drawText(k, RAND + 12f, zy, stift(10f, farbe = GRAU))
                s.c.drawText(v, RAND + 130f, zy, stift(10f, fett = true))
                zy += 18f
            }
            s.y += hoehe + 10f
            s.text("Der Kursplatz gilt erst nach fristgerechtem Zahlungseingang als fest gebucht.", stift(9.5f, farbe = GRAU))

            // Haftungserklärung auf eigener Seite.
            s.neu()
            kopf(s, st.verein, e.haftungTitel, null)
            s.text(e.haftungText, stift(11f))
            s.beenden()
            datei.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    private val DATUM = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")

    private fun kopf(s: Seiten, verein: String, titel: String, untertitel: String?) {
        if (verein.isNotBlank()) {
            s.c.drawText(verein, RAND, s.y + 9f, stift(9f, farbe = GRAU))
            s.y += 16f
        }
        s.c.drawRect(RAND, s.y, RAND + 40f, s.y + 3f, Paint().apply { color = AKZENT })
        s.y += 26f
        s.c.drawText(titel, RAND, s.y, stift(20f, fett = true))
        s.y += 8f
        if (untertitel != null) s.text(untertitel, stift(12f, farbe = GRAU), 0f)
        s.y += 14f
    }

    private fun tabelle(s: Seiten, kopf: List<String>, anteile: List<Float>, zeilen: List<List<String>>) {
        val h = 18f
        val xs = anteile.runningFold(RAND) { x, a -> x + a * TEXT_BREITE }
        val linie = Paint().apply { color = 0xFFDADCE0.toInt(); strokeWidth = 0.7f }
        fun zeile(werte: List<String>, paint: TextPaint, hinterlegt: Boolean) {
            s.platz(h)
            if (hinterlegt) s.c.drawRect(RAND, s.y, BREITE - RAND, s.y + h, Paint().apply { color = HELL })
            werte.forEachIndexed { i, w -> s.c.drawText(w, xs[i] + 6f, s.y + 12.5f, paint) }
            s.y += h
            s.c.drawLine(RAND, s.y, BREITE - RAND, s.y, linie)
        }
        zeile(kopf, stift(9.5f, fett = true, farbe = GRAU), false)
        zeilen.forEachIndexed { i, z -> zeile(z, stift(10f), i % 2 == 1) }
        s.y += 6f
    }

}
