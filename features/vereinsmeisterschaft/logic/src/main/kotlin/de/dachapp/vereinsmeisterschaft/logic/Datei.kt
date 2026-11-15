package de.dachapp.vereinsmeisterschaft.logic

import kotlinx.serialization.json.Json

/** Speichern und Laden als JSON (eine Datei für alle Veranstaltungen). */
object Datei {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        allowStructuredMapKeys = true
    }

    fun schreiben(d: Daten): String = json.encodeToString(Daten.serializer(), d)

    fun lesen(text: String): Daten = json.decodeFromString(Daten.serializer(), text).let { d ->
        // Bis 1.24.0 war „Schulschwimmhalle Fischach“ vorbelegt; Bastian will „Hallenbad Fischach“ (2026-11-15).
        d.copy(veranstaltungen = d.veranstaltungen.map { if (it.ort == "Schulschwimmhalle Fischach") it.copy(ort = "Hallenbad Fischach") else it })
    }
}
