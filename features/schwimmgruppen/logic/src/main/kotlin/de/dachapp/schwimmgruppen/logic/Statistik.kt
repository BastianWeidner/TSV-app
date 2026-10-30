package de.dachapp.schwimmgruppen.logic

import java.time.LocalDate
import java.time.YearMonth

data class MonatsWert(val monat: YearMonth, val trainings: Int, val teilnahmen: Int) {
    val durchschnitt: Double get() = if (trainings == 0) 0.0 else teilnahmen.toDouble() / trainings
}

data class GruppenStatistik(
    val gruppe: Gruppe,
    /** Trainings mit erfasster Anwesenheit. */
    val trainings: Int,
    val teilnahmen: Int,
    val hoechstens: Int,
    val ausgefallen: Int,
    /** Planmäßige Trainings in der Vergangenheit ohne Eintrag. */
    val offen: Int,
    val monate: List<MonatsWert>,
) {
    val durchschnitt: Double get() = if (trainings == 0) 0.0 else teilnahmen.toDouble() / trainings
}

data class Teilnahme(val anwesend: Int, val trainings: Int) {
    val quote: Int? get() = if (trainings == 0) null else (100.0 * anwesend / trainings).toInt()
}

object Statistik {

    fun gruppen(bestand: Bestand, von: LocalDate, bis: LocalDate, heute: LocalDate = LocalDate.now()): List<GruppenStatistik> {
        val alle = Kalender.zeitraum(bestand, von, bis)
        return bestand.gruppen.map { g ->
            val eig = alle.filter { it.gruppeId == g.id }
            val erfasst = eig.filter { it.erfasst }
            GruppenStatistik(
                gruppe = g,
                trainings = erfasst.size,
                teilnahmen = erfasst.sumOf { it.anzahl },
                hoechstens = erfasst.maxOfOrNull { it.anzahl } ?: 0,
                ausgefallen = eig.count { it.ausgefallen },
                offen = eig.count { !it.ausgefallen && it.anzahl == 0 && it.datum.isBefore(heute) },
                monate = erfasst.groupBy { YearMonth.from(it.datum) }.toSortedMap()
                    .map { (m, l) -> MonatsWert(m, l.size, l.sumOf { it.anzahl }) },
            )
        }
    }

    /**
     * Wie oft ein Mitglied da war, gemessen an den Trainings seiner Gruppen, bei denen Namen abgehakt wurden.
     * Nur gezählte Trainings bleiben außen vor, sonst sähe die Quote schlechter aus, als sie ist.
     */
    fun mitglied(bestand: Bestand, mitgliedId: String, von: LocalDate, bis: LocalDate): Teilnahme {
        val m = bestand.mitglied(mitgliedId) ?: return Teilnahme(0, 0)
        val erfasst = bestand.einheiten.filter { it.datum in von..bis && it.erfasst && it.anwesend.isNotEmpty() }
        val eigene = erfasst.filter { it.gruppeId in m.gruppen || mitgliedId in it.anwesend }
        return Teilnahme(eigene.count { mitgliedId in it.anwesend }, eigene.size)
    }

    /** Wie oft jeder Trainer im Zeitraum eine Stunde geleitet hat (stattgefunden und nicht in der Zukunft). */
    fun trainerEinsaetze(bestand: Bestand, von: LocalDate, bis: LocalDate, heute: LocalDate = LocalDate.now()): Map<String, Int> =
        Kalender.zeitraum(bestand, von, minOf(bis, heute))
            .filter { !it.ausgefallen }
            .flatMap { e -> bestand.trainerVon(e).map { it.id } }
            .groupingBy { it }.eachCount()

    /** Letzte Trainings, bei denen das Mitglied da war, neueste zuerst. */
    fun besuche(bestand: Bestand, mitgliedId: String, anzahl: Int = 10): List<Einheit> =
        bestand.einheiten.filter { mitgliedId in it.anwesend }
            .sortedWith(compareByDescending<Einheit> { it.datum }.thenByDescending { it.beginn }).take(anzahl)
}
