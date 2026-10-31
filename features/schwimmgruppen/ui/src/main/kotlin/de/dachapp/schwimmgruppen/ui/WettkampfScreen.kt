package de.dachapp.schwimmgruppen.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.dachapp.module.BigButton
import de.dachapp.module.Leer
import de.dachapp.module.Page
import de.dachapp.module.Schalter
import de.dachapp.schwimmgruppen.logic.Ausschreibung
import de.dachapp.schwimmgruppen.logic.Dsv
import de.dachapp.schwimmgruppen.logic.ErgebnisTreffer
import de.dachapp.schwimmgruppen.logic.Interesse
import de.dachapp.schwimmgruppen.logic.Mitglied
import de.dachapp.schwimmgruppen.logic.Zeiten

private val DATEITYPEN = arrayOf("*/*")

/**
 * Wettkämpfe: Ausschreibung (DSV 7/8 oder LENEX) öffnen, Starts je Schwimmer wählen und die Vereinsmeldeliste als
 * DSV-Datei speichern; Ergebnislisten einlesen und Zeiten und Platzierungen bei den Schwimmern speichern.
 */
@Composable
fun WettkampfScreen(state: UiState, vm: SchwimmgruppenViewModel) {
    val wk = state.wettkampf
    val oeffnen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::wettkampfDateiLesen) }
    val speichern = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let(vm::meldungSchreiben)
    }
    val a = wk.ausschreibung
    Page(
        title = "Wettkampf",
        onBack = { vm.zurueck() },
        bottom = {
            OutlinedButton(onClick = { oeffnen.launch(DATEITYPEN) }, modifier = Modifier.fillMaxWidth()) {
                Text("Ausschreibung oder Ergebnisse öffnen")
            }
            if (wk.ergebnisse != null) {
                val neu = wk.treffer.count { it.mitgliedId != null && !it.schonDa }
                BigButton("$neu Ergebnisse übernehmen", vm::ergebnisseUebernehmen, enabled = neu > 0)
            } else if (a != null) {
                BigButton("Meldedatei speichern (${wk.anzahlStarts} Starts)", {
                    val fehlt = vm.meldungFehlt()
                    if (fehlt != null) vm.showMessage(fehlt) else speichern.launch(vm.meldeDateiname())
                })
            }
        },
    ) {
        when {
            wk.ergebnisse != null -> ErgebnisListe(state, wk.treffer, vm)
            a != null -> MeldeListe(state, a, vm)
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Leer("Noch keine Datei geöffnet.")
                Text(
                    "Ausschreibung: die Wettkampfdefinitionsliste (.DSV7, .DSV8, .DSV8z) oder die LENEX-Einladung (.lef, .lxf) " +
                        "vom Ausrichter. Danach je Schwimmer die Starts antippen und die Meldedatei speichern; sie geht per Mail " +
                        "an die Meldeadresse oder ins Meldeportal.\n\nErgebnisse: Wettkampf- oder Vereinsergebnisliste (DSV oder LENEX). " +
                        "Zeiten und Platz 1–3 landen bei den Schwimmern.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun MeldeListe(state: UiState, a: Ausschreibung, vm: SchwimmgruppenViewModel) {
    val wk = state.wettkampf
    val schwimmer = state.bestand.mitglieder
        .filter { it.aktiv && (wk.alle || it.interesse == Interesse.JA || it.id in wk.starts) }
        .sortedWith(compareBy({ it.nachname }, { it.vorname }))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(a.veranstaltung, style = MaterialTheme.typography.titleMedium)
                val tage = a.abschnitte.mapNotNull { it.datum }.distinct().sorted()
                    .joinToString(", ") { "%02d.%02d.%d".format(it.dayOfMonth, it.monthValue, it.year) }
                Text(listOf(a.ort, "${a.bahn}-m-Bahn", tage).filter { it.isNotBlank() }.joinToString(" · "))
                if (a.meldeschluss.isNotBlank()) Text("Meldeschluss: ${a.meldeschluss}")
                if (a.meldeadresse.isNotBlank()) Text("Meldung an: ${a.meldeadresse}")
                a.einzelMeldegeldCent?.let { cent ->
                    Text("Meldegeld: %.2f € je Start, bisher %.2f €".format(cent / 100.0, cent * wk.anzahlStarts / 100.0).replace('.', ','))
                }
                Schalter("Alle aktiven Schwimmer zeigen", wk.alle, vm::wettkampfAlle, hilfe = "Sonst nur die mit „ja“ beim Wettkampfinteresse.")
                OutlinedButton(onClick = vm::ausschreibungSchliessen) { Text("Ausschreibung schließen") }
            }
        }
        if (schwimmer.isEmpty()) item { Leer("Niemand mit Wettkampfinteresse „ja“. Oben „Alle aktiven Schwimmer zeigen“ einschalten.") }
        items(schwimmer, key = { it.id }) { m -> SchwimmerStarts(m, a, wk.starts[m.id].orEmpty(), vm) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SchwimmerStarts(m: Mitglied, a: Ausschreibung, starts: Map<Int, Int>, vm: SchwimmgruppenViewModel) {
    val g = Dsv.geschlecht(m.geschlecht)
    val passend = if (g.isEmpty()) emptyList() else a.passendFuer(g, m.jahrgang)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(m.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(listOfNotNull(m.jahrgang?.toString(), g.ifEmpty { null }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            when {
                g.isEmpty() -> Text("Geschlecht fehlt, bitte beim Schwimmer eintragen.", color = MaterialTheme.colorScheme.error)
                passend.isEmpty() -> Text("Keine passenden Einzelwettkämpfe.", style = MaterialTheme.typography.bodySmall)
            }
            if (m.jahrgang == null) Text("Geburtsdatum fehlt; Jahrgänge werden nicht geprüft.", color = MaterialTheme.colorScheme.error)
            if (m.dsvId.isBlank() && starts.isNotEmpty()) {
                Text("Ohne DSV-ID: der Ausrichter muss den Schwimmer selbst zuordnen.", style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                passend.forEach { w ->
                    val zeit = starts[w.nr]
                    val pflicht = a.pflichtzeit(w, g, m.jahrgang)
                    val zuLangsam = zeit != null && pflicht != null && (zeit == 0 || zeit > pflicht)
                    FilterChip(
                        selected = zeit != null,
                        onClick = { vm.startUmschalten(m.id, w) },
                        label = {
                            val text = "${w.nr}: ${w.streckenText}" + when {
                                zeit == null -> ""
                                zeit == 0 -> " · o. Zeit"
                                else -> " · ${Zeiten.text(zeit)}"
                            }
                            Text(if (zuLangsam) "$text ⚠" else text)
                        },
                    )
                }
            }
            val warnungen = passend.filter { w ->
                val zeit = starts[w.nr] ?: return@filter false
                val pflicht = a.pflichtzeit(w, g, m.jahrgang) ?: return@filter false
                zeit == 0 || zeit > pflicht
            }
            warnungen.forEach { w ->
                Text("⚠ ${w.streckenText}: Pflichtzeit ${Zeiten.text(a.pflichtzeit(w, g, m.jahrgang)!!)} nicht nachgewiesen", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ErgebnisListe(state: UiState, treffer: List<ErgebnisTreffer>, vm: SchwimmgruppenViewModel) {
    val liste = state.wettkampf.ergebnisse ?: return
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(liste.veranstaltung, style = MaterialTheme.typography.titleMedium)
                Text("${treffer.size} Starts des eigenen Vereins, ${treffer.count { it.mitgliedId == null }} ohne passenden Schwimmer.")
                OutlinedButton(onClick = vm::ergebnisseVerwerfen) { Text("Verwerfen") }
            }
        }
        items(treffer) { t ->
            val e = t.ergebnis
            val m = t.mitgliedId?.let { state.bestand.mitglied(it) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row {
                        Text(m?.name ?: e.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            when {
                                e.grund.isNotEmpty() -> e.grund
                                e.platz > 0 -> "${e.platz}. Platz"
                                else -> ""
                            },
                        )
                    }
                    Text("${e.strecke} · ${if (e.hundertstel > 0) Zeiten.text(e.hundertstel) else "–"}" + if (e.wertung.isNotBlank()) " · ${e.wertung}" else "")
                    when {
                        m == null -> Text("Kein Schwimmer mit diesem Namen gefunden, wird übersprungen.", color = MaterialTheme.colorScheme.error)
                        t.schonDa -> Text("Schon gespeichert.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
