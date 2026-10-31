package de.dachapp.schwimmgruppen.logic

import de.dachapp.abgleich.Abgleich
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Zerlegt den Bestand für den Abgleich mit anderen Trainern (siehe :core:abgleich) in einzelne Einträge und setzt
 * ihn wieder zusammen. So kann jeder Trainer auf seinem Gerät Anwesenheit erfassen, und alles landet in einer
 * gemeinsamen Datei.
 */
object Teilen {
    const val APP = "schwimmgruppen"

    /** Nur auf diesem Gerät: welche Datei geteilt wird und der zuletzt gemeinsame Stand. */
    @Serializable
    data class Verbindung(
        /** content://-Adresse der geteilten Datei aus dem Dateidialog; null = nicht verbunden. */
        val datei: String? = null,
        /** Zeitpunkt des letzten erfolgreichen Abgleichs, 0 = noch nie. */
        val letzter: Long = 0,
        val stand: Abgleich.Stand = Abgleich.Stand(app = APP),
    )

    fun encodeVerbindung(v: Verbindung): String = json.encodeToString(Verbindung.serializer(), v)
    fun decodeVerbindung(text: String): Verbindung = json.decodeFromString(Verbindung.serializer(), text)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun <T> roh(s: KSerializer<T>, l: List<T>, id: (T) -> String): Map<String, JsonElement> =
        l.associate { id(it) to json.encodeToJsonElement(s, it) }

    /** Noch nichts Eigenes erfasst (nur die vorgegebenen Gruppen): beim ersten Abgleich nur die Datei übernehmen. */
    fun nurVorgaben(b: Bestand): Boolean = b.mitglieder.isEmpty() && b.einheiten.isEmpty() && b.trainer.isEmpty()

    fun zerlegen(b: Bestand): Map<String, Map<String, JsonElement>> = mapOf(
        "mitglied" to roh(Mitglied.serializer(), b.mitglieder) { it.id },
        "gruppe" to roh(Gruppe.serializer(), b.gruppen) { it.id },
        "trainer" to roh(Trainer.serializer(), b.trainer) { it.id },
        "einheit" to roh(Einheit.serializer(), b.einheiten) { it.schluessel },
        "einstellungen" to mapOf("verein" to json.encodeToJsonElement(Einstellungen.serializer(), b.einstellungen)),
    )

    /**
     * Baut den Bestand aus abgeglichenen Einträgen. Die Reihenfolge von [basis] bleibt (wichtig für die Gruppen);
     * Neues kommt hinten an. Unlesbare Einträge (z. B. aus einer neueren App-Version) werden übersprungen.
     */
    fun zusammensetzen(basis: Bestand, werte: Map<String, Map<String, JsonElement>>): Bestand {
        fun <T> liste(art: String, s: KSerializer<T>, alt: List<T>, id: (T) -> String): List<T> {
            val w = werte[art].orEmpty()
            val altIds = alt.map(id)
            val reihenfolge = altIds.filter { it in w } + w.keys.filter { it !in altIds }.sorted()
            return reihenfolge.mapNotNull { k -> runCatching { json.decodeFromJsonElement(s, w.getValue(k)) }.getOrNull() }
        }
        return basis.copy(
            mitglieder = liste("mitglied", Mitglied.serializer(), basis.mitglieder) { it.id },
            gruppen = liste("gruppe", Gruppe.serializer(), basis.gruppen) { it.id },
            trainer = liste("trainer", Trainer.serializer(), basis.trainer) { it.id },
            einheiten = liste("einheit", Einheit.serializer(), basis.einheiten) { it.schluessel },
            einstellungen = werte["einstellungen"]?.get("verein")
                ?.let { runCatching { json.decodeFromJsonElement(Einstellungen.serializer(), it) }.getOrNull() }
                ?: basis.einstellungen,
        )
    }
}
