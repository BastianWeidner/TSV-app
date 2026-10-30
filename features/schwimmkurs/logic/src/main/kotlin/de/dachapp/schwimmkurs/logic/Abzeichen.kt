package de.dachapp.schwimmkurs.logic

/** Rangfolge und Erkennung von Abzeichen anhand der Liste aus den Einstellungen (niedrig → hoch). */
class Abzeichen(private val stufen: List<String>) {

    private fun key(s: String) = s.trim().lowercase()

    /** Index des Abzeichens in der Stufenliste, -1 wenn unbekannt. */
    fun stufe(name: String): Int = stufen.indexOfFirst { key(it) == key(name) }

    /** Höchste bekannte Stufe des Kindes, -1 ohne Abzeichen. */
    fun hoechsteStufe(kind: Kind): Int = kind.abzeichen.maxOfOrNull { stufe(it) } ?: -1

    /** Hat das Kind [name] oder ein höheres? Freie (nicht gestufte) Leistungen zählen nur bei genauem Namen. */
    fun hat(kind: Kind, name: String): Boolean {
        val s = stufe(name)
        return if (s < 0) kind.abzeichen.any { key(it) == key(name) } else hoechsteStufe(kind) >= s
    }

    /**
     * Ordnet einen frei geschriebenen Eintrag einem bekannten Abzeichen zu: genauer Name, enthaltener Name
     * ("DLRG Bronze", "Seepferdchen (2023)") oder alte Bezeichnung (Freischwimmer = Bronze usw.).
     * Unbekannte Einträge bleiben so, wie sie geschrieben sind.
     */
    fun erkennen(roh: String): String? {
        val text = roh.trim().trim('-', '–', '.', ':').trim()
        if (text.isEmpty() || key(text) in KEINE) return null
        stufen.firstOrNull { key(it) == key(text) }?.let { return it }
        val k = key(text)
        stufen.sortedByDescending { it.length }.firstOrNull { k.contains(key(it)) }?.let { return it }
        for ((alt, neu) in ALTE_NAMEN) if (k.contains(alt)) stufen.firstOrNull { key(it) == neu }?.let { return it }
        return text
    }

    /** Zerlegt eine Zelle wie "Seepferdchen, Bronze" oder "Seepferdchen und Bronze". */
    fun ausZelle(zelle: String): Set<String> =
        zelle.split(Regex("[,;/+\\n]|\\s+und\\s+|\\s+&\\s+"))
            .mapNotNull { erkennen(it) }
            .toCollection(LinkedHashSet())

    companion object {
        private val KEINE = setOf("keine", "keins", "kein", "nein", "nichts", "-", "0", "x", "n/a", "na")

        /** Frühere Namen der Jugendschwimmabzeichen. */
        private val ALTE_NAMEN = listOf(
            "frühschwimmer" to "seepferdchen",
            "freischwimmer" to "bronze",
            "fahrtenschwimmer" to "silber",
            "jugendschwimmer" to "gold",
        )
    }
}
