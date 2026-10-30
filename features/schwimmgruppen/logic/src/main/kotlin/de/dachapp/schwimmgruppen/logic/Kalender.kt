package de.dachapp.schwimmgruppen.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** Trainings eines Zeitraums aus dem Wochenplan der Gruppen, zusammengeführt mit den erfassten Einheiten. */
object Kalender {

    fun montag(tag: LocalDate): LocalDate = tag.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * Alle Trainings von [von] bis [bis] (je einschließlich), sortiert nach Tag und Uhrzeit. Termine aus dem Plan
     * ohne Eintrag erscheinen als leere Einheit; Zusatztermine und Einheiten zu früheren Planzeiten kommen dazu.
     */
    fun zeitraum(bestand: Bestand, von: LocalDate, bis: LocalDate): List<Einheit> {
        val gespeichert = bestand.einheiten
            .filter { it.datum in von..bis && bestand.gruppe(it.gruppeId) != null }
            .associateBy { it.schluessel }
        val liste = mutableMapOf<String, Einheit>()
        var d = von
        while (!d.isAfter(bis)) {
            for (g in bestand.gruppen) for (z in g.zeiten) {
                if (z.wochentag != d.dayOfWeek.value) continue
                val k = Einheit.schluessel(g.id, d, z.beginn)
                liste[k] = gespeichert[k] ?: Einheit(g.id, d, z.beginn, z.ende)
            }
            d = d.plusDays(1)
        }
        for ((k, e) in gespeichert) liste.putIfAbsent(k, e)
        return liste.values.sortedWith(compareBy({ it.datum }, { it.beginn }, { bestand.gruppen.indexOfFirst { g -> g.id == it.gruppeId } }))
    }

    fun woche(bestand: Bestand, tag: LocalDate): List<Einheit> = montag(tag).let { zeitraum(bestand, it, it.plusDays(6)) }

    fun tag(bestand: Bestand, tag: LocalDate): List<Einheit> = zeitraum(bestand, tag, tag)

    /** Das Training, das gerade läuft oder als nächstes heute beginnt, sonst das letzte von heute. Für den Hallenmodus. */
    fun jetzt(bestand: Bestand, jetzt: LocalDateTime): Einheit? {
        val heute = tag(bestand, jetzt.toLocalDate())
        val uhr = jetzt.toLocalTime()
        return heute.firstOrNull { !uhr.isBefore(it.beginn.minusMinutes(15)) && uhr.isBefore(it.ende) }
            ?: heute.firstOrNull { uhr.isBefore(it.beginn) }
            ?: heute.lastOrNull()
    }

    /** Wer zu einer Einheit erwartet wird: aktive Mitglieder der Gruppe plus alle, die angehakt sind. */
    fun teilnehmerliste(bestand: Bestand, e: Einheit): List<Mitglied> {
        val gruppe = bestand.mitgliederIn(e.gruppeId)
        val extra = e.anwesend.filter { id -> gruppe.none { it.id == id } }.mapNotNull { bestand.mitglied(it) }
        return gruppe + extra.sortedWith(Bestand.NACH_NAME)
    }
}
