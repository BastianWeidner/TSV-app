package de.dachapp.schwimmleitung.ui

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.util.AtomicFile
import de.dachapp.schwimmleitung.logic.LeitungCodec
import de.dachapp.schwimmleitung.logic.LokalerStand
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.Executors

/** Speichert den Stand dieses Geräts als eine JSON-Datei im App-Speicher. Schreiben läuft im Hintergrund, in Reihenfolge. */
class LeitungStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "schwimmleitung.json"))
    private val writer = Executors.newSingleThreadExecutor()

    /** Startbestand beim ersten Öffnen; null, wenn die Datei da, aber unlesbar ist. */
    fun load(): LokalerStand? = runCatching {
        if (!file.baseFile.exists()) LokalerStand() else LeitungCodec.decodeLokal(file.readFully().decodeToString())
    }.getOrNull()

    fun save(stand: LokalerStand) {
        val text = LeitungCodec.encodeLokal(stand)
        writer.execute {
            val out = file.startWrite()
            try {
                out.write(text.encodeToByteArray())
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
            }
        }
    }

    /** Hebt eine unlesbare Datei auf, statt sie beim nächsten Speichern zu überschreiben. */
    fun beiseitelegen() {
        val f = file.baseFile
        if (f.exists()) f.renameTo(File(f.parentFile, "schwimmleitung-defekt-${System.currentTimeMillis()}.json"))
    }

    fun close() = writer.shutdown()
}

/**
 * Lesen und Schreiben der geteilten Datei über den Dateidialog (z. B. in Google Drive). Kein Internet nötig:
 * Die Drive-App auf dem Gerät lädt die Datei hoch und holt Änderungen der anderen Geräte.
 */
class GeteilteDatei(private val resolver: ContentResolver) {

    /** Inhalt und Länge in Bytes (für [schreiben]). */
    fun lesen(uri: Uri): Pair<String, Int> {
        val bytes = (resolver.openInputStream(uri) ?: throw FileNotFoundException()).use { it.readBytes() }
        return bytes.decodeToString() to bytes.size
    }

    /**
     * Schreibt [text] und füllt mit Leerzeichen bis zur bisherigen Länge auf. Manche Anbieter kürzen beim Schreiben
     * nicht; ohne Auffüllen blieben dann Reste des alten Inhalts stehen. Leerzeichen am Ende stören beim Lesen nicht.
     */
    fun schreiben(uri: Uri, text: String, bisherigeLaenge: Int) {
        val bytes = text.encodeToByteArray()
        val voll = if (bytes.size >= bisherigeLaenge) bytes else bytes + ByteArray(bisherigeLaenge - bytes.size) { ' '.code.toByte() }
        val out = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri, "w")
            ?: throw FileNotFoundException()
        out.use { it.write(voll) }
    }
}
