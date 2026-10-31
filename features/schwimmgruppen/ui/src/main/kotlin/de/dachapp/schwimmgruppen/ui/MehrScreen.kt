package de.dachapp.schwimmgruppen.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import de.dachapp.module.Abschnitt
import de.dachapp.module.Auswahl
import de.dachapp.module.ConfirmDialog
import de.dachapp.module.Eingabe
import de.dachapp.module.FormPage
import de.dachapp.schwimmgruppen.logic.Vorgaben

/** Einstellungen, Sicherung (z. B. nach Google Drive) und Löschen. */
@Composable
fun MehrTab(state: UiState, vm: SchwimmgruppenViewModel) {
    val e = state.bestand.einstellungen
    var strecken by remember(state.ladeNummer) { mutableStateOf(e.strecken.joinToString("\n")) }
    var abzeichen by remember(state.ladeNummer) { mutableStateOf(e.abzeichen.joinToString("\n")) }
    val sichern = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::sicherungSchreiben) }
    val laden = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::sicherungLesen) }
    var ladenFragen by remember { mutableStateOf(false) }
    var loeschenFragen by remember { mutableStateOf(false) }

    fun zeilen(t: String) = t.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    FormPage(title = "Einstellungen", onBack = null) {
        Abschnitt("Verein") {
            Eingabe("Name", e.verein, { vm.einstellungen(e.copy(verein = it)) })
            Eingabe(
                "Import: nur Abteilung", e.abteilungFilter, { vm.einstellungen(e.copy(abteilungFilter = it)) },
                hilfe = "Hat die Mitgliederliste eine Abteilungs-Spalte, werden nur Zeilen übernommen, die diesen Text enthalten. Leer = alle.",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Gesundheitserklärung anzeigen")
                    Text("In Listen, Unterseite, Halle und Gruppenliste.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = e.gesundheitAnzeigen, onCheckedChange = { vm.einstellungen(e.copy(gesundheitAnzeigen = it)) })
            }
        }
        Abschnitt("Wettkampfmeldungen") {
            OutlinedButton(onClick = { vm.oeffnen(Ansicht.Wettkampf) }, modifier = Modifier.fillMaxWidth()) {
                Text("Ausschreibung melden, Ergebnisse einlesen")
            }
            Eingabe("DSV-Vereinskennzahl", e.vereinsnummer, { vm.einstellungen(e.copy(vereinsnummer = it.filter(Char::isDigit).take(4))) }, typ = KeyboardType.Number, hilfe = "4-stellig, steht z. B. im DSV-Vereinsportal")
            Auswahl("Landesverband", (1..18).toList(), e.landesverband, { LANDESVERBAENDE[it - 1] }, { vm.einstellungen(e.copy(landesverband = it)) })
            Eingabe("Meldende Person (Nachname, Vorname)", e.meldenderName, { vm.einstellungen(e.copy(meldenderName = it)) })
            Eingabe("E-Mail der meldenden Person", e.meldenderEmail, { vm.einstellungen(e.copy(meldenderEmail = it)) }, typ = KeyboardType.Email)
            Eingabe("Telefon (optional)", e.meldenderTelefon, { vm.einstellungen(e.copy(meldenderTelefon = it)) }, typ = KeyboardType.Phone)
        }
        Abschnitt("Strecken für Zeiten") {
            Eingabe("Eine Strecke pro Zeile", strecken, { strecken = it; vm.einstellungen(e.copy(strecken = zeilen(it))) }, mehrzeilig = true)
            OutlinedButton(onClick = {
                strecken = Vorgaben.STRECKEN.joinToString("\n"); vm.einstellungen(e.copy(strecken = Vorgaben.STRECKEN))
            }) { Text("Standard wiederherstellen") }
        }
        Abschnitt("Abzeichen zur Auswahl") {
            Eingabe("Ein Abzeichen pro Zeile", abzeichen, { abzeichen = it; vm.einstellungen(e.copy(abzeichen = zeilen(it))) }, mehrzeilig = true)
        }
        Abschnitt("Sicherung") {
            Text(
                "Alle Schwimmer, Gruppen, Anwesenheiten, Zeiten und Erfolge als eine Datei. Im Dateidialog lässt sich " +
                    "Google Drive wählen. So kommen die Daten auch aufs Tablet in der Halle.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { sichern.launch(vm.dateiname("schwimmgruppen", "json")) }, modifier = Modifier.weight(1f)) { Text("Sichern") }
                OutlinedButton(onClick = { ladenFragen = true }, modifier = Modifier.weight(1f)) { Text("Einspielen") }
            }
        }
        OutlinedButton(onClick = { loeschenFragen = true }, modifier = Modifier.fillMaxWidth()) { Text("Alle Daten löschen") }
    }
    if (ladenFragen) {
        ConfirmDialog(
            "Eine Sicherung ersetzt alle Daten auf diesem Gerät. Weiter?",
            "Datei wählen",
            { laden.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
            { ladenFragen = false },
        )
    }
    if (loeschenFragen) {
        ConfirmDialog(
            "Alle Schwimmer, Anwesenheiten, Zeiten und Erfolge löschen? Die Gruppen werden auf den Trainingsplan zurückgesetzt. " +
                "Vorher besser sichern.",
            "Löschen",
            vm::allesLoeschen,
            { loeschenFragen = false },
        )
    }
}

/** Die 18 Landesschwimmverbände im DSV, Nummer = Position + 1 (Anhang des DSV-Standards). */
val LANDESVERBAENDE = listOf(
    "1 Baden", "2 Bayern", "3 Berlin", "4 Brandenburg", "5 Bremen", "6 Hamburg", "7 Hessen", "8 Mecklenburg-Vorpommern",
    "9 Niedersachsen", "10 Rheinland", "11 Saarland", "12 Sachsen", "13 Sachsen-Anhalt", "14 Schleswig-Holstein",
    "15 Südwest", "16 Thüringen", "17 Nordrhein-Westfalen", "18 Württemberg",
)
