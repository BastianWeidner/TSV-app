package de.dachapp.schwimmkurs.logic

import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Kursliste für die Vereinswebsite (Repo TSV-relaunch, variante-2/src/data/kurse-app.csv), in den sechs Spalten,
 * die die Website zulässt. Nur Kurs, Termin, Beginn, freie Plätze, Status und Hinweis, keine Kinder oder Eltern.
 */
object Website {
    const val DATEINAME = "kurse-app.csv"
    const val KOPF = "kurs,termin,beginn,plaetze_frei,status,hinweis"

    private val TAG = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    private val UHR = DateTimeFormatter.ofPattern("HH:mm")
    private val DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** „Seepferdchen 2“ → „Seepferdchenkurs“, „Aufbaukurs 1“ → „Aufbaukurs“: so heißen die Kurse auf der Website. */
    fun kursart(name: String): String {
        val ohneNummer = name.trim().replace(Regex("""\s+(\d+|[IVX]+)$"""), "").trim()
        return if (ohneNummer.contains("kurs", ignoreCase = true)) ohneNummer else ohneNummer + "kurs"
    }

    /** Kurse, die noch laufen oder kommen; abgeschlossene fallen weg. */
    fun kurseCsv(b: Bestand, heute: LocalDate): String {
        val zeilen = b.kurse
            .filter { k -> k.termine.isEmpty() || k.termine.any { !it.datum.isBefore(heute) } }
            .sortedWith(compareBy({ it.ersterTag ?: LocalDate.MAX }, { it.name }))
            .map { k -> zeile(k, heute) }
        return (listOf(KOPF) + zeilen).joinToString("\n", postfix = "\n")
    }

    private fun zeile(k: Kurs, heute: LocalDate): String {
        val t = k.sortierteTermine
        // Häufigster Wochentag mit Uhrzeit, z. B. „Sa 11:00–11:45“; Ausweichtermine stören so nicht.
        val termin = t.groupingBy { Triple(it.datum.dayOfWeek.value, it.beginn, it.ende) }.eachCount()
            .maxByOrNull { it.value }?.key
            ?.let { (tag, von, bis) -> "${TAG[tag - 1]} ${von.format(UHR)}–${bis.format(UHR)}" } ?: "–"
        val beginn = when {
            t.isEmpty() -> "nach Ansage"
            t.first().datum.isAfter(heute) -> t.first().datum.format(DATUM)
            else -> "läuft"
        }
        val minuten = t.firstOrNull()?.let { Duration.between(it.beginn, it.ende).toMinutes() }
        val hinweis = when {
            t.isEmpty() -> ""
            minuten != null && minuten > 0 -> "${t.size} Termine à $minuten Minuten"
            else -> "${t.size} Termine"
        }
        // Volle Kurse zeigen „Warteliste offen“: Plätze werden über die Warteliste vergeben.
        val status = if (k.frei > 0) "offen" else "warteliste"
        return listOf(kursart(k.name), termin, beginn, k.frei.toString(), status, hinweis).joinToString(",") { feld(it) }
    }

    private fun feld(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
