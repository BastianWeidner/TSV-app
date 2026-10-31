package de.dachapp.module

import android.content.Context
import java.io.File

/**
 * Gemeinsames Holzbuch zwischen Unter-Apps, ohne dass sie sich kennen: Nachlegen schreibt jedes Nachlegen mit der
 * Zahl der Scheite hinein, Brennholz liest es und bucht das Holz vom Vorrat ab. Eine Textdatei mit einer Zeile je
 * Eintrag ("Zeitpunkt in ms;Scheite"); nur angehängt, nie umgeschrieben, damit nichts verloren geht.
 */
object Holzbuch {
    data class Eintrag(val zeit: Long, val scheite: Int)

    private const val DATEI = "holzbuch.txt"
    private val sperre = Any()

    private fun datei(context: Context) = File(context.applicationContext.filesDir, DATEI)

    fun eintragen(context: Context, zeit: Long, scheite: Int) {
        if (scheite <= 0) return
        synchronized(sperre) { datei(context).appendText("$zeit;$scheite\n") }
    }

    /** Alle Einträge nach [nach] (ms), älteste zuerst. */
    fun seit(context: Context, nach: Long): List<Eintrag> = synchronized(sperre) {
        val f = datei(context)
        if (!f.exists()) return emptyList()
        lesen(f.readText()).filter { it.zeit > nach }
    }

    fun lesen(text: String): List<Eintrag> = text.lineSequence().mapNotNull { z ->
        val teile = z.trim().split(';')
        val zeit = teile.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val anzahl = teile.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
        Eintrag(zeit, anzahl)
    }.sortedBy { it.zeit }.toList()
}
