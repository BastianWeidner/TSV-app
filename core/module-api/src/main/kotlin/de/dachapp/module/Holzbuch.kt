package de.dachapp.module

import android.content.Context
import java.io.File

/**
 * Gemeinsames Holzbuch: Der Feuer-Timer schreibt jedes Nachlegen hinein, der Brennholz-Vorrat liest es und bucht das
 * Holz ab. Eine Textdatei mit einer Zeile je Eintrag, nur angehängt, nie umgeschrieben, damit nichts verloren geht:
 * "Zeitpunkt in ms;Scheite" (bis 1.23) bzw. "Zeitpunkt;Scheite;Liter;Fremd" (ab 1.24, Liter = gefüllter Teil des
 * Füllraums, Fremd 1 = Paletten, Reste o. Ä., nicht vom Vorrat).
 */
object Holzbuch {
    /** [liter] > 0: Menge als gefülltes Volumen, dann zählt [scheite] nicht. [fremd]: Holz, das nicht aus dem Vorrat stammt. */
    data class Eintrag(val zeit: Long, val scheite: Int, val liter: Double = 0.0, val fremd: Boolean = false)

    private const val DATEI = "holzbuch.txt"
    private val sperre = Any()

    private fun datei(context: Context) = File(context.applicationContext.filesDir, DATEI)

    fun eintragen(context: Context, zeit: Long, scheite: Int, liter: Double = 0.0, fremd: Boolean = false) {
        val e = Eintrag(zeit, scheite, liter, fremd)
        if (scheite <= 0 && liter <= 0.0) return
        synchronized(sperre) { datei(context).appendText(zeile(e)) }
    }

    /** Alle Einträge nach [nach] (ms), älteste zuerst. */
    fun seit(context: Context, nach: Long): List<Eintrag> = synchronized(sperre) {
        val f = datei(context)
        if (!f.exists()) return emptyList()
        lesen(f.readText()).filter { it.zeit > nach }
    }

    fun zeile(e: Eintrag): String =
        if (e.liter <= 0.0 && !e.fremd) "${e.zeit};${e.scheite}\n"
        else "${e.zeit};${e.scheite};${e.liter};${if (e.fremd) 1 else 0}\n"

    fun lesen(text: String): List<Eintrag> = text.lineSequence().mapNotNull { z ->
        val teile = z.trim().split(';')
        val zeit = teile.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val anzahl = teile.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
        val liter = teile.getOrNull(2)?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        Eintrag(zeit, anzahl, liter, teile.getOrNull(3) == "1")
    }.sortedBy { it.zeit }.toList()
}
