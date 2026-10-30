package de.dachapp.schwimmleitung.logic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Zusammenführen zweier Stände (dieses Gerät und die geteilte Datei in Google Drive). Je ID gewinnt der Eintrag mit
 * dem neueren [Eintrag.geaendert]; bei Gleichstand entscheidet ein fester Vergleich, damit alle Geräte dasselbe
 * Ergebnis haben. Wer überschrieben wurde, hat seinen Eintrag noch und schreibt ihn beim nächsten Abgleich wieder
 * hinein, solange er neuer ist.
 */
object Abgleich {

    data class Ergebnis(
        val daten: LeitungDaten,
        /** Die geteilte Datei weicht vom zusammengeführten Stand ab und muss neu geschrieben werden. */
        val schreiben: Boolean,
    )

    fun abgleich(lokal: LeitungDaten, entfernt: LeitungDaten?): Ergebnis {
        val zusammen = zusammenfuehren(lokal, entfernt ?: LeitungDaten())
        return Ergebnis(zusammen, entfernt == null || ordnen(entfernt) != zusammen)
    }

    fun zusammenfuehren(a: LeitungDaten, b: LeitungDaten): LeitungDaten = ordnen(
        LeitungDaten(
            einstellungen = liste(a.einstellungen, b.einstellungen),
            personen = liste(a.personen, b.personen),
            nachweise = liste(a.nachweise, b.nachweise),
            aufgaben = liste(a.aufgaben, b.aufgaben),
            sitzungen = liste(a.sitzungen, b.sitzungen),
            material = liste(a.material, b.material),
            meldungen = liste(a.meldungen, b.meldungen),
            belege = liste(a.belege, b.belege),
            dokumente = liste(a.dokumente, b.dokumente),
            kursAuszug = liste(a.kursAuszug, b.kursAuszug),
            gruppenAuszug = liste(a.gruppenAuszug, b.gruppenAuszug),
        ),
    )

    /** Feste Reihenfolge (nach ID); so sind gleiche Stände auch gleich. */
    fun ordnen(d: LeitungDaten): LeitungDaten = d.copy(
        format = LeitungDaten.FORMAT,
        version = LeitungDaten.AKTUELLE_VERSION,
        einstellungen = d.einstellungen.sortedBy { it.id },
        personen = d.personen.sortedBy { it.id },
        nachweise = d.nachweise.sortedBy { it.id },
        aufgaben = d.aufgaben.sortedBy { it.id },
        sitzungen = d.sitzungen.sortedBy { it.id },
        material = d.material.sortedBy { it.id },
        meldungen = d.meldungen.sortedBy { it.id },
        belege = d.belege.sortedBy { it.id },
        dokumente = d.dokumente.sortedBy { it.id },
        kursAuszug = d.kursAuszug.sortedBy { it.id },
        gruppenAuszug = d.gruppenAuszug.sortedBy { it.id },
    )

    private fun <T : Eintrag> liste(a: List<T>, b: List<T>): List<T> =
        (a + b).groupBy { it.id }.values.map { gleiche -> gleiche.reduce(::neuer) }

    private fun <T : Eintrag> neuer(x: T, y: T): T = when {
        x.geaendert != y.geaendert -> if (x.geaendert > y.geaendert) x else y
        // Gleichstand: Löschen gewinnt, sonst der Text-Vergleich (auf allen Geräten gleich).
        x.geloescht != y.geloescht -> if (x.geloescht) x else y
        else -> if (x.toString() >= y.toString()) x else y
    }
}

/** Nur auf diesem Gerät, wird nicht geteilt. */
@Serializable
data class GeraetEinstellung(
    /** Zufällig beim ersten Start; Teil neuer IDs. */
    val id: String = "",
    /** Wer an diesem Gerät meldet, z. B. "Tablet Halle" oder ein Trainername. */
    val name: String = "",
    /** content://-Adresse der geteilten Datei aus dem Dateidialog. */
    val dateiUri: String? = null,
    val intervallSekunden: Int = 60,
    /** Person, deren Aufgaben im Hallenbericht oben stehen. */
    val ichPersonId: String? = null,
)

@Serializable
data class LokalerStand(
    val geraet: GeraetEinstellung = GeraetEinstellung(),
    val daten: LeitungDaten = LeitungDaten(),
    /** Zeitpunkt des letzten erfolgreichen Abgleichs mit der Datei, 0 = noch nie. */
    val letzterAbgleich: Long = 0,
)

object LeitungCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(d: LeitungDaten): String = json.encodeToString(LeitungDaten.serializer(), d)

    /**
     * Null bei leerer Datei (frisch angelegt). Wirft [IllegalArgumentException], wenn die Datei etwas anderes ist,
     * damit sie nie aus Versehen überschrieben wird.
     */
    fun decode(text: String): LeitungDaten? {
        if (text.isBlank()) return null
        val baum = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("Die gewählte Datei ist keine Schwimmleitung-Datei oder beschädigt.", e)
        }
        // Ausdrücklich prüfen: Fehlende Felder hätten sonst Vorgabewerte und jede JSON-Datei sähe passend aus.
        require((baum["format"] as? JsonPrimitive)?.contentOrNull == LeitungDaten.FORMAT) {
            "Die gewählte Datei ist keine Schwimmleitung-Datei. Bitte eine eigene, leere Datei dafür anlegen " +
                "(z. B. eine Sicherung von Schwimmkurs oder Schwimmgruppen gehört unter „Daten übernehmen“)."
        }
        val d = try {
            json.decodeFromJsonElement(LeitungDaten.serializer(), baum)
        } catch (e: Exception) {
            throw IllegalArgumentException("Die Schwimmleitung-Datei ist beschädigt.", e)
        }
        require(d.version <= LeitungDaten.AKTUELLE_VERSION) {
            "Die Datei stammt aus einer neueren App-Version. Bitte zuerst die App auf diesem Gerät aktualisieren."
        }
        return d
    }

    fun encodeLokal(s: LokalerStand): String = json.encodeToString(LokalerStand.serializer(), s)
    fun decodeLokal(text: String): LokalerStand = json.decodeFromString(LokalerStand.serializer(), text)
}

/**
 * Leitungs-PIN. Gespeichert wird nur ein Hash mit Salz, damit die PIN auch in der geteilten Datei nicht lesbar ist.
 * Sie schützt die Bedienung, nicht die Datei selbst.
 */
object Pin {
    const val MIN_LAENGE = 4

    fun gueltig(pin: String) = pin.length >= MIN_LAENGE && pin.all { it.isDigit() }

    fun setzen(e: Einstellungen, pin: String, salz: String = neuesSalz()): Einstellungen =
        e.copy(pinSalz = salz, pinHash = hash(salz, pin))

    fun pruefen(e: Einstellungen, pin: String): Boolean = e.pinGesetzt && hash(e.pinSalz, pin) == e.pinHash

    fun hash(salz: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salz:$pin".encodeToByteArray()).joinToString("") { "%02x".format(it) }

    fun neuesSalz(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
}
