package de.dachapp.schwimmkurs.logic

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period

object LocalDateIso : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object LocalTimeIso : KSerializer<LocalTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalTime = LocalTime.parse(decoder.decodeString())
}

@Serializable
data class Kind(
    val id: String,
    val vorname: String,
    val nachname: String,
    @Serializable(LocalDateIso::class) val geburtsdatum: LocalDate? = null,
    /** Name des Elternteils für die Anrede, z. B. "Sabine Müller". */
    val eltern: String = "",
    /** Eine oder mehrere Adressen, getrennt durch Komma oder Semikolon. */
    val email: String = "",
    val telefon: String = "",
    val strasse: String = "",
    val plz: String = "",
    val ort: String = "",
    /** Abzeichen und sonstige Leistungen, Namen wie in [Einstellungen.abzeichen] oder frei. */
    val abzeichen: Set<String> = emptySet(),
    /** Abzeichen-Prüfung: Abzeichen → Teilleistung → Datum (ISO), siehe Schwimmabzeichen in core. */
    val pruefung: Map<String, Map<String, String>> = emptyMap(),
    @Serializable(LocalDateIso::class) val anmeldedatum: LocalDate? = null,
    val notiz: String = "",
    /** Feste Familienzuordnung. Leer = automatisch aus E-Mail bzw. Name und Adresse. */
    val familie: String = "",
    /** Wird beim automatischen Befüllen übergangen. */
    val pausiert: Boolean = false,
    /** Jahr der Einschulung als Text, z. B. "2026" oder "2026 evtl. 2027". */
    val einschulung: String = "",
    /** Beim automatischen Befüllen nicht in denselben Kurs wie Geschwister setzen. */
    val geschwisterGetrennt: Boolean = false,
    /** Position in der Anmeldeliste (Reihenfolge beim Import). Bei gleichem Vorrang kommt die kleinere zuerst. */
    val listenplatz: Int? = null,
) {
    val name: String get() = "$vorname $nachname".trim()

    /** Kinder mit gleichem Schlüssel gelten als Geschwister und bekommen eine gemeinsame Mail. */
    val familienSchluessel: String
        get() {
            fun norm(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")
            val mail = Einladungen.adressen(email).firstOrNull()
            return when {
                familie.isNotBlank() -> "f:" + norm(familie)
                mail != null -> "e:" + mail.lowercase()
                strasse.isNotBlank() -> "a:" + norm(nachname) + "|" + norm(strasse) + "|" + norm(plz)
                eltern.isNotBlank() -> "p:" + norm(nachname) + "|" + norm(eltern)
                else -> "k:$id"
            }
        }

    /** Alter in vollen Jahren an [stichtag], null ohne Geburtsdatum. */
    fun alterAm(stichtag: LocalDate): Int? = geburtsdatum?.let { Period.between(it, stichtag).years }
}

@Serializable
data class Termin(
    @Serializable(LocalDateIso::class) val datum: LocalDate,
    @Serializable(LocalTimeIso::class) val beginn: LocalTime,
    @Serializable(LocalTimeIso::class) val ende: LocalTime,
)

@Serializable
data class Kurs(
    val id: String,
    val name: String,
    val termine: List<Termin> = emptyList(),
    val plaetze: Int = 8,
    /** Altersgrenzen in vollen Jahren am ersten Kurstag, jeweils einschließlich. */
    val minAlter: Int? = null,
    val maxAlter: Int? = null,
    /** Dieses Abzeichen (oder ein höheres) muss das Kind schon haben. */
    val voraussetzung: String? = null,
    /** Kursziel. Kinder, die es (oder ein höheres) schon haben, passen nicht mehr. */
    val ziel: String? = null,
    /** Schwimmbad bzw. Treffpunkt. */
    val ort: String = "",
    /** Fester Preis pro Kind in Cent. null = Termine × (Kursgebühr + Eintritt) aus den Einstellungen. */
    val preisCent: Long? = null,
    val kursleitung: String = "",
    /** Kürzel für den Verwendungszweck, z. B. "SK04 2526". */
    val kuerzel: String = "",
    /** Zusätzlicher Hinweis in der Einladung, z. B. "Am 20.12. dürfen Eltern mit ins Wasser." */
    val hinweis: String = "",
    /** Zahlungseingang spätestens an diesem Tag. null = Text aus den Einstellungen. */
    @Serializable(LocalDateIso::class) val zahlungsfrist: LocalDate? = null,
    /** Kind-IDs in Reihenfolge der Zuteilung. */
    val teilnehmer: List<String> = emptyList(),
) {
    val sortierteTermine: List<Termin> get() = termine.sortedWith(compareBy({ it.datum }, { it.beginn }))
    val ersterTag: LocalDate? get() = termine.minOfOrNull { it.datum }
    val frei: Int get() = (plaetze - teilnehmer.size).coerceAtLeast(0)
}

@Serializable
data class Einstellungen(
    val verein: String = Vorlagen.VEREIN,
    /** Unterschrift unter der Mail. */
    val absender: String = Vorlagen.ABSENDER,
    val kontoinhaber: String = Vorlagen.KONTOINHABER,
    val iban: String = Vorlagen.IBAN,
    val bic: String = "",
    /** Gilt, wenn der Kurs keine eigene Zahlungsfrist hat, z. B. "innerhalb von 14 Tagen". */
    val zahlungsfrist: String = "innerhalb von 14 Tagen",
    val verwendungszweck: String = "{Kuerzel} - {KinderListe}",
    /** Kursgebühr und Eintrittspauschale je Termin, daraus ergibt sich der Kurspreis. */
    val gebuehrProTerminCent: Long = 1200,
    val eintrittProTerminCent: Long = 50,
    /** Vorbelegung für neue Kurse. */
    val standardOrt: String = Vorlagen.ORT,
    /** Gilt für Kurse ohne eigenes Mindestalter. 0 = keine Grenze. */
    val mindestalter: Int = 5,
    /** Größter Altersunterschied in Jahren innerhalb eines Kurses beim automatischen Befüllen. 0 = egal. */
    val maxAltersspanne: Int = 2,
    /** Nachlass in Cent für jedes weitere Geschwisterkind einer Familie. */
    val geschwisterRabattCent: Long = 0,
    /** Abzeichen von niedrig nach hoch. Wer ein höheres hat, erfüllt auch die niedrigeren. */
    val abzeichen: List<String> = STANDARD_ABZEICHEN,
    /**
     * Einzugsgebiet in Vorrangreihenfolge, eine Zeile je Gemeinde: "Fischach: Willmatshofen, Reitenbuch".
     * Die Ortsteile hinter dem Doppelpunkt zählen wie die Gemeinde. Auch Postleitzahlen ("86850", "868*").
     */
    val wohnortVorrang: List<String> = Vorlagen.EINZUGSGEBIET,
    /** Bekannte Orte außerhalb des Einzugsgebiets, damit sie auch in der Bemerkung erkannt werden. Diese Kinder kommen zuletzt. */
    val orteAusserhalb: List<String> = Vorlagen.AUSSERHALB,
    /** Stand der automatischen Umstellungen von Einzugsgebiet und Orten außerhalb, siehe [Vorlagen.aktualisieren]. */
    val vorrangStand: Int = 0,
    val betreff: String = Vorlagen.BETREFF,
    val mailText: String = Vorlagen.MAIL,
    val haftungTitel: String = Vorlagen.HAFTUNG_TITEL,
    val haftungText: String = Vorlagen.HAFTUNG,
) {
    companion object {
        val STANDARD_ABZEICHEN = listOf("Seepferdchen", "Bronze", "Silber", "Gold")
    }
}

/** Alles, was die Unter-App speichert. Wird als eine Datei gesichert und wiederhergestellt. */
@Serializable
data class Bestand(
    val version: Int = AKTUELLE_VERSION,
    val kinder: List<Kind> = emptyList(),
    val kurse: List<Kurs> = emptyList(),
    val einstellungen: Einstellungen = Einstellungen(),
    /** Familienschlüssel → Zeitpunkt, an dem die Einladung zuletzt geöffnet wurde (ISO). */
    val versendet: Map<String, String> = emptyMap(),
    /** Familienschlüssel → Zahlungseingang (aus dem Kontoauszug oder von Hand). */
    val zahlungen: Map<String, Zahlung> = emptyMap(),
) {
    fun kind(id: String): Kind? = kinder.firstOrNull { it.id == id }
    fun kurs(id: String): Kurs? = kurse.firstOrNull { it.id == id }

    /** Kurs, in dem das Kind sitzt (ein Kind ist höchstens in einem Kurs). */
    fun kursVon(kindId: String): Kurs? = kurse.firstOrNull { kindId in it.teilnehmer }

    /** Kinder, die noch in keinem Kurs sitzen. */
    val ohneKurs: List<Kind>
        get() {
            val belegt = kurse.flatMap { it.teilnehmer }.toSet()
            return kinder.filter { it.id !in belegt }
        }

    fun kindEntfernen(id: String) = copy(
        kinder = kinder.filter { it.id != id },
        kurse = kurse.map { k -> k.copy(teilnehmer = k.teilnehmer - id) },
    )

    fun kindSetzen(kind: Kind) = copy(
        kinder = if (kinder.any { it.id == kind.id }) kinder.map { if (it.id == kind.id) kind else it } else kinder + kind,
    )

    fun kursSetzen(kurs: Kurs) = copy(
        kurse = if (kurse.any { it.id == kurs.id }) kurse.map { if (it.id == kurs.id) kurs else it } else kurse + kurs,
    )

    fun kursEntfernen(id: String) = copy(kurse = kurse.filter { it.id != id })

    /** Setzt das Kind in [kursId] (oder nimmt es mit null aus jedem Kurs heraus). */
    fun zuteilen(kindId: String, kursId: String?) = copy(
        kurse = kurse.map { k ->
            when {
                k.id == kursId && kindId !in k.teilnehmer -> k.copy(teilnehmer = k.teilnehmer + kindId)
                k.id != kursId && kindId in k.teilnehmer -> k.copy(teilnehmer = k.teilnehmer - kindId)
                else -> k
            }
        },
    )

    companion object {
        const val AKTUELLE_VERSION = 1
    }
}
