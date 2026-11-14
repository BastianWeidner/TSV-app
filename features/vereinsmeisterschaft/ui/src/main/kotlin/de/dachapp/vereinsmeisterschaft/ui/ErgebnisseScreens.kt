package de.dachapp.vereinsmeisterschaft.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.dachapp.vereinsmeisterschaft.logic.GesamtZeile
import de.dachapp.vereinsmeisterschaft.logic.Import
import de.dachapp.vereinsmeisterschaft.logic.Querverbindung
import de.dachapp.vereinsmeisterschaft.logic.TabellenFehler
import de.dachapp.vereinsmeisterschaft.logic.TabellenLeser
import de.dachapp.vereinsmeisterschaft.logic.Veranstaltung
import de.dachapp.vereinsmeisterschaft.logic.Wertung
import de.dachapp.vereinsmeisterschaft.logic.Zeit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ErgebnisseSeite(v: Veranstaltung, logo: String?, meldung: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var arbeitet by remember { mutableStateOf<String?>(null) }
    var pokale by rememberSaveable { mutableStateOf(false) }

    fun hinter(text: String, block: suspend () -> Unit) {
        arbeitet = text
        scope.launch {
            try { block() } catch (e: TabellenFehler) { meldung(e.message ?: "Datei nicht lesbar.") } catch (e: Exception) { meldung("Fehler: ${e.message}") } finally { arbeitet = null }
        }
    }

    fun pdf(name: String, erzeugen: (java.io.File) -> Unit) = hinter("PDF wird erstellt …") {
        val f = Dateien.neueDatei(context, name, "pdf")
        withContext(Dispatchers.IO) { erzeugen(f) }
        Dateien.teilen(context, f, "application/pdf", name)
    }

    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) hinter("Zeiten werden gelesen …") {
            val b = withContext(Dispatchers.IO) { Import.ergebnisse(v, TabellenLeser.lesen(Dateien.lesen(context, uri))) }
            VmStore.aendern { b.veranstaltung }
            meldung("Zeiten übernommen: " + b.text)
        }
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) hinter("Speichern …") {
            // Mit BOM, damit Excel die Umlaute richtig zeigt.
            withContext(Dispatchers.IO) { Dateien.schreiben(context, uri, "\uFEFF" + Import.ergebnisCsv(v)) }
            meldung("Ergebnisse als CSV gespeichert.")
        }
    }
    val dsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) hinter("Speichern …") {
            // DSV 8: UTF-8 ohne BOM.
            withContext(Dispatchers.IO) { Dateien.schreiben(context, uri, Querverbindung.dsv(v)) }
            meldung(
                "Ergebnisdatei gespeichert. In den Schwimmgruppen unter Mehr › Wettkampfmeldungen › Datei öffnen wählen; " +
                    "die Zeiten werden den Mitgliedern über DSV-ID oder Name und Jahrgang zugeordnet.",
            )
        }
    }

    val hinweise = Wertung.hinweise(v)
    val r = v.regeln
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    pdf("Urkunden ${v.titel}") { Druck.urkunden(it, v, Wertung.urkunden(v), Wertung.punkteurkunden(v), v.staffeln, Dateien.logo(logo)) }
                }) { Text("Alle Urkunden PDF") }
                OutlinedButton(onClick = {
                    pdf("Punkteurkunden ${v.titel}") { Druck.urkunden(it, v, emptyList(), Wertung.punkteurkunden(v), emptyList(), Dateien.logo(logo)) }
                }) { Text("Punkteurkunden") }
                OutlinedButton(onClick = { pdf("Ergebnisliste ${v.titel}") { Druck.ergebnisliste(it, v) } }) { Text("Ergebnisliste PDF") }
                OutlinedButton(onClick = { import.launch(arrayOf("*/*")) }) { Text("Zeiten importieren") }
                OutlinedButton(onClick = { csv.launch(Dateien.dateiname("Ergebnisse ${v.titel}") + ".csv") }) { Text("CSV für Excel") }
                OutlinedButton(onClick = { dsv.launch(Querverbindung.dateiname(v)) }) { Text("An Schwimmgruppen (DSV)") }
            }
        }
        arbeitet?.let { t ->
            item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(Modifier.width(24.dp)); Text(t) } }
        }
        if (hinweise.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(12.dp)) { hinweise.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) } }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !pokale, onClick = { pokale = false }, label = { Text("Jahrgänge") })
                FilterChip(selected = pokale, onClick = { pokale = true }, label = { Text("Pokale (Meister)") })
            }
        }
        if (!pokale) {
            val w = Wertung.jahrgaenge(v)
            val medaillen = Wertung.medaillen(v, w)
            for (gw in w) {
                item(key = "g" + gw.gruppe.titel) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(gw.gruppe.titel, style = MaterialTheme.typography.titleMedium)
                            Text("Gesamt (Zeitpunkte)", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                            gw.gesamt.forEach { z -> GesamtZeileText(v, z, z.teilnehmer.id in medaillen) }
                            gw.lagen.forEach { lw ->
                                Text(lw.strecke.text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                                lw.zeilen.forEach { z ->
                                    Row {
                                        Text(z.platz?.let { "$it." } ?: "–", Modifier.width(32.dp))
                                        Text(z.teilnehmer.name, Modifier.weight(1f))
                                        Text(z.ergebnis?.let { if (it.gueltig) Zeit.text(it.zeit) else it.status.kurz } ?: "fehlt")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (w.isEmpty()) item { Text("Noch keine Wertung. Erst Teilnehmer mit Geschlecht und Zeiten eintragen.") }
        } else {
            for (pw in Wertung.pokale(v)) {
                item(key = "p" + pw.titel) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(pw.titel, style = MaterialTheme.typography.titleMedium)
                            Text(
                                (pw.pokal.stufen.mapNotNull { r.stufe(it)?.name }.joinToString(" + ")) + " · " + r.wertung.text,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            pw.zeilen.forEach { z -> GesamtZeileText(v, z, false) }
                        }
                    }
                }
            }
        }
        item { Text(" ", Modifier.padding(bottom = 24.dp)) }
    }
}

@Composable
private fun GesamtZeileText(v: Veranstaltung, z: GesamtZeile, medaille: Boolean) {
    Row {
        Text(z.platz?.let { "$it." } ?: "–", Modifier.width(32.dp))
        Text(z.teilnehmer.name + (if (medaille) " ●" else "") + if (!z.vollstaendig) " (unvollständig)" else "", Modifier.weight(1f))
        Text(Wertung.wertText(v.regeln, z))
    }
}
