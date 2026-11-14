package de.dachapp.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Sucht im Kamerabild nach Barcodes und meldet jeden Code einmal (derselbe erst nach [sperreMs] wieder).
 * Die Prüfziffer prüft ML Kit selbst.
 *
 * [nurHandel]: nur EAN/UPC (Lebensmittel); sonst auch Code 128/39, ITF, QR und Data Matrix.
 * [textBei]: gibt für einen Code true zurück, wenn im selben Bild noch Text gelesen werden soll (etwa der Produktname
 * eines unbekannten Artikels). Dann bleibt die Analyse bis [weiter] angehalten.
 */
class BarcodeAnalyse(
    private val nurHandel: Boolean = false,
    private val sperreMs: Long = 2500L,
    private val textBei: (String) -> Boolean = { false },
    private val treffer: (code: String, text: List<Zeile>) -> Unit,
) : ImageAnalysis.Analyzer {
    // Erst beim ersten Bild anlegen: ML Kit braucht dafür die laufende Kamera-Umgebung.
    private val scannerLazy = lazy {
        val formate = if (nurHandel) {
            intArrayOf(Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
        } else {
            intArrayOf(
                Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39, Barcode.FORMAT_ITF, Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX,
            )
        }
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13, *formate).build())
    }
    private val scanner by scannerLazy

    /** Solange gesetzt (z. B. Namensfeld offen), werden Bilder verworfen. */
    @Volatile var pause = false
    private var letzter: String? = null
    private var letzterZeit = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (pause || media == null) {
            proxy.close(); return
        }
        val bild = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        scanner.process(bild)
            .addOnSuccessListener { codes ->
                val code = codes.mapNotNull { it.rawValue?.trim() }.firstOrNull { it.isNotEmpty() }
                val jetzt = System.currentTimeMillis()
                if (code == null || pause || (code == letzter && jetzt - letzterZeit < sperreMs)) {
                    proxy.close(); return@addOnSuccessListener
                }
                letzter = code
                letzterZeit = jetzt
                if (!textBei(code)) {
                    proxy.close()
                    treffer(code, emptyList())
                } else {
                    pause = true
                    Texterkennung.prozess(bild)
                        .addOnSuccessListener { t -> treffer(code, Texterkennung.zeilen(t)) }
                        .addOnFailureListener { treffer(code, emptyList()) }
                        .addOnCompleteListener { proxy.close() }
                }
            }
            .addOnFailureListener { proxy.close() }
    }

    /** Weiter scannen, denselben Code aber nicht sofort wieder melden. */
    fun weiter() {
        letzterZeit = System.currentTimeMillis()
        pause = false
    }

    fun close() {
        if (scannerLazy.isInitialized()) scanner.close()
    }
}
