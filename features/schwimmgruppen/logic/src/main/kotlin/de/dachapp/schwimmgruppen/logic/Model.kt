package de.dachapp.schwimmgruppen.logic

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.format.TextStyle
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

@Serializable
enum class Interesse(val text: String) {
    OFFEN("noch offen"),
    NEIN("kein Interesse"),
    VIELLEICHT("vielleicht"),
    JA("ja, möchte zu Wettkämpfen"),
}

@Serializable
enum class ErfolgArt(val text: String) {
    ABZEICHEN("Abzeichen"),
    WETTKAMPF("Wettkampf"),
    SONSTIGES("Sonstiges"),
}

/** Abzeichen, Platzierung oder sonstiger Erfolg, z. B. "Bronze" oder "2. Platz 50 m Brust, Bezirksmeisterschaft". */
@Serializable
data class Erfolg(
    val id: String,
    val text: String,
    val art: ErfolgArt = ErfolgArt.ABZEICHEN,
    @Serializable(LocalDateIso::class) val datum: LocalDate? = null,
    val notiz: String = "",
)

/** Eine geschwommene Zeit über eine Strecke, z. B. 50 m Freistil in 38,52 s. */
@Serializable
data class Zeit(
    val id: String,
    val strecke: String,
    val hundertstel: Int,
    @Serializable(LocalDateIso::class) val datum: LocalDate? = null,
    /** true = im Wettkampf geschwommen, false = im Training gestoppt. */
    val wettkampf: Boolean = false,
    /** Wettkampf oder Anlass, z. B. "Bezirksmeisterschaft Augsburg". */
    val anlass: String = "",
)

@Serializable
data class Mitglied(
    val id: String,
    val vorname: String,
    val nachname: String,
    @Serializable(LocalDateIso::class) val geburtsdatum: LocalDate? = null,
    /** "w", "m", "d" oder leer. */
    val geschlecht: String = "",
    val mitgliedsnummer: String = "",
    val email: String = "",
    val telefon: String = "",
    val strasse: String = "",
    val plz: String = "",
    val ort: String = "",
    /** Eintritt als Jahr ("2021") oder Datum, wie in der Mitgliederliste ("EJ"). */
    val eintritt: String = "",
    /** Fotos erlaubt? null = unbekannt. */
    val fotorechte: Boolean? = null,
    /** Gesundheitserklärung liegt vor? null = unbekannt. */
    val gesundheitserklaerung: Boolean? = null,
    val notiz: String = "",
    /** Gruppen-IDs. Meist eine, Kader-Schwimmer z. B. zusätzlich in einer Gruppe. */
    val gruppen: Set<String> = emptySet(),
    val interesse: Interesse = Interesse.OFFEN,
    /** Z. B. "nur Heimwettkämpfe" oder "ab Sommer". */
    val interesseNotiz: String = "",
    val erfolge: List<Erfolg> = emptyList(),
    val zeiten: List<Zeit> = emptyList(),
    /** false = ausgetreten oder ruhend; bleibt mit Verlauf erhalten, erscheint aber nicht mehr in Gruppen. */
    val aktiv: Boolean = true,
) {
    val name: String get() = "$vorname $nachname".trim()
    val jahrgang: Int? get() = geburtsdatum?.year

    fun alterAm(stichtag: LocalDate): Int? = geburtsdatum?.let { Period.between(it, stichtag).years }
}

@Serializable
enum class GruppenArt(val text: String) {
    VEREIN("Vereinstraining"),
    EXTERN("Angebot für Externe"),
}

/** Fester Trainingstermin in der Woche. [wochentag] 1 = Montag … 7 = Sonntag. */
@Serializable
data class Trainingszeit(
    val wochentag: Int,
    @Serializable(LocalTimeIso::class) val beginn: LocalTime,
    @Serializable(LocalTimeIso::class) val ende: LocalTime,
) {
    val tag: DayOfWeek get() = DayOfWeek.of(wochentag)
}

@Serializable
data class Gruppe(
    val id: String,
    val name: String,
    val art: GruppenArt = GruppenArt.VEREIN,
    val zeiten: List<Trainingszeit> = emptyList(),
    /** Freier Text aus früheren Versionen; neu sind [trainerIds]. */
    val trainer: String = "",
    /** Stammtrainer der Gruppe (IDs aus [Bestand.trainer]); gelten für jede Stunde, solange dort nichts anderes steht. */
    val trainerIds: List<String> = emptyList(),
    /** Höchstzahl, nur als Hinweis bei der Einteilung. */
    val plaetze: Int? = null,
    val notiz: String = "",
    /** Anwesenheit nur als Zahl erfassen, ohne Namen abzuhaken. */
    val nurZaehlen: Boolean = false,
)

/**
 * Ein einzelnes Training an einem Tag. Wird erst gespeichert, wenn etwas erfasst wurde (Anwesenheit, Vermerk,
 * Ausfall) oder wenn es ein Zusatztermin außerhalb des Wochenplans ist.
 */
@Serializable
data class Einheit(
    val gruppeId: String,
    @Serializable(LocalDateIso::class) val datum: LocalDate,
    @Serializable(LocalTimeIso::class) val beginn: LocalTime,
    @Serializable(LocalTimeIso::class) val ende: LocalTime,
    /** IDs anwesender Mitglieder. */
    val anwesend: Set<String> = emptySet(),
    /** Teilnehmer ohne Namen: Externe, Gäste, Probeschwimmer. */
    val weitere: Int = 0,
    val ausgefallen: Boolean = false,
    /** Kalendarischer Vermerk, z. B. "Bahn 3 gesperrt" oder "Abzeichenprüfung". */
    val vermerk: String = "",
    /** Nicht aus dem Wochenplan, sondern eigens angelegt. */
    val zusatz: Boolean = false,
    /** Trainer dieser Stunde. null = Stammtrainer der Gruppe. */
    val trainer: List<String>? = null,
    /** Vertretungen in dieser Stunde: eingeteilter Trainer → Vertreter. */
    val vertretungen: Map<String, String> = emptyMap(),
) {
    val schluessel: String get() = schluessel(gruppeId, datum, beginn)
    val anzahl: Int get() = anwesend.size + weitere
    val erfasst: Boolean get() = !ausgefallen && anzahl > 0
    val leer: Boolean get() = anwesend.isEmpty() && weitere == 0 && !ausgefallen && vermerk.isBlank() && !zusatz && trainer == null && vertretungen.isEmpty()

    companion object {
        fun schluessel(gruppeId: String, datum: LocalDate, beginn: LocalTime) = "$gruppeId|$datum|$beginn"
    }
}

@Serializable
enum class TrainerRang(val text: String) {
    TRAINER("Trainer"),
    JUNGTRAINER("Jungtrainer"),
    HELFER("Helfer"),
}

/**
 * Trainer, Jungtrainer oder Helfer. Meist ein Mitglied ([mitgliedId]); Name und Geburtsdatum kommen dann von dort.
 * Ohne Mitgliedschaft stehen sie direkt hier.
 */
@Serializable
data class Trainer(
    val id: String,
    val mitgliedId: String? = null,
    val name: String = "",
    @Serializable(LocalDateIso::class) val geburtsdatum: LocalDate? = null,
    val rang: TrainerRang = TrainerRang.TRAINER,
    /** Schein oder Qualifikation, z. B. "Trainer C Schwimmen" oder "Rettungsschwimmer Silber". */
    val schein: String = "",
    val telefon: String = "",
    val aktiv: Boolean = true,
)

@Serializable
data class Einstellungen(
    val verein: String = "TSV Fischach – Abteilung Schwimmen",
    /** Beim Import nur Zeilen übernehmen, deren Abteilung diesen Text enthält. Leer = alle. */
    val abteilungFilter: String = "Schwimm",
    /** Gesundheitserklärung in Listen, Unterseite und Halle anzeigen. */
    val gesundheitAnzeigen: Boolean = true,
    val strecken: List<String> = Vorgaben.STRECKEN,
    val abzeichen: List<String> = Vorgaben.ABZEICHEN,
)

@Serializable
data class Bestand(
    val version: Int = AKTUELLE_VERSION,
    val mitglieder: List<Mitglied> = emptyList(),
    val gruppen: List<Gruppe> = Vorgaben.gruppen(),
    val einheiten: List<Einheit> = emptyList(),
    val einstellungen: Einstellungen = Einstellungen(),
    val trainer: List<Trainer> = emptyList(),
) {
    fun trainer(id: String): Trainer? = trainer.firstOrNull { it.id == id }

    fun trainerName(t: Trainer): String = t.mitgliedId?.let { mitglied(it)?.name } ?: t.name

    fun trainerGeburtsdatum(t: Trainer): LocalDate? = t.mitgliedId?.let { mitglied(it)?.geburtsdatum } ?: t.geburtsdatum

    /** Volljährig an [stichtag]? null, wenn kein Geburtsdatum bekannt ist. */
    fun volljaehrig(t: Trainer, stichtag: LocalDate): Boolean? =
        trainerGeburtsdatum(t)?.let { Period.between(it, stichtag).years >= 18 }

    /** Aktive Trainer nach Rang, dann Name. */
    fun trainerListe(): List<Trainer> =
        trainer.filter { it.aktiv }.sortedWith(compareBy<Trainer> { it.rang.ordinal }.thenBy { trainerName(it).lowercase() })

    /** Eingeteilte Trainer einer Stunde, vor Vertretungen: eigens eingetragen oder die Stammtrainer der Gruppe. */
    fun eingeteilt(e: Einheit): List<String> = e.trainer ?: gruppe(e.gruppeId)?.trainerIds.orEmpty()

    /** Wer eine Stunde tatsächlich leitet: die Eingeteilten, Vertretene ersetzt durch ihre Vertreter. */
    fun trainerVon(e: Einheit): List<Trainer> =
        eingeteilt(e).map { e.vertretungen[it] ?: it }.distinct().mapNotNull { trainer(it) }

    /** "Tom Tief (für Lisa Lang)" bzw. nur der Name, ohne Vertretung. */
    fun trainerText(e: Einheit): String = eingeteilt(e).mapNotNull { id ->
        val t = trainer(id) ?: return@mapNotNull null
        val v = e.vertretungen[id]?.let { trainer(it) }
        if (v == null) trainerName(t) else "${trainerName(v)} (für ${trainerName(t)})"
    }.joinToString(", ")

    /** Setzt den Vertreter für [trainerId] in dieser Stunde; null nimmt die Vertretung zurück. */
    fun vertreten(e: Einheit, trainerId: String, vertreterId: String?): Einheit =
        e.copy(vertretungen = if (vertreterId == null || vertreterId == trainerId) e.vertretungen - trainerId else e.vertretungen + (trainerId to vertreterId))

    /** true, wenn Trainer eingeteilt sind, aber keiner nachweislich volljährig ist. */
    fun ohneVolljaehrige(e: Einheit): Boolean {
        val t = trainerVon(e)
        return t.isNotEmpty() && t.none { volljaehrig(it, e.datum) == true }
    }

    fun trainerSetzen(t: Trainer): Bestand =
        if (trainer.any { it.id == t.id }) copy(trainer = trainer.map { if (it.id == t.id) t else it }) else copy(trainer = trainer + t)

    /** Entfernt den Trainer aus der Liste, den Gruppen und den Stunden. */
    fun trainerEntfernen(id: String): Bestand = copy(
        trainer = trainer.filterNot { it.id == id },
        gruppen = gruppen.map { if (id in it.trainerIds) it.copy(trainerIds = it.trainerIds - id) else it },
        einheiten = einheiten.map { e ->
            e.copy(
                trainer = e.trainer?.let { it - id },
                vertretungen = e.vertretungen.filter { (wer, durch) -> wer != id && durch != id },
            )
        },
    )

    /** Schaltet einen Trainer für eine Stunde an oder aus; ab dann gilt für diese Stunde die eigene Liste. */
    fun trainerUmschalten(e: Einheit, trainerId: String): Einheit {
        val jetzt = eingeteilt(e)
        return if (trainerId in jetzt) e.copy(trainer = jetzt - trainerId, vertretungen = e.vertretungen - trainerId)
        else e.copy(trainer = jetzt + trainerId)
    }

    fun mitglied(id: String): Mitglied? = mitglieder.firstOrNull { it.id == id }
    fun gruppe(id: String): Gruppe? = gruppen.firstOrNull { it.id == id }
    fun einheit(schluessel: String): Einheit? = einheiten.firstOrNull { it.schluessel == schluessel }

    /** Aktive Mitglieder einer Gruppe, nach Nachname. */
    fun mitgliederIn(gruppeId: String): List<Mitglied> =
        mitglieder.filter { it.aktiv && gruppeId in it.gruppen }.sortedWith(NACH_NAME)

    fun ohneGruppe(): List<Mitglied> =
        mitglieder.filter { m -> m.aktiv && m.gruppen.none { g -> gruppe(g) != null } }.sortedWith(NACH_NAME)

    fun mitgliedSetzen(m: Mitglied): Bestand =
        if (mitglieder.any { it.id == m.id }) copy(mitglieder = mitglieder.map { if (it.id == m.id) m else it })
        else copy(mitglieder = mitglieder + m)

    /** Entfernt das Mitglied. Seine bisherigen Anwesenheiten zählen als namenlose Teilnehmer weiter. */
    fun mitgliedEntfernen(id: String): Bestand = copy(
        mitglieder = mitglieder.filterNot { it.id == id },
        // Trainer bleiben, nur ohne Verknüpfung; Name und Geburtsdatum werden übernommen.
        trainer = trainer.map { t ->
            if (t.mitgliedId != id) t else t.copy(mitgliedId = null, name = trainerName(t), geburtsdatum = trainerGeburtsdatum(t))
        },
        einheiten = einheiten.map { e -> if (id in e.anwesend) e.copy(anwesend = e.anwesend - id, weitere = e.weitere + 1) else e },
    )

    /** Setzt oder entfernt ein Mitglied in einer Gruppe. */
    fun einteilen(mitgliedId: String, gruppeId: String, drin: Boolean): Bestand {
        val m = mitglied(mitgliedId) ?: return this
        return mitgliedSetzen(m.copy(gruppen = if (drin) m.gruppen + gruppeId else m.gruppen - gruppeId))
    }

    /** Verschiebt ein Mitglied von [von] (oder ohne Gruppe) nach [nach]; andere Gruppen bleiben. */
    fun verschieben(mitgliedId: String, von: String?, nach: String?): Bestand {
        val m = mitglied(mitgliedId) ?: return this
        var g = m.gruppen
        if (von != null) g = g - von
        if (nach != null) g = g + nach
        return mitgliedSetzen(m.copy(gruppen = g))
    }

    fun gruppeSetzen(g: Gruppe): Bestand =
        if (gruppen.any { it.id == g.id }) copy(gruppen = gruppen.map { if (it.id == g.id) g else it })
        else copy(gruppen = gruppen + g)

    /** Entfernt die Gruppe samt ihren erfassten Trainings; Mitglieder bleiben, nur ohne diese Gruppe. */
    fun gruppeEntfernen(id: String): Bestand = copy(
        gruppen = gruppen.filterNot { it.id == id },
        mitglieder = mitglieder.map { if (id in it.gruppen) it.copy(gruppen = it.gruppen - id) else it },
        einheiten = einheiten.filterNot { it.gruppeId == id },
    )

    /**
     * Teilt eine Gruppe mit mehreren Trainingszeiten in eigene Gruppen je Trainingstag, z. B. „Gruppe I“ in
     * „Gruppe I Mittwoch“ und „Gruppe I Samstag“. Die erste behält die ID. Eingeteilte Schwimmer kommen in alle
     * neuen Gruppen, erfasste Trainings zur Gruppe mit passendem Tag.
     */
    fun gruppeAufteilen(id: String): Bestand {
        val g = gruppe(id) ?: return this
        val zeiten = g.zeiten.sortedWith(compareBy({ it.wochentag }, { it.beginn }))
        if (zeiten.size < 2) return this
        val doppelt = zeiten.groupBy { it.wochentag }.filterValues { it.size > 1 }.keys
        val teile = zeiten.mapIndexed { i, z ->
            val mitUhr = z.wochentag in doppelt
            val tag = z.tag.getDisplayName(TextStyle.FULL, Locale.GERMAN)
            g.copy(
                id = when {
                    i == 0 -> g.id
                    mitUhr -> "${g.id}-${z.wochentag}-${z.beginn.hour}${z.beginn.minute}"
                    else -> "${g.id}-${z.wochentag}"
                },
                name = "${g.name} $tag" + if (mitUhr) " ${z.beginn}" else "",
                zeiten = listOf(z),
            )
        }
        val ids = teile.map { it.id }.toSet()
        fun ziel(e: Einheit): String =
            (teile.firstOrNull { t -> t.zeiten[0].wochentag == e.datum.dayOfWeek.value && t.zeiten[0].beginn == e.beginn }
                ?: teile.firstOrNull { t -> t.zeiten[0].wochentag == e.datum.dayOfWeek.value }
                ?: teile.first()).id
        return copy(
            gruppen = gruppen.flatMap { if (it.id == id) teile else listOf(it) },
            mitglieder = mitglieder.map { if (id in it.gruppen) it.copy(gruppen = it.gruppen + ids) else it },
            einheiten = einheiten.map { if (it.gruppeId == id) it.copy(gruppeId = ziel(it)) else it },
        )
    }

    /** Speichert eine Einheit. Eine Einheit aus dem Wochenplan ohne Eintrag wird nicht aufgehoben. */
    fun einheitSetzen(e: Einheit): Bestand {
        val rest = einheiten.filterNot { it.schluessel == e.schluessel }
        return copy(einheiten = if (e.leer) rest else rest + e)
    }

    fun einheitEntfernen(schluessel: String): Bestand = copy(einheiten = einheiten.filterNot { it.schluessel == schluessel })

    companion object {
        const val AKTUELLE_VERSION = 2
        val NACH_NAME: Comparator<Mitglied> = compareBy({ it.nachname.lowercase() }, { it.vorname.lowercase() })
    }
}
