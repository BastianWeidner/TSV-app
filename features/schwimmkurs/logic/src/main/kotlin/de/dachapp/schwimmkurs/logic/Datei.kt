package de.dachapp.schwimmkurs.logic

import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Speicherformat. Dieselbe Datei dient als App-Speicher, als Sicherung zum Teilen oder Ablegen in Google Drive
 * und später als Grundlage für eine Synchronisation.
 */
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
            throw IllegalArgumentException("Die Datei ist keine Schwimmkurs-Sicherung oder beschädigt.", e)
        }
        require(b.version <= Bestand.AKTUELLE_VERSION) {
            "Die Sicherung stammt aus einer neueren App-Version. Bitte zuerst die App aktualisieren."
        }
        return b.copy(version = Bestand.AKTUELLE_VERSION, einstellungen = Vorlagen.aktualisieren(b.einstellungen))
    }
}

object Kursliste {
    private val DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** Teilnehmerliste als CSV mit Semikolon und BOM, öffnet sich in Excel direkt richtig. */
    fun csv(kurs: Kurs, bestand: Bestand): String {
        fun z(s: String) = if (s.any { it == ';' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        val kopf = listOf("Vorname", "Nachname", "Geburtsdatum", "Alter", "Abzeichen", "Elternteil", "Telefon", "E-Mail", "Bemerkung")
        val stichtag = kurs.ersterTag ?: LocalDate.now()
        val zeilen = kurs.teilnehmer.mapNotNull { bestand.kind(it) }.map { k ->
            listOf(
                k.vorname, k.nachname, k.geburtsdatum?.format(DATUM).orEmpty(), k.alterAm(stichtag)?.toString().orEmpty(),
                k.abzeichen.joinToString(", "), k.eltern, k.telefon, k.email, k.notiz,
            )
        }
        return "﻿" + (listOf(kopf) + zeilen).joinToString("\r\n") { it.joinToString(";") { s -> z(s) } } + "\r\n"
    }
}

object Terminserie {
    /** [anzahl] Termine im Wochenabstand, beginnend am ersten [wochentag] ab [ab]. */
    fun woechentlich(ab: LocalDate, wochentag: DayOfWeek, anzahl: Int, beginn: LocalTime, ende: LocalTime): List<Termin> {
        val start = ab.with(TemporalAdjusters.nextOrSame(wochentag))
        return (0 until anzahl).map { Termin(start.plusWeeks(it.toLong()), beginn, ende) }
    }
}
