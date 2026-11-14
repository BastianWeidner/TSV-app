package de.dachapp.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.abs
import kotlin.math.min

/** Eine erkannte Textzeile mit Lage im Bild. [block] zählt die Absätze, [sicherheit] ist 0 bis 1. */
data class Zeile(
    val text: String,
    val links: Int,
    val oben: Int,
    val rechts: Int,
    val unten: Int,
    val block: Int = 0,
    val sicherheit: Float = 1f,
) {
    val hoehe: Int get() = unten - oben
}

/**
 * Texterkennung mit ML Kit für alle Unter-Apps. Das Modell ist in der App enthalten und läuft ohne Internet.
 * Alle Aufrufe blockieren, also nicht im Hauptthread.
 */
object Texterkennung {
    private val texter by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    internal fun zeilen(t: Text, dy: Int = 0, blockStart: Int = 0): List<Zeile> =
        t.textBlocks.flatMapIndexed { bi, b ->
            b.lines.mapNotNull { l ->
                val r = l.boundingBox ?: return@mapNotNull null
                Zeile(l.text, r.left, r.top + dy, r.right, r.bottom + dy, blockStart + bi, l.confidence)
            }
        }

    internal fun lesen(bild: InputImage): List<Zeile> = zeilen(Tasks.await(texter.process(bild)))

    internal fun prozess(bild: InputImage) = texter.process(bild)

    /** Kamerafoto aus ImageCapture; das Bild wird danach in jedem Fall geschlossen. */
    @OptIn(ExperimentalGetImage::class)
    fun lesen(foto: ImageProxy): List<Zeile> = try {
        val media = foto.image ?: return emptyList()
        lesen(InputImage.fromMediaImage(media, foto.imageInfo.rotationDegrees))
    } finally {
        foto.close()
    }

    /**
     * Text eines Bildes. Ist es deutlich höher als [streifen] mal die Breite (lange Screenshots, Kassenzettel),
     * wird es in überlappenden Streifen gelesen, damit die Schrift groß genug bleibt. Doppelte Zeilen aus der
     * Überlappung kommen nur einmal; die Absatznummern zählen je Streifen um 1000 weiter.
     */
    fun lesen(bild: Bitmap, streifen: Double = 1.6): List<Zeile> {
        val hoehe = (bild.width * streifen).toInt()
        if (bild.height <= hoehe * 1.2) return lesen(InputImage.fromBitmap(bild, 0))
        val ergebnis = ArrayList<Zeile>()
        val ueberlapp = hoehe / 6
        var y = 0
        var block = 0
        while (y < bild.height) {
            val h = min(hoehe, bild.height - y)
            val teil = Bitmap.createBitmap(bild, 0, y, bild.width, h)
            val z = zeilen(Tasks.await(texter.process(InputImage.fromBitmap(teil, 0))), y, block)
            ergebnis += z.filter { neu -> ergebnis.none { alt -> alt.text == neu.text && abs(alt.oben - neu.oben) < neu.hoehe } }
            block += 1000
            if (y + h >= bild.height) break
            y += h - ueberlapp
        }
        return ergebnis
    }

    /** Bild aus der Galerie, auf höchstens [maxBreite] Pixel Breite verkleinert (mehr bringt der Erkennung nichts). */
    fun laden(context: Context, uri: Uri, maxBreite: Int = 2000): Bitmap {
        val quelle = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(quelle) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            if (w > maxBreite) decoder.setTargetSize(maxBreite, (info.size.height.toLong() * maxBreite / w).toInt())
        }
    }

    /** Bild aus der Galerie laden und lesen. */
    fun lesen(context: Context, uri: Uri, streifen: Double = 1.6): List<Zeile> = lesen(laden(context, uri), streifen)
}
