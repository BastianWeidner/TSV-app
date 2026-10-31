package de.dachapp.module

import android.content.ContentResolver
import android.net.Uri
import java.io.FileNotFoundException

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
