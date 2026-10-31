package de.dachapp.schwimmkurs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.dachapp.module.Abschnitt
import de.dachapp.module.Auswahl
import de.dachapp.module.BigButton
import de.dachapp.module.ConfirmDialog
import de.dachapp.module.Eingabe
import de.dachapp.module.FormPage
import de.dachapp.module.Leer
import de.dachapp.module.Page
import de.dachapp.schwimmkurs.logic.Einladungen
import de.dachapp.schwimmkurs.logic.Einstellungen
import de.dachapp.schwimmkurs.logic.Kurs
import de.dachapp.schwimmkurs.logic.Kursplaner
import de.dachapp.schwimmkurs.logic.Termin
import de.dachapp.schwimmkurs.logic.Terminserie
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.UUID

/** Kurzinfo eines Kurses: erster Tag, Anzahl Termine, Uhrzeit. */
private fun kurzinfo(k: Kurs): String {
    val t = k.sortierteTermine
    if (t.isEmpty()) return "noch keine Termine"
    val zeiten = t.map { it.beginn to it.ende }.distinct()
    val zeit = if (zeiten.size == 1) Einladungen.zeit(zeiten[0].first, zeiten[0].second) else "verschiedene Zeiten"
    return "${t.size}× ab ${Einladungen.datum(t.first().datum)}, $zeit"
}

@Composable
fun KurseTab(state: UiState, vm: SchwimmkursViewModel) {
    val b = state.bestand
    val ohne = b.ohneKurs.count { !it.pausiert }
    val eingeteilt = b.kurse.sumOf { it.teilnehmer.size }
    var leeren by remember { mutableStateOf(false) }
    Page(
        title = "Kurse (${b.kurse.size})",
        onBack = null,
        bottom = {
            BigButton("Kurse automatisch befüllen ($ohne Kinder ohne Kurs)", vm::planen, enabled = b.kurse.isNotEmpty() && ohne > 0)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.oeffnen(Ansicht.KursBearbeiten(null)) }, modifier = Modifier.weight(1f)) { Text("Neuer Kurs") }
                OutlinedButton(onClick = { leeren = true }, enabled = eingeteilt > 0, modifier = Modifier.weight(1f)) { Text("Einteilung leeren") }
            }
        },
    ) {
        if (leeren) {
            ConfirmDialog(
                "Alle $eingeteilt Kinder aus allen Kursen nehmen? Die Kurse bleiben, danach kannst du neu befüllen lassen.",
                "Leeren",
                vm::einteilungLeeren,
                { leeren = false },
            )
        }
        if (b.kurse.isEmpty()) Leer("Noch keine Kurse. Lege einen Kurs mit Altersgrenzen, Abzeichen und Terminen an.")
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(b.kurse.sortedWith(compareBy({ it.ersterTag ?: LocalDate.MAX }, { it.sortierteTermine.firstOrNull()?.beginn }, { it.name })), key = { it.id }) { k ->
                Card(onClick = { vm.oeffnen(Ansicht.KursDetail(k.id)) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(k.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(
                                "${k.teilnehmer.size}/${k.plaetze}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (k.frei == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        Text(kurzinfo(k), style = MaterialTheme.typography.bodyMedium)
                        Text(bedingungen(k, b.einstellungen), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun bedingungen(k: Kurs, e: Einstellungen): String = listOfNotNull(
    when {
        k.minAlter != null && k.maxAlter != null -> "${k.minAlter}–${k.maxAlter} Jahre"
        k.minAlter != null -> "ab ${k.minAlter} Jahren"
        k.maxAlter != null -> "bis ${k.maxAlter} Jahre"
        else -> null
    },
    k.voraussetzung?.let { "braucht $it" },
    k.ziel?.let { "Ziel: $it" },
    k.ort.ifBlank { null },
    Einladungen.preis(k, e).takeIf { it > 0 }?.let { Einladungen.euro(it) },
    k.kuerzel.ifBlank { null },
).joinToString(" · ").ifBlank { "ohne Bedingungen" }

@Composable
fun KursDetailScreen(id: String, state: UiState, vm: SchwimmkursViewModel) {
    val b = state.bestand
    val kurs = b.kurs(id) ?: return
    val context = LocalContext.current
    var hinzufuegen by remember { mutableStateOf(false) }
    var loeschen by remember { mutableStateOf(false) }
    val teilnehmer = kurs.teilnehmer.mapNotNull { b.kind(it) }
    val stichtag = kurs.ersterTag ?: LocalDate.now()

    Page(
        title = kurs.name,
        onBack = { vm.zurueck() },
        actions = { TextButton(onClick = { vm.oeffnen(Ansicht.KursBearbeiten(id)) }) { Text("Bearbeiten") } },
        bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { hinzufuegen = true }, enabled = kurs.frei > 0, modifier = Modifier.weight(1f)) { Text("Kind hinzufügen") }
                OutlinedButton(onClick = { Versand.kurslisteTeilen(context, kurs, b) }, enabled = teilnehmer.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text("Liste teilen")
                }
            }
            TextButton(onClick = { loeschen = true }, modifier = Modifier.fillMaxWidth()) { Text("Kurs löschen") }
        },
    ) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(bedingungen(kurs, b.einstellungen), style = MaterialTheme.typography.bodyMedium)
                Text(Einladungen.termine(kurs), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                if (kurs.kursleitung.isNotBlank()) Text("Kursleitung: ${kurs.kursleitung}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Teilnehmer: ${teilnehmer.size} von ${kurs.plaetze}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            items(teilnehmer, key = { it.id }) { k ->
                val grund = Kursplaner.grund(k, kurs, b.einstellungen)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        TextButton(onClick = { vm.oeffnen(Ansicht.KindBearbeiten(k.id)) }) {
                            Text(k.name + (k.alterAm(stichtag)?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.bodyLarge)
                        }
                        if (grund != null) Text("  passt nicht: $grund", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { vm.zuteilen(k.id, null) }) { Text("Entfernen") }
                }
                HorizontalDivider()
            }
        }
    }

    if (hinzufuegen) {
        // Passende Kinder zuerst, die anderen mit Grund darunter.
        val kandidaten = b.ohneKurs.map { it to Kursplaner.grund(it, kurs, b.einstellungen) }
            .sortedWith(compareBy({ it.second != null }, { it.first.nachname.lowercase() }, { it.first.vorname.lowercase() }))
        AlertDialog(
            onDismissRequest = { hinzufuegen = false },
            confirmButton = { TextButton(onClick = { hinzufuegen = false }) { Text("Fertig") } },
            title = { Text("Kind hinzufügen") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (kandidaten.isEmpty()) Text("Alle Kinder sind schon in einem Kurs.")
                    val aktuell = b.kurs(id) ?: kurs
                    kandidaten.forEach { (k, grund) ->
                        TextButton(onClick = { vm.zuteilen(k.id, id) }, enabled = aktuell.frei > 0, modifier = Modifier.fillMaxWidth()) {
                            Text(k.name + (grund?.let { " – $it" } ?: ""), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
        )
    }
    if (loeschen) {
        ConfirmDialog(
            "Kurs „${kurs.name}“ löschen? Die ${teilnehmer.size} Kinder bleiben erhalten und sind danach ohne Kurs.",
            "Löschen",
            { vm.kursLoeschen(id) },
            { loeschen = false },
        )
    }
}

@Composable
fun KursBearbeitenScreen(id: String?, state: UiState, vm: SchwimmkursViewModel) {
    val b = state.bestand
    val alt = id?.let { b.kurs(it) }
    val context = LocalContext.current
    val stufen = b.einstellungen.abzeichen
    val neueId = remember { UUID.randomUUID().toString() }

    var name by remember { mutableStateOf(alt?.name.orEmpty()) }
    var plaetze by remember { mutableStateOf((alt?.plaetze ?: 8).toString()) }
    var minAlter by remember { mutableStateOf(alt?.minAlter?.toString().orEmpty()) }
    var maxAlter by remember { mutableStateOf(alt?.maxAlter?.toString().orEmpty()) }
    var voraussetzung by remember { mutableStateOf(alt?.voraussetzung) }
    var ziel by remember { mutableStateOf(alt?.ziel) }
    var ort by remember { mutableStateOf(alt?.ort ?: b.einstellungen.standardOrt) }
    var preis by remember { mutableStateOf(alt?.preisCent?.let { "%d,%02d".format(it / 100, it % 100) }.orEmpty()) }
    var kuerzel by remember { mutableStateOf(alt?.kuerzel.orEmpty()) }
    var hinweis by remember { mutableStateOf(alt?.hinweis.orEmpty()) }
    var frist by remember { mutableStateOf(alt?.zahlungsfrist?.format(DATUM).orEmpty()) }
    var leitung by remember { mutableStateOf(alt?.kursleitung.orEmpty()) }
    val termine = remember { mutableStateListOf<Termin>().apply { addAll(alt?.sortierteTermine.orEmpty()) } }
    var serie by remember { mutableStateOf(false) }
    var versucht by remember { mutableStateOf(false) }

    val plaetzeZahl = plaetze.trim().toIntOrNull()?.takeIf { it in 1..200 }
    val minZahl = minAlter.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val maxZahl = maxAlter.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val preisCent = preis.trim().replace("€", "").trim().takeIf { it.isNotEmpty() }?.let { p ->
        val zahl = if (',' in p) p.replace(".", "").replace(',', '.') else p
        zahl.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }?.movePointRight(2)?.toLong()
    }
    val fehlerAlter = when {
        minAlter.isNotBlank() && minZahl == null || maxAlter.isNotBlank() && maxZahl == null -> "Bitte ganze Zahl"
        minZahl != null && maxZahl != null && minZahl > maxZahl -> "Mindestalter ist größer als Höchstalter"
        else -> null
    }
    val fristFehler = if (frist.isNotBlank() && datumOderNull(frist) == null) "Datum nicht erkannt" else null
    val gueltig = name.isNotBlank() && plaetzeZahl != null && fehlerAlter == null && (preis.isBlank() || preisCent != null) && fristFehler == null
    val e = b.einstellungen
    val autoPreis = termine.size * (e.gebuehrProTerminCent + e.eintrittProTerminCent)

    FormPage(
        title = if (alt == null) "Neuer Kurs" else "Kurs bearbeiten",
        onBack = { vm.zurueck() },
        bottom = {
            BigButton("Speichern", {
                versucht = true
                if (gueltig) {
                    vm.kursSpeichern(
                        Kurs(
                            id = alt?.id ?: neueId, name = name.trim(), termine = termine.sortedWith(compareBy({ it.datum }, { it.beginn })),
                            plaetze = plaetzeZahl!!, minAlter = minZahl, maxAlter = maxZahl, voraussetzung = voraussetzung, ziel = ziel,
                            ort = ort.trim(), preisCent = preisCent, kursleitung = leitung.trim(), teilnehmer = alt?.teilnehmer.orEmpty(),
                            kuerzel = kuerzel.trim(), hinweis = hinweis.trim(), zahlungsfrist = datumOderNull(frist),
                        ),
                    )
                }
            })
        },
    ) {
        Eingabe("Name", name, { name = it }, fehler = if (versucht && name.isBlank()) "Bitte einen Namen eingeben" else null, pflicht = true, hilfe = "z. B. Seepferdchen Samstag 9 Uhr")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Eingabe(
                "Plätze", plaetze, { plaetze = it }, Modifier.weight(1f), KeyboardType.Number,
                fehler = if (plaetze.isNotBlank() && plaetzeZahl == null) "1 bis 200" else null, pflicht = true,
            )
            Eingabe(
                "Preis pro Kind (€)", preis, { preis = it }, Modifier.weight(1f), KeyboardType.Decimal,
                fehler = if (preis.isNotBlank() && preisCent == null) "z. B. 85,00" else null,
                hilfe = if (preis.isBlank()) "leer = automatisch: ${Einladungen.euro(autoPreis)}" else null,
            )
        }
        Abschnitt("Wer passt hinein?") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Eingabe("Alter von", minAlter, { minAlter = it }, Modifier.weight(1f), KeyboardType.Number, fehler = fehlerAlter)
                Eingabe("bis", maxAlter, { maxAlter = it }, Modifier.weight(1f), KeyboardType.Number)
            }
            Text("Alter in vollen Jahren am ersten Kurstag.", style = MaterialTheme.typography.bodySmall)
            val optionen = listOf<String?>(null) + stufen
            Auswahl("Braucht schon", optionen, voraussetzung, { it ?: "nichts" }, { voraussetzung = it })
            Auswahl("Kursziel", optionen, ziel, { it ?: "keins" }, { ziel = it })
            Text("Kinder, die das Kursziel schon haben, werden nicht eingeplant.", style = MaterialTheme.typography.bodySmall)
        }
        Eingabe("Schwimmbad / Treffpunkt", ort, { ort = it })
        Eingabe("Kursleitung", leitung, { leitung = it })
        Abschnitt("Für die Einladung") {
            Eingabe("Kürzel für den Verwendungszweck", kuerzel, { kuerzel = it }, hilfe = "z. B. SK04 2526")
            DatumEingabe("Zahlungseingang spätestens", frist, { frist = it }, context, fristFehler)
            Eingabe(
                "Hinweis", hinweis, { hinweis = it }, mehrzeilig = true,
                hilfe = "z. B. „Am 20.12. dürfen Eltern und Geschwister mit ins Wasser.“ Ausfallende Samstage nennt die Mail automatisch.",
            )
        }

        Abschnitt("Termine (${termine.size})") {
            termine.sortedWith(compareBy({ it.datum }, { it.beginn })).forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        datumWaehlen(context, t.datum) { d -> termine[termine.indexOf(t)] = t.copy(datum = d) }
                    }, modifier = Modifier.weight(1f)) {
                        Text("${Einladungen.datum(t.datum)}, ${Einladungen.zeit(t.beginn, t.ende)}", modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = { termine.remove(t) }) { Text("✕") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { serie = true }, modifier = Modifier.weight(1f)) { Text("Serie anlegen") }
                OutlinedButton(onClick = {
                    val letzter = termine.maxByOrNull { it.datum }
                    val start = letzter?.datum?.plusWeeks(1) ?: LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
                    datumWaehlen(context, start) { d ->
                        termine.add(Termin(d, letzter?.beginn ?: LocalTime.of(9, 0), letzter?.ende ?: LocalTime.of(9, 45)))
                    }
                }, modifier = Modifier.weight(1f)) { Text("Einzeltermin") }
            }
        }
    }

    if (serie) SerieDialog(
        start = termine.maxByOrNull { it.datum }?.datum?.plusWeeks(1),
        onFertig = { neu -> termine.addAll(neu.filter { n -> termine.none { it.datum == n.datum && it.beginn == n.beginn } }) },
        onDismiss = { serie = false },
    )
}

/** Mehrere Termine im Wochenabstand, standardmäßig samstags 9:00–9:45. */
@Composable
private fun SerieDialog(start: LocalDate?, onFertig: (List<Termin>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var ab by remember { mutableStateOf(start ?: LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SATURDAY))) }
    var anzahl by remember { mutableStateOf("8") }
    var beginn by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var ende by remember { mutableStateOf(LocalTime.of(9, 45)) }
    val n = anzahl.trim().toIntOrNull()?.takeIf { it in 1..52 }
    val tag = ab.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMANY)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Terminserie") },
        confirmButton = {
            TextButton(
                onClick = { onDismiss(); onFertig(Terminserie.woechentlich(ab, ab.dayOfWeek, n!!, beginn, ende)) },
                enabled = n != null && ende.isAfter(beginn),
            ) { Text("Anlegen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { datumWaehlen(context, ab) { ab = it } }, modifier = Modifier.fillMaxWidth()) {
                    Text("Erster Termin: ${Einladungen.datum(ab)}")
                }
                Eingabe("Anzahl (wöchentlich, jeden $tag)", anzahl, { anzahl = it }, typ = KeyboardType.Number, fehler = if (n == null) "1 bis 52" else null)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { uhrzeitWaehlen(context, beginn) { beginn = it } }, modifier = Modifier.weight(1f)) {
                        Text("von ${beginn.format(UHRZEIT)}")
                    }
                    OutlinedButton(onClick = { uhrzeitWaehlen(context, ende) { ende = it } }, modifier = Modifier.weight(1f)) {
                        Text("bis ${ende.format(UHRZEIT)}")
                    }
                }
                if (!ende.isAfter(beginn)) Text("Ende muss nach Beginn liegen.", color = MaterialTheme.colorScheme.error)
                Text("Einzelne Termine (z. B. Ferien) kannst du danach antippen und verschieben oder entfernen.", style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

@Composable
fun VorschlagScreen(state: UiState, vm: SchwimmkursViewModel) {
    val v = state.vorschlag ?: return
    val b = state.bestand
    Page(
        title = "Vorschlag",
        onBack = { vm.zurueck() },
        bottom = { BigButton("${v.anzahl} Kinder einteilen", vm::vorschlagUebernehmen, enabled = v.anzahl > 0) },
    ) {
        Text(
            "Reihenfolge: Einzugsgebiet laut Einstellungen (Orte außerhalb zuletzt), dann ältere Kinder, dann frühere Einschulung. Geschwister kommen möglichst in denselben Kurs " +
                "oder an dieselben Tage. Bereits eingeteilte Kinder bleiben, wo sie sind.",
            style = MaterialTheme.typography.bodyMedium,
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(b.kurse.filter { it.id in v.zuteilung }, key = { it.id }) { k ->
                val neu = v.zuteilung.getValue(k.id)
                Abschnitt("${k.name}: +${neu.size} (dann ${k.teilnehmer.size + neu.size}/${k.plaetze})") {
                    neu.mapNotNull { b.kind(it) }.forEach { kind ->
                        Text("${kind.name}${kind.ort.ifBlank { null }?.let { ", $it" } ?: ""}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (v.warteliste.isNotEmpty()) item {
                Abschnitt("Warteliste (${v.warteliste.size})") {
                    v.warteliste.forEach { (kid, grund) ->
                        Text("${b.kind(kid)?.name.orEmpty()}: $grund", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
