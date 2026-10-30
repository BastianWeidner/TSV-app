package de.dachapp.schwimmgruppen.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.dachapp.schwimmgruppen.logic.Bestand
import de.dachapp.schwimmgruppen.logic.Gruppe
import de.dachapp.schwimmgruppen.logic.GruppenArt
import de.dachapp.schwimmgruppen.logic.Mitglied
import de.dachapp.schwimmgruppen.logic.Trainingszeit
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

fun wochentag(tag: Int): String = DayOfWeek.of(tag).getDisplayName(TextStyle.FULL, Locale.GERMAN)

/** Stammtrainer mit Alterskennzeichen, sonst der alte Freitext. */
fun trainerText(b: Bestand, g: Gruppe): String =
    g.trainerIds.mapNotNull { b.trainer(it) }.joinToString(", ") { b.trainerName(it) }.ifEmpty { g.trainer }

fun zeitenText(g: Gruppe): String =
    g.zeiten.sortedWith(compareBy({ it.wochentag }, { it.beginn }))
        .joinToString(", ") { "${wochentag(it.wochentag).take(2)} ${it.beginn.format(UHRZEIT)}–${it.ende.format(UHRZEIT)}" }

/**
 * Einteilung durch die Trainer: jede Gruppe mit ihren Schwimmern, dazu „ohne Gruppe“. Antippen eines Namens
 * öffnet die Auswahl der Gruppen. Auf dem Tablet stehen die Gruppen als Spalten nebeneinander.
 */
@Composable
fun GruppenTab(state: UiState, vm: SchwimmgruppenViewModel, breit: Boolean) {
    val b = state.bestand
    var einteilen by remember { mutableStateOf<String?>(null) }
    var suche by rememberSaveable { mutableStateOf("") }
    fun passt(m: Mitglied) = suche.isBlank() || m.name.contains(suche.trim(), ignoreCase = true)
    val spalten: List<Pair<Gruppe?, List<Mitglied>>> =
        listOf<Pair<Gruppe?, List<Mitglied>>>(null to b.ohneGruppe().filter(::passt)) +
            b.gruppen.map { g -> g to b.mitgliederIn(g.id).filter(::passt) }

    Page(
        title = "Gruppen",
        onBack = null,
        actions = {
            TextButton(onClick = { vm.oeffnen(Ansicht.Trainer) }) { Text("Trainer") }
            TextButton(onClick = { vm.oeffnen(Ansicht.GruppeBearbeiten(null)) }) { Text("Neue Gruppe") }
        },
    ) {
        Eingabe("Schwimmer suchen", suche, { suche = it })
        if (breit) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                spalten.filter { (g, l) -> g != null || l.isNotEmpty() }.forEach { (g, l) ->
                    Card(Modifier.width(260.dp).fillMaxHeight()) {
                        GruppenKopf(g, l.size, b, vm)
                        LazyColumn(Modifier.padding(horizontal = 12.dp)) {
                            items(l, key = { it.id }) { m -> NamenZeile(m, b) { einteilen = m.id } }
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                spalten.filter { (g, l) -> g != null || l.isNotEmpty() }.forEach { (g, l) ->
                    item(key = g?.id ?: "-") { Card(Modifier.fillMaxWidth().padding(top = 8.dp)) { GruppenKopf(g, l.size, b, vm) } }
                    items(l, key = { (g?.id ?: "-") + it.id }) { m ->
                        Column(Modifier.padding(horizontal = 12.dp)) { NamenZeile(m, b) { einteilen = m.id } }
                    }
                }
            }
        }
    }
    einteilen?.let { id -> b.mitglied(id)?.let { m -> EinteilenDialog(m, b, vm) { einteilen = null } } }
}

@Composable
private fun GruppenKopf(g: Gruppe?, anzahl: Int, b: Bestand, vm: SchwimmgruppenViewModel) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val platz = g?.plaetze?.let { " / $it" }.orEmpty()
            Text((g?.name ?: "Ohne Gruppe") + "  ($anzahl$platz)", style = MaterialTheme.typography.titleMedium)
            if (g != null) {
                Text(
                    listOf(zeitenText(g), trainerText(b, g), if (g.art == GruppenArt.EXTERN) g.art.text else "").filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (g?.plaetze?.let { anzahl > it } == true) Text("Mehr Schwimmer als Plätze", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (g != null) TextButton(onClick = { vm.oeffnen(Ansicht.GruppeBearbeiten(g.id)) }) { Text("✎") }
    }
}

@Composable
private fun NamenZeile(m: Mitglied, b: Bestand, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(m.nachname + ", " + m.vorname + zeichen(m, b.einstellungen.gesundheitAnzeigen), Modifier.weight(1f))
        Text(m.jahrgang?.toString().orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EinteilenDialog(m: Mitglied, b: Bestand, vm: SchwimmgruppenViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(m.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    listOfNotNull(m.jahrgang?.let { "Jahrgang $it" }, "Mehrere Gruppen sind möglich, z. B. Kader und Gruppe II.").joinToString(". "),
                    style = MaterialTheme.typography.bodySmall,
                )
                b.gruppen.forEach { g ->
                    val drin = g.id in m.gruppen
                    Row(Modifier.fillMaxWidth().clickable { vm.einteilen(m.id, g.id, !drin) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = drin, onCheckedChange = { vm.einteilen(m.id, g.id, it) })
                        Text(g.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } },
        dismissButton = {
            TextButton(onClick = { onDismiss(); vm.auswaehlen(m.id); vm.oeffnen(Ansicht.Schwimmer(m.id)) }) { Text("Unterseite") }
        },
    )
}

@Composable
fun GruppeBearbeitenScreen(id: String?, state: UiState, vm: SchwimmgruppenViewModel) {
    val alt = id?.let { state.bestand.gruppe(it) }
    val context = LocalContext.current
    var name by remember(state.ladeNummer) { mutableStateOf(alt?.name.orEmpty()) }
    var art by remember(state.ladeNummer) { mutableStateOf(alt?.art ?: GruppenArt.VEREIN) }
    var trainer by remember(state.ladeNummer) { mutableStateOf(alt?.trainer.orEmpty()) }
    var trainerIds by remember(state.ladeNummer) { mutableStateOf(alt?.trainerIds ?: emptyList()) }
    var plaetze by remember(state.ladeNummer) { mutableStateOf(alt?.plaetze?.toString().orEmpty()) }
    var notiz by remember(state.ladeNummer) { mutableStateOf(alt?.notiz.orEmpty()) }
    var nurZaehlen by remember(state.ladeNummer) { mutableStateOf(alt?.nurZaehlen ?: false) }
    var zeiten by remember(state.ladeNummer) { mutableStateOf(alt?.zeiten ?: emptyList()) }
    var loeschenFragen by remember { mutableStateOf(false) }
    var aufteilenFragen by remember { mutableStateOf(false) }
    val liste = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null && alt != null) vm.gruppenlisteSchreiben(uri, alt.id)
    }

    FormPage(
        title = if (alt == null) "Neue Gruppe" else alt.name,
        onBack = { vm.zurueck() },
        bottom = {
            BigButton("Speichern", enabled = name.isNotBlank(), onClick = {
                vm.gruppeSpeichern(
                    Gruppe(
                        id = alt?.id ?: UUID.randomUUID().toString(), name = name.trim(), art = art,
                        zeiten = zeiten.sortedWith(compareBy({ it.wochentag }, { it.beginn })), trainer = trainer.trim(), trainerIds = trainerIds,
                        plaetze = plaetze.trim().toIntOrNull()?.takeIf { it > 0 }, notiz = notiz.trim(), nurZaehlen = nurZaehlen,
                    ),
                )
            })
        },
    ) {
        Eingabe("Name", name, { name = it }, pflicht = true)
        Auswahl("Art", GruppenArt.entries, art, { it.text }, { a -> art = a; if (a == GruppenArt.EXTERN) nurZaehlen = true })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Anwesenheit nur zählen")
                Text(
                    if (nurZaehlen) "Im Training gibt es nur den Zähler, keine Namen."
                    else "Im Training Namen abhaken; zusätzlich lässt sich ohne Namen zählen.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = nurZaehlen, onCheckedChange = { nurZaehlen = it })
        }
        Abschnitt("Stammtrainer") {
            Text("Gelten für jede Stunde der Gruppe. Einzelne Stunden lassen sich im Training ändern.", style = MaterialTheme.typography.bodySmall)
            TrainerChips(state.bestand, trainerIds, java.time.LocalDate.now()) { id ->
                trainerIds = if (id in trainerIds) trainerIds - id else trainerIds + id
            }
            TextButton(onClick = { vm.oeffnen(Ansicht.Trainer) }) { Text("Trainerliste bearbeiten") }
        }
        // Freitext aus Version 1.2.x, nur zeigen, solange etwas drinsteht.
        if (trainer.isNotBlank()) Eingabe("Trainer (Notiz)", trainer, { trainer = it })
        Eingabe("Plätze (optional)", plaetze, { plaetze = it.filter(Char::isDigit) }, typ = KeyboardType.Number)
        Abschnitt("Trainingszeiten") {
            zeiten.forEachIndexed { i, z ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Auswahl("Tag", (1..7).toList(), z.wochentag, { wochentag(it) }, { t -> zeiten = zeiten.mapIndexed { j, a -> if (j == i) a.copy(wochentag = t) else a } }, Modifier.weight(1f))
                    TextButton(onClick = { uhrzeitWaehlen(context, z.beginn) { u -> zeiten = zeiten.mapIndexed { j, a -> if (j == i) a.copy(beginn = u) else a } } }) {
                        Text(z.beginn.format(UHRZEIT))
                    }
                    Text("–")
                    TextButton(onClick = { uhrzeitWaehlen(context, z.ende) { u -> zeiten = zeiten.mapIndexed { j, a -> if (j == i) a.copy(ende = u) else a } } }) {
                        Text(z.ende.format(UHRZEIT))
                    }
                    TextButton(onClick = { zeiten = zeiten.filterIndexed { j, _ -> j != i } }) { Text("✕") }
                }
            }
            OutlinedButton(onClick = {
                val letzte = zeiten.lastOrNull()
                zeiten = zeiten + (letzte?.copy(wochentag = letzte.wochentag % 7 + 1) ?: Trainingszeit(1, LocalTime.of(17, 0), LocalTime.of(18, 0)))
            }) { Text("Zeit hinzufügen") }
            Text(
                "Geänderte Zeiten gelten für den Kalender ab sofort. Schon erfasste Trainings bleiben mit ihrer alten Uhrzeit erhalten.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Eingabe("Notiz", notiz, { notiz = it }, mehrzeilig = true)
        if (alt != null) {
            OutlinedButton(onClick = { liste.launch(vm.dateiname(alt.name.replace(Regex("[^A-Za-z0-9ÄÖÜäöüß]+"), "_"), "csv")) }, modifier = Modifier.fillMaxWidth()) {
                Text("Gruppenliste als CSV speichern")
            }
            if (alt.zeiten.size > 1) {
                OutlinedButton(onClick = { aufteilenFragen = true }, modifier = Modifier.fillMaxWidth()) { Text("Je Trainingstag eine eigene Gruppe") }
            }
            OutlinedButton(onClick = { loeschenFragen = true }, modifier = Modifier.fillMaxWidth()) { Text("Gruppe löschen") }
        }
    }
    if (aufteilenFragen && alt != null) {
        ConfirmDialog(
            "„${alt.name}“ in ${alt.zeiten.size} Gruppen aufteilen, je Trainingstag eine? Sinnvoll, wenn an den Tagen " +
                "verschiedene Schwimmer kommen. Erfasste Trainings wandern mit.",
            "Aufteilen",
            { vm.gruppeAufteilen(alt.id) },
            { aufteilenFragen = false },
        )
    }
    if (loeschenFragen && alt != null) {
        ConfirmDialog(
            "Gruppe „${alt.name}“ mit allen erfassten Trainings löschen? Die Schwimmer bleiben erhalten.",
            "Löschen",
            { vm.gruppeLoeschen(alt.id) },
            { loeschenFragen = false },
        )
    }
}
