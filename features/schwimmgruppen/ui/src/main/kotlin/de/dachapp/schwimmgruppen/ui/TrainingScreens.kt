package de.dachapp.schwimmgruppen.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.dachapp.schwimmgruppen.logic.Bestand
import de.dachapp.schwimmgruppen.logic.Einheit
import de.dachapp.schwimmgruppen.logic.Gruppe
import de.dachapp.schwimmgruppen.logic.Kalender
import de.dachapp.schwimmgruppen.logic.Mitglied
import de.dachapp.schwimmgruppen.logic.Statistik
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TAG_LANG = DateTimeFormatter.ofPattern("EEEE, dd.MM.", Locale.GERMAN)
private val MONAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)

private fun status(e: Einheit): String = when {
    e.ausgefallen -> "ausgefallen"
    e.anzahl > 0 -> "${e.anzahl} da"
    e.datum.isAfter(LocalDate.now()) -> ""
    else -> "nicht erfasst"
}.let { s -> listOf(s, e.vermerk, if (e.zusatz) "Zusatztermin" else "").filter { it.isNotBlank() }.joinToString(" · ") }

/** Wochenkalender aller Trainings mit Anwesenheit, Ausfällen und Vermerken. */
@Composable
fun TrainingTab(state: UiState, vm: SchwimmgruppenViewModel) {
    val b = state.bestand
    val montag = Kalender.montag(state.woche)
    val heute = LocalDate.now()
    val einheiten = Kalender.woche(b, montag)
    var zusatz by remember { mutableStateOf(false) }

    Page(
        title = "Training",
        onBack = null,
        bottom = {
            BigButton("Hallenmodus", { vm.oeffnen(Ansicht.Halle) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.oeffnen(Ansicht.Statistik) }, modifier = Modifier.weight(1f)) { Text("Statistik") }
                OutlinedButton(onClick = { zusatz = true }, modifier = Modifier.weight(1f)) { Text("Zusatztermin") }
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.woche(montag.minusWeeks(1)) }) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
            Text(
                "${montag.format(DATUM_KURZ)} – ${montag.plusDays(6).format(DATUM_KURZ)}",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clickable { vm.woche(heute) },
            )
            TextButton(onClick = { vm.woche(montag.plusWeeks(1)) }) { Text("›", style = MaterialTheme.typography.headlineSmall) }
        }
        if (montag != Kalender.montag(heute)) TextButton(onClick = { vm.woche(heute) }) { Text("Zur aktuellen Woche") }
        if (einheiten.isEmpty()) Leer("In dieser Woche ist kein Training geplant.")
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            einheiten.groupBy { it.datum }.forEach { (tag, liste) ->
                item(key = tag.toString()) {
                    Text(
                        tag.format(TAG_LANG) + if (tag == heute) "  (heute)" else "",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (tag == heute) FontWeight.Bold else null,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(liste, key = { it.schluessel }) { e ->
                    val g = b.gruppe(e.gruppeId)
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { vm.oeffnen(Ansicht.Training(e)) },
                        colors = if (e.ausgefallen) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer) else CardDefaults.cardColors(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${e.beginn.format(UHRZEIT)}–${e.ende.format(UHRZEIT)}", Modifier.width(100.dp), style = MaterialTheme.typography.bodyMedium)
                            Column(Modifier.weight(1f)) {
                                Text(g?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                                val tr = b.trainerText(e)
                                if (tr.isNotBlank()) {
                                    Text(
                                        (if (b.ohneVolljaehrige(e)) "⚠ " else "") + tr,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (b.ohneVolljaehrige(e)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                val st = status(e)
                                if (st.isNotBlank()) Text(st, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
    if (zusatz) ZusatzDialog(b, state.woche.takeIf { !it.isBefore(montag) } ?: montag, vm) { zusatz = false }
}

@Composable
private fun ZusatzDialog(b: Bestand, start: LocalDate, vm: SchwimmgruppenViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var gruppe by remember { mutableStateOf(b.gruppen.firstOrNull()) }
    var datum by remember { mutableStateOf(start.format(DATUM)) }
    var beginn by remember { mutableStateOf(gruppe?.zeiten?.firstOrNull()?.beginn ?: LocalTime.of(17, 0)) }
    var ende by remember { mutableStateOf(gruppe?.zeiten?.firstOrNull()?.ende ?: LocalTime.of(18, 0)) }
    var vermerk by remember { mutableStateOf("") }
    val tag = datumOderNull(datum)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zusatztermin") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                gruppe?.let { g -> Auswahl("Gruppe", b.gruppen, g, Gruppe::name, { gruppe = it }) }
                DatumEingabe("Datum", datum, { datum = it }, context, if (datum.isNotBlank() && tag == null) "Datum nicht erkannt" else null, pflicht = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Zeit", Modifier.weight(1f))
                    TextButton(onClick = { uhrzeitWaehlen(context, beginn) { beginn = it } }) { Text(beginn.format(UHRZEIT)) }
                    Text("–")
                    TextButton(onClick = { uhrzeitWaehlen(context, ende) { ende = it } }) { Text(ende.format(UHRZEIT)) }
                }
                Eingabe("Vermerk, z. B. Abzeichenprüfung", vermerk, { vermerk = it })
            }
        },
        confirmButton = {
            TextButton(enabled = gruppe != null && tag != null, onClick = {
                val g = gruppe ?: return@TextButton
                vm.zusatztermin(Einheit(g.id, tag ?: return@TextButton, beginn, ende, vermerk = vermerk.trim()))
                onDismiss()
            }) { Text("Anlegen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Ein Training aus dem Kalender: Anwesenheit abhaken, Externe zählen, Ausfall und Vermerk. */
@Composable
fun TrainingScreen(vorlage: Einheit, state: UiState, vm: SchwimmgruppenViewModel, breit: Boolean) {
    val e = state.bestand.einheit(vorlage.schluessel) ?: vorlage
    val g = state.bestand.gruppe(e.gruppeId)
    var loeschenFragen by remember { mutableStateOf(false) }
    Page(
        title = (g?.name ?: "Training") + " · " + e.datum.format(DATUM_KURZ),
        onBack = { vm.zurueck() },
        actions = { if (e.zusatz) TextButton(onClick = { loeschenFragen = true }) { Text("Löschen") } },
    ) {
        Anwesenheit(vorlage, state, vm, gross = breit, Modifier.weight(1f))
    }
    if (loeschenFragen) ConfirmDialog("Diesen Zusatztermin löschen?", "Löschen", { vm.einheitLoeschen(e) }, { loeschenFragen = false })
}

/**
 * Hallenmodus für das Tablet am Beckenrand: wählt das laufende Training, große Kacheln zum Abhaken,
 * der Bildschirm bleibt an.
 */
@Composable
fun HalleScreen(state: UiState, vm: SchwimmgruppenViewModel, breit: Boolean) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    var tag by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val datum = LocalDate.parse(tag)
    val heute = Kalender.tag(state.bestand, datum)
    var gewaehlt by rememberSaveable { mutableStateOf(vm.jetzt()?.schluessel) }
    val e = heute.firstOrNull { it.schluessel == gewaehlt } ?: heute.firstOrNull()

    Page(title = "Halle · " + datum.format(DATUM_KURZ), onBack = { vm.zurueck() }, actions = {
        TextButton(onClick = { tag = datum.minusDays(1).toString(); gewaehlt = null }) { Text("‹") }
        TextButton(onClick = { tag = LocalDate.now().toString(); gewaehlt = vm.jetzt()?.schluessel }) { Text("Heute") }
        TextButton(onClick = { tag = datum.plusDays(1).toString(); gewaehlt = null }) { Text("›") }
    }) {
        if (heute.isEmpty()) {
            Leer("An diesem Tag ist kein Training geplant. Über „Zusatztermin“ im Kalender lässt sich eines anlegen.")
            return@Page
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(heute, key = { it.schluessel }) { x ->
                FilterChip(
                    selected = x.schluessel == e?.schluessel,
                    onClick = { gewaehlt = x.schluessel },
                    label = { Text("${x.beginn.format(UHRZEIT)} ${state.bestand.gruppe(x.gruppeId)?.name.orEmpty()}") },
                )
            }
        }
        if (e != null) androidx.compose.runtime.key(e.schluessel) { Anwesenheit(e, state, vm, gross = true, Modifier.weight(1f)) }
    }
}

@Composable
private fun Anwesenheit(vorlage: Einheit, state: UiState, vm: SchwimmgruppenViewModel, gross: Boolean, modifier: Modifier) {
    val b = state.bestand
    val e = b.einheit(vorlage.schluessel) ?: vorlage
    val g = b.gruppe(e.gruppeId)
    val nurZaehlen = g?.nurZaehlen == true
    val liste = if (nurZaehlen) emptyList() else Kalender.teilnehmerliste(b, e)
    var vermerk by remember(vorlage.schluessel, state.ladeNummer) { mutableStateOf(e.vermerk) }
    var gastWahl by remember { mutableStateOf(false) }
    var ohneZeigen by rememberSaveable { mutableStateOf(false) }
    val ohneGruppe = if (nurZaehlen || g == null) emptyList() else b.ohneGruppe().filter { m -> liste.none { it.id == m.id } }
    val gesundheit = b.einstellungen.gesundheitAnzeigen

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (gross) 170.dp else 130.dp),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${e.beginn.format(UHRZEIT)}–${e.ende.format(UHRZEIT)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                StundenTrainer(b, e, { id -> vm.trainerUmschalten(vorlage, id) }, { wer, durch -> vm.vertreten(vorlage, wer, durch) })
                if (g != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Nur zählen (ohne Namen)", Modifier.weight(1f))
                        Switch(checked = nurZaehlen, onCheckedChange = { vm.gruppeAendern(g.copy(nurZaehlen = it)) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ausgefallen", Modifier.weight(1f))
                    Switch(checked = e.ausgefallen, onCheckedChange = { a -> vm.einheitAendern(vorlage) { it.copy(ausgefallen = a) } })
                }
                Eingabe("Vermerk, z. B. Bahn 3 gesperrt, Abzeichenprüfung", vermerk, { t ->
                    vermerk = t
                    vm.einheitAendern(vorlage) { it.copy(vermerk = t) }
                })
                if (!e.ausgefallen) {
                    Zaehler(if (nurZaehlen) "Teilnehmer" else "Zusätzlich ohne Namen", e.weitere, gross) { d -> vm.weitere(vorlage, d) }
                    Text(
                        "Gesamt: ${e.anzahl}" + when {
                            liste.isNotEmpty() -> " · abgehakt ${e.anwesend.size} von ${liste.size}"
                            e.anwesend.isNotEmpty() -> " · davon ${e.anwesend.size} mit Namen"
                            else -> ""
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
        if (!e.ausgefallen && !nurZaehlen) {
            items(liste, key = { it.id }) { m ->
                Kachel(m, m.id in e.anwesend, gross, gesundheit, if (g != null && g.id !in m.gruppen) "Gast" else null) {
                    vm.anwesend(vorlage, m.id)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { gastWahl = true }) { Text("Schwimmer aus anderer Gruppe") }
                    if (ohneGruppe.isNotEmpty()) {
                        OutlinedButton(onClick = { ohneZeigen = !ohneZeigen }) {
                            Text(if (ohneZeigen) "Ohne Gruppe ausblenden" else "Noch ohne Gruppe (${ohneGruppe.size})")
                        }
                    }
                }
            }
            if (ohneZeigen && g != null && ohneGruppe.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Noch ohne Gruppe: Antippen hakt ab und teilt in „${g.name}“ ein.",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                items(ohneGruppe, key = { "ohne-" + it.id }) { m ->
                    Kachel(m, false, gross, gesundheit, "ohne Gruppe") {
                        vm.einteilen(m.id, g.id, true)
                        vm.anwesend(vorlage, m.id)
                    }
                }
            }
        }
    }
    if (gastWahl) GastDialog(b, liste.map { it.id }.toSet(), { id -> vm.anwesend(vorlage, id) }) { gastWahl = false }
}

/** Große Kachel zum Abhaken; abgehakt in der Hauptfarbe mit Häkchen. */
@Composable
private fun Kachel(m: Mitglied, da: Boolean, gross: Boolean, gesundheit: Boolean, hinweis: String?, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().heightIn(min = if (gross) 88.dp else 64.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (da) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (da) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                (if (da) "✓ " else "") + m.vorname,
                style = if (gross) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            )
            Text(m.nachname + zeichen(m, gesundheit), style = MaterialTheme.typography.bodySmall)
            if (hinweis != null) Text(hinweis, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Zaehler(label: String, wert: Int, gross: Boolean, onDelta: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        FilledTonalButton(onClick = { onDelta(-1) }, enabled = wert > 0, modifier = Modifier.height(if (gross) 64.dp else 48.dp)) {
            Text("−", style = MaterialTheme.typography.headlineSmall)
        }
        Text(wert.toString(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
        FilledTonalButton(onClick = { onDelta(1) }, modifier = Modifier.height(if (gross) 64.dp else 48.dp)) {
            Text("+", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun GastDialog(b: Bestand, schonDa: Set<String>, onWahl: (String) -> Unit, onDismiss: () -> Unit) {
    var suche by remember { mutableStateOf("") }
    val kandidaten = b.mitglieder.filter { it.aktiv && it.id !in schonDa && (suche.isBlank() || it.name.contains(suche.trim(), ignoreCase = true)) }
        .sortedWith(Bestand.NACH_NAME)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schwimmer hinzufügen") },
        text = {
            Column(Modifier.fillMaxHeight(0.7f)) {
                Eingabe("Suchen", suche, { suche = it })
                LazyColumn {
                    items(kandidaten, key = { it.id }) { m ->
                        Text(
                            "${m.name} (${gruppenText(b, m)})",
                            Modifier.fillMaxWidth().clickable { onWahl(m.id); onDismiss() }.padding(vertical = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

private enum class Zeitraum(val text: String) { MONAT("Dieser Monat"), QUARTAL("3 Monate"), SAISON("Saison"), JAHR("Kalenderjahr") }

/** Anwesenheit je Gruppe und Monat, z. B. für Hallenzeiten oder Zuschussanträge. */
@Composable
fun StatistikScreen(state: UiState, vm: SchwimmgruppenViewModel) {
    val heute = LocalDate.now()
    var zeitraum by rememberSaveable { mutableStateOf(Zeitraum.QUARTAL) }
    val (von, bis) = when (zeitraum) {
        Zeitraum.MONAT -> heute.withDayOfMonth(1) to heute
        Zeitraum.QUARTAL -> heute.minusMonths(3).plusDays(1) to heute
        // Saison ab September, wie das Schuljahr.
        Zeitraum.SAISON -> LocalDate.of(if (heute.monthValue >= 9) heute.year else heute.year - 1, 9, 1) to heute
        Zeitraum.JAHR -> LocalDate.of(heute.year, 1, 1) to heute
    }
    val stat = Statistik.gruppen(state.bestand, von, bis, heute)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.anwesenheitSchreiben(it, von, bis) }
    }

    Page(
        title = "Statistik",
        onBack = { vm.zurueck() },
        bottom = { OutlinedButton(onClick = { export.launch(vm.dateiname("anwesenheit", "csv")) }, modifier = Modifier.fillMaxWidth()) { Text("Als CSV speichern (Excel)") } },
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(Zeitraum.entries) { z -> FilterChip(selected = zeitraum == z, onClick = { zeitraum = z }, label = { Text(z.text) }) }
        }
        Text("${von.format(DATUM)} bis ${bis.format(DATUM)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            val einsaetze = Statistik.trainerEinsaetze(state.bestand, von, bis, heute)
            if (einsaetze.isNotEmpty()) {
                item(key = "trainer") {
                    Abschnitt("Trainer-Einsätze") {
                        state.bestand.trainer.filter { it.id in einsaetze }
                            .sortedByDescending { einsaetze[it.id] }
                            .forEach { t ->
                                Row {
                                    Text("${state.bestand.trainerName(t)} (${t.rang.text})", Modifier.weight(1f))
                                    Text("${einsaetze[t.id]}×")
                                }
                            }
                        Text("Gezählt sind stattgefundene Stunden bis heute, mit Stammtrainern oder eigens eingetragenen Trainern.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(stat, key = { it.gruppe.id }) { s ->
                Abschnitt(s.gruppe.name) {
                    Text(
                        if (s.trainings == 0) "Keine Anwesenheit erfasst."
                        else "${s.trainings} Trainings · zusammen ${s.teilnahmen} · Ø ${"%.1f".format(Locale.GERMAN, s.durchschnitt)} · höchstens ${s.hoechstens}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val rest = listOfNotNull(
                        s.ausgefallen.takeIf { it > 0 }?.let { "$it ausgefallen" },
                        s.offen.takeIf { it > 0 }?.let { "$it nicht erfasst" },
                    )
                    if (rest.isNotEmpty()) Text(rest.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    val max = s.monate.maxOfOrNull { it.durchschnitt } ?: 0.0
                    s.monate.forEach { mw -> Balken(mw.monat, mw.durchschnitt, max, mw.trainings) }
                }
            }
        }
    }
}

@Composable
private fun Balken(monat: YearMonth, wert: Double, max: Double, trainings: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(monat.format(MONAT), Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall)
        Box(Modifier.weight(1f).height(14.dp)) {
            Box(
                Modifier.fillMaxWidth(if (max > 0) (wert / max).toFloat().coerceIn(0.02f, 1f) else 0f).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Text(
            " Ø ${"%.1f".format(Locale.GERMAN, wert)} ($trainings×)",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(96.dp),
        )
    }
}
