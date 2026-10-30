package de.dachapp.schwimmgruppen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.dachapp.module.SubApp

/** Registrierung der Schwimmgruppen-Unter-App in der Dach-App. */
object SchwimmgruppenSubApp : SubApp {
    override val id = "schwimmgruppen"
    override val title = "Schwimmgruppen"
    override val description = "Mitgliederliste übernehmen, Schwimmer in Gruppen einteilen, Anwesenheit, Zeiten und Erfolge festhalten."

    @Composable
    override fun Content(onExit: () -> Unit) {
        val vm: SchwimmgruppenViewModel = viewModel()
        val state by vm.state.collectAsStateWithLifecycle()

        // Ohne offenen Bildschirm: auf einem anderen Tab zuerst zu "Schwimmer", danach schließt die Dach-App.
        BackHandler(enabled = state.stapel.isNotEmpty() || state.tab != Tab.SCHWIMMER) {
            if (!vm.zurueck()) vm.tab(Tab.SCHWIMMER)
        }

        // Tablet (ab 840 dp Breite): Navigation links, Listen und Details nebeneinander.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val breit = maxWidth >= 840.dp
            val inhalt = @Composable { mod: Modifier ->
                Column(mod) {
                    if (state.beschaeftigt) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Box(Modifier.weight(1f)) { Bildschirm(state, vm, breit) }
                }
            }
            if (breit) {
                Row(Modifier.fillMaxSize()) {
                    if (state.ansicht == null) {
                        NavigationRail {
                            NavigationRailItem(selected = false, onClick = onExit, icon = { Text("‹") }, label = { Text("Übersicht") })
                            Tab.entries.forEach { t ->
                                NavigationRailItem(
                                    selected = state.tab == t,
                                    onClick = { vm.tab(t) },
                                    icon = { Text(t.symbol) },
                                    label = { Text(t.titel) },
                                )
                            }
                        }
                    }
                    inhalt(Modifier.weight(1f).fillMaxSize())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    inhalt(Modifier.weight(1f).fillMaxWidth())
                    if (state.ansicht == null) {
                        NavigationBar {
                            NavigationBarItem(selected = false, onClick = onExit, icon = { Text("‹") }, label = { Text("Übersicht") })
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = state.tab == t,
                                    onClick = { vm.tab(t) },
                                    icon = { Text(t.symbol) },
                                    label = { Text(t.titel) },
                                )
                            }
                        }
                    }
                }
            }
        }
        MessageDialog(state.message, vm::dismissMessage)
        if (state.message == null && state.nichtInListe.isNotEmpty()) FehlendeDialog(state, vm)
    }
}

@Composable
private fun Bildschirm(state: UiState, vm: SchwimmgruppenViewModel, breit: Boolean) {
    when (val a = state.ansicht) {
        null -> when (state.tab) {
            Tab.SCHWIMMER -> SchwimmerTab(state, vm, breit)
            Tab.GRUPPEN -> GruppenTab(state, vm, breit)
            Tab.TRAINING -> TrainingTab(state, vm)
            Tab.MEHR -> MehrTab(state, vm)
        }
        is Ansicht.Schwimmer -> SchwimmerScreen(a.id, state, vm)
        is Ansicht.SchwimmerBearbeiten -> SchwimmerBearbeitenScreen(a.id, state, vm)
        is Ansicht.GruppeBearbeiten -> GruppeBearbeitenScreen(a.id, state, vm)
        is Ansicht.Training -> TrainingScreen(a.vorlage, state, vm, breit)
        Ansicht.Halle -> HalleScreen(state, vm, breit)
        Ansicht.Statistik -> StatistikScreen(state, vm)
        Ansicht.Trainer -> TrainerListeScreen(state, vm)
        Ansicht.Import -> ImportScreen(state, vm)
    }
}
