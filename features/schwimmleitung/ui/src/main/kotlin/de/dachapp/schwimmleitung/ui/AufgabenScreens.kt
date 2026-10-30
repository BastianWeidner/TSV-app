package de.dachapp.schwimmleitung.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import de.dachapp.schwimmleitung.logic.Aufgabe
import de.dachapp.schwimmleitung.logic.AufgabeStatus
import de.dachapp.schwimmleitung.logic.Format
import de.dachapp.schwimmleitung.logic.Listen
import de.dachapp.schwimmleitung.logic.Protokoll
import java.time.LocalDate

private enum class AufgabenFilter(val text: String) { OFFEN("Offen"), UEBERFAELLIG("Überfällig"), ERLEDIGT("Erledigt"), ALLE("Alle") }

@Composable
fun AufgabenTab(state: UiState, vm: LeitungViewModel) {
    val context = LocalContext.current
    val d = state.daten
    val heute = LocalDate.now()
    var filter by rememberSaveable { mutableStateOf(AufgabenFilter.OFFEN) }
    var person by rememberSaveable { mutableStateOf<String?>(null) }
    val liste = d.sichtbareAufgaben
        .filter {
            when (filter) {
                AufgabenFilter.OFFEN -> it.offen
                AufgabenFilter.UEBERFAELLIG -> it.ueberfaellig(heute)
                AufgabenFilter.ERLEDIGT -> !it.offen
                AufgabenFilter.ALLE -> true
            }
        }
        .filter { person == null || it.personId == person }
        .sortedWith(compareBy<Aufgabe>({ !it.offen }, { it.faellig ?: LocalDate.MAX }, { it.titel.lowercase() }))

    Page(
        title = "Aufgaben",
        onBack = null,
        actions = { TextButton(onClick = { Dateien.csvTeilen(context, "Aufgaben", Listen.aufgaben(d)) }) { Text("CSV") } },
        bottom = { BigButton("Neue Aufgabe", { vm.oeffnen(Ansicht.AufgabeBearbeiten(null)) }) },
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AufgabenFilter.entries.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.text) }) }
        }
        Auswahl("Wer", listOf<String?>(null) + d.aktivePersonen.map { it.id }, person, { d.person(it)?.name ?: "alle" }, { person = it }, Modifier.fillMaxWidth())
        if (liste.isEmpty()) Leer(if (filter == AufgabenFilter.OFFEN) "Keine offenen Aufgaben." else "Keine Aufgaben.")
        LazyColumn(Modifier.fillMaxWidth()) {
            items(liste, key = { it.id }) { a ->
                AufgabeZeile(a, state, heute, { vm.aufgabeStatus(a.id, if (a.offen) AufgabeStatus.ERLEDIGT else AufgabeStatus.OFFEN) }) {
                    vm.oeffnen(Ansicht.AufgabeBearbeiten(a.id))
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun AufgabeZeile(a: Aufgabe, state: UiState, heute: LocalDate, onHaken: () -> Unit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = !a.offen, onCheckedChange = { onHaken() })
        Column(Modifier.weight(1f)) {
            Text(a.titel, style = MaterialTheme.typography.bodyLarge)
            val teile = listOfNotNull(
                state.daten.verantwortlich(a).ifBlank { null },
                a.faellig?.let { "bis ${Format.datum(it)}" },
                if (a.status == AufgabeStatus.LAEUFT) "läuft" else null,
                a.erledigtAm?.let { "erledigt ${Format.datum(it)}" },
            )
            if (teile.isNotEmpty()) {
                Text(
                    teile.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (a.ueberfaellig(heute)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun AufgabeScreen(id: String?, sitzungId: String?, state: UiState, vm: LeitungViewModel) {
    val context = LocalContext.current
    val d = state.daten
    val alt = id?.let { d.aufgabe(it) }
    var titel by rememberSaveable { mutableStateOf(alt?.titel.orEmpty()) }
    var notiz by rememberSaveable { mutableStateOf(alt?.notiz.orEmpty()) }
    var personId by rememberSaveable { mutableStateOf(alt?.personId) }
    var frei by rememberSaveable { mutableStateOf(alt?.verantwortlich.orEmpty()) }
    var faellig by rememberSaveable { mutableStateOf(Format.datum(alt?.faellig)) }
    var status by rememberSaveable { mutableStateOf(alt?.status ?: AufgabeStatus.OFFEN) }
    var loeschen by remember { mutableStateOf(false) }
    val tag = datumOderNull(faellig)
    val ok = titel.isNotBlank() && (faellig.isBlank() || tag != null)

    FormPage(
        title = if (alt == null) "Neue Aufgabe" else "Aufgabe",
        onBack = { vm.zurueck() },
        actions = { if (alt != null) TextButton(onClick = { loeschen = true }) { Text("Löschen") } },
        bottom = {
            BigButton("Speichern", {
                val basis = alt ?: Aufgabe(vm.neueId("aufgabe"), titel, sitzungId = sitzungId)
                val neu = basis.copy(
                    titel = titel, notiz = notiz.trim(), personId = personId, verantwortlich = if (personId == null) frei.trim() else "",
                    faellig = tag, status = status,
                    erledigtAm = if (status == AufgabeStatus.ERLEDIGT) basis.erledigtAm ?: LocalDate.now() else null,
                )
                if (alt != null && alt.offen && status == AufgabeStatus.ERLEDIGT) {
                    vm.speichern(neu.copy(status = alt.status, erledigtAm = null))
                    vm.aufgabeStatus(neu.id, AufgabeStatus.ERLEDIGT)
                } else {
                    vm.speichern(neu)
                }
                vm.zurueck()
            }, enabled = ok)
        },
    ) {
        Eingabe("Was ist zu tun?", titel, { titel = it }, pflicht = true)
        Auswahl(
            "Verantwortlich",
            listOf<String?>(null) + d.aktivePersonen.map { it.id },
            personId,
            { d.person(it)?.name ?: "frei eintragen" },
            { personId = it },
            Modifier.fillMaxWidth(),
        )
        if (personId == null) Eingabe("Name", frei, { frei = it })
        DatumEingabe("Fällig bis", faellig, { faellig = it }, context, fehler = if (faellig.isNotBlank() && tag == null) "Datum wie 31.10.2026" else null)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AufgabeStatus.entries.forEach { s -> FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s.text) }) }
        }
        Eingabe("Notiz", notiz, { notiz = it }, mehrzeilig = true)
        (alt?.sitzungId ?: sitzungId)?.let { sid -> d.sitzung(sid) }?.let { s ->
            Text("Aus: ${Protokoll.titel(s)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        alt?.meldungId?.let { d.meldung(it) }?.let { m ->
            TextButton(onClick = { vm.oeffnen(Ansicht.MeldungZeigen(m.id)) }) { Text("Zur Meldung aus der Halle (${zeitText(m.zeit)})") }
        }
        alt?.erstellt?.let { Text("Angelegt am ${Format.datum(it)}", style = MaterialTheme.typography.bodySmall) }
    }
    if (loeschen && alt != null) ConfirmDialog("Diese Aufgabe löschen?", "Löschen", { vm.aufgabeLoeschen(alt.id) }, { loeschen = false })
}
