package de.dachapp.vereinsmeisterschaft.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

/** Dateien erzeugen, teilen und Fotos annehmen. Kein Netzzugriff. */
object Dateien {

    fun ordner(context: Context) = File(context.cacheDir, "vereinsmeisterschaft").apply { mkdirs() }

    private fun uri(context: Context, datei: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".vereinsmeisterschaft.dateien", datei)

    fun dateiname(text: String) = text.replace(Regex("[^A-Za-z0-9ÄÖÜäöüß_-]+"), "_").trim('_').take(60).ifEmpty { "datei" }

    fun neueDatei(context: Context, name: String, endung: String) = File(ordner(context), dateiname(name) + "." + endung)

    /** Leere Datei für ein Kamerafoto; die Kamera-App schreibt über die Adresse hinein (ohne Kamera-Berechtigung). */
    fun fotoZiel(context: Context): Uri {
        val f = File(ordner(context), "zettel-${System.currentTimeMillis()}.jpg")
        ordner(context).listFiles { d -> d.name.startsWith("zettel-") }?.forEach { it.delete() }
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
        try {
            context.startActivity(Intent.createChooser(senden, "Teilen oder drucken"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Keine passende App gefunden.", Toast.LENGTH_LONG).show()
        }
    }

    /** Text in eine vom Nutzer gewählte Datei schreiben (Speichern-Dialog). */
    fun schreiben(context: Context, ziel: Uri, text: String, zeichensatz: java.nio.charset.Charset = Charsets.UTF_8) {
        context.contentResolver.openOutputStream(ziel, "wt")?.use { it.write(text.toByteArray(zeichensatz)) }
    }

    fun lesen(context: Context, quelle: Uri): ByteArray =
        context.contentResolver.openInputStream(quelle)?.use { it.readBytes() } ?: ByteArray(0)

    /** Logo verkleinern (längste Kante 600 px) und als PNG in Base64; null wenn nicht lesbar. */
    fun logoLesen(context: Context, quelle: Uri): String? = runCatching {
        val bild = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, quelle)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val k = maxOf(info.size.width, info.size.height)
            if (k > 600) d.setTargetSize(info.size.width * 600 / k, info.size.height * 600 / k)
        }
        val out = ByteArrayOutputStream()
        bild.compress(Bitmap.CompressFormat.PNG, 100, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    fun logo(base64: String?): Bitmap? = base64?.let {
        runCatching { Base64.decode(it, Base64.DEFAULT).let { b -> BitmapFactory.decodeByteArray(b, 0, b.size) } }.getOrNull()
    }

    /** Speicherdatei der Schwimmgruppen in derselben App; null wenn es sie (in dieser Variante) nicht gibt. */
    fun schwimmgruppen(context: Context): String? =
        File(context.filesDir, "schwimmgruppen.json").takeIf { it.exists() }?.readText()
}
