package de.dachapp.schwimmkurs.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.dachapp.schwimmkurs.logic.Einladung
import de.dachapp.schwimmkurs.logic.Einladungen
import de.dachapp.schwimmkurs.logic.Vorlagen
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val GEOEFFNET = DateTimeFormatter.ofPattern("dd.MM. HH:mm")

private fun geoeffnetText(iso: String?): String? =
    iso?.let { runCatching { LocalDateTime.parse(it).format(GEOEFFNET) }.getOrDefault(it) }

@Composable
fun EinladungenTab(state: UiState, vm: SchwimmkursViewModel) {
    val b = state.bestand
    val context = LocalContext.current
    val liste = remember(b) { Einladungen.erstellen(b) }
    val offen = liste.filter { it.familienSchluessel !in b.versendet && it.empfaenger.isNotEmpty() }
    val e = b.einstellungen

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
            "Absender fehlt in den Einstellungen.".takeIf { e.absender.isBlank() },
        )
        warnungen.forEach { Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
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
            Text("Danach: ${einl.haftungTitel}", style = MaterialTheme.typography.bodySmall)
            Text(einl.haftungText)
        }
    }
}
