package de.dachapp.schwimmleitung.logic

import java.time.LocalDate

/**
 * Änderungen am geteilten Stand. Jede Änderung setzt [Eintrag.geaendert] auf jetzt, mindestens aber eine
 * Millisekunde nach dem bisherigen Stand, damit sie beim Abgleich sicher gewinnt (auch bei schiefer Geräteuhr).
 */
fun neueZeit(alt: Long, jetzt: Long): Long = maxOf(jetzt, alt + 1)

/** Setzt [neu] (ersetzt oder hängt an) mit frischem Zeitstempel. [stempel] kopiert Zeit und Löschmarke hinein. */
private inline fun <T : Eintrag> List<T>.setzen(neu: T, jetzt: Long, geloescht: Boolean, stempel: (T, Long, Boolean) -> T): List<T> {
    val zeit = neueZeit(firstOrNull { it.id == neu.id }?.geaendert ?: 0, jetzt)
    val fertig = stempel(neu, zeit, geloescht)
    return if (any { it.id == neu.id }) map { if (it.id == neu.id) fertig else it } else this + fertig
}

private inline fun <T : Eintrag> List<T>.loeschen(id: String, jetzt: Long, stempel: (T, Long, Boolean) -> T): List<T> =
    map { if (it.id == id) stempel(it, neueZeit(it.geaendert, jetzt), true) else it }

fun LeitungDaten.setzen(x: Einstellungen, jetzt: Long) =
    copy(einstellungen = einstellungen.setzen(x.copy(id = Einstellungen.ID), jetzt, false) { a, t, g -> a.copy(geaendert = t, geloescht = g) })

fun LeitungDaten.setzen(x: Person, jetzt: Long) =
    copy(personen = personen.setzen(x, jetzt, false) { a, t, g -> a.copy(geaendert = t, geloescht = g) })

fun LeitungDaten.setzen(x: Nachweis, jetzt: Long) =
    copy(nachweise = nachweise.setzen(x, jetzt, false) { a, t, g -> a.copy(geaendert = t, geloescht = g) })

fun LeitungDaten.setzen(x: Aufgabe, jetzt: Long) =
    copy(aufgaben = aufgaben.setzen(x, jetzt, false) { a, t, g -> a.copy(geaendert = t, geloescht = g) })

fun LeitungDaten.setzen(x: Sitzung, jetzt: Long) =
    copy(sitzungen = sitzungen.setzen(x, jetzt, false) { a, t, g -> a.copy(geaendert = t, geloescht = g) })

fun LeitungDaten.setzen(x: Material, jetzt: Long) =
    copy(material = material.setzen(x, jetzt, false) { a, t, g -> a.copy(geaendert = t, geloescht = g) })

fun LeitungDaten.setzen(x: Meldung, jetzt: Long) =
    copy(meldungen = meldungen.setzen(x, jetzt, false) { a, t, g -> a.copy(geaendert = t, geloescht = g) })

fun LeitungDaten.setzen(x: Beleg, jetzt: Long) =
    copy(belege = belege.setzen(x, jetzt, false) { a, t, g -> a.copy(geaendert = t, geloescht = g) })

fun LeitungDaten.setzen(x: Dokument, jetzt: Long) =
    copy(dokumente = dokumente.setzen(x, jetzt, false) { a, t, g -> a.copy(geaendert = t, geloescht = g) })

fun LeitungDaten.setzen(x: KursAuszug, jetzt: Long) =
    copy(kursAuszug = kursAuszug.setzen(x.copy(id = KursAuszug.ID), jetzt, false) { a, t, g -> a.copy(geaendert = t, geloescht = g) })

fun LeitungDaten.setzen(x: GruppenAuszug, jetzt: Long) =
    copy(gruppenAuszug = gruppenAuszug.setzen(x.copy(id = GruppenAuszug.ID), jetzt, false) { a, t, g -> a.copy(geaendert = t, geloescht = g) })

fun LeitungDaten.personLoeschen(id: String, jetzt: Long) =
    copy(
        personen = personen.loeschen(id, jetzt) { a, t, g -> a.copy(geaendert = t, geloescht = g) },
        // Nachweise gehören zur Person und gehen mit.
        nachweise = nachweise.map { if (it.personId == id && !it.geloescht) it.copy(geloescht = true, geaendert = neueZeit(it.geaendert, jetzt)) else it },
    )

fun LeitungDaten.nachweisLoeschen(id: String, jetzt: Long) =
    copy(nachweise = nachweise.loeschen(id, jetzt) { a, t, g -> a.copy(geaendert = t, geloescht = g) })

fun LeitungDaten.aufgabeLoeschen(id: String, jetzt: Long) =
    copy(aufgaben = aufgaben.loeschen(id, jetzt) { a, t, g -> a.copy(geaendert = t, geloescht = g) })

fun LeitungDaten.sitzungLoeschen(id: String, jetzt: Long) =
    copy(sitzungen = sitzungen.loeschen(id, jetzt) { a, t, g -> a.copy(geaendert = t, geloescht = g) })

fun LeitungDaten.materialLoeschen(id: String, jetzt: Long) =
    copy(material = material.loeschen(id, jetzt) { a, t, g -> a.copy(geaendert = t, geloescht = g) })

fun LeitungDaten.meldungLoeschen(id: String, jetzt: Long) =
    copy(meldungen = meldungen.loeschen(id, jetzt) { a, t, g -> a.copy(geaendert = t, geloescht = g) })

fun LeitungDaten.belegLoeschen(id: String, jetzt: Long) =
    copy(belege = belege.loeschen(id, jetzt) { a, t, g -> a.copy(geaendert = t, geloescht = g) })

fun LeitungDaten.dokumentLoeschen(id: String, jetzt: Long) =
    copy(dokumente = dokumente.loeschen(id, jetzt) { a, t, g -> a.copy(geaendert = t, geloescht = g) })

/** Aufgabe auf einen neuen Status setzen; erledigt bekommt das Datum. */
fun LeitungDaten.aufgabeStatus(id: String, status: AufgabeStatus, heute: LocalDate, jetzt: Long): LeitungDaten {
    val a = aufgabe(id) ?: return this
    return setzen(a.copy(status = status, erledigtAm = if (status == AufgabeStatus.ERLEDIGT) heute else null), jetzt)
}

/**
 * Neue Meldung aus der Halle. Ein Defekt mit Material setzt dieses auf defekt; jede Defektmeldung wird zur offenen
 * Aufgabe für die Leitung, damit sie nicht untergeht.
 */
fun LeitungDaten.melden(m: Meldung, heute: LocalDate, jetzt: Long, aufgabeId: String): LeitungDaten {
    var d = this
    var meldung = m
    if (m.art == MeldungArt.DEFEKT) {
        val stueck = materialStueck(m.materialId)
        if (stueck != null && stueck.zustand == Zustand.IN_ORDNUNG) d = d.setzen(stueck.copy(zustand = Zustand.DEFEKT), jetzt)
        val titel = "Defekt: " + (stueck?.name ?: m.text.lineSequence().first().take(60))
        d = d.setzen(Aufgabe(aufgabeId, titel, notiz = m.text, erstellt = heute, meldungId = m.id), jetzt)
        meldung = m.copy(aufgabeId = aufgabeId)
    }
    return d.setzen(meldung, jetzt)
}

/** Meldung erledigt (oder wieder offen). Ein erledigter Defekt setzt das Material wieder auf in Ordnung. */
fun LeitungDaten.meldungErledigt(id: String, erledigt: Boolean, heute: LocalDate, jetzt: Long): LeitungDaten {
    val m = meldung(id) ?: return this
    var d = setzen(m.copy(erledigt = erledigt), jetzt)
    if (m.art == MeldungArt.DEFEKT) {
        val stueck = materialStueck(m.materialId)
        val nochAndere = sichtbareMeldungen.any { it.id != id && !it.erledigt && it.art == MeldungArt.DEFEKT && it.materialId == m.materialId }
        if (erledigt && stueck?.zustand == Zustand.DEFEKT && !nochAndere) d = d.setzen(stueck.copy(zustand = Zustand.IN_ORDNUNG), jetzt)
        if (!erledigt && stueck?.zustand == Zustand.IN_ORDNUNG) d = d.setzen(stueck.copy(zustand = Zustand.DEFEKT), jetzt)
        d.aufgabe(m.aufgabeId)?.let { a ->
            val status = if (erledigt) AufgabeStatus.ERLEDIGT else AufgabeStatus.OFFEN
            if (a.status != status) d = d.aufgabeStatus(a.id, status, heute, jetzt)
        }
    }
    return d
}

/** Aufgabe erledigt: eine zugehörige Defektmeldung gilt dann auch als erledigt. */
fun LeitungDaten.aufgabeErledigt(id: String, heute: LocalDate, jetzt: Long): LeitungDaten {
    var d = aufgabeStatus(id, AufgabeStatus.ERLEDIGT, heute, jetzt)
    val m = d.aufgabe(id)?.meldungId?.let { d.meldung(it) }
    if (m != null && !m.erledigt) d = d.meldungErledigt(m.id, true, heute, jetzt)
    return d
}

/** Belege als an den Kassenwart übergeben markieren. */
fun LeitungDaten.uebergeben(ids: Collection<String>, heute: LocalDate, jetzt: Long): LeitungDaten =
    ids.fold(this) { d, id -> d.beleg(id)?.let { d.setzen(it.copy(uebergeben = true, uebergebenAm = heute), jetzt) } ?: d }

/**
 * Trainer aus dem Schwimmgruppen-Auszug als Personen übernehmen (neu oder Name/Rolle aktualisiert). Von Hand angelegte
 * Personen bleiben unberührt; ausgeschiedene Trainer werden nicht gelöscht.
 */
fun LeitungDaten.trainerUebernehmen(auszug: GruppenAuszug, jetzt: Long, neueId: () -> String): Pair<LeitungDaten, Int> {
    var d = this
    var neu = 0
    for (t in auszug.trainer) {
        val vorhanden = d.personen.firstOrNull { !it.geloescht && (it.trainerId == t.id || it.name.equals(t.name, ignoreCase = true)) }
        if (vorhanden == null) {
            if (!t.aktiv || t.name.isBlank()) continue
            d = d.setzen(Person(neueId(), t.name, t.rang, trainerId = t.id), jetzt)
            neu++
        } else if (vorhanden.trainerId != t.id || vorhanden.name != t.name || (vorhanden.rolle.isBlank() && t.rang.isNotBlank())) {
            d = d.setzen(vorhanden.copy(name = t.name, trainerId = t.id, rolle = vorhanden.rolle.ifBlank { t.rang }), jetzt)
        }
    }
    return d to neu
}
