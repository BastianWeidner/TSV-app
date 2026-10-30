package de.dachapp.schwimmgruppen.logic

import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Speicherformat. Dieselbe Datei dient als App-Speicher und als Sicherung zum Ablegen in Google Drive. */
object BestandCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(bestand: Bestand): String = json.encodeToString(Bestand.serializer(), bestand)

    /** Wirft [IllegalArgumentException] mit verständlicher Meldung, wenn der Text keine Sicherung ist. */
    fun decode(text: String): Bestand {
        val b = try {
            json.decodeFromString(Bestand.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Die Datei ist keine Schwimmgruppen-Sicherung oder beschädigt.", e)
        }
        require(b.version <= Bestand.AKTUELLE_VERSION) {
            "Die Sicherung stammt aus einer neueren App-Version. Bitte zuerst die App aktualisieren."
        }
        return migrieren(b).copy(version = Bestand.AKTUELLE_VERSION)
    }

    /** Ältere Stände an das aktuelle Format anpassen. */
    private fun migrieren(alt: Bestand): Bestand {
        var b = alt
        // Version 2: Gruppe I und II trainieren je Tag mit anderen Schwimmern und werden deshalb aufgeteilt.
        if (b.version < 2) {
            for (id in listOf("gruppe-1", "gruppe-2")) {
                if ((b.gruppe(id)?.zeiten?.size ?: 0) > 1) b = b.gruppeAufteilen(id)
            }
        }
        return b
    }
}

/** Listen als CSV mit Semikolon und BOM, öffnen sich in Excel direkt richtig. */
object Listen {
    private val DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val UHR = DateTimeFormatter.ofPattern("HH:mm")

    private fun csv(zeilen: List<List<String>>): String {
        fun z(s: String) = if (s.any { it == ';' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        return "﻿" + zeilen.joinToString("\r\n") { it.joinToString(";") { s -> z(s) } } + "\r\n"
    }

    private fun janein(b: Boolean?) = when (b) { true -> "ja"; false -> "nein"; null -> "" }

    fun gruppe(bestand: Bestand, gruppeId: String): String {
        val gesundheit = bestand.einstellungen.gesundheitAnzeigen
        val kopf = listOf("Nachname", "Vorname", "Geburtsdatum", "Jahrgang", "Telefon", "E-Mail", "Fotorechte") +
            listOfNotNull("Gesundheitserklärung".takeIf { gesundheit }) + listOf("Wettkampf", "Bemerkung")
        return csv(listOf(kopf) + bestand.mitgliederIn(gruppeId).map { m ->
            listOf(
                m.nachname, m.vorname, m.geburtsdatum?.format(DATUM).orEmpty(), m.jahrgang?.toString().orEmpty(),
                m.telefon, m.email, janein(m.fotorechte),
            ) + listOfNotNull(janein(m.gesundheitserklaerung).takeIf { gesundheit }) +
                listOf(if (m.interesse == Interesse.OFFEN) "" else m.interesse.text, m.notiz)
        })
    }

    /** Alle Trainings des Zeitraums mit Teilnehmerzahl, Ausfall und Vermerk, z. B. für Zuschussanträge. */
    fun anwesenheit(bestand: Bestand, von: LocalDate, bis: LocalDate): String {
        val kopf = listOf("Datum", "Wochentag", "Gruppe", "Art", "Beginn", "Ende", "Teilnehmer", "davon Mitglieder", "Ausgefallen", "Vermerk", "Trainer", "Anwesend")
        val zeilen = Kalender.zeitraum(bestand, von, bis)
            .filter { it.erfasst || it.ausgefallen || it.vermerk.isNotBlank() || it.trainer != null || it.vertretungen.isNotEmpty() }
            .map { e ->
                val g = bestand.gruppe(e.gruppeId)
                listOf(
                    e.datum.format(DATUM),
                    e.datum.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN),
                    g?.name.orEmpty(),
                    g?.art?.text.orEmpty(),
                    e.beginn.format(UHR),
                    e.ende.format(UHR),
                    if (e.ausgefallen) "" else e.anzahl.toString(),
                    if (e.ausgefallen) "" else e.anwesend.size.toString(),
                    if (e.ausgefallen) "ja" else "",
                    e.vermerk,
                    bestand.trainerText(e),
                    e.anwesend.mapNotNull { bestand.mitglied(it)?.name }.sorted().joinToString(", "),
                )
            }
        return csv(listOf(kopf) + zeilen)
    }
}
