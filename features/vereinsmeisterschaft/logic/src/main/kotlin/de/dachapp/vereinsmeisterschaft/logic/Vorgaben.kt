package de.dachapp.vereinsmeisterschaft.logic

/**
 * Vorgaben nach der Ausschreibung 2026 (TSV Fischach): Jahrgangsgrenzen wandern jedes Jahr mit, lassen sich aber
 * je Veranstaltung frei ändern.
 */
object Vorgaben {

    /**
     * Basiszeiten für die Punkte (50 m, angelehnt an die Kurzbahn-Weltrekorde wie bei den World-Aquatics-Punkten).
     * In der Halle mit 16 2/3-m-Bahnen sind die Punkte dadurch klein, für die Reihenfolge spielt das keine Rolle.
     */
    val basiszeiten: List<Basiszeit> = listOf(
        Basiszeit(Lage.BRUST, Geschlecht.W, 2837),
        Basiszeit(Lage.RUECKEN, Geschlecht.W, 2523),
        Basiszeit(Lage.KRAUL, Geschlecht.W, 2283),
        Basiszeit(Lage.DELPHIN, Geschlecht.W, 2394),
        Basiszeit(Lage.BRUST, Geschlecht.M, 2495),
        Basiszeit(Lage.RUECKEN, Geschlecht.M, 2211),
        Basiszeit(Lage.KRAUL, Geschlecht.M, 1990),
        Basiszeit(Lage.DELPHIN, Geschlecht.M, 2132),
    )

    /**
     * Stufen für das Jahr [jahr]: 2026 = bis Jg. 2019 1 Bahn Brust+Rücken, 2018/2017 2 Bahnen Brust+Rücken,
     * 2016–2009 3 Bahnen Brust/Rücken/Kraul, ab 2008 (Ü18) Master mit zusätzlich 2 Bahnen Delphin.
     */
    fun stufen(jahr: Int): List<Stufe> {
        val d = jahr - 2026
        val br = listOf(Lage.BRUST, Lage.RUECKEN)
        return listOf(
            Stufe("b1", "1 Bahn", abJahrgang = 2019 + d, strecken = br.map { Strecke(it, 1) }),
            Stufe("b2", "2 Bahnen", abJahrgang = 2017 + d, bisJahrgang = 2018 + d, strecken = br.map { Strecke(it, 2) }),
            Stufe(
                "b3", "3 Bahnen", abJahrgang = 2009 + d, bisJahrgang = 2016 + d,
                strecken = (br + Lage.KRAUL).map { Strecke(it, 3) },
            ),
            Stufe(
                "master", "Master", bisJahrgang = 2008 + d,
                strecken = (br + Lage.KRAUL).map { Strecke(it, 3) } + Strecke(Lage.DELPHIN, 2),
            ),
        )
    }

    val pokale: List<Pokal> = listOf(
        Pokal("Kinderpokal", listOf("b1", "b2")),
        Pokal("Jugendpokal", listOf("b3")),
        Pokal("Masterspokal", listOf("master")),
    )

    /** Master werden in der Jahrgangswertung zusammen gewertet (sonst stünde fast jeder allein in seinem Jahrgang). */
    fun kombis(jahr: Int): List<Kombi> = listOf(Kombi(1900, 2008 + (jahr - 2026)))

    fun regeln(jahr: Int) = Regeln(stufen = stufen(jahr), pokale = pokale, kombis = kombis(jahr))

    fun veranstaltung(id: Long, jahr: Int) = Veranstaltung(
        id = id,
        titel = "Vereinsmeisterschaft $jahr",
        jahr = jahr,
        ort = "Schulschwimmhalle Fischach",
        regeln = regeln(jahr),
    )

    /**
     * Neue Veranstaltung aus der vorigen: Regeln übernehmen und die Jahrgangsgrenzen um die Jahre verschieben,
     * Teilnehmer auf Wunsch ohne Zeiten und Läufe mitnehmen (Meldezeiten = Vorjahreszeiten).
     */
    fun folgejahr(alt: Veranstaltung, id: Long, jahr: Int, teilnehmerMit: Boolean): Veranstaltung {
        val d = jahr - alt.jahr
        val regeln = alt.regeln.copy(
            stufen = alt.regeln.stufen.map { s -> s.copy(abJahrgang = s.abJahrgang?.plus(d), bisJahrgang = s.bisJahrgang?.plus(d)) },
            kombis = alt.regeln.kombis.map { k -> k.copy(von = k.von + d, bis = k.bis + d) },
        )
        val teilnehmer = if (!teilnehmerMit) emptyList() else alt.teilnehmer.map { t ->
            val vorjahr = Lage.entries.mapNotNull { l -> alt.ergebnis(t.id, l)?.takeIf { it.gueltig }?.let { e -> l to e.zeit!! } }.toMap()
            t.copy(stufeId = null, ohne = emptySet(), meldezeiten = vorjahr, ausserKonkurrenz = false)
        }
        return alt.copy(
            id = id,
            titel = alt.titel.replace(alt.jahr.toString(), jahr.toString()),
            jahr = jahr,
            datum = "",
            regeln = regeln,
            teilnehmer = teilnehmer,
            ergebnisse = emptyList(),
            laeufe = emptyList(),
            staffeln = emptyList(),
        )
    }
}
