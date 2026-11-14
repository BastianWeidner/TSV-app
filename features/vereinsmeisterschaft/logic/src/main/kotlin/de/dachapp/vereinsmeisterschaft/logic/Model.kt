package de.dachapp.vereinsmeisterschaft.logic

import kotlinx.serialization.Serializable

@Serializable
enum class Geschlecht(val kurz: String, val text: String, val gruppe: String) {
    W("w", "weiblich", "weiblich"),
    M("m", "männlich", "männlich"),
}

@Serializable
enum class Lage(val text: String, val kurz: String) {
    BRUST("Brust", "Br"),
    RUECKEN("Rücken", "Rü"),
    KRAUL("Kraul", "Kr"),
    DELPHIN("Delphin", "De"),
}

/** Eine Strecke: Lage und Anzahl Bahnen (in Fischach sind 3 Bahnen = 50 m). */
@Serializable
data class Strecke(val lage: Lage, val bahnen: Int) {
    val text: String get() = "${bahnenText(bahnen)} ${lage.text}"
}

fun bahnenText(bahnen: Int) = if (bahnen == 1) "1 Bahn" else "$bahnen Bahnen"

/**
 * Eine Stufe der Ausschreibung, z. B. „2 Bahnen“ für die Jahrgänge 2018/2017. Die Grenzen sind Jahrgänge, nicht Alter,
 * und ändern sich jedes Jahr. [abJahrgang] ist der älteste (kleinste) Jahrgang, [bisJahrgang] der jüngste; null = offen.
 */
@Serializable
data class Stufe(
    val id: String,
    val name: String,
    val abJahrgang: Int? = null,
    val bisJahrgang: Int? = null,
    val strecken: List<Strecke> = emptyList(),
) {
    fun passt(jahrgang: Int): Boolean =
        (abJahrgang == null || jahrgang >= abJahrgang) && (bisJahrgang == null || jahrgang <= bisJahrgang)

    fun strecke(lage: Lage): Strecke? = strecken.firstOrNull { it.lage == lage }

    /** z. B. „Jahrgang 2018–2017“ oder „Jahrgang 2008 und älter“. */
    val jahrgaenge: String
        get() = when {
            abJahrgang == null && bisJahrgang == null -> "alle Jahrgänge"
            abJahrgang == null -> "Jahrgang $bisJahrgang und älter"
            bisJahrgang == null -> "Jahrgang $abJahrgang und jünger"
            abJahrgang == bisJahrgang -> "Jahrgang $abJahrgang"
            else -> "Jahrgang $bisJahrgang–$abJahrgang"
        }
}

/** Ein Pokal (Gesamtwertung, „Meister“) über eine oder mehrere Stufen, immer weiblich und männlich getrennt. */
@Serializable
data class Pokal(val name: String, val stufen: List<String>)

/** Jahrgänge, die in der Jahrgangswertung zusammen gewertet werden; [geschlecht] null = für beide. */
@Serializable
data class Kombi(val von: Int, val bis: Int, val geschlecht: Geschlecht? = null) {
    val aelter: Int get() = minOf(von, bis)
    val juenger: Int get() = maxOf(von, bis)
    fun passt(jahrgang: Int, g: Geschlecht?): Boolean =
        jahrgang in aelter..juenger && (geschlecht == null || geschlecht == g)
}

/** Wie die Pokale und die Jahrgangs-Gesamtwertung rechnen. */
@Serializable
enum class Wertungsart(val text: String) {
    /**
     * Zeit = Punkte (Bastian 2026-11-14): Summe der auf 3 Bahnen hochgerechneten Sekunden, kleinste gewinnt; nur wer alle
     * Strecken gültig geschwommen hat.
     */
    ZEITSUMME("Zeit = Punkte (hochgerechnete Sekunden, kleinste Summe gewinnt)"),

    /** Alternative: 1000 × (Basiszeit ÷ Zeit auf 3 Bahnen)³ je Strecke, höchste Summe gewinnt. */
    PUNKTE("Punkte-Formel 1000 × (Basiszeit ÷ Zeit)³, höchste Summe gewinnt"),
}

@Serializable
enum class Medaillen(val text: String) {
    GESAMT("je Jahrgang über alle Lagen"),
    JE_LAGE("je Jahrgang und Lage"),
}

@Serializable
enum class LaufOrdnung(val text: String) {
    ALTER("nach Jahrgang (jüngste zuerst), dann Meldezeit"),
    ZEIT("nach Meldezeit (langsamste zuerst), ohne Zeit nach Jahrgang"),
}

@Serializable
enum class Folge(val text: String) {
    NACH_LAGE("erst alle Brust, dann Rücken …"),
    NACH_STRECKE("erst kurze Strecken (Kinder können früher gehen)"),
}

/** Basiszeit für die Punkte: 50 m (3 Bahnen) je Lage und Geschlecht, in Hundertstelsekunden. */
@Serializable
data class Basiszeit(val lage: Lage, val geschlecht: Geschlecht, val hundertstel: Int)

@Serializable
data class Regeln(
    val stufen: List<Stufe> = emptyList(),
    val pokale: List<Pokal> = emptyList(),
    val kombis: List<Kombi> = emptyList(),
    /** Auf so viele Bahnen werden Zeiten für Punkte und Pokale hochgerechnet (3 Bahnen = 50 m). */
    val normBahnen: Int = 3,
    val bahnLaenge: Double = 50.0 / 3,
    val wertung: Wertungsart = Wertungsart.ZEITSUMME,
    val basiszeiten: List<Basiszeit> = Vorgaben.basiszeiten,
    val medaillen: Medaillen = Medaillen.GESAMT,
    val medaillenPlaetze: Int = 3,
    /** Punkteurkunde für die Plätze 1 bis … jedes Pokals (Meister = 1). */
    val punkteurkundeBis: Int = 1,
    val bahnenImBecken: Int = 4,
    val laufOrdnung: LaufOrdnung = LaufOrdnung.ALTER,
    val folge: Folge = Folge.NACH_LAGE,
    val lagenFolge: List<Lage> = listOf(Lage.BRUST, Lage.RUECKEN, Lage.KRAUL, Lage.DELPHIN),
    /** Läufe nach Geschlecht trennen (Bastian 2026-11-14: nein, Läufe gemischt). */
    val laeufeNachGeschlecht: Boolean = false,
) {
    fun stufe(id: String?): Stufe? = stufen.firstOrNull { it.id == id }
    fun basis(lage: Lage, g: Geschlecht): Int? = basiszeiten.firstOrNull { it.lage == lage && it.geschlecht == g }?.hundertstel
}

@Serializable
enum class Status(val text: String, val kurz: String) {
    OK("gewertet", ""),
    NA("nicht angetreten", "n. a."),
    DSQ("disqualifiziert", "disq."),
    AUFG("aufgegeben", "aufg."),
}

@Serializable
data class Teilnehmer(
    val id: Long,
    val vorname: String,
    val nachname: String,
    val jahrgang: Int,
    val geschlecht: Geschlecht? = null,
    /** Von Hand gewählte Stufe; null = nach Jahrgang. */
    val stufeId: String? = null,
    /** Lagen, die der Schwimmer nicht schwimmt (abgemeldet). */
    val ohne: Set<Lage> = emptySet(),
    val meldezeiten: Map<Lage, Int> = emptyMap(),
    val gruppe: String = "",
    val ausserKonkurrenz: Boolean = false,
    val notiz: String = "",
    /** Aus den Schwimmgruppen übernommen: deren Mitglieds-ID und DSV-ID, für den Rückweg der Zeiten. */
    val mitgliedId: String = "",
    val dsvId: String = "",
) {
    val name: String get() = "$vorname $nachname".trim()
}

@Serializable
data class Ergebnis(
    val teilnehmer: Long,
    val lage: Lage,
    val zeit: Int? = null,
    val status: Status = Status.OK,
) {
    /** Wertbar: Zeit da und nicht disqualifiziert o. Ä. */
    val gueltig: Boolean get() = status == Status.OK && zeit != null && zeit > 0
}

@Serializable
data class Belegung(val bahn: Int, val teilnehmer: Long)

@Serializable
data class Lauf(val nr: Int, val strecke: Strecke, val belegung: List<Belegung>) {
    fun auf(bahn: Int): Long? = belegung.firstOrNull { it.bahn == bahn }?.teilnehmer
}

/** Familienstaffel: nur zum Spaß, jedes Team bekommt eine Urkunde. */
@Serializable
data class Staffel(val id: Long, val name: String, val mitglieder: String = "")

@Serializable
data class Veranstaltung(
    val id: Long,
    val titel: String,
    val jahr: Int,
    /** ISO-Datum yyyy-MM-dd oder leer. */
    val datum: String = "",
    val ort: String = "",
    val verein: String = "TSV Fischach · Abteilung Schwimmen",
    val unterschrift: String = "Sportlicher Leiter",
    /** Vereinskennzahl (DSV), nur für die Ergebnisdatei; leer = 0. */
    val vereinsnummer: String = "",
    val regeln: Regeln = Regeln(),
    val teilnehmer: List<Teilnehmer> = emptyList(),
    val ergebnisse: List<Ergebnis> = emptyList(),
    val laeufe: List<Lauf> = emptyList(),
    val staffeln: List<Staffel> = emptyList(),
    val naechsteId: Long = 1,
) {
    fun person(id: Long): Teilnehmer? = teilnehmer.firstOrNull { it.id == id }

    /** Stufe eines Schwimmers: von Hand gewählt, sonst nach Jahrgang. */
    fun stufe(t: Teilnehmer): Stufe? = regeln.stufe(t.stufeId) ?: regeln.stufen.firstOrNull { it.passt(t.jahrgang) }

    /** Strecken, die ein Schwimmer schwimmt. */
    fun starts(t: Teilnehmer): List<Strecke> = stufe(t)?.strecken.orEmpty().filter { it.lage !in t.ohne }

    fun ergebnis(teilnehmer: Long, lage: Lage): Ergebnis? =
        ergebnisse.firstOrNull { it.teilnehmer == teilnehmer && it.lage == lage }

    /** Ergebnis setzen oder ersetzen; null löscht. */
    fun mitErgebnis(teilnehmer: Long, lage: Lage, neu: Ergebnis?): Veranstaltung =
        copy(ergebnisse = ergebnisse.filterNot { it.teilnehmer == teilnehmer && it.lage == lage } + listOfNotNull(neu))

    fun lauf(teilnehmer: Long, lage: Lage): Pair<Lauf, Int>? {
        for (l in laeufe) if (l.strecke.lage == lage) l.belegung.firstOrNull { it.teilnehmer == teilnehmer }?.let { return l to it.bahn }
        return null
    }

    /** Anzahl Starts, Anzahl erfasster Ergebnisse. */
    fun fortschritt(): Pair<Int, Int> {
        var starts = 0
        var erfasst = 0
        teilnehmer.forEach { t ->
            starts(t).forEach { s -> starts++; if (ergebnis(t.id, s.lage) != null) erfasst++ }
        }
        return starts to erfasst
    }
}

@Serializable
data class Daten(
    val veranstaltungen: List<Veranstaltung> = emptyList(),
    val aktiv: Long? = null,
    /** Vereinslogo für Urkunden als PNG/JPEG in Base64, optional. */
    val logo: String? = null,
    val naechsteId: Long = 1,
) {
    val aktuelle: Veranstaltung? get() = veranstaltungen.firstOrNull { it.id == aktiv } ?: veranstaltungen.lastOrNull()
}
