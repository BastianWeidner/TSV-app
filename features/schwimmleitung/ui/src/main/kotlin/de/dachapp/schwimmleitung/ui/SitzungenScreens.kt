package de.dachapp.schwimmleitung.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.dachapp.schwimmleitung.logic.AufgabeStatus
import de.dachapp.schwimmleitung.logic.Format
import de.dachapp.schwimmleitung.logic.Protokoll
import de.dachapp.schwimmleitung.logic.Sitzung
import de.dachapp.schwimmleitung.logic.SitzungArt
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun SitzungenTab(state: UiState, vm: LeitungViewModel) {
    val d = state.daten
    Page(
        title = "Sitzungen",
        onBack = null,
        bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton("Trainersitzung", { vm.sitzungNeu(SitzungArt.TRAINER) }, modifier = Modifier.weight(1f))
                BigButton("Leitungssitzung", { vm.sitzungNeu(SitzungArt.LEITUNG) }, modifier = Modifier.weight(1f))
            }
        },
    ) {
        val liste = d.sichtbareSitzungen
        if (liste.isEmpty()) Leer("Noch keine Sitzungen. Unten eine neue anlegen; das Protokoll folgt der Gliederung Datum, Anwesenheiten, Leitungsbericht, offene Runde.")
        LazyColumn(Modifier.fillMaxWidth()) {
            items(liste, key = { it.id }) { s ->
                val offen = d.sichtbareAufgaben.count { it.sitzungId == s.id && it.offen }
                Column(Modifier.fillMaxWidth().clickable { vm.oeffnen(Ansicht.SitzungBearbeiten(s.id)) }.padding(vertical = 10.dp)) {
                    Text("${s.art.text}, ${Format.datum(s.datum)}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(
                            "${s.anwesend.size + gaeste(s.gaeste).size} anwesend",
                            s.ort.ifBlank { null },
                            if (offen > 0) "$offen offene Aufgaben" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

private fun gaeste(text: String) = text.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }

/** Protokoll bearbeiten. Gespeichert wird beim Verlassen und vor jedem Wechsel auf einen anderen Bildschirm. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SitzungScreen(id: String, state: UiState, vm: LeitungViewModel) {
    val context = LocalContext.current
    val d = state.daten
    val alt = d.sitzung(id) ?: return Leer("Sitzung nicht gefunden.")
    var art by rememberSaveable { mutableStateOf(alt.art) }
    var datum by rememberSaveable { mutableStateOf(Format.datum(alt.datum)) }
    var beginn by rememberSaveable { mutableStateOf(alt.beginn?.toString()?.take(5).orEmpty()) }
    var ort by rememberSaveable { mutableStateOf(alt.ort) }
    var anwesend by rememberSaveable { mutableStateOf(alt.anwesend) }
    var entschuldigt by rememberSaveable { mutableStateOf(alt.entschuldigt) }
    var gaesteText by rememberSaveable { mutableStateOf(alt.gaeste) }
    var bericht by rememberSaveable { mutableStateOf(alt.leitungsbericht) }
    var kennzahlen by rememberSaveable(alt.kennzahlen) { mutableStateOf(alt.kennzahlen) }
    var runde by rememberSaveable { mutableStateOf(alt.offeneRunde) }
    var protokollant by rememberSaveable { mutableStateOf(alt.protokollant) }
    var loeschen by remember { mutableStateOf(false) }
    val tag = datumOderNull(datum)
    val uhr = runCatching { if (beginn.isBlank()) null else LocalTime.parse(beginn.padStart(5, '0').replace('.', ':')) }

    fun stand() = alt.copy(
        art = art, datum = tag ?: alt.datum, beginn = uhr.getOrNull() ?: alt.beginn.takeIf { beginn.isNotBlank() }, ort = ort.trim(),
        anwesend = anwesend, entschuldigt = entschuldigt, gaeste = gaesteText.trim(), leitungsbericht = bericht,
        kennzahlen = kennzahlen, offeneRunde = runde, protokollant = protokollant.trim(),
    )
    fun sichern() {
        val neu = stand()
        if (neu != alt) vm.speichern(neu)
    }
    fun weiter(a: Ansicht) { sichern(); vm.oeffnen(a) }
    BackHandler { sichern(); vm.zurueck() }

    FormPage(
        title = "${art.text} ${Format.datum(tag ?: alt.datum)}",
        onBack = { sichern(); vm.zurueck() },
        actions = { TextButton(onClick = { loeschen = true }) { Text("Löschen") } },
        bottom = {
            BigButton("Protokoll als PDF teilen", {
                sichern()
                val s = stand()
                val mitStand = d.copy(sitzungen = d.sitzungen.map { if (it.id == s.id) s else it })
                Dateien.protokollTeilen(context, d.e.verein, Protokoll.titel(s), Protokoll.abschnitte(mitStand, s), s.protokollant)
            })
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SitzungArt.entries.forEach { a -> FilterChip(selected = art == a, onClick = { art = a }, label = { Text(a.text) }) }
        }

        Abschnitt("Datum") {
            DatumEingabe("Datum", datum, { datum = it }, context, fehler = if (tag == null) "Datum wie 21.10.2026" else null)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Eingabe("Beginn", beginn, { beginn = it }, Modifier.weight(1f), KeyboardType.Number, fehler = if (uhr.isFailure) "Uhrzeit wie 19:30" else null)
                Eingabe("Ort", ort, { ort = it }, Modifier.weight(2f))
            }
        }

        Abschnitt("Anwesenheiten") {
            Text("Antippen: anwesend → entschuldigt → nicht da", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val personen = (d.aktivePersonen + (anwesend + entschuldigt).mapNotNull { d.person(it) }.filter { !it.aktiv }).distinctBy { it.id }
                personen.forEach { p ->
                    val da = p.id in anwesend
                    val ent = p.id in entschuldigt
                    FilterChip(
                        selected = da || ent,
                        onClick = {
                            when {
                                da -> { anwesend = anwesend - p.id; entschuldigt = entschuldigt + p.id }
                                ent -> entschuldigt = entschuldigt - p.id
                                else -> anwesend = anwesend + p.id
                            }
                        },
                        label = { Text(p.name + if (ent) " (entsch.)" else "") },
                    )
                }
            }
            if (d.aktivePersonen.isEmpty()) {
                TextButton(onClick = { weiter(Ansicht.Personen) }) { Text("Personen anlegen") }
            }
            Eingabe("Weitere Anwesende", gaesteText, { gaesteText = it }, hilfe = "Mit Komma getrennt, z. B. Gäste oder Eltern")
            Text("${anwesend.size + gaeste(gaesteText).size} anwesend, ${entschuldigt.size} entschuldigt", style = MaterialTheme.typography.bodySmall)
        }

        Abschnitt("Leitungsbericht") {
            Eingabe("Bericht der Leitung", bericht, { bericht = it }, mehrzeilig = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { kennzahlen = vm.kennzahlenText(alt.id, tag ?: alt.datum) }, modifier = Modifier.weight(1f)) {
                    Text(if (kennzahlen.isBlank()) "Kennzahlen einfügen" else "Kennzahlen neu")
                }
                if (kennzahlen.isNotBlank()) OutlinedButton(onClick = { kennzahlen = "" }) { Text("Entfernen") }
            }
            if (kennzahlen.isNotBlank()) Eingabe("Kennzahlen (seit der vorigen Sitzung)", kennzahlen, { kennzahlen = it }, mehrzeilig = true)
        }

        Abschnitt("Offene Runde") {
            Eingabe("Themen, Wünsche, Beschlüsse", runde, { runde = it }, mehrzeilig = true)
        }

        Abschnitt("Aufgaben aus der Sitzung") {
            val heute = LocalDate.now()
            val aufgaben = d.sichtbareAufgaben.filter { it.sitzungId == alt.id }.sortedBy { it.titel.lowercase() }
            aufgaben.forEach { a ->
                AufgabeZeile(a, state, heute, { vm.aufgabeStatus(a.id, if (a.offen) AufgabeStatus.ERLEDIGT else AufgabeStatus.OFFEN) }) {
                    weiter(Ansicht.AufgabeBearbeiten(a.id))
                }
            }
            OutlinedButton(onClick = { weiter(Ansicht.AufgabeBearbeiten(null, alt.id)) }, modifier = Modifier.fillMaxWidth()) { Text("Aufgabe hinzufügen") }
        }

        Eingabe("Protokoll geführt von", protokollant, { protokollant = it })
    }
    if (loeschen) ConfirmDialog("Diese Sitzung samt Protokoll löschen? Aufgaben daraus bleiben.", "Löschen", { vm.sitzungLoeschen(alt.id) }, { loeschen = false })
}
