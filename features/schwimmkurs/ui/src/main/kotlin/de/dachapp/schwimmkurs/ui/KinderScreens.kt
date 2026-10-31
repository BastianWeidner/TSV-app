package de.dachapp.schwimmkurs.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import de.dachapp.module.Abschnitt
import de.dachapp.module.Auswahl
import de.dachapp.module.BigButton
import de.dachapp.module.ConfirmDialog
import de.dachapp.module.Eingabe
import de.dachapp.module.FormPage
import de.dachapp.module.Leer
import de.dachapp.module.Page
import de.dachapp.module.Pflichtfeld
import de.dachapp.schwimmkurs.logic.Kind
import de.dachapp.schwimmkurs.logic.Kursplaner
import java.time.LocalDate
import java.util.UUID

private enum class Filter(val text: String) { ALLE("Alle"), OHNE("Ohne Kurs"), MIT("Im Kurs") }

@Composable
fun KinderTab(state: UiState, vm: SchwimmkursViewModel) {
    val b = state.bestand
    var suche by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.ALLE) }
    val datei = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importLesen) }
    val heute = LocalDate.now()

    val liste = b.kinder
        .filter { k -> suche.isBlank() || listOf(k.name, k.ort, k.eltern, k.email, k.notiz).any { it.contains(suche.trim(), ignoreCase = true) } }
        .filter { k ->
            when (filter) {
                Filter.ALLE -> true
                Filter.OHNE -> b.kursVon(k.id) == null
                Filter.MIT -> b.kursVon(k.id) != null
            }
        }
        .sortedWith(compareBy({ it.nachname.lowercase() }, { it.vorname.lowercase() }))

    Page(
        title = "Kinder (${b.kinder.size})",
        onBack = null,
        bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { datei.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) { Text("Aus Excel/CSV") }
                OutlinedButton(onClick = { vm.oeffnen(Ansicht.KindBearbeiten(null)) }, modifier = Modifier.weight(1f)) { Text("Neues Kind") }
            }
        },
    ) {
        Eingabe("Suchen (Name, Ort, E-Mail, Bemerkung)", suche, { suche = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Filter.entries.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.text) }) }
        }
        if (b.kinder.isEmpty()) {
            Leer("Noch keine Kinder. Importiere eine Excel- oder CSV-Datei, zum Beispiel die Anmeldeliste, oder lege Kinder von Hand an.")
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(liste, key = { it.id }) { k ->
                val kurs = b.kursVon(k.id)
                Card(onClick = { vm.oeffnen(Ansicht.KindBearbeiten(k.id)) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(k.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(
                                k.alterAm(heute)?.let { "$it Jahre" },
                                Kursplaner.wohnort(k, b.einstellungen.wohnortVorrang, b.einstellungen.orteAusserhalb),
                                k.einschulung.ifBlank { null }?.let { "Einschulung $it" },
                                k.abzeichen.joinToString(", ").ifBlank { null },
                            ).joinToString(" · ").ifBlank { "keine Angaben" },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            when {
                                kurs != null -> "Kurs: ${kurs.name}"
                                k.pausiert -> "pausiert, wird nicht eingeplant"
                                else -> "noch ohne Kurs"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (kurs == null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KindBearbeitenScreen(id: String?, state: UiState, vm: SchwimmkursViewModel) {
    val b = state.bestand
    val alt = id?.let { b.kind(it) }
    val context = LocalContext.current
    val stufen = b.einstellungen.abzeichen

    var vorname by remember { mutableStateOf(alt?.vorname.orEmpty()) }
    var nachname by remember { mutableStateOf(alt?.nachname.orEmpty()) }
    var geburt by remember { mutableStateOf(alt?.geburtsdatum?.format(DATUM).orEmpty()) }
    var eltern by remember { mutableStateOf(alt?.eltern.orEmpty()) }
    var email by remember { mutableStateOf(alt?.email.orEmpty()) }
    var telefon by remember { mutableStateOf(alt?.telefon.orEmpty()) }
    var strasse by remember { mutableStateOf(alt?.strasse.orEmpty()) }
    var plz by remember { mutableStateOf(alt?.plz.orEmpty()) }
    var ort by remember { mutableStateOf(alt?.ort.orEmpty()) }
    var abzeichen by remember { mutableStateOf(alt?.abzeichen.orEmpty()) }
    var weitere by remember { mutableStateOf(alt?.abzeichen.orEmpty().filter { a -> stufen.none { it.equals(a, true) } }.joinToString(", ")) }
    var anmeldung by remember { mutableStateOf(alt?.anmeldedatum?.format(DATUM).orEmpty()) }
    var notiz by remember { mutableStateOf(alt?.notiz.orEmpty()) }
    var familie by remember { mutableStateOf(alt?.familie.orEmpty()) }
    var pausiert by remember { mutableStateOf(alt?.pausiert ?: false) }
    var getrennt by remember { mutableStateOf(alt?.geschwisterGetrennt ?: false) }
    var einschulung by remember { mutableStateOf(alt?.einschulung.orEmpty()) }
    var kursId by remember { mutableStateOf(alt?.let { b.kursVon(it.id)?.id }) }
    val neueId = remember { UUID.randomUUID().toString() }
    var loeschen by remember { mutableStateOf(false) }
    var versucht by remember { mutableStateOf(false) }

    val geburtFehler = if (geburt.isNotBlank() && datumOderNull(geburt) == null) "Datum nicht erkannt" else null
    val anmeldFehler = if (anmeldung.isNotBlank() && datumOderNull(anmeldung) == null) "Datum nicht erkannt" else null
    val ohneName = vorname.isBlank() && nachname.isBlank()
    val nameFehler = if (versucht && ohneName) "Bitte einen Namen eingeben" else null

    fun ergebnis(): Kind {
        val frei = weitere.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        return Kind(
            id = alt?.id ?: neueId,
            vorname = vorname.trim(), nachname = nachname.trim(), geburtsdatum = datumOderNull(geburt),
            eltern = eltern.trim(), email = email.trim(), telefon = telefon.trim(),
            strasse = strasse.trim(), plz = plz.trim(), ort = ort.trim(),
            abzeichen = abzeichen.filter { a -> stufen.any { it.equals(a, true) } }.toSet() + frei,
            anmeldedatum = datumOderNull(anmeldung), notiz = notiz.trim(), familie = familie.trim(), pausiert = pausiert,
            einschulung = einschulung.trim(), geschwisterGetrennt = getrennt,
        )
    }

    FormPage(
        title = if (alt == null) "Neues Kind" else alt.name,
        onBack = { vm.zurueck() },
        actions = { if (alt != null) TextButton(onClick = { loeschen = true }) { Text("Löschen") } },
        bottom = {
            BigButton("Speichern", {
                versucht = true
                if ((vorname.isNotBlank() || nachname.isNotBlank()) && geburtFehler == null && anmeldFehler == null) vm.kindSpeichern(ergebnis(), kursId)
            })
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Eingabe(
                "Vorname", vorname, { vorname = it }, Modifier.weight(1f), fehler = nameFehler,
                pflicht = true, pflichtFehlt = ohneName, pflichtHinweis = Pflichtfeld.HINWEIS_EINES,
            )
            Eingabe(
                "Nachname", nachname, { nachname = it }, Modifier.weight(1f),
                pflicht = true, pflichtFehlt = ohneName, pflichtHinweis = Pflichtfeld.HINWEIS_EINES,
            )
        }
        DatumEingabe("Geburtsdatum", geburt, { geburt = it }, context, geburtFehler)
        Eingabe("Einschulung", einschulung, { einschulung = it }, hilfe = "Jahr, z. B. 2027")

        Abschnitt("Abzeichen und Leistungen") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stufen.forEach { st ->
                    val an = abzeichen.any { it.equals(st, true) }
                    FilterChip(
                        selected = an,
                        onClick = { abzeichen = if (an) abzeichen.filterNot { it.equals(st, true) }.toSet() else abzeichen + st },
                        label = { Text(st) },
                    )
                }
            }
            Eingabe("Weitere Leistungen", weitere, { weitere = it }, hilfe = "durch Komma getrennt, z. B. Tauchring")
        }

        Abschnitt("Kurs") {
            val kurse = listOf<String?>(null) + b.kurse.map { it.id }
            Auswahl("Kurs", kurse, kursId, { kid -> kid?.let { b.kurs(it) }?.let { k -> "${k.name} (${k.teilnehmer.size}/${k.plaetze})" } ?: "keiner" }, { kursId = it })
            val gewaehlt = kursId?.let { b.kurs(it) }
            val grund = gewaehlt?.let { Kursplaner.grund(ergebnis(), it, b.einstellungen) }
            if (grund != null) Text("Passt eigentlich nicht: $grund", color = MaterialTheme.colorScheme.error)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Beim automatischen Befüllen übergehen", Modifier.weight(1f))
                Switch(checked = pausiert, onCheckedChange = { pausiert = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nicht mit Geschwistern in denselben Kurs", Modifier.weight(1f))
                Switch(checked = getrennt, onCheckedChange = { getrennt = it })
            }
        }

        Abschnitt("Familie und Kontakt") {
            Eingabe("Elternteil (für die Anrede)", eltern, { eltern = it })
            Eingabe("E-Mail", email, { email = it }, typ = KeyboardType.Email, hilfe = "mehrere durch Komma getrennt")
            Eingabe("Telefon", telefon, { telefon = it }, typ = KeyboardType.Phone)
            Eingabe("Straße und Hausnummer", strasse, { strasse = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Eingabe("PLZ", plz, { plz = it }, Modifier.weight(1f), KeyboardType.Number)
                Eingabe("Wohnort", ort, { ort = it }, Modifier.weight(2f))
            }
            Eingabe(
                "Familie (optional)", familie, { familie = it },
                hilfe = "Geschwister werden über gleiche E-Mail bzw. Adresse erkannt. Hier ein gleiches Wort eintragen, um Kinder fest zu verbinden.",
            )
            val schluessel = ergebnis().familienSchluessel
            val geschwister = b.kinder.filter { it.id != (alt?.id ?: neueId) && it.familienSchluessel == schluessel }
            if (geschwister.isNotEmpty()) Text("Geschwister: " + geschwister.joinToString { it.name }, style = MaterialTheme.typography.bodyMedium)
        }

        DatumEingabe("Anmeldedatum", anmeldung, { anmeldung = it }, context, anmeldFehler)
        Eingabe("Bemerkung", notiz, { notiz = it }, mehrzeilig = true)
    }

    if (loeschen && alt != null) {
        ConfirmDialog("${alt.name} löschen?", "Löschen", { vm.kindLoeschen(alt.id) }, { loeschen = false })
    }
}
