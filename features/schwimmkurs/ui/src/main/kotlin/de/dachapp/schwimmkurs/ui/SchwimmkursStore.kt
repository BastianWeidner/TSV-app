package de.dachapp.schwimmkurs.ui

import android.content.Context
import androidx.core.util.AtomicFile
import de.dachapp.schwimmkurs.logic.Bestand
import de.dachapp.schwimmkurs.logic.BestandCodec
import java.io.File
import java.util.concurrent.Executors

/** Speichert den gesamten Bestand als eine JSON-Datei im App-Speicher. Schreiben läuft im Hintergrund, in Reihenfolge. */
class SchwimmkursStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "schwimmkurs.json"))
    private val writer = Executors.newSingleThreadExecutor()

    /** Leerer Bestand beim ersten Start; null, wenn die Datei da, aber unlesbar ist. */
    fun load(): Bestand? = runCatching {
        if (!file.baseFile.exists()) Bestand() else BestandCodec.decode(file.readFully().decodeToString())
    }.getOrNull()

    fun save(bestand: Bestand) {
        val text = BestandCodec.encode(bestand)
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
        if (f.exists()) f.renameTo(File(f.parentFile, "schwimmkurs-defekt-${System.currentTimeMillis()}.json"))
    }

    fun close() = writer.shutdown()
}
