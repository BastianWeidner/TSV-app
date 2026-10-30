package de.dachapp.schwimmgruppen.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.dachapp.schwimmgruppen.logic.Bestand
import de.dachapp.schwimmgruppen.logic.Einheit
import de.dachapp.schwimmgruppen.logic.Statistik
import de.dachapp.schwimmgruppen.logic.Trainer
import de.dachapp.schwimmgruppen.logic.TrainerRang
import java.time.LocalDate
import java.util.UUID

/** Kleines Abzeichen: „18+“, „U18“ oder „Alter ?“, wenn kein Geburtsdatum bekannt ist. */
@Composable
fun Ue18(volljaehrig: Boolean?) {
    val (text, farbe) = when (volljaehrig) {
        true -> "18+" to MaterialTheme.colorScheme.primaryContainer
        false -> "U18" to MaterialTheme.colorScheme.tertiaryContainer
        null -> "Alter ?" to MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = farbe, shape = MaterialTheme.shapes.small) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

fun ue18Text(v: Boolean?): String = when (v) { true -> "18+"; false -> "U18"; null -> "Alter ?" }

/** Trainerliste: Trainer, Jungtrainer und Helfer mit Schein, Alter und Einsätzen. */
@Composable
fun TrainerListeScreen(state: UiState, vm: SchwimmgruppenViewModel) {
    val b = state.bestand
    val heute = LocalDate.now()
    var bearbeiten by remember { mutableStateOf<Trainer?>(null) }
    var ausMitgliedern by remember { mutableStateOf(false) }
    val einsaetze = Statistik.trainerEinsaetze(b, heute.minusMonths(3), heute, heute)
    val liste = b.trainer.sortedWith(compareBy<Trainer>({ !it.aktiv }, { it.rang.ordinal }, { b.trainerName(it).lowercase() }))

    Page(
        title = "Trainer (${b.trainer.count { it.aktiv }})",
        onBack = { vm.zurueck() },
        bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { ausMitgliedern = true }, modifier = Modifier.weight(1f)) { Text("Aus Mitgliedern") }
                OutlinedButton(onClick = { bearbeiten = Trainer("", name = "") }, modifier = Modifier.weight(1f)) { Text("Ohne Mitgliedschaft") }
            }
        },
    ) {
        if (liste.isEmpty()) {
            Leer("Noch keine Trainer. Wähle sie aus der Mitgliederliste aus und lege Rang und Schein fest. Danach lassen sie sich Gruppen und einzelnen Stunden zuweisen.")
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(liste, key = { it.id }) { t ->
                Card(Modifier.fillMaxWidth().clickable { bearbeiten = t }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.trainerName(t) + if (t.aktiv) "" else " (inaktiv)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOf(t.rang.text, t.schein, gruppenVon(b, t)).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("${einsaetze[t.id] ?: 0} Einsätze in 3 Monaten", style = MaterialTheme.typography.bodySmall)
                        }
                        Ue18(b.volljaehrig(t, heute))
                    }
                }
            }
        }
    }
    if (ausMitgliedern) {
        MitgliedWahlDialog(b, onWahl = { m ->
            ausMitgliedern = false
            val volljaehrig = m.geburtsdatum?.let { java.time.Period.between(it, heute).years >= 18 }
            bearbeiten = Trainer("", mitgliedId = m.id, rang = if (volljaehrig == false) TrainerRang.JUNGTRAINER else TrainerRang.TRAINER)
        }) { ausMitgliedern = false }
    }
    bearbeiten?.let { t -> TrainerDialog(t, b, vm) { bearbeiten = null } }
}

private fun gruppenVon(b: Bestand, t: Trainer): String = b.gruppen.filter { t.id in it.trainerIds }.joinToString(", ") { it.name }

@Composable
private fun MitgliedWahlDialog(b: Bestand, onWahl: (de.dachapp.schwimmgruppen.logic.Mitglied) -> Unit, onDismiss: () -> Unit) {
    var suche by remember { mutableStateOf("") }
    val schon = b.trainer.mapNotNull { it.mitgliedId }.toSet()
    val kandidaten = b.mitglieder.filter { it.id !in schon && (suche.isBlank() || it.name.contains(suche.trim(), ignoreCase = true)) }
        .sortedWith(Bestand.NACH_NAME)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trainer aus Mitgliedern") },
        text = {
            Column(Modifier.fillMaxHeight(0.7f)) {
                Eingabe("Suchen", suche, { suche = it })
                LazyColumn {
                    items(kandidaten, key = { it.id }) { m ->
                        Text(
                            m.name + (m.jahrgang?.let { " (Jg. $it)" } ?: ""),
                            Modifier.fillMaxWidth().clickable { onWahl(m) }.padding(vertical = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun TrainerDialog(t: Trainer, b: Bestand, vm: SchwimmgruppenViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val mitglied = t.mitgliedId?.let { b.mitglied(it) }
    var name by remember { mutableStateOf(t.name) }
    var geburt by remember { mutableStateOf(t.geburtsdatum?.format(DATUM).orEmpty()) }
    var rang by remember { mutableStateOf(t.rang) }
    var schein by remember { mutableStateOf(t.schein) }
    var telefon by remember { mutableStateOf(t.telefon.ifEmpty { mitglied?.telefon.orEmpty() }) }
    var aktiv by remember { mutableStateOf(t.aktiv) }
    var loeschenFragen by remember { mutableStateOf(false) }
    val gebFehler = geburt.isNotBlank() && datumOderNull(geburt) == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(mitglied?.name ?: if (t.id.isEmpty()) "Neuer Trainer" else t.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mitglied == null) {
                    Eingabe("Name", name, { name = it }, pflicht = true)
                    DatumEingabe("Geburtsdatum", geburt, { geburt = it }, context, if (gebFehler) "Datum nicht erkannt" else null)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(mitglied.geburtsdatum?.let { "geb. ${it.format(DATUM)}" } ?: "Geburtsdatum fehlt in der Mitgliederliste")
                        Ue18(b.volljaehrig(t, LocalDate.now()))
                    }
                }
                Auswahl("Rang", TrainerRang.entries, rang, { it.text }, { rang = it })
                Eingabe("Schein / Qualifikation", schein, { schein = it }, hilfe = "z. B. Trainer C Schwimmen, Rettungsschwimmer Silber")
                Eingabe("Telefon", telefon, { telefon = it }, typ = KeyboardType.Phone)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aktiv", Modifier.weight(1f))
                    Switch(checked = aktiv, onCheckedChange = { aktiv = it })
                }
                if (t.id.isNotEmpty()) TextButton(onClick = { loeschenFragen = true }) { Text("Aus der Trainerliste löschen") }
            }
        },
        confirmButton = {
            TextButton(enabled = (mitglied != null || name.isNotBlank()) && !gebFehler, onClick = {
                vm.trainerSpeichern(
                    t.copy(
                        id = t.id.ifEmpty { UUID.randomUUID().toString() },
                        name = if (mitglied == null) name.trim() else t.name,
                        geburtsdatum = if (mitglied == null) datumOderNull(geburt) else t.geburtsdatum,
                        rang = rang, schein = schein.trim(), telefon = telefon.trim(), aktiv = aktiv,
                    ),
                )
                onDismiss()
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
    if (loeschenFragen) {
        ConfirmDialog(
            "Aus der Trainerliste löschen? Der Trainer verschwindet auch aus Gruppen und Stunden. Wer nur pausiert, besser auf inaktiv setzen.",
            "Löschen",
            { vm.trainerLoeschen(t.id); onDismiss() },
            { loeschenFragen = false },
        )
    }
}

/** Auswahl der Trainer als Chips mit Alters-Kennzeichen; für Stammtrainer einer Gruppe und einzelne Stunden. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrainerChips(b: Bestand, gewaehlt: List<String>, stichtag: LocalDate, onUmschalten: (String) -> Unit) {
    val alle = b.trainerListe() + gewaehlt.mapNotNull { b.trainer(it) }.filter { !it.aktiv }
    if (alle.isEmpty()) {
        Text("Noch keine Trainer angelegt (Gruppen → Trainer).", style = MaterialTheme.typography.bodySmall)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        alle.forEach { t ->
            FilterChip(
                selected = t.id in gewaehlt,
                onClick = { onUmschalten(t.id) },
                label = { Text("${b.trainerName(t)} · ${ue18Text(b.volljaehrig(t, stichtag))}") },
            )
        }
    }
}

/**
 * Trainer einer Stunde: Chips zum An- und Abwählen, darunter je eingeteiltem Trainer „vertreten durch“.
 * Hinweis, wenn keiner volljährig ist.
 */
@Composable
fun StundenTrainer(b: Bestand, e: Einheit, onUmschalten: (String) -> Unit, onVertreten: (String, String?) -> Unit) {
    val gewaehlt = b.eingeteilt(e)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Trainer" + if (e.trainer == null && gewaehlt.isNotEmpty()) " (Stammtrainer der Gruppe)" else "",
            style = MaterialTheme.typography.titleSmall,
        )
        TrainerChips(b, gewaehlt, e.datum, onUmschalten)
        gewaehlt.mapNotNull { b.trainer(it) }.forEach { t ->
            val vertreter = e.vertretungen[t.id]?.let { b.trainer(it) }
            val optionen = listOf<Trainer?>(null) + b.trainerListe().filter { it.id != t.id && it.id !in gewaehlt }
            Auswahl(
                b.trainerName(t) + if (vertreter != null) " vertreten durch" else "",
                optionen,
                vertreter,
                { v -> v?.let { "${b.trainerName(it)} · ${ue18Text(b.volljaehrig(it, e.datum))}" } ?: "leitet selbst" },
                { v -> onVertreten(t.id, v?.id) },
            )
        }
        if (b.ohneVolljaehrige(e)) {
            Text("⚠ Kein volljähriger Trainer eingeteilt.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
