package de.dachapp.module

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.concurrent.Executors

/**
 * Eine Datei im App-Speicher für den Stand einer Unter-App (meist JSON). Schreiben läuft im Hintergrund und atomar,
 * eine halb geschriebene Datei gibt es nicht. Für neue Unter-Apps, damit nicht jede ihren eigenen Store kopiert.
 */
class Speicher(context: Context, private val name: String) {
    private val datei = AtomicFile(File(context.applicationContext.filesDir, name))
    private val schreiber = Executors.newSingleThreadExecutor()

    /** Inhalt, null wenn es die Datei noch nicht gibt. */
    fun lesen(): String? = if (datei.baseFile.exists()) datei.readFully().decodeToString() else null

    fun schreiben(text: String) {
        val bytes = text.encodeToByteArray()
        schreiber.execute {
            val out = datei.startWrite()
            try {
                out.write(bytes)
                datei.finishWrite(out)
            } catch (e: Exception) {
                datei.failWrite(out)
            }
        }
    }

    /** Hebt eine unlesbare Datei auf, statt sie beim nächsten Speichern zu überschreiben. */
    fun beiseitelegen() {
        val f = datei.baseFile
        if (f.exists()) f.renameTo(File(f.parentFile, name.substringBeforeLast('.') + "-defekt-${System.currentTimeMillis()}." + name.substringAfterLast('.')))
    }

    fun schliessen() = schreiber.shutdown()
}
