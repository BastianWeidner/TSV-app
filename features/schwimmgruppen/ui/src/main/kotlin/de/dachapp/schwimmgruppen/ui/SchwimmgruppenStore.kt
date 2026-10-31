package de.dachapp.schwimmgruppen.ui

import android.content.Context
import androidx.core.util.AtomicFile
import de.dachapp.schwimmgruppen.logic.Bestand
import de.dachapp.schwimmgruppen.logic.BestandCodec
import de.dachapp.schwimmgruppen.logic.Teilen
import java.io.File
import java.util.concurrent.Executors

/** Speichert den gesamten Bestand als eine JSON-Datei im App-Speicher. Schreiben läuft im Hintergrund, in Reihenfolge. */
class SchwimmgruppenStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "schwimmgruppen.json"))
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
        if (f.exists()) f.renameTo(File(f.parentFile, "schwimmgruppen-defekt-${System.currentTimeMillis()}.json"))
    }

    fun close() = writer.shutdown()
}

/** Verbindung zur geteilten Datei (nur dieses Gerät). Klein, wird direkt geschrieben. */
class VerbindungStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "schwimmgruppen-abgleich.json"))

    fun load(): Teilen.Verbindung =
        runCatching { if (!file.baseFile.exists()) null else Teilen.decodeVerbindung(file.readFully().decodeToString()) }.getOrNull()
            ?: Teilen.Verbindung()

    fun save(v: Teilen.Verbindung) {
        val out = file.startWrite()
        try {
            out.write(Teilen.encodeVerbindung(v).encodeToByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }
}
