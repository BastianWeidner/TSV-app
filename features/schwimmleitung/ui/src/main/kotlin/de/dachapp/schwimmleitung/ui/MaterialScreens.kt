package de.dachapp.schwimmleitung.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.dachapp.schwimmleitung.logic.Format
import de.dachapp.schwimmleitung.logic.Listen
import de.dachapp.schwimmleitung.logic.Material
import de.dachapp.schwimmleitung.logic.Zustand
import java.time.LocalDate

private enum class MaterialFilter(val text: String) { ALLE("Alle"), DEFEKT("Defekt"), PRUEFEN("Prüfung fällig"), AUSGESONDERT("Ausgesondert") }

@Composable
fun MaterialListeScreen(state: UiState, vm: LeitungViewModel) {
    val context = LocalContext.current
    val d = state.daten
    val heute = LocalDate.now()
    var filter by rememberSaveable { mutableStateOf(MaterialFilter.ALLE) }
    val liste = d.sichtbaresMaterial.filter {
        when (filter) {
            MaterialFilter.ALLE -> it.zustand != Zustand.AUSGESONDERT
            MaterialFilter.DEFEKT -> it.zustand == Zustand.DEFEKT
            MaterialFilter.PRUEFEN -> it.zustand != Zustand.AUSGESONDERT && it.pruefen?.isAfter(heute.plusDays(30)) == false
            MaterialFilter.AUSGESONDERT -> it.zustand == Zustand.AUSGESONDERT
        }
    }
    Page(
        title = "Material",
        onBack = { vm.zurueck() },
        actions = { TextButton(onClick = { Dateien.csvTeilen(context, "Material", Listen.material(d)) }) { Text("CSV") } },
        bottom = { BigButton("Material anlegen", { vm.oeffnen(Ansicht.MaterialBearbeiten(null)) }) },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MaterialFilter.entries.take(3).forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.text) }) }
        }
        if (liste.isEmpty()) Leer(if (filter == MaterialFilter.ALLE) "Noch kein Material erfasst, z. B. Bahnleinen, Bretter, Pull-Buoys, Rettungsstange, Erste-Hilfe-Kasten." else "Nichts.")
        LazyColumn(Modifier.fillMaxWidth()) {
            items(liste, key = { it.id }) { m ->
                Row(Modifier.fillMaxWidth().clickable { vm.oeffnen(Ansicht.MaterialBearbeiten(m.id)) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${m.name}" + if (m.anzahl != 1) " (${m.anzahl})" else "")
                        Text(
                            listOfNotNull(m.ort.ifBlank { null }, m.pruefen?.let { "Prüfung bis ${Format.datum(it)}" }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (m.zustand != Zustand.IN_ORDNUNG) {
                        Text(m.zustand.text, color = if (m.zustand == Zustand.DEFEKT) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
        }
        TextButton(onClick = { filter = MaterialFilter.AUSGESONDERT }) { Text("Ausgesondertes zeigen") }
    }
}

@Composable
fun MaterialScreen(id: String?, state: UiState, vm: LeitungViewModel) {
    val context = LocalContext.current
    val d = state.daten
    val alt = id?.let { d.materialStueck(it) }
    var name by rememberSaveable { mutableStateOf(alt?.name.orEmpty()) }
    var ort by rememberSaveable { mutableStateOf(alt?.ort.orEmpty()) }
    var anzahl by rememberSaveable { mutableStateOf((alt?.anzahl ?: 1).toString()) }
    var zustand by rememberSaveable { mutableStateOf(alt?.zustand ?: Zustand.IN_ORDNUNG) }
    var pruefen by rememberSaveable { mutableStateOf(Format.datum(alt?.pruefen)) }
    var notiz by rememberSaveable { mutableStateOf(alt?.notiz.orEmpty()) }
    var loeschen by remember { mutableStateOf(false) }
    val tag = datumOderNull(pruefen)
    val zahl = anzahl.trim().toIntOrNull()
    val ok = name.isNotBlank() && zahl != null && zahl >= 0 && (pruefen.isBlank() || tag != null)

    FormPage(
        title = if (alt == null) "Material anlegen" else alt.name,
        onBack = { vm.zurueck() },
        actions = { if (alt != null) TextButton(onClick = { loeschen = true }) { Text("Löschen") } },
        bottom = {
            BigButton("Speichern", {
                vm.speichern(Material(alt?.id ?: vm.neueId(name), name, ort.trim(), zahl!!, zustand, tag, notiz.trim()))
                vm.zurueck()
            }, enabled = ok)
        },
    ) {
        Eingabe("Bezeichnung", name, { name = it }, pflicht = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Eingabe(
                "Anzahl", anzahl, { anzahl = it }, Modifier.weight(1f), KeyboardType.Number, pflicht = true,
                fehler = if (anzahl.isNotBlank() && (zahl == null || zahl < 0)) "Ganze Zahl" else null,
            )
            Eingabe("Ort", ort, { ort = it }, Modifier.weight(2f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Zustand.entries.forEach { z -> FilterChip(selected = zustand == z, onClick = { zustand = z }, label = { Text(z.text) }) }
        }
        DatumEingabe("Nächste Prüfung", pruefen, { pruefen = it }, context, fehler = if (pruefen.isNotBlank() && tag == null) "Datum wie 01.03.2027" else null)
        Eingabe("Notiz", notiz, { notiz = it }, mehrzeilig = true)
        val meldungen = alt?.let { a -> d.sichtbareMeldungen.filter { it.materialId == a.id } }.orEmpty()
        if (meldungen.isNotEmpty()) {
            Abschnitt("Meldungen") {
                meldungen.forEach { m ->
                    TextButton(onClick = { vm.oeffnen(Ansicht.MeldungZeigen(m.id)) }) {
                        Text("${zeitText(m.zeit)}: ${m.text.take(50)}" + if (m.erledigt) " ✓" else "", modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    if (loeschen && alt != null) ConfirmDialog("„${alt.name}“ löschen? Besser „ausgesondert“ wählen, dann bleibt der Verlauf.", "Löschen", { vm.materialLoeschen(alt.id) }, { loeschen = false })
}
