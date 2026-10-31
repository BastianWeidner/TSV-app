package de.dachapp.module

import android.content.Context
import java.io.File

/**
 * Gemeinsames Trainingsbuch zwischen Unter-Apps, ohne dass sie sich kennen: Krafttraining schreibt je Training den
 * geschätzten Zusatzverbrauch hinein, der Tracker rechnet ihn ins Kalorienziel des Tages ein. Eine Textdatei mit einer
 * Zeile je Training ("id;Start in ms;kcal;Name"); Krafttraining schreibt die Datei bei jeder Änderung neu (die letzten
 * 60 Tage), so stimmen auch gelöschte oder bearbeitete Trainings.
 */
object Trainingsbuch {
    data class Eintrag(val id: String, val start: Long, val kcal: Int, val name: String)

    private const val DATEI = "trainingsbuch.txt"
    private val sperre = Any()

    private fun datei(context: Context) = File(context.applicationContext.filesDir, DATEI)

    fun schreiben(context: Context, eintraege: List<Eintrag>) = synchronized(sperre) {
        datei(context).writeText(text(eintraege))
    }

    /** Alle Einträge, älteste zuerst. */
    fun lesen(context: Context): List<Eintrag> = synchronized(sperre) {
        val f = datei(context)
        if (!f.exists()) return emptyList()
        lesen(f.readText())
    }

    fun text(eintraege: List<Eintrag>): String =
        eintraege.joinToString("") { "${it.id.replace(";", ",")};${it.start};${it.kcal};${it.name.replace(";", ",").replace("\n", " ")}\n" }

    fun lesen(text: String): List<Eintrag> = text.lineSequence().mapNotNull { z ->
        val t = z.trim().split(';', limit = 4)
        if (t.size < 3) return@mapNotNull null
        Eintrag(t[0], t[1].toLongOrNull() ?: return@mapNotNull null, t[2].toIntOrNull() ?: return@mapNotNull null, t.getOrElse(3) { "" })
    }.sortedBy { it.start }.toList()
}
