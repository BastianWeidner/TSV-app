package de.dachapp.schwimmleitung.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.dachapp.module.SubApp
import de.dachapp.schwimmleitung.logic.Pin

/** Registrierung der Schwimmleitung-Unter-App in der Dach-App. */
object SchwimmleitungSubApp : SubApp {
    override val id = "schwimmleitung"
    override val title = "Schwimmleitung"
    override val description = "Hallenbericht für Trainer; mit PIN: Kennzahlen, Sitzungsbericht, Protokolle, Aufgaben, Material, Belege und Scheine."

    @Composable
    override fun Content(onExit: () -> Unit) {
        val vm: LeitungViewModel = viewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        var pinFragen by remember { mutableStateOf(false) }

        // Abgleich nur, solange sichtbar; beim Verlassen schließt der Leitungsbereich wieder.
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle) {
            val beobachter = LifecycleEventObserver { _, e ->
                when (e) {
                    Lifecycle.Event.ON_START -> vm.sichtbar(true)
                    Lifecycle.Event.ON_STOP -> { vm.sichtbar(false); vm.sperren() }
                    else -> Unit
                }
            }
            lifecycle.addObserver(beobachter)
            onDispose {
                lifecycle.removeObserver(beobachter)
                vm.sichtbar(false)
                vm.sperren()
            }
        }

        BackHandler(enabled = state.stapel.isNotEmpty() || state.tab != Tab.HALLE) {
            if (!vm.zurueck()) vm.tab(Tab.HALLE)
        }

        val tabs = Tab.entries.filter { !it.leitung || state.leitungOffen }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val breit = maxWidth >= 840.dp
            val inhalt = @Composable { mod: Modifier ->
                Column(mod) {
                    if (state.beschaeftigt) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Box(Modifier.weight(1f)) { Bildschirm(state, vm, breit) }
                }
            }
            val schloss = @Composable {
                if (!state.leitungOffen) "🔒" to "Leitung" else if (state.e.pinGesetzt) "🔓" to "Sperren" else null
            }
            if (breit) {
                Row(Modifier.fillMaxSize()) {
                    if (state.ansicht == null) {
                        NavigationRail {
                            NavigationRailItem(selected = false, onClick = onExit, icon = { Text("‹") }, label = { Text("Übersicht") })
                            tabs.forEach { t ->
                                NavigationRailItem(selected = state.tab == t, onClick = { vm.tab(t) }, icon = { Text(t.symbol) }, label = { Text(t.titel) })
                            }
                            schloss()?.let { (symbol, text) ->
                                NavigationRailItem(
                                    selected = false,
                                    onClick = { if (state.leitungOffen) vm.sperren() else pinFragen = true },
                                    icon = { Text(symbol) },
                                    label = { Text(text) },
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
                            // Gesperrt bleibt Platz für "Übersicht"; offen führt die Zurück-Taste aus der Unter-App.
                            if (!state.leitungOffen) {
                                NavigationBarItem(selected = false, onClick = onExit, icon = { Text("‹") }, label = { Text("Übersicht") })
                            }
                            tabs.forEach { t ->
                                NavigationBarItem(selected = state.tab == t, onClick = { vm.tab(t) }, icon = { Text(t.symbol) }, label = { Text(t.titel, maxLines = 1) })
                            }
                            if (!state.leitungOffen) {
                                NavigationBarItem(selected = false, onClick = { pinFragen = true }, icon = { Text("🔒") }, label = { Text("Leitung") })
                            }
                        }
                    }
                }
            }
        }
        if (pinFragen) PinDialog(onPin = { vm.entsperren(it) }, onDismiss = { pinFragen = false })
        MessageDialog(state.message, vm::dismissMessage)
    }
}

/** PIN-Eingabe für den Leitungsbereich. [onPin] liefert true, wenn die PIN stimmt. */
@Composable
fun PinDialog(onPin: (String) -> Boolean, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var falsch by remember { mutableStateOf(false) }
    fun pruefen() {
        if (onPin(pin)) onDismiss() else { falsch = true; pin = "" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Leitungs-PIN") },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { v -> pin = v.filter { it.isDigit() }.take(12); falsch = false },
                label = { Text("PIN") },
                singleLine = true,
                isError = falsch,
                supportingText = { if (falsch) Text("Falsche PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
        },
        confirmButton = { TextButton(onClick = ::pruefen, enabled = pin.length >= Pin.MIN_LAENGE) { Text("Öffnen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun Bildschirm(state: UiState, vm: LeitungViewModel, breit: Boolean) {
    when (val a = state.ansicht) {
        null -> when (state.tab) {
            Tab.HALLE -> HalleTab(state, vm, breit)
            Tab.UEBERSICHT -> UebersichtTab(state, vm, breit)
            Tab.AUFGABEN -> AufgabenTab(state, vm)
            Tab.SITZUNGEN -> SitzungenTab(state, vm)
            Tab.MEHR -> MehrTab(state, vm)
        }
        is Ansicht.MeldungNeu -> MeldungNeuScreen(a.art, state, vm)
        is Ansicht.MeldungZeigen -> MeldungScreen(a.id, state, vm)
        Ansicht.Meldungen -> MeldungenScreen(state, vm)
        is Ansicht.BelegBearbeiten -> BelegScreen(a.id, state, vm)
        Ansicht.Belege -> BelegeScreen(state, vm)
        Ansicht.Bericht -> BerichtScreen(state, vm)
        is Ansicht.AufgabeBearbeiten -> AufgabeScreen(a.id, a.sitzungId, state, vm)
        is Ansicht.SitzungBearbeiten -> SitzungScreen(a.id, state, vm)
        Ansicht.MaterialListe -> MaterialListeScreen(state, vm)
        is Ansicht.MaterialBearbeiten -> MaterialScreen(a.id, state, vm)
        Ansicht.Ablage -> AblageScreen(state, vm)
        is Ansicht.DokumentBearbeiten -> DokumentScreen(a.id, state, vm)
        Ansicht.Personen -> PersonenScreen(state, vm)
        is Ansicht.PersonBearbeiten -> PersonScreen(a.id, state, vm)
        is Ansicht.NachweisBearbeiten -> NachweisScreen(a.id, a.personId, state, vm)
        Ansicht.Einstellungen -> EinstellungenScreen(state, vm)
    }
}
