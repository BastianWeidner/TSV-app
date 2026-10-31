package de.dachapp.schwimmgruppen.logic

import java.time.LocalDate
import java.time.LocalTime

/** Ein Wettkampf (Strecke) einer Veranstaltung. [technik]: F, R, B, S, L oder X; [geschlecht]: M, W, D oder X. */
data class WkDef(
    val nr: Int,
    val art: String,
    val abschnitt: Int,
    val strecke: Int,
    val technik: String,
    val ausuebung: String = "GL",
    val geschlecht: String,
    val starter: Int = 1,
    val bestenliste: String = "SW",
    val qualiNr: String = "",
    val qualiArt: String = "",
) {
    val staffel: Boolean get() = starter > 1

    /** Text wie in den Strecken der App: "50 m Freistil"; Staffeln "4×50 m Freistil". */
    val streckenText: String
        get() {
            val lage = TECHNIK[technik] ?: "Sonderform"
            val zusatz = when (ausuebung) { "GL", "" -> ""; "BE" -> " Beine"; "AR" -> " Arme"; else -> " ($ausuebung)" }
            return (if (staffel) "$starter×" else "") + "$strecke m $lage$zusatz"
        }

    companion object {
        val TECHNIK = mapOf("F" to "Freistil", "R" to "Rücken", "B" to "Brust", "S" to "Schmetterling", "L" to "Lagen")
    }
}

/** Wertung (Jahrgangs- oder Altersklasse) eines Wettkampfs. Jahrgänge: [von] ältester, [bis] jüngster, 0 = offen. */
data class WertungDef(val wk: Int, val art: String, val id: Int, val typ: String, val von: String, val bis: String, val geschlecht: String, val name: String)

data class Pflichtzeit(val wk: Int, val art: String, val typ: String, val von: String, val bis: String, val hundertstel: Int, val geschlecht: String)

data class Abschnitt(val nr: Int, val datum: LocalDate?, val beginn: LocalTime?)

/** Was aus einer Ausschreibung (Wettkampfdefinitionsliste oder LENEX-Einladung) gebraucht wird. */
data class Ausschreibung(
    val version: Int,
    val veranstaltung: String,
    val ort: String,
    val bahn: String,
    val zeitmessung: String,
    val meldeschluss: String = "",
    val meldeadresse: String = "",
    val abschnitte: List<Abschnitt> = emptyList(),
    val wettkaempfe: List<WkDef> = emptyList(),
    val wertungen: List<WertungDef> = emptyList(),
    val pflichtzeiten: List<Pflichtzeit> = emptyList(),
    /** Meldegeld je Einzelstart in Cent, falls angegeben. */
    val einzelMeldegeldCent: Long? = null,
) {
    val letzterTag: LocalDate? get() = abschnitte.mapNotNull { it.datum }.maxOrNull()

    /** Einzelwettkämpfe, für die ein Schwimmer nach Geschlecht und Jahrgang in Frage kommt (nur Vorlauf/Entscheidung). */
    fun passendFuer(geschlecht: String, jahrgang: Int?): List<WkDef> = wettkaempfe.filter { w ->
        !w.staffel && w.art in setOf("V", "E") && (w.geschlecht == "X" || w.geschlecht == geschlecht) &&
            (jahrgang == null || wertungen.none { it.wk == w.nr && it.art == w.art } ||
                wertungen.any { it.wk == w.nr && it.art == w.art && it.typ == "JG" && jgPasst(it.von, it.bis, jahrgang) } ||
                wertungen.any { it.wk == w.nr && it.art == w.art && it.typ != "JG" })
    }

    /** Pflichtzeit für den Jahrgang, falls verlangt. */
    fun pflichtzeit(w: WkDef, geschlecht: String, jahrgang: Int?): Int? = pflichtzeiten.firstOrNull { p ->
        p.wk == w.nr && p.art == w.art && (p.geschlecht.isEmpty() || p.geschlecht == geschlecht) &&
            (p.typ != "JG" || jahrgang == null || jgPasst(p.von, p.bis, jahrgang))
    }?.hundertstel

    companion object {
        /** von = ältester Jahrgang (0 = offen), bis = jüngster (leer = wie von, 0 bzw. 9999 = und jünger). */
        fun jgPasst(von: String, bis: String, jahrgang: Int): Boolean {
            val a = von.trim().toIntOrNull() ?: return true
            val b = bis.trim().ifEmpty { von.trim() }.toIntOrNull() ?: a
            val unten = if (a == 0) Int.MIN_VALUE else a
            val oben = if (b == 0 || b == 9999) Int.MAX_VALUE else b
            return jahrgang in minOf(unten, oben)..maxOf(unten, oben)
        }
    }
}

/** Ein Ergebnis aus einer Ergebnisliste (DSV oder LENEX). [platz] 0 = nicht gewertet, [grund] z. B. "DS". */
data class Ergebnis(
    val name: String,
    val vorname: String,
    val nachname: String,
    val dsvId: String,
    val jahrgang: Int?,
    val geschlecht: String,
    val verein: String,
    val vereinsnummer: String,
    val strecke: String,
    val hundertstel: Int,
    val platz: Int,
    val wertung: String,
    val grund: String,
    val datum: LocalDate?,
)

data class Ergebnisliste(val veranstaltung: String, val ort: String, val bahn: String, val ergebnisse: List<Ergebnis>)

/** Meldung eines Schwimmers: Wettkampfnummer → Meldezeit in Hundertstel (0 = ohne Zeit). */
data class Meldung(val mitgliedId: String, val starts: Map<Int, Int>)

/** Vereinsangaben für die Meldedatei. */
data class Meldender(
    val verein: String,
    val vereinsnummer: String,
    val lsv: Int,
    val name: String,
    val email: String,
    val telefon: String = "",
)

/** Zuordnung eines Ergebnisses zu einem Mitglied. */
data class ErgebnisTreffer(val ergebnis: Ergebnis, val mitgliedId: String?, val schonDa: Boolean)

object Wettkampfabgleich {

    /** Ergebnisse des eigenen Vereins (über Vereinsnummer, sonst Namensteil) den Mitgliedern zuordnen. */
    fun zuordnen(liste: Ergebnisliste, b: Bestand, vereinsnummer: String, vereinsname: String): List<ErgebnisTreffer> {
        val nummer = vereinsnummer.trim().trimStart('0')
        val namensteil = norm(vereinsname).split(' ').filter { it.length >= 4 && it !in setOf("abteilung", "schwimmen", "verein") }
        val eigene = liste.ergebnisse.filter { e ->
            (nummer.isNotEmpty() && e.vereinsnummer.trim().trimStart('0') == nummer) ||
                (namensteil.isNotEmpty() && namensteil.any { norm(e.verein).contains(it) })
        }
        // Je Schwimmer und Strecke nur die beste Platzierung (mehrere Wertungen je Start).
        val jeStart = eigene.groupBy { Triple(it.name.lowercase(), it.strecke, it.hundertstel) }
            .map { (_, l) -> l.filter { it.platz > 0 }.minByOrNull { it.platz } ?: l.first() }
        return jeStart.map { e ->
            val m = b.mitglieder.firstOrNull { it.dsvId.isNotBlank() && it.dsvId.trimStart('0') == e.dsvId.trimStart('0') && e.dsvId.trimStart('0').isNotEmpty() }
                ?: b.mitglieder.firstOrNull { norm(it.nachname) == norm(e.nachname) && norm(it.vorname) == norm(e.vorname) && (e.jahrgang == null || it.jahrgang == null || it.jahrgang == e.jahrgang) }
            val schonDa = m?.zeiten?.any { it.strecke == e.strecke && it.hundertstel == e.hundertstel && it.wettkampf && it.datum == e.datum } == true
            ErgebnisTreffer(e, m?.id, schonDa)
        }.sortedWith(compareBy({ it.mitgliedId == null }, { it.ergebnis.nachname }, { it.ergebnis.strecke }))
    }

    /** Übernimmt die Zeiten (gültige Ergebnisse) und Platz 1–3 als Erfolg; ergänzt fehlende DSV-IDs. */
    fun uebernehmen(b: Bestand, treffer: List<ErgebnisTreffer>, liste: Ergebnisliste, neueId: () -> String): Bestand {
        var neu = b
        for (t in treffer) {
            val id = t.mitgliedId ?: continue
            if (t.schonDa) continue
            val m = neu.mitglied(id) ?: continue
            val e = t.ergebnis
            var mm = m
            if (mm.dsvId.isBlank() && e.dsvId.trimStart('0').isNotEmpty()) mm = mm.copy(dsvId = e.dsvId)
            if (e.grund.isEmpty() && e.hundertstel > 0) {
                mm = mm.copy(zeiten = mm.zeiten + Zeit(neueId(), e.strecke, e.hundertstel, e.datum, wettkampf = true, anlass = anlass(liste)))
            }
            if (e.grund.isEmpty() && e.platz in 1..3) {
                val text = "${e.platz}. Platz ${e.strecke}" + if (e.wertung.isNotBlank()) " (${e.wertung})" else ""
                mm = mm.copy(erfolge = mm.erfolge + Erfolg(neueId(), text, ErfolgArt.WETTKAMPF, e.datum, anlass(liste)))
            }
            neu = neu.mitgliedSetzen(mm)
        }
        return neu
    }

    fun anlass(l: Ergebnisliste): String = l.veranstaltung + (if (l.bahn.isNotBlank() && l.bahn != "X") " (${l.bahn} m)" else "")

    fun norm(s: String) = s.lowercase().replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
}

/** Inhalt einer gewählten Wettkampfdatei: Ausschreibung oder Ergebnisse, DSV 7/8 oder LENEX. */
sealed interface WettkampfDatei {
    data class Einladung(val ausschreibung: Ausschreibung) : WettkampfDatei
    data class Ergebnisse(val liste: Ergebnisliste) : WettkampfDatei

    companion object {
        fun lesen(bytes: ByteArray): WettkampfDatei {
            if (Lenex.istLenex(bytes)) {
                val l = Lenex.ergebnisse(bytes)
                return if (l.ergebnisse.isNotEmpty()) Ergebnisse(l) else Einladung(Lenex.ausschreibung(bytes))
            }
            val text = Dsv.text(bytes)
            if (!Dsv.istDsv(text)) throw WettkampfFehler("Weder DSV-Datei (.DSV7, .DSV8) noch LENEX (.lef, .lxf).")
            val art = Dsv.elemente(text).firstOrNull { it.name == "FORMAT" }?.get(0)?.lowercase()
            return if (art == "wettkampfdefinitionsliste") Einladung(Dsv.ausschreibung(text)) else Ergebnisse(Dsv.ergebnisse(text))
        }

        /** Meldezeit = beste gespeicherte Zeit über die Strecke (Training oder Wettkampf), 0 = ohne Zeit. */
        fun meldezeit(m: Mitglied, w: WkDef): Int = m.zeiten.filter { it.strecke == w.streckenText }.minOfOrNull { it.hundertstel } ?: 0
    }
}
