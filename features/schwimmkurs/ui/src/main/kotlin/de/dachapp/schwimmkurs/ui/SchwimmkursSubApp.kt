package de.dachapp.schwimmkurs.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.dachapp.module.SubApp

/** Registrierung der Schwimmkurs-Unter-App in der Dach-App. */
object SchwimmkursSubApp : SubApp {
    override val id = "schwimmkurs"
    override val title = "Schwimmkurse"
    override val description = "Kinder aus Excel/CSV übernehmen, Kurse passend befüllen und Einladungen per Mail verschicken."

    @Composable
    override fun Content(onExit: () -> Unit) {
        val vm: SchwimmkursViewModel = viewModel()
        val state by vm.state.collectAsStateWithLifecycle()

        // Ohne offenen Bildschirm: auf einem anderen Tab zuerst zu "Kinder", danach schließt die Dach-App.
        BackHandler(enabled = state.stapel.isNotEmpty() || state.tab != Tab.KINDER) {
            if (!vm.zurueck()) vm.tab(Tab.KINDER)
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxWidth().fillMaxSize()) {
                if (state.beschaeftigt) LinearProgressIndicator(Modifier.fillMaxWidth())
                Box(Modifier.weight(1f)) {
                    when (val a = state.ansicht) {
                        null -> when (state.tab) {
                            Tab.KINDER -> KinderTab(state, vm)
                            Tab.KURSE -> KurseTab(state, vm)
                            Tab.EINLADUNGEN -> EinladungenTab(state, vm)
                            Tab.EINSTELLUNGEN -> EinstellungenTab(state, vm)
                        }
                        is Ansicht.KindBearbeiten -> KindBearbeitenScreen(a.id, state, vm)
                        is Ansicht.KursBearbeiten -> KursBearbeitenScreen(a.id, state, vm)
                        is Ansicht.KursDetail -> KursDetailScreen(a.id, state, vm)
                        Ansicht.Import -> ImportScreen(state, vm)
                        Ansicht.Vorschlag -> VorschlagScreen(state, vm)
                        is Ansicht.EinladungVorschau -> EinladungVorschauScreen(a.familienSchluessel, state, vm)
                    }
                }
                if (state.ansicht == null) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = false,
                            onClick = onExit,
                            icon = { Text("‹") },
                            label = { Text("Übersicht") },
                        )
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
        MessageDialog(state.message, vm::dismissMessage)
    }
}
