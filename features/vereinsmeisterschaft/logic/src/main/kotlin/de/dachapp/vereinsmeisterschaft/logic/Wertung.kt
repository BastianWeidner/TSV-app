package de.dachapp.vereinsmeisterschaft.logic

import kotlin.math.pow

/** Eine Zeile einer Lagen-Wertung. [platz] null = ohne Platz (keine Zeit, disqualifiziert, außer Konkurrenz). */
data class LagenZeile(val teilnehmer: Teilnehmer, val ergebnis: Ergebnis?, val platz: Int?)

data class LagenWertung(val strecke: Strecke, val zeilen: List<LagenZeile>)

/** Gesamtwertung eines Schwimmers über alle seine Strecken. */
data class GesamtZeile(
    val teilnehmer: Teilnehmer,
    val punkte: Double,
    /** Summe der auf 3 Bahnen hochgerechneten Zeiten, null wenn nicht alle Strecken gültig. */
    val zeitsumme: Int?,
    val vollstaendig: Boolean,
    val platz: Int?,
    /** Punkte je Strecke (nur gültige). */
    val einzeln: Map<Strecke, Double>,
)

/** Eine Wertungsgruppe der Jahrgangswertung, z. B. „Jahrgang 2017 · weiblich“. */
data class Gruppe(val geschlecht: Geschlecht, val aelter: Int, val juenger: Int) {
    val jahrgaenge: String get() = when {
        aelter == juenger -> "Jahrgang $aelter"
        aelter <= 1900 -> "Jahrgang $juenger und älter"
        else -> "Jahrgang $juenger/$aelter".let { if (juenger - aelter > 1) "Jahrgang $juenger–$aelter" else it }
    }
    val titel: String get() = "$jahrgaenge · ${geschlecht.text}"
}

data class GruppenWertung(val gruppe: Gruppe, val lagen: List<LagenWertung>, val gesamt: List<GesamtZeile>)

data class PokalWertung(val pokal: Pokal, val geschlecht: Geschlecht, val zeilen: List<GesamtZeile>) {
    val titel: String get() = "${pokal.name} · ${geschlecht.text}"
}

data class UrkundenZeile(val strecke: Strecke, val ergebnis: Ergebnis?, val platz: Int?)

/** Urkunde für jeden Teilnehmer: alle Zeiten mit Platz in Jahrgang und Geschlecht. */
data class Urkunde(
    val teilnehmer: Teilnehmer,
    val gruppe: Gruppe,
    val zeilen: List<UrkundenZeile>,
    val gesamtPlatz: Int?,
    val medaille: Boolean,
)

/** Punkteurkunde für die Meister (Plätze eines Pokals). */
data class Punkteurkunde(val teilnehmer: Teilnehmer, val wertung: PokalWertung, val zeile: GesamtZeile)

object Wertung {

    /** Zeit auf [Regeln.normBahnen] hochgerechnet (Hundertstel, mit Nachkommastellen). */
    fun hochgerechnet(r: Regeln, zeit: Int, bahnen: Int): Double = zeit.toDouble() * r.normBahnen / bahnen.coerceAtLeast(1)

    /** Punkte = 1000 × (Basiszeit ÷ hochgerechnete Zeit)³; 0 ohne gültige Zeit oder Basiszeit. */
    fun punkte(r: Regeln, e: Ergebnis?, strecke: Strecke, g: Geschlecht): Double {
        if (e == null || !e.gueltig) return 0.0
        val basis = r.basis(strecke.lage, g) ?: return 0.0
        return 1000.0 * (basis / hochgerechnet(r, e.zeit!!, strecke.bahnen)).pow(3)
    }

    fun punkteText(p: Double): String = "%.1f".format(java.util.Locale.GERMANY, p)

    /** Wert einer Gesamtzeile nach der eingestellten Wertung: Zeitpunkte („123,45“) bzw. Punkte. */
    fun wertText(r: Regeln, z: GesamtZeile): String = when (r.wertung) {
        Wertungsart.ZEITSUMME -> z.zeitsumme?.let { "%d,%02d".format(it / 100, it % 100) } ?: "–"
        Wertungsart.PUNKTE -> if (z.punkte > 0) punkteText(z.punkte) else "–"
    }

    /** Wert einer einzelnen Strecke für die Punkteurkunde (hochgerechnete Sekunden bzw. Punkte). */
    fun einzelText(r: Regeln, e: Ergebnis?, s: Strecke, g: Geschlecht): String {
        if (e == null || !e.gueltig) return "–"
        return when (r.wertung) {
            Wertungsart.ZEITSUMME -> Math.round(hochgerechnet(r, e.zeit!!, s.bahnen)).toInt().let { "%d,%02d".format(it / 100, it % 100) }
            Wertungsart.PUNKTE -> punkteText(punkte(r, e, s, g))
        }
    }

    /** Plätze nach Wettkampfregel: gleiche Werte gleicher Platz, danach wird übersprungen (1, 1, 3). */
    private fun <T> plaetze(liste: List<T>, wert: (T) -> Double?, aufsteigend: Boolean, zaehlt: (T) -> Boolean): Map<T, Int> {
        val gewertet = liste.filter { zaehlt(it) && wert(it) != null }
            .sortedBy { if (aufsteigend) wert(it)!! else -wert(it)!! }
        val ergebnis = HashMap<T, Int>()
        var vorher: Double? = null
        var platz = 0
        gewertet.forEachIndexed { i, x ->
            val w = wert(x)!!
            if (vorher == null || kotlin.math.abs(w - vorher!!) > 1e-9) platz = i + 1
            vorher = w
            ergebnis[x] = platz
        }
        return ergebnis
    }

    /** Gruppe der Jahrgangswertung eines Schwimmers (Kombis fassen Jahrgänge zusammen). */
    fun gruppe(v: Veranstaltung, t: Teilnehmer): Gruppe? {
        val g = t.geschlecht ?: return null
        val k = v.regeln.kombis.firstOrNull { it.passt(t.jahrgang, g) }
        return if (k != null) Gruppe(g, k.aelter, k.juenger) else Gruppe(g, t.jahrgang, t.jahrgang)
    }

    /** Gesamtwertung einer Liste von Schwimmern nach [Regeln.wertung]. */
    fun gesamt(v: Veranstaltung, leute: List<Teilnehmer>): List<GesamtZeile> {
        val r = v.regeln
        val roh = leute.map { t ->
            val g = t.geschlecht ?: Geschlecht.W
            val starts = v.starts(t)
            val ergebnisse = starts.associateWith { v.ergebnis(t.id, it.lage) }
            val einzeln = ergebnisse.filterValues { it?.gueltig == true }.mapValues { (s, e) -> punkte(r, e, s, g) }
            val voll = starts.isNotEmpty() && ergebnisse.values.all { it?.gueltig == true }
            val summe = if (voll) ergebnisse.entries.sumOf { (s, e) -> hochgerechnet(r, e!!.zeit!!, s.bahnen) }.let { Math.round(it).toInt() } else null
            GesamtZeile(t, einzeln.values.sum(), summe, voll, null, einzeln)
        }
        val plaetze = when (r.wertung) {
            Wertungsart.PUNKTE -> plaetze(roh, { z -> z.punkte.takeIf { it > 0 } }, false) { !it.teilnehmer.ausserKonkurrenz }
            Wertungsart.ZEITSUMME -> plaetze(roh, { z -> z.zeitsumme?.toDouble() }, true) { !it.teilnehmer.ausserKonkurrenz }
        }
        return roh.map { it.copy(platz = plaetze[it]) }.sortedWith(
            compareBy<GesamtZeile> { it.platz ?: Int.MAX_VALUE }
                .thenByDescending { it.punkte }
                .thenBy { it.teilnehmer.nachname }.thenBy { it.teilnehmer.vorname },
        )
    }

    /** Jahrgangswertung: je Gruppe (Jahrgang bzw. Kombi, Geschlecht) jede Strecke und die Gesamtwertung. */
    fun jahrgaenge(v: Veranstaltung): List<GruppenWertung> {
        val nachGruppe = v.teilnehmer.filter { v.starts(it).isNotEmpty() }.groupBy { gruppe(v, it) }
        return nachGruppe.filterKeys { it != null }.map { (gruppe, leute) ->
            val strecken = leute.flatMap { v.starts(it) }.distinct()
                .sortedWith(compareBy({ v.regeln.lagenFolge.indexOf(it.lage) }, { it.bahnen }))
            val lagen = strecken.map { s ->
                val schwimmer = leute.filter { s in v.starts(it) }
                val zeilen = schwimmer.map { t -> LagenZeile(t, v.ergebnis(t.id, s.lage), null) }
                val p = plaetze(zeilen, { z -> z.ergebnis?.takeIf { it.gueltig }?.zeit?.toDouble() }, true) { !it.teilnehmer.ausserKonkurrenz }
                LagenWertung(
                    s,
                    zeilen.map { it.copy(platz = p[it]) }.sortedWith(
                        compareBy<LagenZeile> { it.platz ?: Int.MAX_VALUE }
                            .thenBy { if (it.ergebnis?.gueltig == true) it.ergebnis.zeit else Int.MAX_VALUE }
                            .thenBy { it.teilnehmer.nachname },
                    ),
                )
            }
            GruppenWertung(gruppe!!, lagen, gesamt(v, leute))
        }.sortedWith(compareByDescending<GruppenWertung> { it.gruppe.juenger }.thenBy { it.gruppe.geschlecht.ordinal })
    }

    /** Pokale (Meister), immer weiblich und männlich getrennt. */
    fun pokale(v: Veranstaltung): List<PokalWertung> = v.regeln.pokale.flatMap { p ->
        Geschlecht.entries.mapNotNull { g ->
            val leute = v.teilnehmer.filter { it.geschlecht == g && v.stufe(it)?.id in p.stufen && v.starts(it).isNotEmpty() }
            if (leute.isEmpty()) null else PokalWertung(p, g, gesamt(v, leute))
        }
    }

    /** Medaillen: Plätze 1 bis [Regeln.medaillenPlaetze] je Jahrgang (gesamt oder je Lage). */
    fun medaillen(v: Veranstaltung, w: List<GruppenWertung> = jahrgaenge(v)): Set<Long> {
        val bis = v.regeln.medaillenPlaetze
        return when (v.regeln.medaillen) {
            Medaillen.GESAMT -> w.flatMap { gw -> gw.gesamt.filter { (it.platz ?: Int.MAX_VALUE) <= bis }.map { it.teilnehmer.id } }
            Medaillen.JE_LAGE -> w.flatMap { gw -> gw.lagen.flatMap { lw -> lw.zeilen.filter { (it.platz ?: Int.MAX_VALUE) <= bis }.map { it.teilnehmer.id } } }
        }.toSet()
    }

    fun urkunden(v: Veranstaltung): List<Urkunde> {
        val w = jahrgaenge(v)
        val medaillen = medaillen(v, w)
        return w.flatMap { gw ->
            gw.gesamt.map { z ->
                val t = z.teilnehmer
                val zeilen = v.starts(t).map { s ->
                    val platz = gw.lagen.firstOrNull { it.strecke == s }?.zeilen?.firstOrNull { it.teilnehmer.id == t.id }?.platz
                    UrkundenZeile(s, v.ergebnis(t.id, s.lage), platz)
                }
                Urkunde(t, gw.gruppe, zeilen, z.platz, t.id in medaillen)
            }
        }.sortedWith(compareBy({ it.teilnehmer.nachname.lowercase() }, { it.teilnehmer.vorname.lowercase() }))
    }

    fun punkteurkunden(v: Veranstaltung): List<Punkteurkunde> = pokale(v).flatMap { pw ->
        pw.zeilen.filter { (it.platz ?: Int.MAX_VALUE) <= v.regeln.punkteurkundeBis }.map { Punkteurkunde(it.teilnehmer, pw, it) }
    }

    /** Hinweise vor dem Drucken: fehlendes Geschlecht, fehlende Zeiten, Schwimmer ohne Stufe. */
    fun hinweise(v: Veranstaltung): List<String> {
        val h = ArrayList<String>()
        val ohneG = v.teilnehmer.filter { it.geschlecht == null }
        if (ohneG.isNotEmpty()) h += "${ohneG.size} ohne Geschlecht (werden nicht gewertet): " + ohneG.take(5).joinToString { it.name }
        val ohneStufe = v.teilnehmer.filter { v.stufe(it) == null }
        if (ohneStufe.isNotEmpty()) h += "${ohneStufe.size} passen in keine Stufe: " + ohneStufe.take(5).joinToString { "${it.name} (${it.jahrgang})" }
        val (starts, erfasst) = v.fortschritt()
        if (erfasst < starts) h += "${starts - erfasst} von $starts Zeiten fehlen noch."
        return h
    }
}
