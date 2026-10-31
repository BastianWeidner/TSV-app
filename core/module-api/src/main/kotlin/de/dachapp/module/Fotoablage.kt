package de.dachapp.module

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Fotos (Belege, Stellplätze, Kassenzettel) in einem eigenen Ordner der Unter-App ablegen, verkleinert auf
 * [maxPixel] an der längsten Seite. Gespeichert wird nur der Dateiname, das Bild bleibt auf dem Gerät.
 */
class Fotoablage(context: Context, ordnerName: String, private val maxPixel: Int = 1600) {
    private val resolver = context.applicationContext.contentResolver
    val ordner = File(context.applicationContext.filesDir, ordnerName)

    /** Bild aus [uri] übernehmen; Dateiname oder null, wenn es sich nicht lesen ließ. Nicht im Hauptthread aufrufen. */
    fun uebernehmen(uri: Uri): String? = runCatching {
        val bild = laden(uri)
        ordner.mkdirs()
        val n = "${UUID.randomUUID()}.jpg"
        File(ordner, n).outputStream().use { bild.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        n
    }.getOrNull()

    /** Verkleinertes Bild aus [uri], z. B. für die Texterkennung. */
    fun laden(uri: Uri): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { dec, info, _ ->
        val s = info.size
        val f = maxPixel.toDouble() / maxOf(s.width, s.height)
        if (f < 1) dec.setTargetSize((s.width * f).toInt(), (s.height * f).toInt())
        dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }

    fun datei(name: String) = File(ordner, name)

    /** Bild zum Anzeigen, null wenn leer oder nicht (mehr) vorhanden. */
    fun bild(name: String): Bitmap? = name.takeIf { it.isNotBlank() }?.let { BitmapFactory.decodeFile(datei(it).path) }

    fun loeschen(name: String) {
        if (name.isNotBlank()) datei(name).delete()
    }
}
