package de.dachapp

import de.dachapp.module.SubApp
import de.dachapp.schwimmgruppen.ui.SchwimmgruppenSubApp
import de.dachapp.schwimmkurs.ui.SchwimmkursSubApp
import de.dachapp.schwimmleitung.ui.SchwimmleitungSubApp

/** Variante "schwimmen": nur die Unter-Apps der Schwimmabteilung, in der Reihenfolge der Übersicht. */
object ModuleRegistry {
    val all: List<SubApp> = listOf(
        SchwimmkursSubApp,
        SchwimmgruppenSubApp,
        SchwimmleitungSubApp,
    )

    init {
        check(all.map { it.id }.toSet().size == all.size) { "Unter-App-IDs müssen eindeutig sein" }
    }
}
