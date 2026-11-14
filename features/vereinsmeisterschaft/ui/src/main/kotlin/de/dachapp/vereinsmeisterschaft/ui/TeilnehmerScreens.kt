package de.dachapp.vereinsmeisterschaft.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import de.dachapp.module.Schalter
import de.dachapp.vereinsmeisterschaft.logic.Geschlecht
import de.dachapp.vereinsmeisterschaft.logic.Import
import de.dachapp.vereinsmeisterschaft.logic.Laufeinteilung
import de.dachapp.vereinsmeisterschaft.logic.Querverbindung
import de.dachapp.vereinsmeisterschaft.logic.TabellenFehler
import de.dachapp.vereinsmeisterschaft.logic.TabellenLeser
import de.dachapp.vereinsmeisterschaft.logic.Teilnehmer
import de.dachapp.vereinsmeisterschaft.logic.Veranstaltung
import de.dachapp.vereinsmeisterschaft.logic.Zeit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun ErsteVeranstaltung(onExit: () -> Unit) {
    Page("Vereinsmeisterschaft", onExit) {
        Text(
            "Hier wertest du die Vereinsmeisterschaft aus: Teilnehmer aus den Schwimmgruppen oder einer Meldeliste holen, " +
                "Läufe einteilen, Zeiten eintragen oder vom Zeitzettel abfotografieren, und am Ende Urkunden, Punkteurkunden " +
                "und die Ergebnisliste als PDF drucken. Die Regeln (Jahrgänge, Strecken, Pokale) sind nach der Ausschreibung " +
                "2026 vorbelegt und lassen sich ändern.",
            modifier = Modifier.padding(vertical = 16.dp),
        )
        BigButton("Vereinsmeisterschaft ${LocalDate.now().year} anlegen", { VmStore.neu(LocalDate.now().year) })
        OutlinedButton(onClick = { VmStore.neu(LocalDate.now().year + 1) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Für ${LocalDate.now().year + 1} anlegen")
        }
    }
}

/** Starts eines Schwimmers kurz, z. B. „Br Rü Kr · 3 Bahnen“. */
private fun startsText(v: Veranstaltung, t: Teilnehmer): String {
    val s = v.starts(t)
    if (s.isEmpty()) return "keine Strecke"
    return s.joinToString(" ") { it.lage.kurz } + " · " + (v.stufe(t)?.name ?: "")
}

@Composable
fun TeilnehmerListe(v: Veranstaltung, nav: (Ansicht) -> Unit, meldung: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var suche by remember { mutableStateOf("") }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val b = withContext(Dispatchers.IO) { Import.meldeliste(v, TabellenLeser.lesen(Dateien.lesen(context, uri))) }
                VmStore.aendern { b.veranstaltung }
                meldung("Meldeliste übernommen: " + b.text)
            } catch (e: TabellenFehler) {
                meldung(e.message ?: "Datei nicht lesbar.")
            } catch (e: Exception) {
                meldung("Datei nicht lesbar: ${e.message}")
            }
        }
    }
    val ohneG = v.teilnehmer.filter { it.geschlecht == null }
    val gefiltert = v.teilnehmer.filter { suche.isBlank() || Import.norm(it.name).contains(Import.norm(suche)) || it.jahrgang.toString() == suche.trim() }
    val gruppen = gefiltert.groupBy { v.stufe(it) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { nav(Ansicht.Person(null)) }) { Text("+ Schwimmer") }
                OutlinedButton(onClick = { nav(Ansicht.Mitgliederwahl) }) { Text("Aus Schwimmgruppen") }
                OutlinedButton(onClick = { import.launch(arrayOf("*/*")) }) { Text("Meldeliste (CSV/Excel)") }
            }
        }
        if (v.teilnehmer.size > 8) item { Eingabe("Suchen (Name oder Jahrgang)", suche, { suche = it }) }
        if (ohneG.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Geschlecht fehlt (${ohneG.size}) – ohne wird nicht gewertet", fontWeight = FontWeight.Bold)
                    ohneG.take(30).forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${t.name} (${t.jahrgang})", Modifier.weight(1f))
                            Geschlecht.entries.forEach { g ->
                                TextButton(onClick = { VmStore.aendern { it.copy(teilnehmer = it.teilnehmer.map { x -> if (x.id == t.id) x.copy(geschlecht = g) else x }) } }) {
                                    Text(g.kurz.uppercase())
                                }
                            }
                        }
                    }
                }
            }
        }
        if (v.teilnehmer.isEmpty()) item { Leer("Noch keine Teilnehmer. Hol sie aus den Schwimmgruppen, lies eine Meldeliste ein oder trag sie einzeln ein.") }
        val reihenfolge = v.regeln.stufen + listOf(null)
        for (stufe in reihenfolge) {
            val leute = gruppen[stufe].orEmpty().sortedWith(compareByDescending<Teilnehmer> { it.jahrgang }.thenBy { it.nachname.lowercase() })
            if (leute.isEmpty()) continue
            item {
                Text(
                    (stufe?.let { "${it.name} · ${it.jahrgaenge}" } ?: "Ohne Stufe (Jahrgang passt nirgends)") + " · ${leute.size}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            items(leute, key = { it.id }) { t ->
                Column(Modifier.fillMaxWidth().clickable { nav(Ansicht.Person(t.id)) }.padding(vertical = 6.dp)) {
                    Row {
                        Text(t.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text("${t.jahrgang} ${t.geschlecht?.kurz ?: "?"}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        listOf(startsText(v, t), t.gruppe, if (t.ausserKonkurrenz) "a. K." else "").filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
        item { Text(" ", Modifier.padding(bottom = 24.dp)) }
    }
}

@Composable
fun PersonBearbeiten(v: Veranstaltung, id: Long?, zurueck: () -> Unit) {
    val alt = id?.let { v.person(it) }
    var vor by remember { mutableStateOf(alt?.vorname.orEmpty()) }
    var nach by remember { mutableStateOf(alt?.nachname.orEmpty()) }
    var jg by remember { mutableStateOf(alt?.jahrgang?.toString().orEmpty()) }
    var g by remember { mutableStateOf(alt?.geschlecht) }
    var stufeId by remember { mutableStateOf(alt?.stufeId) }
    var ohne by remember { mutableStateOf(alt?.ohne.orEmpty()) }
    var melde by remember { mutableStateOf(alt?.meldezeiten.orEmpty().mapValues { Zeit.text(it.value) }) }
    var gruppe by remember { mutableStateOf(alt?.gruppe.orEmpty()) }
    var ak by remember { mutableStateOf(alt?.ausserKonkurrenz ?: false) }
    var notiz by remember { mutableStateOf(alt?.notiz.orEmpty()) }
    var loeschen by remember { mutableStateOf(false) }

    val jahrgang = jg.trim().toIntOrNull()?.takeIf { it in 1900..2100 }
    val probe = Teilnehmer(0, vor, nach, jahrgang ?: 0, g, stufeId, ohne)
    val stufe = if (jahrgang != null) v.stufe(probe) else null
    val ok = (vor.isNotBlank() || nach.isNotBlank()) && jahrgang != null

    fun speichern() {
        val zeiten = melde.mapNotNull { (l, t) -> Zeit.lesen(t)?.let { l to it } }.toMap()
        VmStore.aendern { ver ->
            val nid = id ?: ver.naechsteId
            val t = (alt ?: Teilnehmer(nid, "", "", 0)).copy(
                vorname = vor.trim(), nachname = nach.trim(), jahrgang = jahrgang!!, geschlecht = g, stufeId = stufeId,
                ohne = ohne, meldezeiten = zeiten, gruppe = gruppe.trim(), ausserKonkurrenz = ak, notiz = notiz.trim(),
            )
            var neu = if (alt == null) ver.copy(teilnehmer = ver.teilnehmer + t, naechsteId = nid + 1)
            else ver.copy(teilnehmer = ver.teilnehmer.map { if (it.id == t.id) t else it })
            // Läufe aktuell halten: nicht mehr geschwommene Strecken austragen, neue nachtragen.
            if (neu.laeufe.isNotEmpty()) {
                val starts = neu.starts(t)
                var l = neu.laeufe
                l.filter { lauf -> lauf.belegung.any { it.teilnehmer == t.id } && lauf.strecke !in starts }
                    .forEach { lauf -> l = Laufeinteilung.entfernen(l, t.id, lauf.strecke.lage) }
                starts.forEach { s -> l = Laufeinteilung.nachtragen(l, t.id, s, neu.regeln.bahnenImBecken) }
                neu = neu.copy(laeufe = l)
            }
            neu
        }
        zurueck()
    }

    if (loeschen && alt != null) {
        ConfirmDialog("${alt.name} mit allen Zeiten löschen?", "Löschen", {
            VmStore.aendern { ver ->
                ver.copy(
                    teilnehmer = ver.teilnehmer.filterNot { it.id == alt.id },
                    ergebnisse = ver.ergebnisse.filterNot { it.teilnehmer == alt.id },
                    laeufe = Laufeinteilung.entfernen(ver.laeufe, alt.id),
                )
            }
            zurueck()
        }, { loeschen = false })
    }

    FormPage(
        if (alt == null) "Schwimmer anlegen" else alt.name, zurueck,
        actions = { if (alt != null) TextButton(onClick = { loeschen = true }) { Text("Löschen") } },
        bottom = { BigButton("Speichern", ::speichern, enabled = ok) },
    ) {
        Eingabe("Vorname", vor, { vor = it }, pflicht = true, pflichtFehlt = vor.isBlank() && nach.isBlank())
        Eingabe("Nachname", nach, { nach = it })
        Eingabe("Jahrgang", jg, { jg = it.filter(Char::isDigit).take(4) }, typ = KeyboardType.Number, pflicht = true, pflichtFehlt = jahrgang == null)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Geschlecht", Modifier.weight(1f))
            Geschlecht.entries.forEach { x -> FilterChip(selected = g == x, onClick = { g = x }, label = { Text(x.text) }) }
        }
        Auswahl(
            "Stufe", listOf<String?>(null) + v.regeln.stufen.map { it.id }, stufeId,
            { sid -> if (sid == null) "nach Jahrgang" + (stufe?.let { s -> " (${s.name})" } ?: "") else v.regeln.stufe(sid)?.name ?: sid },
            { stufeId = it },
        )
        if (stufe != null) Abschnitt("Strecken und Meldezeiten") {
            stufe.strecken.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = s.lage !in ohne, onCheckedChange = { an -> ohne = if (an) ohne - s.lage else ohne + s.lage })
                    Text(s.text, Modifier.weight(1f))
                    Eingabe("Meldezeit", melde[s.lage].orEmpty(), { melde = melde + (s.lage to it) }, Modifier.weight(1f), typ = KeyboardType.Decimal,
                        fehler = melde[s.lage]?.takeIf { it.isNotBlank() && Zeit.lesen(it) == null }?.let { "?" })
                }
            }
            Text("Meldezeit leer = ohne Zeit; sie entscheidet nur über die Läufe.", style = MaterialTheme.typography.bodySmall)
        } else if (jahrgang != null) Text("Dieser Jahrgang passt in keine Stufe. Bitte Stufe wählen oder die Regeln anpassen.", color = MaterialTheme.colorScheme.error)
        Eingabe("Trainingsgruppe", gruppe, { gruppe = it })
        Schalter("Außer Konkurrenz", ak, { ak = it }, "schwimmt mit, bekommt Zeit und Urkunde, aber keinen Platz")
        Eingabe("Notiz", notiz, { notiz = it }, mehrzeilig = true)
    }
}

@Composable
fun MitgliederWahl(v: Veranstaltung, zurueck: () -> Unit, meldung: (String) -> Unit) {
    val context = LocalContext.current
    val kandidaten = remember(v.teilnehmer) {
        runCatching { Dateien.schwimmgruppen(context)?.let { Querverbindung.mitglieder(it, v) } }.getOrNull()
    }
    var gewaehlt by remember { mutableStateOf(emptySet<String>()) }
    var gruppe by remember { mutableStateOf<String?>(null) }
    Page("Aus Schwimmgruppen", zurueck, bottom = {
        BigButton("${gewaehlt.size} übernehmen", {
            val b = Querverbindung.uebernehmen(v, kandidaten.orEmpty().filter { it.id in gewaehlt })
            VmStore.aendern { b.veranstaltung }
            meldung("Übernommen: " + b.text)
            zurueck()
        }, enabled = gewaehlt.isNotEmpty())
    }) {
        if (kandidaten == null) {
            Leer("Keine Schwimmgruppen-Daten auf diesem Gerät gefunden. Die Mitglieder lassen sich auch als Meldeliste (CSV/Excel) einlesen.")
            return@Page
        }
        val offen = kandidaten.filter { !it.schonDa }
        val gruppen = offen.flatMap { it.gruppe.split(", ") }.filter { it.isNotBlank() }.distinct().sorted()
        val sichtbar = offen.filter { gruppe == null || it.gruppe.split(", ").contains(gruppe) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = gruppe == null, onClick = { gruppe = null }, label = { Text("Alle") })
            gruppen.forEach { gr -> FilterChip(selected = gruppe == gr, onClick = { gruppe = gr }, label = { Text(gr) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${sichtbar.size} Mitglieder" + if (kandidaten.size > offen.size) " (${kandidaten.size - offen.size} schon dabei)" else "", Modifier.weight(1f))
            TextButton(onClick = { gewaehlt = gewaehlt + sichtbar.map { it.id } }) { Text("Alle wählen") }
            TextButton(onClick = { gewaehlt = gewaehlt - sichtbar.map { it.id }.toSet() }) { Text("Keine") }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(sichtbar, key = { it.id }) { k ->
                Row(Modifier.fillMaxWidth().clickable { gewaehlt = if (k.id in gewaehlt) gewaehlt - k.id else gewaehlt + k.id }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = k.id in gewaehlt, onCheckedChange = null)
                    Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                        Text(k.name)
                        Text(
                            listOf(k.jahrgang?.toString() ?: "Jahrgang fehlt", k.geschlecht?.text ?: "Geschlecht fehlt", k.gruppe).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
