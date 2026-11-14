package de.dachapp.vereinsmeisterschaft.logic

/**
 * Läufe einteilen: je Strecke (Lage + Bahnen) ein Wettkampf, darin Läufe zu höchstens [Regeln.bahnenImBecken] Schwimmern.
 * Reihenfolge nach Jahrgang (jüngste zuerst) und Meldezeit (langsamste zuerst) bzw. nur nach Meldezeit. Die Läufe werden
 * gleichmäßig gefüllt (9 Schwimmer auf 4 Bahnen = 3 + 3 + 3, nicht 1 + 4 + 4); die Schnellsten eines Laufs kommen auf
 * die mittleren Bahnen.
 */
object Laufeinteilung {

    /** Wettkämpfe in der Reihenfolge der Veranstaltung. */
    fun wettkaempfe(v: Veranstaltung): List<Strecke> {
        val alle = v.teilnehmer.flatMap { v.starts(it) }.distinct()
        val folge = v.regeln.lagenFolge
        fun lageNr(l: Lage) = folge.indexOf(l).let { if (it < 0) folge.size + l.ordinal else it }
        return when (v.regeln.folge) {
            Folge.NACH_LAGE -> alle.sortedWith(compareBy({ lageNr(it.lage) }, { it.bahnen }))
            Folge.NACH_STRECKE -> alle.sortedWith(compareBy({ it.bahnen }, { lageNr(it.lage) }))
        }
    }

    /** Schwimmer einer Strecke, langsamste zuerst. */
    fun reihenfolge(v: Veranstaltung, strecke: Strecke): List<Teilnehmer> {
        val starter = v.teilnehmer.filter { strecke in v.starts(it) }
        // Ohne Meldezeit gilt: langsamer als jede gemeldete Zeit, also in die ersten Läufe.
        val zeitAbsteigend = compareByDescending<Teilnehmer> { it.meldezeiten[strecke.lage] ?: Int.MAX_VALUE }
        val sortiert = when (v.regeln.laufOrdnung) {
            LaufOrdnung.ALTER -> starter.sortedWith(compareByDescending<Teilnehmer> { it.jahrgang }.then(zeitAbsteigend).thenBy { it.nachname }.thenBy { it.vorname })
            LaufOrdnung.ZEIT -> starter.sortedWith(zeitAbsteigend.thenByDescending { it.jahrgang }.thenBy { it.nachname }.thenBy { it.vorname })
        }
        return if (v.regeln.laeufeNachGeschlecht) sortiert.sortedBy { it.geschlecht?.ordinal ?: 2 } else sortiert
    }

    /** Größen der Läufe für [n] Schwimmer auf [bahnen] Bahnen, möglichst gleich, hintere Läufe eher voller. */
    fun groessen(n: Int, bahnen: Int): List<Int> {
        if (n <= 0) return emptyList()
        val k = bahnen.coerceAtLeast(1)
        val laeufe = (n + k - 1) / k
        val basis = n / laeufe
        val extra = n % laeufe
        return List(laeufe) { i -> if (i >= laeufe - extra) basis + 1 else basis }
    }

    /** Bahnen von der Mitte nach außen: bei 4 Bahnen 2, 3, 1, 4. */
    fun mitteZuerst(bahnen: Int): List<Int> {
        val mitte = (bahnen + 1) / 2.0
        return (1..bahnen).sortedWith(compareBy({ kotlin.math.abs(it - mitte) }, { it }))
    }

    fun einteilen(v: Veranstaltung): List<Lauf> {
        val k = v.regeln.bahnenImBecken.coerceAtLeast(1)
        val laeufe = ArrayList<Lauf>()
        for (strecke in wettkaempfe(v)) {
            val gruppen = if (v.regeln.laeufeNachGeschlecht) {
                reihenfolge(v, strecke).groupBy { it.geschlecht }.values.toList()
            } else listOf(reihenfolge(v, strecke))
            for (gruppe in gruppen) {
                var i = 0
                for (g in groessen(gruppe.size, k)) {
                    val imLauf = gruppe.subList(i, i + g)
                    i += g
                    // imLauf ist langsam → schnell; der Schnellste kommt auf die mittlere Bahn.
                    val bahnen = mitteZuerst(k)
                    val belegung = imLauf.reversed().mapIndexed { j, t -> Belegung(bahnen[j], t.id) }.sortedBy { it.bahn }
                    laeufe += Lauf(laeufe.size + 1, strecke, belegung)
                }
            }
        }
        return laeufe
    }

    /** Zwei Plätze tauschen (oder auf eine freie Bahn verschieben); nur innerhalb derselben Strecke. */
    fun tauschen(laeufe: List<Lauf>, lauf1: Int, bahn1: Int, lauf2: Int, bahn2: Int): List<Lauf> {
        val a = laeufe.firstOrNull { it.nr == lauf1 } ?: return laeufe
        val b = laeufe.firstOrNull { it.nr == lauf2 } ?: return laeufe
        if (a.strecke != b.strecke) return laeufe
        val ta = a.auf(bahn1)
        val tb = b.auf(bahn2)
        fun setzen(l: Lauf, bahn: Int, t: Long?, weg: Int?): List<Belegung> {
            var bel = l.belegung.filterNot { it.bahn == bahn || (weg != null && it.bahn == weg) }
            if (t != null) bel = bel + Belegung(bahn, t)
            return bel.sortedBy { it.bahn }
        }
        return laeufe.map { l ->
            when {
                l.nr == lauf1 && l.nr == lauf2 -> {
                    val ohne = l.belegung.filterNot { it.bahn == bahn1 || it.bahn == bahn2 }
                    l.copy(belegung = (ohne + listOfNotNull(tb?.let { Belegung(bahn1, it) }, ta?.let { Belegung(bahn2, it) })).sortedBy { it.bahn })
                }
                l.nr == lauf1 -> l.copy(belegung = setzen(l, bahn1, tb, null))
                l.nr == lauf2 -> l.copy(belegung = setzen(l, bahn2, ta, null))
                else -> l
            }
        }
    }

    /**
     * Nachmeldung: freie Bahn im letzten passenden Lauf, sonst ein neuer Lauf direkt dahinter (die folgenden Läufe
     * rücken eine Nummer weiter).
     */
    fun nachtragen(laeufe: List<Lauf>, teilnehmer: Long, strecke: Strecke, bahnen: Int): List<Lauf> {
        if (laeufe.any { l -> l.strecke == strecke && l.belegung.any { it.teilnehmer == teilnehmer } }) return laeufe
        val passend = laeufe.filter { it.strecke == strecke }
        val frei = passend.lastOrNull { it.belegung.size < bahnen }
        if (frei != null) {
            val bahn = mitteZuerst(bahnen).first { b -> frei.auf(b) == null }
            return laeufe.map { if (it.nr == frei.nr) it.copy(belegung = (it.belegung + Belegung(bahn, teilnehmer)).sortedBy { b -> b.bahn }) else it }
        }
        val nach = passend.lastOrNull()?.nr ?: laeufe.size
        val neu = Lauf(nach + 1, strecke, listOf(Belegung(mitteZuerst(bahnen).first(), teilnehmer)))
        return (laeufe.map { if (it.nr > nach) it.copy(nr = it.nr + 1) else it } + neu).sortedBy { it.nr }
    }

    /** Schwimmer aus allen Läufen nehmen (z. B. abgemeldet); leere Läufe fallen weg, Nummern rücken nach. */
    fun entfernen(laeufe: List<Lauf>, teilnehmer: Long, lage: Lage? = null): List<Lauf> =
        laeufe.map { l -> if (lage == null || l.strecke.lage == lage) l.copy(belegung = l.belegung.filterNot { it.teilnehmer == teilnehmer }) else l }
            .filter { it.belegung.isNotEmpty() }
            .mapIndexed { i, l -> l.copy(nr = i + 1) }

    /** Starts, die in keinem Lauf stehen (nach Nachmeldungen oder geänderten Stufen). */
    fun fehlend(v: Veranstaltung): List<Pair<Teilnehmer, Strecke>> =
        v.teilnehmer.flatMap { t ->
            v.starts(t).filter { s -> v.laeufe.none { l -> l.strecke == s && l.belegung.any { it.teilnehmer == t.id } } }.map { t to it }
        }
}
