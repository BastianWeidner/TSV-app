package de.dachapp.schwimmleitung.logic

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.text.Collator
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

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

/**
 * Jeder geteilte Eintrag trägt eine feste ID und den Zeitpunkt seiner letzten Änderung; beim Abgleich gewinnt je
 * Eintrag der neuere Stand (siehe [Abgleich]). Gelöscht wird über [geloescht], damit das Löschen auch auf den
 * anderen Geräten ankommt.
 */
interface Eintrag {
    val id: String
    /** Millisekunden seit 1970, auf dem ändernden Gerät gesetzt. */
    val geaendert: Long
    val geloescht: Boolean
}

// ---------- Personen und Nachweise ----------

/** Trainer, Leitung oder Helfer, für Aufgaben, Anwesenheit in Sitzungen und Nachweise. */
@Serializable
data class Person(
    override val id: String,
    val name: String,
    /** Z. B. "Abteilungsleitung", "Trainer", "Kassenwart". */
    val rolle: String = "",
    val telefon: String = "",
    /** Herkunft aus der Trainerliste der Schwimmgruppen, damit ein neuer Import sie wiedererkennt. */
    val trainerId: String? = null,
    val aktiv: Boolean = true,
    override val geaendert: Long = 0,
    override val geloescht: Boolean = false,
) : Eintrag

@Serializable
enum class NachweisArt(val text: String, val kurz: String, val gueltigJahre: Int?) {
    UEBUNGSLEITER("Übungsleiter / Trainer", "ÜL", 4),
    RETTUNGSSCHWIMMER("Rettungsschwimmer", "RS", 2),
    ERSTE_HILFE("Erste Hilfe", "EH", 2),
    FUEHRUNGSZEUGNIS("Führungszeugnis", "FZ", 5),
    KAMPFRICHTER("Kampfrichter", "KR", null),
}

/** Schein oder Nachweis einer Person, z. B. Rettungsschwimmer Silber bis 03/2027. */
@Serializable
data class Nachweis(
    override val id: String,
    val personId: String,
    val art: NachweisArt,
    /** Stufe oder Bezeichnung, z. B. "Trainer C Schwimmen" oder "Silber". */
    val bezeichnung: String = "",
    @Serializable(LocalDateIso::class) val ausgestellt: LocalDate? = null,
    /** null = unbefristet. */
    @Serializable(LocalDateIso::class) val gueltigBis: LocalDate? = null,
    val notiz: String = "",
    override val geaendert: Long = 0,
    override val geloescht: Boolean = false,
) : Eintrag

// ---------- Aufgaben ----------

@Serializable
enum class AufgabeStatus(val text: String) {
    OFFEN("offen"),
    LAEUFT("läuft"),
    ERLEDIGT("erledigt"),
}

@Serializable
data class Aufgabe(
    override val id: String,
    val titel: String,
    val notiz: String = "",
    /** Verantwortliche Person (ID) oder freier Name, wenn [personId] fehlt. */
    val personId: String? = null,
    val verantwortlich: String = "",
    @Serializable(LocalDateIso::class) val faellig: LocalDate? = null,
    val status: AufgabeStatus = AufgabeStatus.OFFEN,
    @Serializable(LocalDateIso::class) val erstellt: LocalDate? = null,
    @Serializable(LocalDateIso::class) val erledigtAm: LocalDate? = null,
    /** Aus welcher Sitzung die Aufgabe stammt. */
    val sitzungId: String? = null,
    /** Aus welcher Meldung (Defekt) die Aufgabe stammt. */
    val meldungId: String? = null,
    override val geaendert: Long = 0,
    override val geloescht: Boolean = false,
) : Eintrag {
    val offen: Boolean get() = status != AufgabeStatus.ERLEDIGT
    fun ueberfaellig(heute: LocalDate) = offen && faellig != null && faellig.isBefore(heute)
}

// ---------- Sitzungen ----------

@Serializable
enum class SitzungArt(val text: String) {
    TRAINER("Trainersitzung"),
    LEITUNG("Leitungssitzung"),
}

/** Protokoll in der festen Gliederung: Datum – Anwesenheiten – Leitungsbericht – offene Runde. */
@Serializable
data class Sitzung(
    override val id: String,
    val art: SitzungArt = SitzungArt.TRAINER,
    @Serializable(LocalDateIso::class) val datum: LocalDate,
    @Serializable(LocalTimeIso::class) val beginn: LocalTime? = null,
    val ort: String = "",
    /** Personen-IDs. */
    val anwesend: List<String> = emptyList(),
    val entschuldigt: List<String> = emptyList(),
    /** Weitere Anwesende ohne Eintrag in der Personenliste, frei getippt. */
    val gaeste: String = "",
    val leitungsbericht: String = "",
    /** Eingefügter Kennzahlen-Bericht (Text), Teil des Leitungsberichts. */
    val kennzahlen: String = "",
    val offeneRunde: String = "",
    val protokollant: String = "",
    override val geaendert: Long = 0,
    override val geloescht: Boolean = false,
) : Eintrag

// ---------- Material und Hallenbericht ----------

@Serializable
enum class Zustand(val text: String) {
    IN_ORDNUNG("in Ordnung"),
    DEFEKT("defekt"),
    AUSGESONDERT("ausgesondert"),
}

@Serializable
data class Material(
    override val id: String,
    val name: String,
    /** Z. B. "Geräteraum", "Schrank Beckenrand". */
    val ort: String = "",
    val anzahl: Int = 1,
    val zustand: Zustand = Zustand.IN_ORDNUNG,
    /** Nächste Prüfung, z. B. Rettungsmittel oder Erste-Hilfe-Kasten. */
    @Serializable(LocalDateIso::class) val pruefen: LocalDate? = null,
    val notiz: String = "",
    override val geaendert: Long = 0,
    override val geloescht: Boolean = false,
) : Eintrag

@Serializable
enum class MeldungArt(val text: String) {
    DEFEKT("Defekt"),
    VORKOMMNIS("Vorkommnis"),
}

/** Was Trainer ohne PIN in der Halle melden: ein Defekt oder ein Vorkommnis (z. B. kleiner Unfall, Bahn gesperrt). */
@Serializable
data class Meldung(
    override val id: String,
    val art: MeldungArt,
    val text: String,
    val materialId: String? = null,
    val von: String = "",
    /** Millisekunden seit 1970. */
    val zeit: Long,
    /** JPEG als Base64, verkleinert. */
    val foto: String? = null,
    val erledigt: Boolean = false,
    val aufgabeId: String? = null,
    override val geaendert: Long = 0,
    override val geloescht: Boolean = false,
) : Eintrag

// ---------- Belege ----------

@Serializable
data class Beleg(
    override val id: String,
    @Serializable(LocalDateIso::class) val datum: LocalDate,
    /** Immer positiv; Richtung über [einnahme]. */
    val betragCent: Long,
    val einnahme: Boolean = false,
    val zweck: String,
    val von: String = "",
    val foto: String? = null,
    val uebergeben: Boolean = false,
    @Serializable(LocalDateIso::class) val uebergebenAm: LocalDate? = null,
    override val geaendert: Long = 0,
    override val geloescht: Boolean = false,
) : Eintrag

// ---------- Ablage ----------

@Serializable
enum class Ablage(val text: String) {
    WICHTIG("Wichtige Dokumente"),
    TRAINING("Trainingsunterlagen"),
}

/** Verweis auf ein Dokument, meist ein Link in Google Drive. */
@Serializable
data class Dokument(
    override val id: String,
    val titel: String,
    val ablage: Ablage = Ablage.WICHTIG,
    val link: String = "",
    val notiz: String = "",
    @Serializable(LocalDateIso::class) val gueltigBis: LocalDate? = null,
    override val geaendert: Long = 0,
    override val geloescht: Boolean = false,
) : Eintrag

// ---------- Einstellungen ----------

/** Für alle Geräte gleich. Genau ein Eintrag mit der ID [ID]. */
@Serializable
data class Einstellungen(
    override val id: String = ID,
    val verein: String = "TSV Fischach – Abteilung Schwimmen",
    /** Leitungs-PIN als SHA-256 mit Salz; leer = noch keine PIN gesetzt. */
    val pinSalz: String = "",
    val pinHash: String = "",
    /** Monat, in dem die Saison beginnt (9 = September). */
    val saisonBeginn: Int = 9,
    /** So viele Tage vor Ablauf warnt die App bei Nachweisen. */
    val warnTage: Int = 90,
    override val geaendert: Long = 0,
    override val geloescht: Boolean = false,
) : Eintrag {
    val pinGesetzt: Boolean get() = pinHash.isNotEmpty()

    companion object {
        const val ID = "einstellungen"
    }
}

/** Alles, was über die geteilte Datei zwischen den Geräten läuft. */
@Serializable
data class LeitungDaten(
    val format: String = FORMAT,
    val version: Int = AKTUELLE_VERSION,
    val einstellungen: List<Einstellungen> = emptyList(),
    val personen: List<Person> = emptyList(),
    val nachweise: List<Nachweis> = emptyList(),
    val aufgaben: List<Aufgabe> = emptyList(),
    val sitzungen: List<Sitzung> = emptyList(),
    val material: List<Material> = emptyList(),
    val meldungen: List<Meldung> = emptyList(),
    val belege: List<Beleg> = emptyList(),
    val dokumente: List<Dokument> = emptyList(),
    val kursAuszug: List<KursAuszug> = emptyList(),
    val gruppenAuszug: List<GruppenAuszug> = emptyList(),
) {
    val e: Einstellungen get() = einstellungen.firstOrNull { !it.geloescht } ?: Einstellungen()
    val kurs: KursAuszug? get() = kursAuszug.firstOrNull { !it.geloescht }
    val gruppen: GruppenAuszug? get() = gruppenAuszug.firstOrNull { !it.geloescht }

    fun person(id: String?) = personen.firstOrNull { it.id == id && !it.geloescht }
    fun aufgabe(id: String?) = aufgaben.firstOrNull { it.id == id && !it.geloescht }
    fun sitzung(id: String?) = sitzungen.firstOrNull { it.id == id && !it.geloescht }
    fun materialStueck(id: String?) = material.firstOrNull { it.id == id && !it.geloescht }
    fun meldung(id: String?) = meldungen.firstOrNull { it.id == id && !it.geloescht }
    fun beleg(id: String?) = belege.firstOrNull { it.id == id && !it.geloescht }
    fun dokument(id: String?) = dokumente.firstOrNull { it.id == id && !it.geloescht }
    fun nachweis(id: String?) = nachweise.firstOrNull { it.id == id && !it.geloescht }

    val aktivePersonen get() = personen.filter { !it.geloescht && it.aktiv }.sortedWith(compareBy(DEUTSCH) { it.name })
    val allePersonen get() = personen.filter { !it.geloescht }.sortedWith(compareBy(DEUTSCH) { it.name })
    val sichtbareAufgaben get() = aufgaben.filter { !it.geloescht }
    val sichtbareSitzungen get() = sitzungen.filter { !it.geloescht }.sortedByDescending { it.datum }
    val sichtbaresMaterial get() = material.filter { !it.geloescht }.sortedWith(compareBy(DEUTSCH) { it.name })
    val sichtbareMeldungen get() = meldungen.filter { !it.geloescht }.sortedByDescending { it.zeit }
    val sichtbareBelege get() = belege.filter { !it.geloescht }.sortedWith(compareByDescending<Beleg> { it.datum }.thenByDescending { it.geaendert })
    val sichtbareDokumente get() = dokumente.filter { !it.geloescht }.sortedWith(compareBy(DEUTSCH) { it.titel })
    fun nachweiseVon(personId: String) = nachweise.filter { !it.geloescht && it.personId == personId }.sortedBy { it.art.ordinal }

    /** Name der verantwortlichen Person oder der frei eingetragene Name. */
    fun verantwortlich(a: Aufgabe): String = person(a.personId)?.name ?: a.verantwortlich

    companion object {
        const val FORMAT = "schwimmleitung"
        const val AKTUELLE_VERSION = 1

        /** Alphabetisch mit Umlauten an der richtigen Stelle (Ä wie A). */
        val DEUTSCH: Collator = Collator.getInstance(Locale.GERMAN)
    }
}
