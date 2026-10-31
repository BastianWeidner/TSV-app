package de.dachapp.schwimmgruppen.logic

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Trainingszeiten für die Vereinswebsite (Repo TSV-relaunch, variante-2/src/data/zeiten-app.json).
 * Nur Gruppe, Wochentag und Uhrzeit, keine Namen. Die Website ersetzt damit die Termine der genannten Gruppen
 * und behält Namen, Seiten und Zusätze („mit Anmeldung“) aus ihrer eigenen zeiten.json.
 */
object Website {
    const val DATEINAME = "zeiten-app.json"

    private val TAGE = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
    private val UHR = DateTimeFormatter.ofPattern("HH:mm")
    private val ROEMISCH = mapOf("I" to 1, "II" to 2, "III" to 3, "IV" to 4, "V" to 5, "VI" to 6)

    /**
     * Kennung der Gruppe auf der Website. Nach Trainingstag geteilte Gruppen („gruppe-1-6“ = Gruppe I am Samstag)
     * gehören zur selben Website-Gruppe, „Gruppe IV …“ wird „gruppe-4“, sonst der Name in Kleinbuchstaben.
     */
    fun webId(g: Gruppe): String {
        Regex("""^(gruppe-\d+)(-.+)?$""").find(g.id)?.let { return it.groupValues[1] }
        if (g.id == "erwachsene") return "erwachsenenschwimmen"
        Regex("""^Gruppe\s+([IVX]+)\b""").find(g.name.trim())?.let { m -> ROEMISCH[m.groupValues[1]]?.let { return "gruppe-$it" } }
        if (Regex("""^[a-z0-9-]+$""").matches(g.id) && !Regex("""[0-9a-f]{8}-""").containsMatchIn(g.id)) return g.id
        return g.name.lowercase()
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            .replace(Regex("[^a-z0-9]+"), "-").trim('-')
    }

    fun zeitenJson(b: Bestand, heute: LocalDate): String {
        val termine = b.gruppen
            .flatMap { g -> g.zeiten.map { webId(g) to it } }
            .sortedWith(compareBy({ it.second.wochentag }, { it.second.beginn }, { it.first }))
            .distinct()
            .map { (id, z) ->
                JsonObject(
                    mapOf(
                        "tag" to JsonPrimitive(TAGE[z.wochentag - 1]),
                        "gruppe" to JsonPrimitive(id),
                        "von" to JsonPrimitive(z.beginn.format(UHR)),
                        "bis" to JsonPrimitive(z.ende.format(UHR)),
                    ),
                )
            }
        val inhalt = JsonObject(
            mapOf(
                "_hinweis" to JsonPrimitive(
                    "Aus der Schwimmgruppen-App (Mehr › Für die Website). Ersetzt beim Bauen die Termine dieser Gruppen in zeiten.json.",
                ),
                "stand" to JsonPrimitive(heute.toString()),
                "termine" to JsonArray(termine),
            ),
        )
        return formatieren(inhalt)
    }

    /** Ein Termin je Zeile, gut lesbar im Git-Diff. */
    private fun formatieren(o: JsonObject): String {
        val sb = StringBuilder("{\n")
        sb.append("  \"_hinweis\": ").append(o["_hinweis"]).append(",\n")
        sb.append("  \"stand\": ").append(o["stand"]).append(",\n")
        sb.append("  \"termine\": [\n")
        val t = o["termine"] as JsonArray
        t.forEachIndexed { i, e -> sb.append("    ").append(e).append(if (i < t.size - 1) ",\n" else "\n") }
        sb.append("  ]\n}\n")
        return sb.toString()
    }
}
