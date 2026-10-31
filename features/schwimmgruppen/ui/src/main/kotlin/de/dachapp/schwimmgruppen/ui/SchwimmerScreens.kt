package de.dachapp.schwimmgruppen.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
import de.dachapp.schwimmgruppen.logic.Bestand
import de.dachapp.schwimmgruppen.logic.Erfolg
import de.dachapp.schwimmgruppen.logic.ErfolgArt
import de.dachapp.schwimmgruppen.logic.Interesse
import de.dachapp.schwimmgruppen.logic.Mitglied
import de.dachapp.schwimmgruppen.logic.Statistik
import de.dachapp.schwimmgruppen.logic.Zeit
import de.dachapp.schwimmgruppen.logic.Zeiten
import java.time.LocalDate
import java.util.UUID

private enum class Filter(val text: String) { AKTIV("Aktiv"), OHNE("Ohne Gruppe"), WETTKAMPF("Wettkampf"), INAKTIV("Inaktiv") }

/** Kleine Hinweise am Namen: keine Fotos erlaubt, Gesundheitserklärung fehlt, Wettkampfinteresse. */
fun zeichen(m: Mitglied, gesundheit: Boolean): String = buildString {
    if (m.fotorechte == false) append(" 📵")
    if (gesundheit && m.gesundheitserklaerung == false) append(" ⚠")
    if (m.interesse == Interesse.JA) append(" 🏅")
}

fun gruppenText(b: Bestand, m: Mitglied): String =
    b.gruppen.filter { it.id in m.gruppen }.joinToString(", ") { it.name }.ifEmpty { "ohne Gruppe" }

@Composable
fun SchwimmerTab(state: UiState, vm: SchwimmgruppenViewModel, breit: Boolean) {
    if (!breit) return SchwimmerListe(state, vm, breit)
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(0.45f)) { SchwimmerListe(state, vm, breit) }
        VerticalDivider()
        Box(Modifier.weight(0.55f)) {
            val id = state.auswahl?.takeIf { state.bestand.mitglied(it) != null }
            if (id == null) {
                Column(Modifier.padding(24.dp)) { Leer("Schwimmer links antippen, um Erfolge, Zeiten und Anwesenheit zu sehen.") }
            } else {
                key(id) { SchwimmerScreen(id, state, vm, onBack = null) }
            }
        }
    }
}

@Composable
private fun SchwimmerListe(state: UiState, vm: SchwimmgruppenViewModel, breit: Boolean) {
    val b = state.bestand
    var suche by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.AKTIV) }
    val datei = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importLesen) }

    val liste = b.mitglieder
        .filter { m -> suche.isBlank() || listOf(m.name, m.ort, m.email, m.notiz, m.mitgliedsnummer).any { it.contains(suche.trim(), ignoreCase = true) } }
        .filter { m ->
            when (filter) {
                Filter.AKTIV -> m.aktiv
                Filter.OHNE -> m.aktiv && m.gruppen.none { b.gruppe(it) != null }
                Filter.WETTKAMPF -> m.aktiv && (m.interesse == Interesse.JA || m.interesse == Interesse.VIELLEICHT)
                Filter.INAKTIV -> !m.aktiv
            }
        }
        .sortedWith(Bestand.NACH_NAME)

    Page(
        title = "Schwimmer (${b.mitglieder.count { it.aktiv }})",
        onBack = null,
        bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { datei.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) { Text("Mitgliederliste") }
                OutlinedButton(onClick = { vm.oeffnen(Ansicht.SchwimmerBearbeiten(null)) }, modifier = Modifier.weight(1f)) { Text("Neu") }
            }
        },
    ) {
        Eingabe("Suchen (Name, Ort, E-Mail, Nummer)", suche, { suche = it })
        // Scrollbar, damit „Inaktiv“ auf schmalen Handys nicht umbricht.
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Filter.entries.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.text) }) }
        }
        if (b.mitglieder.isEmpty()) {
            Leer("Noch keine Schwimmer. Übernimm die Mitgliederliste aus Excel oder CSV oder lege Schwimmer von Hand an.")
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(liste, key = { it.id }) { m ->
                val gewaehlt = breit && state.auswahl == m.id
                Card(
                    Modifier.fillMaxWidth().clickable {
                        if (breit) vm.auswaehlen(m.id) else vm.oeffnen(Ansicht.Schwimmer(m.id))
                    },
                    colors = if (gewaehlt) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(m.nachname + ", " + m.vorname + zeichen(m, b.einstellungen.gesundheitAnzeigen), style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(m.jahrgang?.let { "Jg. $it" }, gruppenText(b, m)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Unterseite eines Schwimmers: Gruppen, Wettkampf, Erfolge, Zeiten, Anwesenheit, Kontakt. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchwimmerScreen(id: String, state: UiState, vm: SchwimmgruppenViewModel, onBack: (() -> Unit)? = { vm.zurueck() }) {
    val b = state.bestand
    val m = b.mitglied(id) ?: return
    val heute = LocalDate.now()
    var erfolgDialog by remember { mutableStateOf<Erfolg?>(null) }
    var zeitDialog by remember { mutableStateOf<Zeit?>(null) }
    var alleZeiten by remember { mutableStateOf(false) }

    Page(
        title = m.name + zeichen(m, b.einstellungen.gesundheitAnzeigen),
        onBack = onBack,
        actions = { TextButton(onClick = { vm.oeffnen(Ansicht.SchwimmerBearbeiten(id)) }) { Text("Bearbeiten") } },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val alter = m.alterAm(heute)
            Text(
                listOfNotNull(
                    m.geburtsdatum?.let { "geb. ${it.format(DATUM)}" + (alter?.let { a -> " ($a Jahre)" } ?: "") },
                    m.eintritt.takeIf { it.isNotBlank() }?.let { "Eintritt $it" },
                    "inaktiv".takeIf { !m.aktiv },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (m.fotorechte == false) Text("📵 Keine Fotorechte: nicht fotografieren oder veröffentlichen.", color = MaterialTheme.colorScheme.error)
            if (b.einstellungen.gesundheitAnzeigen && m.gesundheitserklaerung == false) Text("⚠ Gesundheitserklärung fehlt.", color = MaterialTheme.colorScheme.error)

            Abschnitt("Gruppen") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    b.gruppen.forEach { g ->
                        FilterChip(selected = g.id in m.gruppen, onClick = { vm.einteilen(id, g.id, g.id !in m.gruppen) }, label = { Text(g.name) })
                    }
                }
            }

            Abschnitt("Wettkampf") {
                Auswahl("Interesse", Interesse.entries, m.interesse, { it.text }, { i -> vm.mitgliedAendern(id) { it.copy(interesse = i) } })
                if (m.interesseNotiz.isNotBlank()) Text(m.interesseNotiz, style = MaterialTheme.typography.bodyMedium)
            }

            Abschnitt("Erfolge") {
                if (m.erfolge.isEmpty()) Text("Noch keine eingetragen.", style = MaterialTheme.typography.bodyMedium)
                m.erfolge.sortedByDescending { it.datum ?: LocalDate.MIN }.forEach { e ->
                    Row(Modifier.fillMaxWidth().clickable { erfolgDialog = e }.padding(vertical = 4.dp)) {
                        Text(
                            (e.datum?.format(DATUM) ?: "ohne Datum") + "  ",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(e.text, style = MaterialTheme.typography.bodyLarge)
                            val zusatz = listOf(e.art.text, e.notiz).filter { it.isNotBlank() }.joinToString(" · ")
                            Text(zusatz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                OutlinedButton(onClick = { erfolgDialog = Erfolg("", "", datum = heute) }) { Text("Erfolg eintragen") }
            }

            Abschnitt("Zeiten") {
                val best = Zeiten.bestzeiten(m, b.einstellungen.strecken)
                if (best.isEmpty()) Text("Noch keine Zeiten.", style = MaterialTheme.typography.bodyMedium)
                best.forEach { z -> ZeitZeile(z, "Bestzeit") { zeitDialog = z } }
                if (m.zeiten.size > best.size) {
                    TextButton(onClick = { alleZeiten = !alleZeiten }) { Text(if (alleZeiten) "Nur Bestzeiten" else "Alle ${m.zeiten.size} Zeiten") }
                    if (alleZeiten) {
                        m.zeiten.sortedWith(compareBy<Zeit> { it.strecke }.thenByDescending { it.datum ?: LocalDate.MIN }).forEach { z ->
                            ZeitZeile(z, null) { zeitDialog = z }
                        }
                    }
                }
                OutlinedButton(onClick = { zeitDialog = Zeit("", b.einstellungen.strecken.firstOrNull().orEmpty(), 0, heute) }) { Text("Zeit eintragen") }
            }

            Abschnitt("Anwesenheit") {
                val t = Statistik.mitglied(b, id, heute.minusMonths(3), heute)
                Text(
                    if (t.trainings == 0) "In den letzten 3 Monaten noch nichts erfasst."
                    else "Letzte 3 Monate: ${t.anwesend} von ${t.trainings} erfassten Trainings (${t.quote} %)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Statistik.besuche(b, id, 6).forEach { e ->
                    Text(
                        "${e.datum.format(DATUM_KURZ)} ${e.beginn.format(UHRZEIT)}  ${b.gruppe(e.gruppeId)?.name.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Abschnitt("Kontakt") {
                val zeilen = listOf(
                    m.telefon,
                    m.email,
                    listOf(m.strasse, listOf(m.plz, m.ort).filter { it.isNotBlank() }.joinToString(" ")).filter { it.isNotBlank() }.joinToString(", "),
                    m.mitgliedsnummer.takeIf { it.isNotBlank() }?.let { "Mitgliedsnummer $it" }.orEmpty(),
                    m.notiz,
                ).filter { it.isNotBlank() }
                if (zeilen.isEmpty()) Text("Keine Angaben.", style = MaterialTheme.typography.bodyMedium)
                zeilen.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }

    erfolgDialog?.let { e ->
        ErfolgDialog(
            e, b.einstellungen.abzeichen,
            onSave = { neu ->
                vm.mitgliedAendern(id) { mm -> mm.copy(erfolge = mm.erfolge.filterNot { it.id == neu.id } + neu) }
                erfolgDialog = null
            },
            onDelete = if (e.id.isEmpty()) null else ({
                vm.mitgliedAendern(id) { mm -> mm.copy(erfolge = mm.erfolge.filterNot { it.id == e.id }) }
                erfolgDialog = null
            }),
            onDismiss = { erfolgDialog = null },
        )
    }
    zeitDialog?.let { z ->
        ZeitDialog(
            z, b.einstellungen.strecken,
            onSave = { neu ->
                vm.mitgliedAendern(id) { mm -> mm.copy(zeiten = mm.zeiten.filterNot { it.id == neu.id } + neu) }
                zeitDialog = null
            },
            onDelete = if (z.id.isEmpty()) null else ({
                vm.mitgliedAendern(id) { mm -> mm.copy(zeiten = mm.zeiten.filterNot { it.id == z.id }) }
                zeitDialog = null
            }),
            onDismiss = { zeitDialog = null },
        )
    }
}

@Composable
private fun ZeitZeile(z: Zeit, markierung: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(z.strecke, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(z.datum?.format(DATUM), if (z.wettkampf) "Wettkampf" else "Training", z.anlass.takeIf { it.isNotBlank() }, markierung)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(Zeiten.text(z.hundertstel) + " s", style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ErfolgDialog(e: Erfolg, abzeichen: List<String>, onSave: (Erfolg) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(e.text) }
    var art by remember { mutableStateOf(e.art) }
    var datum by remember { mutableStateOf(e.datum?.format(DATUM).orEmpty()) }
    var notiz by remember { mutableStateOf(e.notiz) }
    val datumFehler = datum.isNotBlank() && datumOderNull(datum) == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (e.id.isEmpty()) "Erfolg eintragen" else "Erfolg") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Auswahl("Art", ErfolgArt.entries, art, { it.text }, { art = it })
                if (art == ErfolgArt.ABZEICHEN) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        abzeichen.forEach { a -> FilterChip(selected = text == a, onClick = { text = a }, label = { Text(a) }) }
                    }
                }
                Eingabe(if (art == ErfolgArt.WETTKAMPF) "Platzierung, z. B. 2. Platz 50 m Brust" else "Erfolg", text, { text = it }, pflicht = true)
                DatumEingabe("Datum", datum, { datum = it }, context, if (datumFehler) "Datum nicht erkannt" else null)
                Eingabe("Notiz, z. B. Wettkampf oder Ort", notiz, { notiz = it })
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank() && !datumFehler, onClick = {
                onSave(Erfolg(e.id.ifEmpty { UUID.randomUUID().toString() }, text.trim(), art, datumOderNull(datum), notiz.trim()))
            }) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Löschen") }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

@Composable
private fun ZeitDialog(z: Zeit, strecken: List<String>, onSave: (Zeit) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var strecke by remember { mutableStateOf(z.strecke) }
    var zeit by remember { mutableStateOf(if (z.hundertstel > 0) Zeiten.text(z.hundertstel) else "") }
    var datum by remember { mutableStateOf(z.datum?.format(DATUM).orEmpty()) }
    var wettkampf by remember { mutableStateOf(z.wettkampf) }
    var anlass by remember { mutableStateOf(z.anlass) }
    val wert = Zeiten.lesen(zeit)
    val datumFehler = datum.isNotBlank() && datumOderNull(datum) == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (z.id.isEmpty()) "Zeit eintragen" else "Zeit") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (strecken.isNotEmpty()) Auswahl("Strecke", strecken, strecke.takeIf { it in strecken } ?: strecken.first(), { it }, { strecke = it })
                Eingabe("Strecke (frei)", strecke, { strecke = it }, pflicht = true)
                Eingabe(
                    "Zeit", zeit, { zeit = it }, typ = KeyboardType.Text,
                    fehler = if (zeit.isNotBlank() && wert == null) "z. B. 38,52 oder 1:05,32" else null,
                    hilfe = "Sekunden,Hundertstel oder Minuten:Sekunden,Hundertstel", pflicht = true,
                )
                DatumEingabe("Datum", datum, { datum = it }, context, if (datumFehler) "Datum nicht erkannt" else null)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Im Wettkampf", Modifier.weight(1f))
                    Switch(checked = wettkampf, onCheckedChange = { wettkampf = it })
                }
                Eingabe("Wettkampf / Anlass", anlass, { anlass = it })
            }
        },
        confirmButton = {
            TextButton(enabled = strecke.isNotBlank() && wert != null && !datumFehler, onClick = {
                onSave(Zeit(z.id.ifEmpty { UUID.randomUUID().toString() }, strecke.trim(), wert ?: 0, datumOderNull(datum), wettkampf, anlass.trim()))
            }) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Löschen") }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

private fun janeinText(b: Boolean?) = when (b) { true -> "ja"; false -> "nein"; null -> "unbekannt" }

@Composable
fun SchwimmerBearbeitenScreen(id: String?, state: UiState, vm: SchwimmgruppenViewModel) {
    val alt = id?.let { state.bestand.mitglied(it) }
    val context = LocalContext.current
    var vorname by remember(state.ladeNummer) { mutableStateOf(alt?.vorname.orEmpty()) }
    var nachname by remember(state.ladeNummer) { mutableStateOf(alt?.nachname.orEmpty()) }
    var geburt by remember(state.ladeNummer) { mutableStateOf(alt?.geburtsdatum?.format(DATUM).orEmpty()) }
    var geschlecht by remember(state.ladeNummer) { mutableStateOf(alt?.geschlecht.orEmpty()) }
    var nummer by remember(state.ladeNummer) { mutableStateOf(alt?.mitgliedsnummer.orEmpty()) }
    var telefon by remember(state.ladeNummer) { mutableStateOf(alt?.telefon.orEmpty()) }
    var email by remember(state.ladeNummer) { mutableStateOf(alt?.email.orEmpty()) }
    var strasse by remember(state.ladeNummer) { mutableStateOf(alt?.strasse.orEmpty()) }
    var plz by remember(state.ladeNummer) { mutableStateOf(alt?.plz.orEmpty()) }
    var ort by remember(state.ladeNummer) { mutableStateOf(alt?.ort.orEmpty()) }
    var eintritt by remember(state.ladeNummer) { mutableStateOf(alt?.eintritt.orEmpty()) }
    var fotos by remember(state.ladeNummer) { mutableStateOf(alt?.fotorechte) }
    var gesundheit by remember(state.ladeNummer) { mutableStateOf(alt?.gesundheitserklaerung) }
    var interesseNotiz by remember(state.ladeNummer) { mutableStateOf(alt?.interesseNotiz.orEmpty()) }
    var notiz by remember(state.ladeNummer) { mutableStateOf(alt?.notiz.orEmpty()) }
    var aktiv by remember(state.ladeNummer) { mutableStateOf(alt?.aktiv ?: true) }
    var loeschenFragen by remember { mutableStateOf(false) }
    val gebFehler = geburt.isNotBlank() && datumOderNull(geburt) == null

    FormPage(
        title = if (alt == null) "Neuer Schwimmer" else "Bearbeiten",
        onBack = { vm.zurueck() },
        bottom = {
            BigButton("Speichern", enabled = (vorname.isNotBlank() || nachname.isNotBlank()) && !gebFehler, onClick = {
                val basis = alt ?: Mitglied(UUID.randomUUID().toString(), "", "")
                vm.mitgliedSpeichern(
                    basis.copy(
                        vorname = vorname.trim(), nachname = nachname.trim(), geburtsdatum = datumOderNull(geburt),
                        geschlecht = geschlecht, mitgliedsnummer = nummer.trim(), telefon = telefon.trim(), email = email.trim(),
                        strasse = strasse.trim(), plz = plz.trim(), ort = ort.trim(), eintritt = eintritt.trim(),
                        fotorechte = fotos, gesundheitserklaerung = gesundheit, interesseNotiz = interesseNotiz.trim(),
                        notiz = notiz.trim(), aktiv = aktiv,
                    ),
                )
            })
        },
    ) {
        val ohneName = vorname.isBlank() && nachname.isBlank()
        Eingabe("Vorname", vorname, { vorname = it }, pflicht = true, pflichtFehlt = ohneName, pflichtHinweis = Pflichtfeld.HINWEIS_EINES)
        Eingabe("Nachname", nachname, { nachname = it }, pflicht = true, pflichtFehlt = ohneName, pflichtHinweis = Pflichtfeld.HINWEIS_EINES)
        DatumEingabe("Geburtsdatum", geburt, { geburt = it }, context, if (gebFehler) "Datum nicht erkannt" else null)
        Auswahl("Geschlecht", listOf("", "w", "m", "d"), geschlecht, { mapOf("" to "–", "w" to "weiblich", "m" to "männlich", "d" to "divers").getValue(it) }, { geschlecht = it })
        Auswahl("Fotorechte", listOf(null, true, false), fotos, ::janeinText, { fotos = it })
        if (state.bestand.einstellungen.gesundheitAnzeigen) {
            Auswahl("Gesundheitserklärung", listOf(null, true, false), gesundheit, ::janeinText, { gesundheit = it })
        }
        Eingabe("Wettkampf: Notiz", interesseNotiz, { interesseNotiz = it }, hilfe = "z. B. nur Heimwettkämpfe")
        Eingabe("Telefon", telefon, { telefon = it }, typ = KeyboardType.Phone)
        Eingabe("E-Mail", email, { email = it }, typ = KeyboardType.Email)
        Eingabe("Straße", strasse, { strasse = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Eingabe("PLZ", plz, { plz = it }, Modifier.weight(0.4f), KeyboardType.Number)
            Eingabe("Ort", ort, { ort = it }, Modifier.weight(0.6f))
        }
        Eingabe("Mitgliedsnummer", nummer, { nummer = it })
        Eingabe("Eintritt (Jahr oder Datum)", eintritt, { eintritt = it })
        Eingabe("Bemerkung", notiz, { notiz = it }, mehrzeilig = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Aktiv")
                Text("Inaktive (ausgetreten, ruhend) erscheinen nicht in Gruppen und Halle.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = aktiv, onCheckedChange = { aktiv = it })
        }
        if (alt != null) OutlinedButton(onClick = { loeschenFragen = true }, modifier = Modifier.fillMaxWidth()) { Text("Löschen") }
    }
    if (loeschenFragen && alt != null) {
        ConfirmDialog(
            "${alt.name} mit Erfolgen und Zeiten löschen? Bisherige Anwesenheiten zählen ohne Namen weiter. " +
                "Bei Austritt besser „Aktiv“ ausschalten.",
            "Löschen",
            { vm.mitgliedLoeschen(alt.id) },
            { loeschenFragen = false },
        )
    }
}

/** Nach dem Import: Mitglieder, die in der Liste fehlen, inaktiv setzen oder behalten. */
@Composable
fun FehlendeDialog(state: UiState, vm: SchwimmgruppenViewModel) {
    val l = state.nichtInListe
    AlertDialog(
        onDismissRequest = vm::fehlendeBehalten,
        title = { Text("${l.size} nicht mehr in der Liste") },
        text = {
            Column(Modifier.fillMaxHeight(0.6f).verticalScroll(rememberScrollState())) {
                Text("Diese Schwimmer stehen nicht in der eingelesenen Mitgliederliste. Als inaktiv markieren? Ihre Zeiten und Erfolge bleiben erhalten.")
                l.sortedWith(Bestand.NACH_NAME).forEach { Text("• ${it.name}") }
            }
        },
        confirmButton = { TextButton(onClick = vm::fehlendeInaktiv) { Text("Inaktiv setzen") } },
        dismissButton = { TextButton(onClick = vm::fehlendeBehalten) { Text("Behalten") } },
    )
}
