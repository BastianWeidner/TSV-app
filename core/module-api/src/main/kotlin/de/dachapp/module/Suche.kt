package de.dachapp.module

import java.text.Normalizer

/**
 * Einfache Suche für Listen in Unter-Apps (Wunsch Bastian 2026-11-20): jedes Wort der Anfrage muss in einem der Felder
 * vorkommen, Groß-/Kleinschreibung und Akzente egal, „ae“ und „a“ finden „ä“. Leere Anfrage passt immer.
 */
object Suche {
    /** Ab so vielen Einträgen zeigt eine Liste das Suchfeld. */
    const val AB = 6

    fun passt(anfrage: String, vararg felder: String?): Boolean {
        val woerter = norm(anfrage).split(' ').filter { it.isNotBlank() }
        if (woerter.isEmpty()) return true
        val text = felder.filterNotNull().joinToString(" ") { norm(it) }
        return woerter.all { it in text }
    }

    private fun norm(s: String): String {
        // Beide Seiten gleich behandeln: „ä“ und „ae“ werden zu „a“ usw., Akzente fallen weg.
        val ohneAkzent = Normalizer.normalize(s.lowercase().replace("ß", "ss"), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        return ohneAkzent.replace("ae", "a").replace("oe", "o").replace("ue", "u").replace(Regex("\\s+"), " ").trim()
    }
}
