package de.dachapp.module

import android.content.Context
import java.io.File

/**
 * Brücke zur Einkaufsliste, ohne dass die Unter-Apps sich kennen. Andere Unter-Apps (z. B. Rezepte) legen Zeilen wie
 * "500 g Hackfleisch" in einen Eingang; die Einkaufsliste holt sie beim nächsten Öffnen ab und trägt sie ein.
 * Umgekehrt schreibt die Einkaufsliste die Namen im Vorrat und auf der offenen Liste in einen Bestand, damit andere
 * sehen, was schon da oder schon vorgemerkt ist.
 */
object Einkaufszettel {
    private const val EINGANG = "einkaufszettel-eingang.txt"
    private const val BESTAND = "einkaufszettel-bestand.txt"
    private val sperre = Any()

    data class Bestand(val vorrat: Set<String>, val aufListe: Set<String>)

    private fun datei(context: Context, name: String) = File(context.applicationContext.filesDir, name)

    /** Zeilen für die Einkaufsliste vormerken (eine Zeile je Posten, wie in der Schnelleingabe). */
    fun vormerken(context: Context, zeilen: List<String>) {
        val sauber = zeilen.map { it.replace('\n', ' ').trim() }.filter { it.isNotEmpty() }
        if (sauber.isEmpty()) return
        synchronized(sperre) { datei(context, EINGANG).appendText(sauber.joinToString("\n", postfix = "\n")) }
    }

    /** Wartende Zeilen holen und den Eingang leeren. */
    fun abholen(context: Context): List<String> = synchronized(sperre) {
        val f = datei(context, EINGANG)
        if (!f.exists()) return emptyList()
        val zeilen = f.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        f.delete()
        zeilen
    }

    /** Wie viele Zeilen noch auf die Einkaufsliste warten. */
    fun wartend(context: Context): Int = synchronized(sperre) {
        val f = datei(context, EINGANG)
        if (f.exists()) f.readLines().count { it.isNotBlank() } else 0
    }

    fun bestandSchreiben(context: Context, b: Bestand) {
        val text = b.vorrat.joinToString("") { "V;$it\n" } + b.aufListe.joinToString("") { "L;$it\n" }
        synchronized(sperre) {
            val tmp = datei(context, "$BESTAND.tmp")
            tmp.writeText(text)
            tmp.renameTo(datei(context, BESTAND))
        }
    }

    fun bestand(context: Context): Bestand = synchronized(sperre) {
        val f = datei(context, BESTAND)
        if (!f.exists()) return Bestand(emptySet(), emptySet())
        val z = f.readLines()
        Bestand(
            z.filter { it.startsWith("V;") }.map { it.drop(2) }.toSet(),
            z.filter { it.startsWith("L;") }.map { it.drop(2) }.toSet(),
        )
    }
}
