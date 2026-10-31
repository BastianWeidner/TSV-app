package de.dachapp.schwimmgruppen.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import de.dachapp.module.AbzeichenPruefung
import de.dachapp.module.Auswahl
import de.dachapp.module.Pruefling
import de.dachapp.schwimmgruppen.logic.ErfolgArt

/** Abzeichen-Prüfung für eine Gruppe (oder alle aktiven Schwimmer); fertige Abzeichen landen als Erfolg. */
@Composable
fun PruefungScreen(start: String?, state: UiState, vm: SchwimmgruppenViewModel) {
    val b = state.bestand
    var gruppeId by rememberSaveable { mutableStateOf(start) }
    val gruppe = gruppeId?.let { b.gruppe(it) }
    val leute = b.mitglieder.filter { it.aktiv && (gruppe == null || gruppe.id in it.gruppen) }
    AbzeichenPruefung(
        titel = "Abzeichen · " + (gruppe?.name ?: "alle"),
        prueflinge = leute.map { m -> Pruefling(m.id, m.name, m.pruefung, m.erfolge.filter { it.art == ErfolgArt.ABZEICHEN }.map { it.text }.toSet()) },
        onBack = { vm.zurueck() },
        onSetzen = vm::pruefungSetzen,
    ) {
        Auswahl<String?>("Gruppe", listOf<String?>(null) + b.gruppen.map { it.id }, gruppeId, { id -> id?.let { b.gruppe(it)?.name } ?: "alle aktiven" }, { gruppeId = it })
    }
}
