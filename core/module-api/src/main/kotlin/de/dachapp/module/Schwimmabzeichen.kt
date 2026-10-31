package de.dachapp.module

import java.time.LocalDate

/**
 * Teilleistungen der Deutschen Schwimmabzeichen (Prüfungsordnung ab 2020), für Schwimmkurs und Schwimmgruppen.
 *
 * Der Prüfstand eines Kindes ist in den Unter-Apps einfach gespeichert: Abzeichen → Teil-ID → Datum (ISO-Text).
 * So braucht keine Unter-App eine Klasse von hier in ihren Daten.
 */
object Schwimmabzeichen {

    data class Teil(val id: String, val text: String)
    data class Abzeichen(val name: String, val teile: List<Teil>)

    private const val BADEREGELN = "Kenntnis der Baderegeln"
    private const val SELBSTRETTUNG = "Baderegeln und Selbstrettung (Verhalten bei Erschöpfung, Lösen von Krämpfen)"
    private const val SPRUENGE = "Sprung aus 3 m Höhe oder zwei verschiedene Sprünge aus 1 m Höhe"

    val ALLE: List<Abzeichen> = listOf(
        Abzeichen(
            "Seepferdchen",
            listOf(
                Teil("sprung25", "Sprung vom Beckenrand, danach 25 m Schwimmen in Bauch- oder Rückenlage (in Bauchlage erkennbar ins Wasser ausatmen)"),
                Teil("heraufholen", "Gegenstand mit den Händen aus schultertiefem Wasser heraufholen"),
                Teil("regeln", BADEREGELN),
            ),
        ),
        Abzeichen(
            "Bronze",
            listOf(
                Teil("15min", "Sprung kopfwärts vom Beckenrand und 15 Minuten Schwimmen, mindestens 200 m (150 m in Bauch- oder Rückenlage, 50 m in der anderen Lage)"),
                Teil("tief1", "Einmal ca. 2 m Tieftauchen mit Heraufholen eines Gegenstands"),
                Teil("paket", "Paketsprung vom Startblock oder 1-m-Brett"),
                Teil("regeln", BADEREGELN),
            ),
        ),
        Abzeichen(
            "Silber",
            listOf(
                Teil("20min", "Sprung kopfwärts vom Beckenrand und 20 Minuten Schwimmen, mindestens 400 m (300 m in Bauch- oder Rückenlage, 100 m in der anderen Lage)"),
                Teil("tief2", "Zweimal ca. 2 m Tieftauchen mit Heraufholen je eines Gegenstands"),
                Teil("strecke10", "10 m Streckentauchen mit Abstoßen vom Beckenrand im Wasser"),
                Teil("spruenge", SPRUENGE),
                Teil("regeln", SELBSTRETTUNG),
            ),
        ),
        Abzeichen(
            "Gold",
            listOf(
                Teil("30min", "Sprung kopfwärts vom Beckenrand und 30 Minuten Schwimmen, mindestens 800 m (650 m in Bauch- oder Rückenlage, 150 m in der anderen Lage)"),
                Teil("kraul25", "Startsprung und 25 m Kraulschwimmen"),
                Teil("brust50", "Startsprung und 50 m Brustschwimmen in höchstens 1:15 Minuten"),
                Teil("ruecken50", "50 m Rückenschwimmen mit Grätschschwung ohne Armtätigkeit oder Rückenkraulschwimmen"),
                Teil("strecke10", "10 m Streckentauchen aus der Schwimmlage (ohne Abstoßen)"),
                Teil("tief3", "Dreimal ca. 2 m Tieftauchen mit Heraufholen je eines Gegenstands, innerhalb von 3 Minuten"),
                Teil("spruenge", SPRUENGE),
                Teil("transport", "50 m Transportschwimmen: Schieben oder Ziehen"),
                Teil("regeln", "$SELBSTRETTUNG und einfache Fremdrettung (Hilfsmittel einsetzen)"),
            ),
        ),
    )

    /** Abzeichen zu einem Namen aus Einstellungen oder Importen, auch alte Namen („Freischwimmer“). */
    fun zu(name: String): Abzeichen? {
        val n = name.trim().lowercase()
        val ziel = when {
            "seepferd" in n || "frühschwimmer" in n || "fruehschwimmer" in n -> "Seepferdchen"
            "bronze" in n || n == "freischwimmer" -> "Bronze"
            "silber" in n || "fahrtenschwimmer" in n -> "Silber"
            "gold" in n || n == "jugendschwimmer" -> "Gold"
            else -> return null
        }
        return ALLE.first { it.name == ziel }
    }

    /** Erledigte Teile eines Abzeichens mit Datum; unlesbare Daten zählen als erledigt ohne Datum. */
    fun erledigt(stand: Map<String, Map<String, String>>, a: Abzeichen): Map<String, LocalDate?> =
        stand[a.name].orEmpty().filterKeys { id -> a.teile.any { it.id == id } }
            .mapValues { runCatching { LocalDate.parse(it.value) }.getOrNull() }

    fun fertig(stand: Map<String, Map<String, String>>, a: Abzeichen): Boolean = erledigt(stand, a).size == a.teile.size

    /** Tag der letzten Teilleistung, also der Tag, an dem das Abzeichen bestanden ist; null solange etwas fehlt. */
    fun bestandenAm(stand: Map<String, Map<String, String>>, a: Abzeichen): LocalDate? =
        if (fertig(stand, a)) erledigt(stand, a).values.filterNotNull().maxOrNull() ?: LocalDate.now() else null

    /** Setzt (Datum) oder löscht (null) eine Teilleistung. */
    fun setzen(stand: Map<String, Map<String, String>>, abzeichen: String, teil: String, datum: LocalDate?): Map<String, Map<String, String>> {
        val alt = stand[abzeichen].orEmpty()
        val neu = if (datum == null) alt - teil else alt + (teil to datum.toString())
        return if (neu.isEmpty()) stand - abzeichen else stand + (abzeichen to neu)
    }
}
