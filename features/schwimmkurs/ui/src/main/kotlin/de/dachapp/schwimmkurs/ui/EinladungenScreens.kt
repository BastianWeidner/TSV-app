package de.dachapp.schwimmkurs.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.dachapp.module.Abschnitt
import de.dachapp.module.BigButton
import de.dachapp.module.FormPage
import de.dachapp.module.Leer
import de.dachapp.module.Page
import de.dachapp.schwimmkurs.logic.Einladung
import de.dachapp.schwimmkurs.logic.Einladungen
import de.dachapp.schwimmkurs.logic.GiroCode
import de.dachapp.schwimmkurs.logic.Vorlagen
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val GEOEFFNET = DateTimeFormatter.ofPattern("dd.MM. HH:mm")
private val TAG = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private fun geoeffnetText(iso: String?): String? =
    iso?.let { runCatching { LocalDateTime.parse(it).format(GEOEFFNET) }.getOrDefault(it) }

@Composable
fun EinladungenTab(state: UiState, vm: SchwimmkursViewModel) {
    val b = state.bestand
    val context = LocalContext.current
    val liste = remember(b) { Einladungen.erstellen(b) }
    val offen = liste.filter { it.familienSchluessel !in b.versendet && it.empfaenger.isNotEmpty() }
    val e = b.einstellungen
    val auszug = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::kontoauszugLesen) }

    fun oeffnen(einladung: Einladung) {
        if (Versand.einladungOeffnen(context, einladung, e)) vm.alsGeoeffnetMarkieren(einladung.familienSchluessel)
    }

    Page(
        title = "Einladungen (${liste.size})",
        onBack = null,
        bottom = {
            if (liste.isNotEmpty()) {
                BigButton(
                    if (offen.isEmpty()) "Alle Mails geöffnet" else "Nächste Mail öffnen (${offen.size} offen)",
                    { oeffnen(offen.first()) },
                    enabled = offen.isNotEmpty(),
                )
            }
        },
    ) {
        val warnungen = listOfNotNull(
            "Die Haftungserklärung ist noch der Mustertext. Bitte in den Einstellungen anpassen.".takeIf { e.haftungText == Vorlagen.HAFTUNG },
            "IBAN fehlt in den Einstellungen.".takeIf { e.iban.isBlank() },
            "IBAN in den Einstellungen ist ungültig (Prüfziffer), darum ohne GiroCode.".takeIf { e.iban.isNotBlank() && !GiroCode.ibanGueltig(e.iban) },
            "Absender fehlt in den Einstellungen.".takeIf { e.absender.isBlank() },
        )
        warnungen.forEach { Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        if (liste.isNotEmpty()) {
            val bezahlt = liste.count { it.familienSchluessel in b.zahlungen }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bezahlt: $bezahlt von ${liste.size}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { auszug.launch(arrayOf("*/*")) }) { Text("Kontoauszug abgleichen") }
            }
        }
        if (liste.isEmpty()) Leer("Sobald Kinder in Kursen sind, entsteht hier pro Familie eine Einladung mit allen Kindern, Terminen und Zahlungsdaten.")
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(liste, key = { it.familienSchluessel }) { einl ->
                val wann = geoeffnetText(b.versendet[einl.familienSchluessel])
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${einl.kinderNamen} ${einl.plaetze.first().first.nachname}", style = MaterialTheme.typography.titleMedium)
                        Text(einl.plaetze.joinToString(" · ") { "${it.first.vorname}: ${it.second.name}" }, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (einl.empfaenger.isEmpty()) "Keine E-Mail-Adresse hinterlegt" else einl.empfaenger.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (einl.empfaenger.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            Einladungen.euro(einl.betragCent) + (wann?.let { " · Mail geöffnet am $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (wann != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val zahlung = b.zahlungen[einl.familienSchluessel]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                zahlung?.let { z ->
                                    "✓ Bezahlt am ${z.datum.format(TAG)}" +
                                        (if (z.betragCent != einl.betragCent) " (${Einladungen.euro(z.betragCent)})" else "") +
                                        (if (z.quelle == "auszug") " laut Kontoauszug" else "")
                                } ?: "Zahlung offen",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (zahlung != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (zahlung == null) TextButton(onClick = { vm.bezahltSetzen(einl) }) { Text("Bezahlt") }
                            else TextButton(onClick = { vm.bezahltZuruecknehmen(einl.familienSchluessel) }) { Text("Zurücknehmen") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.oeffnen(Ansicht.EinladungVorschau(einl.familienSchluessel)) }) { Text("Vorschau") }
                            Button(onClick = { oeffnen(einl) }, enabled = einl.empfaenger.isNotEmpty()) { Text(if (wann == null) "Mail öffnen" else "Erneut öffnen") }
                            if (wann != null) TextButton(onClick = { vm.versandZuruecksetzen(einl.familienSchluessel) }) { Text("Als offen") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EinladungVorschauScreen(schluessel: String, state: UiState, vm: SchwimmkursViewModel) {
    val einl = remember(state.bestand) { Einladungen.erstellen(state.bestand) }.firstOrNull { it.familienSchluessel == schluessel } ?: return
    FormPage(title = "Vorschau: ${einl.kinderNamen}", onBack = { vm.zurueck() }) {
        Abschnitt("An") { Text(einl.empfaenger.joinToString(", ").ifBlank { "keine Adresse" }) }
        Abschnitt("Betreff") { Text(einl.betreff) }
        Abschnitt("Text") { Text(einl.textOhneMarken) }
        Abschnitt("Anhang (PDF)") {
            Text("Seite 1: Kursübersicht mit allen Terminen und den Überweisungsdaten", style = MaterialTheme.typography.bodySmall)
            einl.giroCode?.let { code ->
                Text("Mit GiroCode für die Banking-App:", style = MaterialTheme.typography.bodySmall)
                GiroCodeBild(code)
            } ?: Text("Ohne GiroCode: IBAN in den Einstellungen fehlt oder ist ungültig.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Text("Danach: ${einl.haftungTitel}", style = MaterialTheme.typography.bodySmall)
            Text(einl.haftungText)
        }
    }
}

/** Vorschläge aus dem Kontoauszug zum Abhaken; übernommen wird nur, was angehakt ist. */
@Composable
fun ZahlungsabgleichScreen(state: UiState, vm: SchwimmkursViewModel) {
    val a = state.abgleich ?: return
    val ab = a.abgleich
    Page(
        title = "Kontoauszug abgleichen",
        onBack = { vm.zurueck() },
        bottom = { BigButton("${a.auswahl.size} als bezahlt übernehmen", vm::abgleichUebernehmen, enabled = a.auswahl.isNotEmpty()) },
    ) {
        Text(
            "${a.dateiname}: ${ab.gutschriften} Gutschriften, ${ab.treffer.size} passen zu offenen Einladungen. " +
                "Sichere Treffer (Kurskürzel, Nachname und Betrag) sind schon angehakt.",
            style = MaterialTheme.typography.bodyMedium,
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ab.treffer.isEmpty()) item { Leer("Keine Gutschrift passt zu einer offenen Einladung.") }
            items(ab.treffer, key = { it.einladung.familienSchluessel }) { t ->
                val an = t.einladung.familienSchluessel in a.auswahl
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = an, onCheckedChange = { vm.abgleichUmschalten(t.einladung.familienSchluessel) })
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${t.einladung.kinderNamen} ${t.einladung.nachname}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(t.buchung.datum?.format(TAG), Einladungen.euro(t.buchung.betragCent), t.buchung.name.ifBlank { null }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(t.buchung.zweck, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (t.hinweis.isNotEmpty()) Text("⚠ ${t.hinweis}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            else if (!t.sicher) Text("Unsicher: bitte prüfen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (ab.unklar.isNotEmpty()) {
                item { Text("Nicht zuzuordnen (bitte von Hand prüfen)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
                items(ab.unklar, key = { "u" + it.zeile }) { bu ->
                    Text(
                        listOfNotNull(bu.datum?.format(TAG), Einladungen.euro(bu.betragCent), bu.name.ifBlank { null }, bu.zweck).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
