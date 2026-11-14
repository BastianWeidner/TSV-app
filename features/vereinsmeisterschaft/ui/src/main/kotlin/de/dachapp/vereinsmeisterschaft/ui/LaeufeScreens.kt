package de.dachapp.vereinsmeisterschaft.ui

import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.dachapp.module.BigButton
import de.dachapp.module.ConfirmDialog
import de.dachapp.module.Leer
import de.dachapp.module.Page
import de.dachapp.scanner.Texterkennung
import de.dachapp.vereinsmeisterschaft.logic.Ergebnis
import de.dachapp.vereinsmeisterschaft.logic.Laufeinteilung
import de.dachapp.vereinsmeisterschaft.logic.OcrZeile
import de.dachapp.vereinsmeisterschaft.logic.Scan
import de.dachapp.vereinsmeisterschaft.logic.Status
import de.dachapp.vereinsmeisterschaft.logic.Veranstaltung
import de.dachapp.vereinsmeisterschaft.logic.Zeit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Zeitzettel-Foto lesen: Texterkennung offline, dann Zuordnung über die gedruckten Kennzeichen. */
suspend fun zettelLesen(context: android.content.Context, v: Veranstaltung, uri: Uri) = withContext(Dispatchers.Default) {
    val bild = Texterkennung.laden(context, uri)
    val zeilen = Texterkennung.lesen(bild).map { OcrZeile(it.text, it.links, it.oben, it.rechts, it.unten, it.sicherheit) }
    Scan.auswerten(
        zeilen,
        bahnenVon = { nr -> v.laeufe.firstOrNull { it.nr == nr }?.strecke?.bahnen },
        erwartet = { nr, bahn ->
            val l = v.laeufe.firstOrNull { it.nr == nr }
            l?.auf(bahn)?.let { v.person(it) }?.meldezeiten?.get(l.strecke.lage)
        },
    )
}

@Composable
fun LaeufeListe(v: Veranstaltung, nav: (Ansicht) -> Unit, meldung: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var neuFragen by remember { mutableStateOf(false) }
    var arbeitet by remember { mutableStateOf<String?>(null) }
    var fotoZiel by remember { mutableStateOf<Uri?>(null) }

    fun einteilen() = VmStore.aendern { it.copy(laeufe = Laufeinteilung.einteilen(it)) }

    fun lesen(uri: Uri) {
        arbeitet = "Zettel wird gelesen …"
        scope.launch {
            try {
                val treffer = zettelLesen(context, v, uri)
                if (treffer.isEmpty()) meldung("Auf dem Foto wurde kein Kennzeichen wie „L03 B2“ gefunden. Bitte den ganzen Zettel gerade und hell fotografieren.")
                else nav(Ansicht.ScanPruefen(treffer, uri.toString()))
            } catch (e: Exception) {
                meldung("Texterkennung fehlgeschlagen: ${e.message}")
            } finally {
                arbeitet = null
            }
        }
    }
    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) fotoZiel?.let(::lesen) }
    val galerie = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::lesen) }

    fun pdf(name: String, erzeugen: (java.io.File) -> Unit) {
        arbeitet = "PDF wird erstellt …"
        scope.launch {
            try {
                val f = Dateien.neueDatei(context, name, "pdf")
                withContext(Dispatchers.IO) { erzeugen(f) }
                Dateien.teilen(context, f, "application/pdf", name)
            } catch (e: Exception) {
                meldung("PDF ging nicht: ${e.message}")
            } finally {
                arbeitet = null
            }
        }
    }

    if (neuFragen) {
        ConfirmDialog(
            "Läufe neu einteilen? Die bisherige Einteilung wird ersetzt. Schon eingetragene Zeiten bleiben erhalten, " +
                "aber gedruckte Lauflisten und Zeitzettel passen danach nicht mehr.",
            "Neu einteilen", ::einteilen,
        ) { neuFragen = false }
    }

    val fehlend = Laufeinteilung.fehlend(v)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (v.laeufe.isEmpty()) einteilen() else neuFragen = true }, enabled = v.teilnehmer.isNotEmpty()) {
                    Text(if (v.laeufe.isEmpty()) "Läufe einteilen" else "Neu einteilen")
                }
                if (v.laeufe.isNotEmpty()) {
                    OutlinedButton(onClick = { pdf("Laufliste ${v.titel}") { Druck.laufliste(it, v) } }) { Text("Laufliste PDF") }
                    OutlinedButton(onClick = { pdf("Zeitzettel ${v.titel}") { Druck.zeitzettel(it, v) } }) { Text("Zeitzettel PDF") }
                    OutlinedButton(onClick = { fotoZiel = Dateien.fotoZiel(context); kamera.launch(fotoZiel!!) }) { Text("Zettel fotografieren") }
                    OutlinedButton(onClick = { galerie.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Foto aus Galerie") }
                }
            }
        }
        arbeitet?.let { t ->
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.width(24.dp)); Text(t)
                }
            }
        }
        item {
            Text(
                "Reihenfolge: ${v.regeln.laufOrdnung.text}; ${v.regeln.bahnenImBecken} Bahnen, Läufe " +
                    (if (v.regeln.laeufeNachGeschlecht) "nach Geschlecht getrennt" else "gemischt") + ". Ändern unter Mehr › Regeln.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (v.laeufe.isNotEmpty() && fehlend.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${fehlend.size} Starts stehen in keinem Lauf (Nachmeldungen): " + fehlend.take(4).joinToString { "${it.first.name} ${it.second.lage.kurz}" })
                    TextButton(onClick = {
                        VmStore.aendern { ver ->
                            ver.copy(laeufe = Laufeinteilung.fehlend(ver).fold(ver.laeufe) { l, (t, s) -> Laufeinteilung.nachtragen(l, t.id, s, ver.regeln.bahnenImBecken) })
                        }
                    }) { Text("In freie Bahnen bzw. neue Läufe nachtragen") }
                }
            }
        }
        if (v.laeufe.isEmpty()) item {
            Leer(if (v.teilnehmer.isEmpty()) "Erst Teilnehmer eintragen, dann Läufe einteilen." else "Noch keine Läufe. „Läufe einteilen“ ordnet nach Jahrgang und Meldezeit.")
        }
        var vorher: de.dachapp.vereinsmeisterschaft.logic.Strecke? = null
        for (lauf in v.laeufe) {
            if (lauf.strecke != vorher) {
                vorher = lauf.strecke
                item(key = "k${lauf.nr}") {
                    Text(lauf.strecke.text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item(key = lauf.nr) {
                val fertig = lauf.belegung.all { b -> v.ergebnis(b.teilnehmer, lauf.strecke.lage) != null }
                Card(Modifier.fillMaxWidth().clickable { nav(Ansicht.Erfassen(lauf.nr)) }) {
                    Column(Modifier.padding(12.dp)) {
                        Row {
                            Text("Lauf ${lauf.nr}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(if (fertig) "✓ erfasst" else "", color = MaterialTheme.colorScheme.primary)
                        }
                        lauf.belegung.forEach { b ->
                            val t = v.person(b.teilnehmer)
                            val e = v.ergebnis(b.teilnehmer, lauf.strecke.lage)
                            Row {
                                Text("Bahn ${b.bahn}", Modifier.width(64.dp), style = MaterialTheme.typography.bodySmall)
                                Text("${t?.name ?: "?"} (${t?.jahrgang ?: ""})", Modifier.weight(1f))
                                Text(if (e == null) "" else if (e.gueltig) Zeit.text(e.zeit) else e.status.kurz)
                            }
                        }
                    }
                }
            }
        }
        item { Text(" ", Modifier.padding(bottom = 24.dp)) }
    }
}

/** Zeiten eines Laufs eintragen: Tippen oder mit der eingebauten Stoppuhr (Start, je Bahn Stopp). */
@Composable
fun LaufErfassen(v: Veranstaltung, nr: Int, gehe: (Int) -> Unit, zurueck: () -> Unit) {
    val lauf = v.laeufe.firstOrNull { it.nr == nr }
    if (lauf == null) {
        Page("Lauf $nr", zurueck) { Leer("Diesen Lauf gibt es nicht mehr.") }
        return
    }
    val lage = lauf.strecke.lage
    var start by remember(nr) { mutableLongStateOf(0L) }
    var jetzt by remember(nr) { mutableLongStateOf(0L) }
    LaunchedEffect(start) { while (start > 0) { jetzt = SystemClock.elapsedRealtime(); delay(47) } }

    fun setzen(tid: Long, e: Ergebnis?) = VmStore.aendern { it.mitErgebnis(tid, lage, e) }

    Page(
        "Lauf $nr · ${lauf.strecke.text}", zurueck,
        bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { gehe(nr - 1) }, enabled = nr > 1, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("‹ Lauf ${nr - 1}") }
                Button(onClick = { if (nr < v.laeufe.size) gehe(nr + 1) else zurueck() }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Text(if (nr < v.laeufe.size) "Lauf ${nr + 1} ›" else "Fertig")
                }
            }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (start == 0L) "Stoppuhr" else Zeit.text(((jetzt - start) / 10).toInt().coerceAtLeast(1)),
                        style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f),
                    )
                    if (start == 0L) Button(onClick = { start = SystemClock.elapsedRealtime(); jetzt = start }) { Text("Start") }
                    else OutlinedButton(onClick = { start = 0L }) { Text("Zurücksetzen") }
                }
            }
            for (bahn in 1..v.regeln.bahnenImBecken) {
                val tid = lauf.auf(bahn)
                val t = tid?.let { v.person(it) }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Bahn $bahn", fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp))
                            Text(t?.let { "${it.name} (${it.jahrgang})" } ?: "frei", Modifier.weight(1f))
                            Verschieben(v, nr, bahn)
                        }
                        if (t != null) ZeitZeile(v.ergebnis(t.id, lage), start, { z, st ->
                            setzen(t.id, if (z == null && st == null) null else Ergebnis(t.id, lage, z, st ?: Status.OK))
                        }, t.meldezeiten[lage])
                    }
                }
            }
        }
    }
}

@Composable
private fun ZeitZeile(e: Ergebnis?, start: Long, setzen: (zeit: Int?, status: Status?) -> Unit, meldezeit: Int?) {
    var text by remember(e?.zeit, e?.status) { mutableStateOf(if (e?.gueltig == true) Zeit.text(e.zeit) else "") }
    val zeit = Zeit.lesen(text)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { neu ->
                text = neu.filter { it.isDigit() || it == ',' || it == '.' || it == ':' }
                val z = Zeit.lesen(text)
                if (text.isBlank()) setzen(null, null) else if (z != null) setzen(z, null)
            },
            label = { Text(if (meldezeit != null) "Zeit (Meldung ${Zeit.text(meldezeit)})" else "Zeit, z. B. 4530") },
            isError = text.isNotBlank() && zeit == null,
            supportingText = { Text(if (zeit != null) Zeit.text(zeit) + " s" else if (e != null && !e.gueltig) e.status.text else "") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        if (start > 0) Button(onClick = {
            val z = ((SystemClock.elapsedRealtime() - start) / 10).toInt()
            text = Zeit.text(z)
            setzen(z, null)
        }) { Text("Stopp") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(Status.NA, Status.DSQ, Status.AUFG).forEach { s ->
            FilterChip(selected = e?.status == s, onClick = {
                if (e?.status == s) setzen(null, null) else { text = ""; setzen(null, s) }
            }, label = { Text(s.kurz) })
        }
    }
}

/** Menü zum Verschieben auf eine andere Bahn oder in einen anderen Lauf derselben Strecke (tauscht, wenn belegt). */
@Composable
private fun Verschieben(v: Veranstaltung, nr: Int, bahn: Int) {
    var offen by remember { mutableStateOf(false) }
    val lauf = v.laeufe.first { it.nr == nr }
    Box {
        TextButton(onClick = { offen = true }) { Text("⇄") }
        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            v.laeufe.filter { it.strecke == lauf.strecke }.forEach { l ->
                for (b in 1..v.regeln.bahnenImBecken) {
                    if (l.nr == nr && b == bahn) continue
                    val wer = l.auf(b)?.let { v.person(it)?.name } ?: "frei"
                    DropdownMenuItem(text = { Text("Lauf ${l.nr}, Bahn $b ($wer)") }, onClick = {
                        offen = false
                        VmStore.aendern { it.copy(laeufe = Laufeinteilung.tauschen(it.laeufe, nr, bahn, l.nr, b)) }
                    })
                }
            }
        }
    }
}
