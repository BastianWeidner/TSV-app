package de.dachapp.schwimmleitung.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.dachapp.schwimmleitung.logic.Berichte
import de.dachapp.schwimmleitung.logic.Format
import de.dachapp.schwimmleitung.logic.Kennzahlen
import de.dachapp.schwimmleitung.logic.Protokoll
import de.dachapp.schwimmleitung.logic.Zeitraum
import java.time.LocalDate

/** Kennzahlen auf einen Blick und alles, was fällig ist. */
@Composable
fun UebersichtTab(state: UiState, vm: LeitungViewModel, breit: Boolean) {
    val d = state.daten
    val heute = LocalDate.now()
    val saison = Zeitraum.saison(heute, state.e.saisonBeginn)
    val l = remember(d) { Kennzahlen.leitung(d, saison, heute) }
    val k = remember(d) { d.kurs?.let { Kennzahlen.kurse(it, saison) } }
    val w = remember(d) { d.kurs?.let { Kennzahlen.warteliste(it, heute) } }
    val g = remember(d) { d.gruppen?.let { Kennzahlen.gruppen(it, saison, heute) } }

    FormPage(title = "Übersicht ${saison.name}", onBack = null, actions = { SyncHinweis(state) }) {
        val kacheln = listOf<@Composable (Modifier) -> Unit>(
            { m -> Kachel("Kursauslastung", k?.auslastung?.let { "$it %" } ?: "–", k?.let { "${it.belegt} von ${it.plaetze} Plätzen" } ?: "Schwimmkurs übernehmen", m) { vm.oeffnen(Ansicht.Bericht) } },
            { m -> Kachel("Warteliste", w?.gesamt?.toString() ?: "–", w?.let { "${it.imMindestalter} im Mindestalter" } ?: "Schwimmkurs übernehmen", m) { vm.oeffnen(Ansicht.Bericht) } },
            { m ->
                val verein = g?.verein.orEmpty()
                val n = verein.sumOf { it.gehalten }
                Kachel("Ø je Training", if (n == 0) "–" else Format.zahl(verein.sumOf { it.teilnahmen }.toDouble() / n), g?.let { "$n Trainings, ${it.aktiveSchwimmer} Schwimmer" } ?: "Schwimmgruppen übernehmen", m) { vm.oeffnen(Ansicht.Bericht) }
            },
            { m -> Kachel("Aufgaben überfällig", "${l.ueberfaellig}", "${l.aufgabenOffen} offen", m, warnen = l.ueberfaellig > 0) { vm.tab(Tab.AUFGABEN) } },
            { m -> Kachel("Material defekt", "${l.materialDefekt}", "${l.meldungenOffen} offene Meldungen", m, warnen = l.materialDefekt > 0) { vm.oeffnen(Ansicht.Meldungen) } },
            { m ->
                val n = l.nachweiseBald.size + l.nachweiseAbgelaufen.size
                Kachel("Scheine fällig", "$n", if (l.nachweiseAbgelaufen.isNotEmpty()) "${l.nachweiseAbgelaufen.size} abgelaufen" else "in ${state.e.warnTage} Tagen", m, warnen = n > 0) { vm.oeffnen(Ansicht.Personen) }
            },
        )
        val proZeile = if (breit) 3 else 2
        kacheln.chunked(proZeile).forEach { reihe ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                reihe.forEach { it(Modifier.weight(1f)) }
                repeat(proZeile - reihe.size) { Box(Modifier.weight(1f)) }
            }
        }
        BigButton("Sitzungsbericht erstellen", { vm.oeffnen(Ansicht.Bericht) })

        val faellig = buildList {
            d.sichtbareAufgaben.filter { it.ueberfaellig(heute) }.sortedBy { it.faellig }.forEach {
                add("Aufgabe überfällig: ${it.titel}" + d.verantwortlich(it).ifBlank { null }?.let { n -> " ($n)" }.orEmpty() + " seit ${Format.datum(it.faellig)}")
            }
            l.nachweiseAbgelaufen.forEach { (p, n) -> add("Abgelaufen: ${p.name}, ${n.art.text} seit ${Format.datum(n.gueltigBis)}") }
            l.nachweiseBald.forEach { (p, n) -> add("Läuft ab: ${p.name}, ${n.art.text} am ${Format.datum(n.gueltigBis)}") }
            l.pruefungFaellig.forEach { add("Prüfung: ${it.name} bis ${Format.datum(it.pruefen)}") }
            d.sichtbareDokumente.filter { it.gueltigBis?.isAfter(heute.plusDays(30)) == false }
                .forEach { add("Dokument: ${it.titel} gültig bis ${Format.datum(it.gueltigBis)}") }
            if (l.nichtUebergeben > 0) add("${l.nichtUebergeben} Belege noch nicht beim Kassenwart (${Format.euro(l.nichtUebergebenCent)})")
        }
        Abschnitt("Fällig") {
            if (faellig.isEmpty()) Text("Nichts fällig.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            faellig.forEach { Text("• $it") }
        }
        if (d.kurs == null || d.gruppen == null) {
            Abschnitt("Daten aus Schwimmkurs und Schwimmgruppen") {
                Text(
                    "Für die Kennzahlen unter Mehr → Einstellungen → „Daten übernehmen“ die Sicherung aus der Schwimmkurs- " +
                        "bzw. Schwimmgruppen-App wählen. Namen und Kontaktdaten der Kinder werden nicht übernommen.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { vm.oeffnen(Ansicht.Einstellungen) }) { Text("Zu den Einstellungen") }
            }
        }
    }
}

@Composable
private fun Kachel(titel: String, wert: String, unten: String, modifier: Modifier, warnen: Boolean = false, onClick: () -> Unit) {
    Card(
        modifier.heightIn(min = 112.dp).clickable(onClick = onClick),
        colors = if (warnen) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titel, style = MaterialTheme.typography.labelLarge)
            Text(wert, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(unten, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
    }
}

/** Sitzungsbericht mit Zeitraumwahl, als PDF teilen oder in eine Sitzung übernehmen. */
@Composable
fun BerichtScreen(state: UiState, vm: LeitungViewModel) {
    val context = LocalContext.current
    val heute = LocalDate.now()
    val z = state.zeitraum(heute)
    val b = remember(state.daten, z) { Berichte.erstellen(state.daten, z, heute) }
    var sitzungWahl by remember { mutableStateOf(false) }
    val sitzungen = state.daten.sichtbareSitzungen.take(6)

    FormPage(
        title = "Sitzungsbericht",
        onBack = { vm.zurueck() },
        bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton("Als PDF teilen", { Dateien.berichtTeilen(context, b) }, modifier = Modifier.weight(1f))
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { sitzungWahl = true }, enabled = sitzungen.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text("In Sitzung übernehmen", textAlign = TextAlign.Center)
                    }
                    DropdownMenu(expanded = sitzungWahl, onDismissRequest = { sitzungWahl = false }) {
                        sitzungen.forEach { s ->
                            DropdownMenuItem(text = { Text(Protokoll.titel(s)) }, onClick = { sitzungWahl = false; vm.berichtFuerSitzung(s.id) })
                        }
                    }
                }
            }
        },
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BerichtArt.entries.forEach { a -> FilterChip(selected = state.berichtArt == a, onClick = { vm.berichtArt(a) }, label = { Text(a.text) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.berichtArt != BerichtArt.SEIT_SITZUNG) TextButton(onClick = { vm.berichtBlaettern(-1) }) { Text("‹") }
            Column(Modifier.weight(1f)) {
                Text(z.name, style = MaterialTheme.typography.titleMedium)
                Text(b.untertitel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.berichtArt != BerichtArt.SEIT_SITZUNG) TextButton(onClick = { vm.berichtBlaettern(1) }) { Text("›") }
        }
        for (t in b.teile) {
            Abschnitt(t.titel) {
                val mitVorjahr = t.zeilen.any { it.vorjahr.isNotEmpty() }
                if (mitVorjahr) {
                    Row {
                        Text("", Modifier.weight(1f))
                        Text("Zeitraum", Modifier.width(96.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                        Text("Vorjahr", Modifier.width(80.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                    }
                }
                t.zeilen.forEach { zl ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(zl.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(zl.wert, Modifier.width(if (mitVorjahr) 96.dp else 160.dp), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                        if (mitVorjahr) Text(zl.vorjahr, Modifier.width(80.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                    }
                    HorizontalDivider()
                }
                if (t.balken.isNotEmpty()) {
                    Text(t.balkenTitel, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    BalkenGrafik(t.balken)
                }
                if (t.hinweis.isNotBlank()) Text(t.hinweis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(b.quellen.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BalkenGrafik(werte: List<de.dachapp.schwimmleitung.logic.Balken>) {
    val farbe = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        werte.forEach { b ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(b.wert, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Box(Modifier.fillMaxWidth(0.7f).fillMaxHeight((b.anteil * 0.7).toFloat().coerceAtLeast(0.01f)).background(farbe))
                Text(b.beschriftung, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = Color.Unspecified)
            }
        }
    }
}
