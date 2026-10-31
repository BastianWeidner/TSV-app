package de.dachapp.schwimmleitung.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import de.dachapp.schwimmleitung.logic.AufgabeStatus
import de.dachapp.schwimmleitung.logic.Beleg
import de.dachapp.schwimmleitung.logic.Format
import de.dachapp.schwimmleitung.logic.Meldung
import de.dachapp.schwimmleitung.logic.MeldungArt
import de.dachapp.schwimmleitung.logic.Zustand
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ZEIT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

fun zeitText(ms: Long): String =
    java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().format(ZEIT)

/** Startseite ohne PIN: melden, Beleg erfassen, eigene Aufgaben abhaken. */
@Composable
fun HalleTab(state: UiState, vm: LeitungViewModel, breit: Boolean) {
    val d = state.daten
    val heute = LocalDate.now()
    val ich = d.person(state.geraet.ichPersonId)
    val meine = if (ich == null) emptyList() else d.sichtbareAufgaben.filter { it.offen && it.personId == ich.id }
        .sortedBy { it.faellig ?: LocalDate.MAX }
    val offen = d.sichtbareMeldungen.filter { !it.erledigt }

    FormPage(title = "Hallenbericht", onBack = null, actions = { SyncHinweis(state) }) {
        Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("Defekt melden", { vm.oeffnen(Ansicht.MeldungNeu(MeldungArt.DEFEKT)) }, modifier = Modifier.weight(1f))
                BigButton("Vorkommnis", { vm.oeffnen(Ansicht.MeldungNeu(MeldungArt.VORKOMMNIS)) }, modifier = Modifier.weight(1f))
            }
            OutlinedButton(onClick = { vm.oeffnen(Ansicht.BelegBearbeiten(null)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Beleg oder Barbewegung erfassen")
            }
            Auswahl(
                "Ich bin",
                listOf<String?>(null) + d.aktivePersonen.map { it.id },
                state.geraet.ichPersonId,
                { id -> d.person(id)?.name ?: "${state.geraet.name} (Gerät)" },
                vm::ich,
                Modifier.fillMaxWidth(),
            )
            if (ich != null) {
                Abschnitt("Meine Aufgaben") {
                    if (meine.isEmpty()) Text("Keine offenen Aufgaben.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    meine.forEach { a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = false, onCheckedChange = { vm.aufgabeStatus(a.id, AufgabeStatus.ERLEDIGT) })
                            Column(Modifier.weight(1f)) {
                                Text(a.titel)
                                a.faellig?.let {
                                    Text(
                                        "bis ${Format.datum(it)}" + if (a.ueberfaellig(heute)) " – überfällig" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (a.ueberfaellig(heute)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Abschnitt("Offene Meldungen (${offen.size})") {
                if (offen.isEmpty()) Text("Nichts offen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                offen.take(if (breit) 12 else 6).forEach { m -> MeldungZeile(m, state) { vm.oeffnen(Ansicht.MeldungZeigen(m.id)) } }
                TextButton(onClick = { vm.oeffnen(Ansicht.Meldungen) }) { Text("Alle Meldungen") }
            }
        }
    }
}

@Composable
fun SyncHinweis(state: UiState) {
    val text = when (val s = state.sync) {
        SyncZustand.KeineDatei -> "nur dieses Gerät"
        SyncZustand.Laeuft -> "gleicht ab …"
        is SyncZustand.Ok -> "abgeglichen " + zeitText(s.zeit).takeLast(5)
        is SyncZustand.Fehler -> "⚠ Abgleich"
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
}

@Composable
private fun MeldungZeile(m: Meldung, state: UiState, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (m.art == MeldungArt.DEFEKT) "🔧" else "📋", modifier = Modifier.padding(end = 8.dp))
        Column(Modifier.weight(1f)) {
            val material = state.daten.materialStueck(m.materialId)?.name
            Text(listOfNotNull(material, m.text.ifBlank { null }).joinToString(": "), maxLines = 2)
            Text(
                zeitText(m.zeit) + (m.von.ifBlank { null }?.let { " · $it" } ?: "") + if (m.foto != null) " · Foto" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (m.erledigt) Text("✓", color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun MeldungenScreen(state: UiState, vm: LeitungViewModel) {
    var nurOffen by rememberSaveable { mutableStateOf(true) }
    val liste = state.daten.sichtbareMeldungen.filter { !nurOffen || !it.erledigt }
    Page(title = "Meldungen", onBack = { vm.zurueck() }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = nurOffen, onClick = { nurOffen = true }, label = { Text("Offen") })
            FilterChip(selected = !nurOffen, onClick = { nurOffen = false }, label = { Text("Alle") })
        }
        if (liste.isEmpty()) Leer("Keine Meldungen.")
        LazyColumn(Modifier.fillMaxWidth()) {
            items(liste, key = { it.id }) { m ->
                MeldungZeile(m, state) { vm.oeffnen(Ansicht.MeldungZeigen(m.id)) }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun MeldungNeuScreen(art: MeldungArt, state: UiState, vm: LeitungViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    var materialId by rememberSaveable { mutableStateOf<String?>(null) }
    var foto by remember { mutableStateOf<String?>(null) }
    val material = state.daten.sichtbaresMaterial.filter { it.zustand != Zustand.AUSGESONDERT }
    val ok = text.isNotBlank() || materialId != null
    FormPage(
        title = if (art == MeldungArt.DEFEKT) "Defekt melden" else "Vorkommnis notieren",
        onBack = { vm.zurueck() },
        bottom = {
            BigButton("Melden", { vm.melden(art, text.ifBlank { "defekt" }, materialId, foto) }, enabled = ok)
        },
    ) {
        if (art == MeldungArt.DEFEKT && material.isNotEmpty()) {
            Auswahl(
                "Was",
                listOf<String?>(null) + material.map { it.id },
                materialId,
                { id -> state.daten.materialStueck(id)?.let { m -> m.name + (m.ort.ifBlank { null }?.let { " ($it)" } ?: "") } ?: "nicht in der Liste" },
                { materialId = it },
                Modifier.fillMaxWidth(),
            )
        }
        Eingabe(
            if (art == MeldungArt.DEFEKT) "Was ist kaputt?" else "Was ist passiert?",
            text,
            { text = it },
            mehrzeilig = true,
            pflicht = true,
            pflichtFehlt = !ok,
            pflichtHinweis = if (art == MeldungArt.DEFEKT && material.isNotEmpty()) "Pflichtfeld, wenn oben nichts gewählt" else Pflichtfeld.HINWEIS,
            hilfe = if (art == MeldungArt.DEFEKT) "Z. B. „Bahnleine 3: Schwimmer gerissen“" else "Z. B. „Kind auf Fliesen ausgerutscht, Pflaster, Eltern informiert“",
        )
        FotoWahl(foto) { foto = it }
        Text("Gemeldet von: ${state.melder}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MeldungScreen(id: String, state: UiState, vm: LeitungViewModel) {
    val m = state.daten.meldung(id) ?: return Leer("Meldung nicht gefunden.")
    var loeschen by remember { mutableStateOf(false) }
    FormPage(
        title = m.art.text,
        onBack = { vm.zurueck() },
        actions = { if (state.leitungOffen) TextButton(onClick = { loeschen = true }) { Text("Löschen") } },
    ) {
        state.daten.materialStueck(m.materialId)?.let { Text(it.name + (it.ort.ifBlank { null }?.let { o -> " · $o" } ?: ""), style = MaterialTheme.typography.titleMedium) }
        Text(m.text, style = MaterialTheme.typography.bodyLarge)
        Text(zeitText(m.zeit) + (m.von.ifBlank { null }?.let { " · $it" } ?: ""), color = MaterialTheme.colorScheme.onSurfaceVariant)
        m.foto?.let { FotoBild(it) }
        if (state.leitungOffen) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Erledigt", Modifier.weight(1f))
                Switch(checked = m.erledigt, onCheckedChange = { vm.meldungErledigt(m.id, it) })
            }
            state.daten.aufgabe(m.aufgabeId)?.let { a ->
                OutlinedButton(onClick = { vm.oeffnen(Ansicht.AufgabeBearbeiten(a.id)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Aufgabe: ${a.titel} (${a.status.text})")
                }
            }
        } else if (m.erledigt) {
            Text("Erledigt ✓", color = MaterialTheme.colorScheme.primary)
        }
    }
    if (loeschen) ConfirmDialog("Diese Meldung löschen?", "Löschen", { vm.meldungLoeschen(m.id) }, { loeschen = false })
}

/** Neuer Beleg (auch ohne PIN) oder Bearbeiten (nur Leitung). */
@Composable
fun BelegScreen(id: String?, state: UiState, vm: LeitungViewModel) {
    val alt = id?.let { state.daten.beleg(it) }
    val context = LocalContext.current
    var datum by rememberSaveable { mutableStateOf(Format.datum(alt?.datum ?: LocalDate.now())) }
    var betrag by rememberSaveable { mutableStateOf(alt?.let { Format.euro(it.betragCent).removeSuffix(" €") }.orEmpty()) }
    var einnahme by rememberSaveable { mutableStateOf(alt?.einnahme ?: false) }
    var zweck by rememberSaveable { mutableStateOf(alt?.zweck.orEmpty()) }
    var von by rememberSaveable { mutableStateOf(alt?.von ?: state.melder) }
    var foto by remember { mutableStateOf(alt?.foto) }
    var uebergeben by rememberSaveable { mutableStateOf(alt?.uebergeben ?: false) }
    var loeschen by remember { mutableStateOf(false) }
    val tag = datumOderNull(datum)
    val cent = Format.cent(betrag)
    val ok = tag != null && cent != null && cent > 0 && zweck.isNotBlank()

    FormPage(
        title = if (alt == null) "Beleg erfassen" else "Beleg",
        onBack = { vm.zurueck() },
        actions = { if (alt != null) TextButton(onClick = { loeschen = true }) { Text("Löschen") } },
        bottom = {
            BigButton("Speichern", {
                vm.speichern(
                    Beleg(
                        id = alt?.id ?: vm.neueId("beleg"),
                        datum = tag!!,
                        betragCent = cent!!,
                        einnahme = einnahme,
                        zweck = zweck,
                        von = von.trim(),
                        foto = foto,
                        uebergeben = uebergeben,
                        uebergebenAm = if (uebergeben) alt?.uebergebenAm ?: LocalDate.now() else null,
                    ),
                )
                vm.zurueck()
                if (alt == null) vm.showMessage("Beleg gespeichert. Den Papierbeleg bitte in die Belegmappe legen.")
            }, enabled = ok)
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !einnahme, onClick = { einnahme = false }, label = { Text("Ausgabe") })
            FilterChip(selected = einnahme, onClick = { einnahme = true }, label = { Text("Einnahme") })
        }
        Eingabe(
            "Betrag in €", betrag, { betrag = it }, typ = KeyboardType.Decimal, pflicht = true,
            fehler = if (betrag.isNotBlank() && (cent == null || cent == 0L)) "Betrag wie 12,50" else null,
        )
        Eingabe("Wofür", zweck, { zweck = it }, pflicht = true, hilfe = "Z. B. „Pflaster Erste-Hilfe-Kasten“ oder „Eintritt Gäste bar“")
        DatumEingabe("Datum", datum, { datum = it }, context, fehler = if (datum.isNotBlank() && tag == null) "Datum wie 21.10.2026" else null, pflicht = true)
        Eingabe("Von", von, { von = it })
        FotoWahl(foto) { foto = it }
        if (alt != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("An Kassenwart übergeben")
                    alt.uebergebenAm?.let { Text("am ${Format.datum(it)}", style = MaterialTheme.typography.bodySmall) }
                }
                Switch(checked = uebergeben, onCheckedChange = { uebergeben = it })
            }
        }
    }
    if (loeschen && alt != null) ConfirmDialog("Diesen Beleg löschen?", "Löschen", { vm.belegLoeschen(alt.id) }, { loeschen = false })
}

/** Foto aufnehmen oder aus der Galerie wählen; zeigt das gewählte Foto mit "Entfernen". */
@Composable
fun FotoWahl(foto: String?, onFoto: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ziel by remember { mutableStateOf<Uri?>(null) }
    var laedt by remember { mutableStateOf(false) }
    fun uebernehmen(uri: Uri) {
        laedt = true
        scope.launch {
            val b = withContext(Dispatchers.IO) { Dateien.fotoLesen(context, uri) }
            laedt = false
            if (b != null) onFoto(b)
        }
    }
    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) ziel?.let(::uebernehmen) }
    val galerie = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::uebernehmen) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val u = Dateien.fotoZiel(context)
                ziel = u
                kamera.launch(u)
            }, modifier = Modifier.weight(1f)) { Text(if (foto == null) "Foto aufnehmen" else "Neues Foto") }
            OutlinedButton(onClick = {
                galerie.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, modifier = Modifier.weight(1f)) { Text("Aus Galerie") }
        }
        if (laedt) Text("Foto wird verkleinert …", style = MaterialTheme.typography.bodySmall)
        if (foto != null) {
            FotoBild(foto)
            TextButton(onClick = { onFoto(null) }) { Text("Foto entfernen") }
        }
    }
}

@Composable
fun FotoBild(base64: String, modifier: Modifier = Modifier) {
    val bild = remember(base64) { Dateien.fotoBitmap(base64)?.asImageBitmap() }
    if (bild != null) {
        Image(
            bild,
            contentDescription = "Foto",
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxWidth().heightIn(max = 420.dp),
        )
    }
}
