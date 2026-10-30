package de.dachapp.schwimmleitung.logic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.LocalTime

/*
 * Auszüge aus den Sicherungen von Schwimmkurs und Schwimmgruppen. Die Leitung liest diese Dateien nur und schreibt
 * nie zurück; es gibt keine Code-Abhängigkeit zu den anderen Unter-Apps. Übernommen wird nur, was die Kennzahlen
 * brauchen: keine Namen von Kindern oder Schwimmern, keine Adressen, keine Kontaktdaten.
 */

@Serializable
enum class Herkunft(val text: String) {
    EINZUGSGEBIET("Einzugsgebiet"),
    NACHBARORTE("Nachbarorte"),
    OHNE_ORT("ohne Ortsangabe"),
    AUSWAERTS("auswärts"),
}

@Serializable
data class KindA(
    val id: String,
    @Serializable(LocalDateIso::class) val geburtsdatum: LocalDate? = null,
    @Serializable(LocalDateIso::class) val anmeldedatum: LocalDate? = null,
    val pausiert: Boolean = false,
    val abzeichen: Set<String> = emptySet(),
    val herkunft: Herkunft = Herkunft.OHNE_ORT,
    /** Gemeinde aus der Vorrangliste oder eingetragener Ort, für die Aufschlüsselung. */
    val gemeinde: String = "",
)

@Serializable
data class KursA(
    val id: String,
    val name: String,
    val termine: List<@Serializable(LocalDateIso::class) LocalDate> = emptyList(),
    val plaetze: Int = 0,
    val teilnehmer: List<String> = emptyList(),
    val preisProKindCent: Long = 0,
    /** Abzeichen, auf das der Kurs hinführt, z. B. "Seepferdchen". */
    val ziel: String? = null,
) {
    val ersterTag: LocalDate? get() = termine.minOrNull()
}

@Serializable
data class KursAuszug(
    override val id: String = ID,
    /** Zeitpunkt des Imports (ms). */
    val importiert: Long = 0,
    val kinder: List<KindA> = emptyList(),
    val kurse: List<KursA> = emptyList(),
    val mindestalter: Int = 5,
    override val geaendert: Long = 0,
    override val geloescht: Boolean = false,
) : Eintrag {
    fun kind(id: String) = kinder.firstOrNull { it.id == id }

    companion object {
        const val ID = "schwimmkurs"
    }
}

@Serializable
data class ErfolgA(
    val text: String,
    /** ABZEICHEN, WETTKAMPF oder SONSTIGES. */
    val art: String = "ABZEICHEN",
    @Serializable(LocalDateIso::class) val datum: LocalDate? = null,
)

@Serializable
data class ZeitA(
    val strecke: String,
    val hundertstel: Int,
    @Serializable(LocalDateIso::class) val datum: LocalDate? = null,
    val wettkampf: Boolean = false,
)

@Serializable
data class MitgliedA(
    val id: String,
    val gruppen: Set<String> = emptySet(),
    val aktiv: Boolean = true,
    /** Möchte zu Wettkämpfen. */
    val wettkampf: Boolean = false,
    val erfolge: List<ErfolgA> = emptyList(),
    val zeiten: List<ZeitA> = emptyList(),
)

@Serializable
data class ZeitplanA(
    val wochentag: Int,
    @Serializable(LocalTimeIso::class) val beginn: LocalTime,
    @Serializable(LocalTimeIso::class) val ende: LocalTime,
)

@Serializable
data class GruppeA(
    val id: String,
    val name: String,
    /** Angebot für Externe (Wassergymnastik, Erwachsenenschwimmen). */
    val extern: Boolean = false,
    val zeiten: List<ZeitplanA> = emptyList(),
    val plaetze: Int? = null,
    val trainerIds: List<String> = emptyList(),
)

@Serializable
data class EinheitA(
    val gruppeId: String,
    @Serializable(LocalDateIso::class) val datum: LocalDate,
    @Serializable(LocalTimeIso::class) val beginn: LocalTime,
    val anzahl: Int = 0,
    val ausgefallen: Boolean = false,
    /** null = Stammtrainer der Gruppe. */
    val trainer: List<String>? = null,
    val vertretungen: Map<String, String> = emptyMap(),
) {
    val erfasst: Boolean get() = !ausgefallen && anzahl > 0
}

@Serializable
data class TrainerA(
    val id: String,
    val name: String,
    @Serializable(LocalDateIso::class) val geburtsdatum: LocalDate? = null,
    /** Trainer, Jungtrainer oder Helfer. */
    val rang: String = "Trainer",
    val schein: String = "",
    val aktiv: Boolean = true,
)

@Serializable
data class GruppenAuszug(
    override val id: String = ID,
    val importiert: Long = 0,
    val mitglieder: List<MitgliedA> = emptyList(),
    val gruppen: List<GruppeA> = emptyList(),
    val einheiten: List<EinheitA> = emptyList(),
    val trainer: List<TrainerA> = emptyList(),
    override val geaendert: Long = 0,
    override val geloescht: Boolean = false,
) : Eintrag {
    fun gruppe(id: String) = gruppen.firstOrNull { it.id == id }
    fun trainer(id: String) = trainer.firstOrNull { it.id == id }

    /** Wer eine Stunde tatsächlich geleitet hat: eingeteilte Trainer, Vertretungen eingesetzt. */
    fun trainerVon(e: EinheitA): List<TrainerA> =
        (e.trainer ?: gruppe(e.gruppeId)?.trainerIds.orEmpty())
            .map { e.vertretungen[it] ?: it }.distinct().mapNotNull { trainer(it) }

    companion object {
        const val ID = "schwimmgruppen"
    }
}

// ---------- Import ----------

/** Liest eine Sicherungsdatei von Schwimmkurs oder Schwimmgruppen und macht daraus einen Auszug. */
object Import {

    sealed interface Ergebnis {
        data class Kurs(val auszug: KursAuszug) : Ergebnis
        data class Gruppen(val auszug: GruppenAuszug) : Ergebnis
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Wirft [IllegalArgumentException] mit verständlicher Meldung, wenn die Datei keine passende Sicherung ist. */
    fun lesen(text: String, jetzt: Long): Ergebnis {
        val baum: JsonObject = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("Die Datei ist keine Sicherung von Schwimmkurs oder Schwimmgruppen.", e)
        }
        if ((baum["format"] as? JsonPrimitive)?.contentOrNull == LeitungDaten.FORMAT) {
            throw IllegalArgumentException("Das ist die Datei der Schwimmleitung selbst. Bitte eine Sicherung aus Schwimmkurs oder Schwimmgruppen wählen.")
        }
        return try {
            when {
                "mitglieder" in baum || "einheiten" in baum ->
                    Ergebnis.Gruppen(gruppen(json.decodeFromJsonElement(SgBestand.serializer(), baum), jetzt))
                "kinder" in baum || "kurse" in baum ->
                    Ergebnis.Kurs(kurs(json.decodeFromJsonElement(SkBestand.serializer(), baum), jetzt))
                else -> throw IllegalArgumentException("Die Datei ist keine Sicherung von Schwimmkurs oder Schwimmgruppen.")
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Die Sicherung ist beschädigt oder stammt aus einer unbekannten Version.", e)
        }
    }

    private fun kurs(b: SkBestand, jetzt: Long): KursAuszug {
        val e = b.einstellungen
        return KursAuszug(
            importiert = jetzt,
            mindestalter = e.mindestalter,
            kinder = b.kinder.map { k ->
                val (h, g) = herkunft(k, e.wohnortVorrang, e.orteAusserhalb)
                KindA(k.id, k.geburtsdatum, k.anmeldedatum, k.pausiert, k.abzeichen, h, g)
            },
            kurse = b.kurse.map { k ->
                KursA(
                    id = k.id,
                    name = k.name,
                    termine = k.termine.map { it.datum }.sorted(),
                    plaetze = k.plaetze,
                    teilnehmer = k.teilnehmer,
                    preisProKindCent = k.preisCent ?: (k.termine.size * (e.gebuehrProTerminCent + e.eintrittProTerminCent)),
                    ziel = k.ziel,
                )
            },
            geaendert = jetzt,
        )
    }

    private fun gruppen(b: SgBestand, jetzt: Long): GruppenAuszug {
        val namen = b.mitglieder.associate { it.id to "${it.vorname} ${it.nachname}".trim() }
        val geburt = b.mitglieder.associate { it.id to it.geburtsdatum }
        return GruppenAuszug(
            importiert = jetzt,
            mitglieder = b.mitglieder.map { m ->
                MitgliedA(
                    id = m.id,
                    gruppen = m.gruppen,
                    aktiv = m.aktiv,
                    wettkampf = m.interesse == "JA",
                    erfolge = m.erfolge.map { ErfolgA(it.text, it.art, it.datum) },
                    zeiten = m.zeiten.map { ZeitA(it.strecke, it.hundertstel, it.datum, it.wettkampf) },
                )
            },
            gruppen = b.gruppen.map { g ->
                GruppeA(g.id, g.name, g.art == "EXTERN", g.zeiten.map { ZeitplanA(it.wochentag, it.beginn, it.ende) }, g.plaetze, g.trainerIds)
            },
            einheiten = b.einheiten.map { e ->
                EinheitA(e.gruppeId, e.datum, e.beginn, e.anwesend.size + e.weitere, e.ausgefallen, e.trainer, e.vertretungen)
            },
            trainer = b.trainer.map { t ->
                TrainerA(
                    id = t.id,
                    name = t.mitgliedId?.let { namen[it] } ?: t.name,
                    geburtsdatum = t.mitgliedId?.let { geburt[it] } ?: t.geburtsdatum,
                    rang = RAENGE[t.rang] ?: t.rang,
                    schein = t.schein,
                    aktiv = t.aktiv,
                )
            },
            geaendert = jetzt,
        )
    }

    private val RAENGE = mapOf("TRAINER" to "Trainer", "JUNGTRAINER" to "Jungtrainer", "HELFER" to "Helfer")

    /** Nachbarorte stehen in der Vorrangliste des Schwimmkurses ganz unten; sie werden eigens gezählt. */
    val NACHBARORTE = listOf("Kutzenhausen", "Mittelneufnach", "Walkertshofen", "Rommelsried")

    private fun namen(zeile: String): List<String> = zeile.split(':', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }
    private fun n(s: String) = s.trim().lowercase()

    private fun passt(name: String, k: SkKind): Boolean {
        val e = n(name)
        return e == n(k.ort) || e == n(k.plz) || (e.endsWith("*") && e.length > 1 && n(k.plz).startsWith(e.dropLast(1)))
    }

    private fun inBemerkung(name: String, notiz: String): Boolean =
        name.length >= 3 && !name.endsWith("*") &&
            Regex("(?<![\\p{L}])" + Regex.escape(name) + "(?![\\p{L}])", RegexOption.IGNORE_CASE).containsMatchIn(notiz)

    /** Wie die Kursplanung des Schwimmkurses: Ort, PLZ oder Ortsname in der Bemerkung. */
    private fun herkunft(k: SkKind, vorrang: List<String>, ausserhalb: List<String>): Pair<Herkunft, String> {
        val listen = vorrang.map(::namen)
        var treffer = listen.indexOfFirst { l -> l.any { passt(it, k) } }
        if (treffer < 0 && k.ort.isBlank()) treffer = listen.indexOfFirst { l -> l.any { inBemerkung(it, k.notiz) } }
        if (treffer >= 0) {
            val gemeinde = listen[treffer].first()
            val nachbar = NACHBARORTE.any { n(it) == n(gemeinde) }
            return (if (nachbar) Herkunft.NACHBARORTE else Herkunft.EINZUGSGEBIET) to gemeinde
        }
        if (k.ort.isNotBlank()) return Herkunft.AUSWAERTS to k.ort.trim()
        ausserhalb.firstOrNull { passt(it, k) || inBemerkung(it.trim(), k.notiz) }?.let { return Herkunft.AUSWAERTS to it.trim() }
        return Herkunft.OHNE_ORT to ""
    }
}

// Spiegel der Sicherungsformate, nur die benötigten Felder. Unbekannte Felder werden übergangen.

@Serializable
private data class SkTermin(
    @Serializable(LocalDateIso::class) val datum: LocalDate,
)

@Serializable
private data class SkKind(
    val id: String,
    @Serializable(LocalDateIso::class) val geburtsdatum: LocalDate? = null,
    val plz: String = "",
    val ort: String = "",
    val abzeichen: Set<String> = emptySet(),
    @Serializable(LocalDateIso::class) val anmeldedatum: LocalDate? = null,
    val notiz: String = "",
    val pausiert: Boolean = false,
)

@Serializable
private data class SkKurs(
    val id: String,
    val name: String = "",
    val termine: List<SkTermin> = emptyList(),
    val plaetze: Int = 8,
    val preisCent: Long? = null,
    val ziel: String? = null,
    val teilnehmer: List<String> = emptyList(),
)

@Serializable
private data class SkEinstellungen(
    val gebuehrProTerminCent: Long = 1200,
    val eintrittProTerminCent: Long = 50,
    val mindestalter: Int = 5,
    val wohnortVorrang: List<String> = emptyList(),
    val orteAusserhalb: List<String> = emptyList(),
)

@Serializable
private data class SkBestand(
    val kinder: List<SkKind> = emptyList(),
    val kurse: List<SkKurs> = emptyList(),
    val einstellungen: SkEinstellungen = SkEinstellungen(),
)

@Serializable
private data class SgErfolg(
    val text: String = "",
    val art: String = "ABZEICHEN",
    @Serializable(LocalDateIso::class) val datum: LocalDate? = null,
)

@Serializable
private data class SgZeit(
    val strecke: String = "",
    val hundertstel: Int = 0,
    @Serializable(LocalDateIso::class) val datum: LocalDate? = null,
    val wettkampf: Boolean = false,
)

@Serializable
private data class SgMitglied(
    val id: String,
    val vorname: String = "",
    val nachname: String = "",
    @Serializable(LocalDateIso::class) val geburtsdatum: LocalDate? = null,
    val gruppen: Set<String> = emptySet(),
    val interesse: String = "OFFEN",
    val erfolge: List<SgErfolg> = emptyList(),
    val zeiten: List<SgZeit> = emptyList(),
    val aktiv: Boolean = true,
)

@Serializable
private data class SgZeitplan(
    val wochentag: Int,
    @Serializable(LocalTimeIso::class) val beginn: LocalTime,
    @Serializable(LocalTimeIso::class) val ende: LocalTime,
)

@Serializable
private data class SgGruppe(
    val id: String,
    val name: String = "",
    val art: String = "VEREIN",
    val zeiten: List<SgZeitplan> = emptyList(),
    val trainerIds: List<String> = emptyList(),
    val plaetze: Int? = null,
)

@Serializable
private data class SgEinheit(
    val gruppeId: String,
    @Serializable(LocalDateIso::class) val datum: LocalDate,
    @Serializable(LocalTimeIso::class) val beginn: LocalTime,
    val anwesend: Set<String> = emptySet(),
    val weitere: Int = 0,
    val ausgefallen: Boolean = false,
    val trainer: List<String>? = null,
    val vertretungen: Map<String, String> = emptyMap(),
)

@Serializable
private data class SgTrainer(
    val id: String,
    val mitgliedId: String? = null,
    val name: String = "",
    @Serializable(LocalDateIso::class) val geburtsdatum: LocalDate? = null,
    val rang: String = "TRAINER",
    val schein: String = "",
    val aktiv: Boolean = true,
)

@Serializable
private data class SgBestand(
    val mitglieder: List<SgMitglied> = emptyList(),
    val gruppen: List<SgGruppe> = emptyList(),
    val einheiten: List<SgEinheit> = emptyList(),
    val trainer: List<SgTrainer> = emptyList(),
)
