package de.dachapp.abgleich

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Ein Eintrag, der zwischen Geräten abgeglichen wird: feste [id], Zeitpunkt der letzten Änderung, Löschmarke. */
interface Abgleichbar {
    val id: String
    /** Millisekunden seit 1970, auf dem ändernden Gerät gesetzt. */
    val geaendert: Long
    /** Gelöschte Einträge bleiben als Marke stehen, damit andere Geräte sie nicht wieder hineinschreiben. */
    val geloescht: Boolean
}

/**
 * Zusammenführen nach dem Café-Verfahren: Je ID gewinnt der neuere Eintrag; bei Gleichstand gewinnt Löschen, sonst
 * ein fester Textvergleich, damit alle Geräte dasselbe Ergebnis haben. Wer überschrieben wurde, hat seinen Eintrag
 * noch bei sich und schreibt ihn beim nächsten Abgleich wieder hinein; so geht nichts verloren.
 */
object Zusammenfuehren {
    /** Beide Listen vereint, je ID der gewinnende Eintrag, nach ID geordnet. */
    fun <T : Abgleichbar> liste(a: List<T>, b: List<T>): List<T> =
        (a + b).groupBy { it.id }.values.map { gleiche -> gleiche.reduce(::neuer) }.sortedBy { it.id }

    fun <T : Abgleichbar> neuer(x: T, y: T): T = when {
        x.geaendert != y.geaendert -> if (x.geaendert > y.geaendert) x else y
        x.geloescht != y.geloescht -> if (x.geloescht) x else y
        else -> if (x.toString() >= y.toString()) x else y
    }
}

/**
 * Abgleich für Unter-Apps, deren Daten keine Änderungszeiten tragen (z. B. Schwimmgruppen). Die Unter-App zerlegt
 * ihren Stand in Arten („mitglied“, „einheit“ …) mit je ID und Inhalt als JSON. Der Abgleich merkt sich den zuletzt
 * gemeinsamen Stand ([Stand]) und erkennt daran, was auf diesem Gerät seitdem neu, geändert oder gelöscht wurde.
 */
object Abgleich {
    const val FORMAT = "dach-abgleich"
    const val VERSION = 1

    /** Löschmarken nach einem halben Jahr vergessen; bis dahin hat sich jedes benutzte Gerät abgeglichen. */
    const val MARKE_AUFBEWAHREN_MS = 183L * 24 * 60 * 60 * 1000

    @Serializable
    data class Eintrag(
        override val id: String,
        override val geaendert: Long,
        override val geloescht: Boolean = false,
        /** Inhalt; null bei Löschmarken. */
        val wert: JsonElement? = null,
    ) : Abgleichbar

    @Serializable
    data class Stand(
        val format: String = FORMAT,
        val version: Int = VERSION,
        /** Welche Unter-App die Datei gehört, z. B. „schwimmgruppen“. Schützt vor der falschen Datei. */
        val app: String = "",
        /** Art → Einträge, nach ID geordnet. */
        val arten: Map<String, List<Eintrag>> = emptyMap(),
    ) {
        val leer: Boolean get() = arten.values.all { l -> l.none { !it.geloescht } }

        /** Die lebenden Inhalte: Art → ID → Inhalt. */
        fun werte(): Map<String, Map<String, JsonElement>> =
            arten.mapValues { (_, l) -> l.filter { !it.geloescht && it.wert != null }.associate { it.id to it.wert!! } }.filterValues { it.isNotEmpty() }
    }

    /**
     * Stand dieses Geräts: Was gleich geblieben ist, behält seinen Zeitpunkt aus [vorher]; Neues und Geändertes
     * bekommt [jetzt]; was fehlt, wird zur Löschmarke. [vorher] ist der zuletzt gemeinsame Stand (leer beim ersten Mal).
     * [alt] = true stempelt Neues mit 1 statt [jetzt], damit ein neu verbundenes Gerät die geteilte Datei nicht mit
     * seinen eigenen Vorgaben überschreibt (eigene Einträge kommen trotzdem hinzu).
     */
    fun lokal(app: String, aktuell: Map<String, Map<String, JsonElement>>, vorher: Stand, jetzt: Long, alt: Boolean = false): Stand {
        val arten = (aktuell.keys + vorher.arten.keys).associateWith { art ->
            val werte = aktuell[art].orEmpty()
            val frueher = vorher.arten[art].orEmpty().associateBy { it.id }
            val lebend = werte.map { (id, w) ->
                val f = frueher[id]
                if (f != null && !f.geloescht && f.wert == w) f else Eintrag(id, if (alt) 1 else jetzt, false, w)
            }
            val weg = frueher.values.filter { it.id !in werte }.map { f ->
                if (f.geloescht) f else Eintrag(f.id, jetzt, true, null)
            }
            (lebend + weg).filter { !it.geloescht || jetzt - it.geaendert <= MARKE_AUFBEWAHREN_MS }.sortedBy { it.id }
        }
        return Stand(app = app, arten = arten.filterValues { it.isNotEmpty() })
    }

    /** Zwei Stände zusammenführen (je ID gewinnt der neuere Eintrag). */
    fun zusammen(a: Stand, b: Stand): Stand {
        val arten = (a.arten.keys + b.arten.keys).associateWith { art ->
            Zusammenfuehren.liste(a.arten[art].orEmpty(), b.arten[art].orEmpty())
        }
        return Stand(app = a.app.ifEmpty { b.app }, arten = arten.filterValues { it.isNotEmpty() })
    }

    data class Ergebnis(
        /** Neuer gemeinsamer Stand; als [vorher] für den nächsten Abgleich aufheben. */
        val stand: Stand,
        /** Die geteilte Datei weicht ab und muss neu geschrieben werden. */
        val schreiben: Boolean,
        /** Auf diesem Gerät ändert sich etwas (andere Geräte haben etwas beigetragen). */
        val uebernehmen: Boolean,
    )

    /**
     * Ein Abgleich: [aktuell] ist der Stand der Unter-App, [vorher] der zuletzt gemeinsame Stand, [entfernt] der Inhalt
     * der geteilten Datei (null = leer, frisch angelegt). [nurVorgaben] = true heißt: Auf diesem Gerät steht noch nichts
     * Eigenes; beim ersten Abgleich mit einer gefüllten Datei wird dann nur die Datei übernommen.
     */
    fun abgleich(
        app: String,
        aktuell: Map<String, Map<String, JsonElement>>,
        vorher: Stand,
        entfernt: Stand?,
        jetzt: Long,
        nurVorgaben: Boolean = false,
    ): Ergebnis {
        // Erstes Mal auf diesem Gerät und in der Datei steht schon etwas: die Datei hat Vorrang.
        val neuVerbunden = vorher.arten.isEmpty() && entfernt != null && !entfernt.leer
        val lokal = lokal(app, if (neuVerbunden && nurVorgaben) emptyMap() else aktuell, vorher, jetzt, alt = neuVerbunden)
        val zusammen = zusammen(lokal, entfernt ?: Stand(app = app)).let { z ->
            z.copy(arten = z.arten.mapValues { (_, l) -> l.filter { !it.geloescht || jetzt - it.geaendert <= MARKE_AUFBEWAHREN_MS } }.filterValues { it.isNotEmpty() })
        }
        return Ergebnis(
            stand = zusammen,
            schreiben = entfernt == null || entfernt.arten != zusammen.arten,
            uebernehmen = zusammen.werte() != aktuell.filterValues { it.isNotEmpty() },
        )
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(s: Stand): String = json.encodeToString(Stand.serializer(), s)

    /**
     * Inhalt der geteilten Datei; null bei leerer Datei (frisch angelegt). Wirft [IllegalArgumentException], wenn die
     * Datei zu etwas anderem gehört, damit sie nie aus Versehen überschrieben wird.
     */
    fun decode(text: String, app: String): Stand? {
        if (text.isBlank()) return null
        val baum = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("Die gewählte Datei ist keine Abgleich-Datei oder beschädigt.", e)
        }
        require((baum["format"] as? JsonPrimitive)?.contentOrNull == FORMAT && (baum["app"] as? JsonPrimitive)?.contentOrNull == app) {
            "Die gewählte Datei gehört nicht hierher. Bitte eine eigene, leere Datei dafür anlegen."
        }
        val s = try {
            json.decodeFromJsonElement(Stand.serializer(), baum)
        } catch (e: Exception) {
            throw IllegalArgumentException("Die Abgleich-Datei ist beschädigt.", e)
        }
        require(s.version <= VERSION) { "Die Datei stammt aus einer neueren App-Version. Bitte zuerst die App aktualisieren." }
        return s
    }
}
