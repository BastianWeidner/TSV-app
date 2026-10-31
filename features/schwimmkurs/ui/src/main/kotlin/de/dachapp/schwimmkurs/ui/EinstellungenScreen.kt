package de.dachapp.schwimmkurs.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.dachapp.module.Abschnitt
import de.dachapp.module.ConfirmDialog
import de.dachapp.module.Eingabe
import de.dachapp.module.FormPage
import de.dachapp.schwimmkurs.logic.Einladungen
import de.dachapp.schwimmkurs.logic.Einstellungen
import de.dachapp.schwimmkurs.logic.Vorlagen
import de.dachapp.schwimmkurs.logic.Website

/** Alle Felder speichern sofort beim Tippen. */
@Composable
fun EinstellungenTab(state: UiState, vm: SchwimmkursViewModel) {
    val e = state.bestand.einstellungen
    fun setzen(neu: Einstellungen) = vm.einstellungen(neu)
    val sichern = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::sicherungSchreiben) }
    val laden = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::sicherungLesen) }
    val website = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(vm::websiteSchreiben) }
    var ladenFragen by remember { mutableStateOf(false) }
    var loeschenFragen by remember { mutableStateOf(false) }

    // Listen als Text, damit beim Tippen Leerzeilen erhalten bleiben; gespeichert wird bereinigt.
    var wohnorte by remember(state.ladeNummer) { mutableStateOf(e.wohnortVorrang.joinToString("\n")) }
    var ausserhalb by remember(state.ladeNummer) { mutableStateOf(e.orteAusserhalb.joinToString("\n")) }
    var stufen by remember(state.ladeNummer) { mutableStateOf(e.abzeichen.joinToString("\n")) }
    var gebuehr by remember(state.ladeNummer) { mutableStateOf("%d,%02d".format(e.gebuehrProTerminCent / 100, e.gebuehrProTerminCent % 100)) }
    var eintritt by remember(state.ladeNummer) { mutableStateOf("%d,%02d".format(e.eintrittProTerminCent / 100, e.eintrittProTerminCent % 100)) }
    var rabatt by remember(state.ladeNummer) { mutableStateOf(e.geschwisterRabattCent.takeIf { it > 0 }?.let { "%d,%02d".format(it / 100, it % 100) }.orEmpty()) }
    fun cent(t: String): Long? = t.trim().replace("€", "").trim().replace(',', '.').ifEmpty { "0" }
        .toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }?.movePointRight(2)?.toLong()
    fun zeilen(t: String) = t.lines().map { it.trim() }.filter { it.isNotEmpty() }

    FormPage(title = "Einstellungen", onBack = null) {
        Abschnitt("Verein und Absender") {
            Eingabe("Verein", e.verein, { setzen(e.copy(verein = it)) })
            Eingabe("Absender (Gruß unter der Mail)", e.absender, { setzen(e.copy(absender = it)) })
        }
        Abschnitt("Kurspreis") {
            Text("Der Preis eines Kurses ist Termine × (Kursgebühr + Eintritt), außer im Kurs steht ein fester Preis.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Eingabe("Kursgebühr je Termin (€)", gebuehr, { t -> gebuehr = t; cent(t)?.let { setzen(e.copy(gebuehrProTerminCent = it)) } }, Modifier.weight(1f), KeyboardType.Decimal)
                Eingabe("Eintritt je Termin (€)", eintritt, { t -> eintritt = t; cent(t)?.let { setzen(e.copy(eintrittProTerminCent = it)) } }, Modifier.weight(1f), KeyboardType.Decimal)
            }
            Eingabe("Schwimmbad für neue Kurse", e.standardOrt, { setzen(e.copy(standardOrt = it)) })
        }
        Abschnitt("Zahlung") {
            Eingabe("Kontoinhaber", e.kontoinhaber, { setzen(e.copy(kontoinhaber = it)) }, hilfe = "leer = Vereinsname")
            Eingabe("IBAN", e.iban, { setzen(e.copy(iban = it)) })
            Eingabe("BIC (optional)", e.bic, { setzen(e.copy(bic = it)) })
            Eingabe("Zahlungsfrist ohne Datum im Kurs", e.zahlungsfrist, { setzen(e.copy(zahlungsfrist = it)) }, hilfe = "z. B. „innerhalb von 14 Tagen“")
            Eingabe("Verwendungszweck", e.verwendungszweck, { setzen(e.copy(verwendungszweck = it)) }, hilfe = "Standard: {Kuerzel} - {KinderListe}")
            Eingabe("Geschwisterrabatt je weiterem Kind (€)", rabatt, { t ->
                rabatt = t
                cent(t)?.let { setzen(e.copy(geschwisterRabattCent = it)) }
            }, typ = KeyboardType.Decimal)
        }
        Abschnitt("Altersgruppen beim Befüllen") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Eingabe("Mindestalter", e.mindestalter.toString(), { t -> t.trim().toIntOrNull()?.takeIf { it in 0..18 }?.let { setzen(e.copy(mindestalter = it)) } }, Modifier.weight(1f), KeyboardType.Number)
                Eingabe("Max. Altersunterschied", e.maxAltersspanne.toString(), { t -> t.trim().toIntOrNull()?.takeIf { it in 0..18 }?.let { setzen(e.copy(maxAltersspanne = it)) } }, Modifier.weight(1f), KeyboardType.Number)
            }
            Text(
                "In Jahren, am ersten Kurstag. Das Mindestalter gilt für Kurse ohne eigene Altersgrenze. Der Altersunterschied " +
                    "ist der größte Abstand zwischen jüngstem und ältestem Kind in einem Kurs (0 = egal).",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Abschnitt("Vorrang nach Wohnort") {
            Text(
                "Einzugsgebiet, eine Gemeinde pro Zeile, die wichtigste zuerst. Ortsteile mit Doppelpunkt dahinter: " +
                    "„Fischach: Willmatshofen, Reitenbuch“. Auch PLZ, „123*“ steht für alle PLZ, die mit 123 beginnen. " +
                    "Danach kommen Kinder ohne bekannten Wohnort, zuletzt Kinder von außerhalb. " +
                    "Innerhalb einer Gemeinde zählt zuerst das Alter (ältere zuerst), dann die Einschulung.",
                style = MaterialTheme.typography.bodySmall,
            )
            Eingabe("Einzugsgebiet", wohnorte, { wohnorte = it; setzen(e.copy(wohnortVorrang = zeilen(it))) }, mehrzeilig = true)
            Text(
                "Orte außerhalb, einer pro Zeile. Ein eingetragener Wohnort, der nicht im Einzugsgebiet steht, zählt immer als außerhalb; " +
                    "diese Liste hilft, Orte auch in der Bemerkung zu erkennen.",
                style = MaterialTheme.typography.bodySmall,
            )
            Eingabe("Orte außerhalb", ausserhalb, { ausserhalb = it; setzen(e.copy(orteAusserhalb = zeilen(it))) }, mehrzeilig = true)
        }
        Abschnitt("Abzeichen") {
            Text("Von niedrig nach hoch, eins pro Zeile. Wer ein höheres hat, erfüllt auch die niedrigeren.", style = MaterialTheme.typography.bodySmall)
            Eingabe("Abzeichen", stufen, { stufen = it; setzen(e.copy(abzeichen = zeilen(it))) }, mehrzeilig = true)
        }
        Abschnitt("Mail-Vorlage") {
            Eingabe("Betreff", e.betreff, { setzen(e.copy(betreff = it)) })
            Eingabe("Text", e.mailText, { setzen(e.copy(mailText = it)) }, mehrzeilig = true)
            TextButton(onClick = { setzen(e.copy(betreff = Vorlagen.BETREFF, mailText = Vorlagen.MAIL)) }) { Text("Mustertext wiederherstellen") }
        }
        Abschnitt("Haftungserklärung (PDF-Anhang)") {
            Eingabe("Überschrift", e.haftungTitel, { setzen(e.copy(haftungTitel = it)) })
            Eingabe("Text", e.haftungText, { setzen(e.copy(haftungText = it)) }, mehrzeilig = true)
            if (e.haftungText == Vorlagen.HAFTUNG) {
                Text("Das ist ein Mustertext. Bitte mit dem Text deines Vereins ersetzen.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        Abschnitt("Platzhalter") {
            Einladungen.PLATZHALTER.forEach { (name, erkl) ->
                Text("{$name}" + if (erkl.isNotEmpty()) " – $erkl" else "", style = MaterialTheme.typography.bodySmall)
            }
        }
        Abschnitt("Für die Website") {
            Text(
                "Speichert die laufenden und kommenden Kurse mit freien Plätzen als ${Website.DATEINAME}, ohne Kinder oder " +
                    "Eltern. Die Datei im Website-Repo unter variante-2/src/data/ hochladen; sie ersetzt dort das Google Sheet.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { website.launch(Website.DATEINAME) }, modifier = Modifier.fillMaxWidth()) { Text("Kursliste speichern") }
        }
        Abschnitt("Sicherung") {
            Text(
                "Speichert alle Kinder, Kurse und Einstellungen in einer Datei. Im Dateidialog kannst du direkt Google Drive " +
                    "wählen. Auf einem anderen Gerät lässt sich die Datei wieder einspielen.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { sichern.launch(vm.sicherungsName()) }, modifier = Modifier.weight(1f)) { Text("Sichern") }
                OutlinedButton(onClick = { ladenFragen = true }, modifier = Modifier.weight(1f)) { Text("Einspielen") }
            }
            TextButton(onClick = { loeschenFragen = true }, modifier = Modifier.fillMaxWidth()) { Text("Alle Kinder und Kurse löschen") }
        }
    }

    if (ladenFragen) {
        ConfirmDialog(
            "Eine Sicherung ersetzt alle Kinder, Kurse und Einstellungen auf diesem Gerät. Weiter?",
            "Datei wählen",
            { laden.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
            { ladenFragen = false },
        )
    }
    if (loeschenFragen) {
        ConfirmDialog(
            "Alle ${state.bestand.kinder.size} Kinder und ${state.bestand.kurse.size} Kurse löschen? Die Einstellungen bleiben. " +
                "Vorher am besten eine Sicherung anlegen.",
            "Löschen",
            vm::allesLoeschen,
            { loeschenFragen = false },
        )
    }
}
