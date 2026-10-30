package de.dachapp.schwimmleitung.ui

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.dachapp.schwimmleitung.logic.Ablage
import de.dachapp.schwimmleitung.logic.Dokument
import de.dachapp.schwimmleitung.logic.Format
import de.dachapp.schwimmleitung.logic.Listen
import de.dachapp.schwimmleitung.logic.Nachweis
import de.dachapp.schwimmleitung.logic.NachweisArt
import de.dachapp.schwimmleitung.logic.Person
import de.dachapp.schwimmleitung.logic.Pin
import java.time.LocalDate

/** Menü für alles, was nicht täglich gebraucht wird. */
@Composable
fun MehrTab(state: UiState, vm: LeitungViewModel) {
    val d = state.daten
    val heute = LocalDate.now()
    val defekt = d.sichtbaresMaterial.count { it.zustand == de.dachapp.schwimmleitung.logic.Zustand.DEFEKT }
    val offeneBelege = d.sichtbareBelege.count { !it.uebergeben }
    val scheine = d.allePersonen.filter { it.aktiv }.sumOf { p ->
        d.nachweiseVon(p.id).count { n -> n.gueltigBis?.isAfter(heute.plusDays(d.e.warnTage.toLong())) == false }
    }
    FormPage(title = "Mehr", onBack = null) {
        MenuZeile("Material", if (defekt > 0) "$defekt defekt" else "Inventar und Prüftermine") { vm.oeffnen(Ansicht.MaterialListe) }
        MenuZeile("Meldungen aus der Halle", "${d.sichtbareMeldungen.count { !it.erledigt }} offen") { vm.oeffnen(Ansicht.Meldungen) }
        MenuZeile("Belege", if (offeneBelege > 0) "$offeneBelege noch nicht beim Kassenwart" else "Belege und Barbewegungen") { vm.oeffnen(Ansicht.Belege) }
        MenuZeile("Ablage", "Wichtige Dokumente und Trainingsunterlagen") { vm.oeffnen(Ansicht.Ablage) }
        MenuZeile("Personen und Scheine", if (scheine > 0) "$scheine Nachweise fällig" else "ÜL, Rettungsschwimmer, Erste Hilfe, Führungszeugnis, Kampfrichter") { vm.oeffnen(Ansicht.Personen) }
        MenuZeile("Einstellungen", "Drive-Datei, Daten übernehmen, PIN, Tablet fixieren") { vm.oeffnen(Ansicht.Einstellungen) }
        if (state.e.pinGesetzt) {
            OutlinedButton(onClick = vm::sperren, modifier = Modifier.fillMaxWidth()) { Text("🔒 Leitungsbereich sperren") }
        }
    }
}

@Composable
private fun MenuZeile(titel: String, text: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(titel, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}

// ---------- Belege ----------

@Composable
fun BelegeScreen(state: UiState, vm: LeitungViewModel) {
    val context = LocalContext.current
    val d = state.daten
    var nurOffen by rememberSaveable { mutableStateOf(true) }
    var uebergebenFragen by remember { mutableStateOf(false) }
    val liste = d.sichtbareBelege.filter { !nurOffen || !it.uebergeben }
    val aus = liste.filter { !it.einnahme }.sumOf { it.betragCent }
    val ein = liste.filter { it.einnahme }.sumOf { it.betragCent }
    Page(
        title = "Belege",
        onBack = { vm.zurueck() },
        actions = { TextButton(onClick = { Dateien.csvTeilen(context, "Belege", Listen.belege(liste)) }, enabled = liste.isNotEmpty()) { Text("CSV") } },
        bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton("PDF für Kassenwart", { Dateien.belegeTeilen(context, d.e.verein, liste) }, enabled = liste.isNotEmpty(), modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { vm.oeffnen(Ansicht.BelegBearbeiten(null)) }, modifier = Modifier.weight(1f).padding(top = 4.dp)) { Text("Beleg erfassen") }
            }
            if (nurOffen && liste.isNotEmpty()) {
                TextButton(onClick = { uebergebenFragen = true }, modifier = Modifier.fillMaxWidth()) { Text("Alle ${liste.size} als übergeben markieren") }
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = nurOffen, onClick = { nurOffen = true }, label = { Text("Nicht übergeben") })
            FilterChip(selected = !nurOffen, onClick = { nurOffen = false }, label = { Text("Alle") })
        }
        Text("Ausgaben ${Format.euro(aus)} · Einnahmen ${Format.euro(ein)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
        if (liste.isEmpty()) Leer("Keine Belege.")
        LazyColumn(Modifier.fillMaxWidth()) {
            items(liste, key = { it.id }) { b ->
                Row(Modifier.fillMaxWidth().clickable { vm.oeffnen(Ansicht.BelegBearbeiten(b.id)) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(b.zweck)
                        Text(
                            listOfNotNull(Format.datum(b.datum), b.von.ifBlank { null }, if (b.foto != null) "Foto" else null, b.uebergebenAm?.let { "übergeben ${Format.datum(it)}" })
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        (if (b.einnahme) "+" else "−") + Format.euro(b.betragCent),
                        color = if (b.einnahme) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                HorizontalDivider()
            }
        }
    }
    if (uebergebenFragen) {
        ConfirmDialog(
            "Alle ${liste.size} Belege als an den Kassenwart übergeben markieren? Vorher am besten das PDF schicken.",
            "Markieren",
            { vm.uebergeben(liste.map { it.id }) },
            { uebergebenFragen = false },
        )
    }
}

// ---------- Ablage ----------

@Composable
fun AblageScreen(state: UiState, vm: LeitungViewModel) {
    val context = LocalContext.current
    val d = state.daten
    val heute = LocalDate.now()
    Page(title = "Ablage", onBack = { vm.zurueck() }, bottom = { BigButton("Dokument hinzufügen", { vm.oeffnen(Ansicht.DokumentBearbeiten(null)) }) }) {
        Text(
            "Die Dateien selbst liegen in Google Drive; hier stehen die Links. Antippen öffnet das Dokument.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(Modifier.fillMaxWidth()) {
            for (a in Ablage.entries) {
                val liste = d.sichtbareDokumente.filter { it.ablage == a }
                item(key = a.name) { Text(a.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
                if (liste.isEmpty()) item(key = a.name + "-leer") { Text("Noch nichts abgelegt.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(liste, key = { it.id }) { dok ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable(enabled = dok.link.isNotBlank()) { Dateien.oeffnen(context, dok.link) }) {
                            Text(dok.titel)
                            val bis = dok.gueltigBis
                            val zusatz = listOfNotNull(
                                dok.notiz.ifBlank { null },
                                bis?.let { "gültig bis ${Format.datum(it)}" + if (it.isBefore(heute)) " – abgelaufen" else "" },
                                if (dok.link.isBlank()) "kein Link" else null,
                            )
                            if (zusatz.isNotEmpty()) {
                                Text(
                                    zusatz.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (bis?.isBefore(heute) == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(onClick = { vm.oeffnen(Ansicht.DokumentBearbeiten(dok.id)) }) { Text("Ändern") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun DokumentScreen(id: String?, state: UiState, vm: LeitungViewModel) {
    val context = LocalContext.current
    val alt = id?.let { state.daten.dokument(it) }
    var titel by rememberSaveable { mutableStateOf(alt?.titel.orEmpty()) }
    var ablage by rememberSaveable { mutableStateOf(alt?.ablage ?: Ablage.WICHTIG) }
    var link by rememberSaveable { mutableStateOf(alt?.link.orEmpty()) }
    var notiz by rememberSaveable { mutableStateOf(alt?.notiz.orEmpty()) }
    var bis by rememberSaveable { mutableStateOf(Format.datum(alt?.gueltigBis)) }
    var loeschen by remember { mutableStateOf(false) }
    val tag = datumOderNull(bis)
    val ok = titel.isNotBlank() && (bis.isBlank() || tag != null)
    FormPage(
        title = if (alt == null) "Dokument hinzufügen" else "Dokument",
        onBack = { vm.zurueck() },
        actions = { if (alt != null) TextButton(onClick = { loeschen = true }) { Text("Löschen") } },
        bottom = {
            BigButton("Speichern", { vm.speichern(Dokument(alt?.id ?: vm.neueId(titel), titel, ablage, link, notiz.trim(), tag)); vm.zurueck() }, enabled = ok)
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Ablage.entries.forEach { a -> FilterChip(selected = ablage == a, onClick = { ablage = a }, label = { Text(a.text) }) }
        }
        Eingabe("Titel", titel, { titel = it }, pflicht = true, hilfe = "Z. B. Hallenordnung, Notfallplan, Trainingsplan Kader")
        Eingabe("Link", link, { link = it }, typ = KeyboardType.Uri, hilfe = "In Google Drive: Datei → Teilen → Link kopieren, hier einfügen")
        Eingabe("Notiz", notiz, { notiz = it })
        DatumEingabe("Gültig bis (optional)", bis, { bis = it }, context, fehler = if (bis.isNotBlank() && tag == null) "Datum wie 31.12.2027" else null)
        if (link.isNotBlank()) OutlinedButton(onClick = { Dateien.oeffnen(context, link) }) { Text("Link testen") }
    }
    if (loeschen && alt != null) ConfirmDialog("„${alt.titel}“ aus der Ablage nehmen? Die Datei in Drive bleibt.", "Entfernen", { vm.dokumentLoeschen(alt.id) }, { loeschen = false })
}

// ---------- Personen und Scheine ----------

@Composable
fun PersonenScreen(state: UiState, vm: LeitungViewModel) {
    val context = LocalContext.current
    val d = state.daten
    val heute = LocalDate.now()
    val grenze = heute.plusDays(d.e.warnTage.toLong())
    Page(
        title = "Personen und Scheine",
        onBack = { vm.zurueck() },
        actions = { TextButton(onClick = { Dateien.csvTeilen(context, "Nachweise", Listen.nachweise(d)) }) { Text("CSV") } },
        bottom = { BigButton("Person anlegen", { vm.oeffnen(Ansicht.PersonBearbeiten(null)) }) },
    ) {
        if (d.allePersonen.isEmpty()) {
            Leer("Noch niemand eingetragen. Die Trainer kommen automatisch dazu, wenn unter Einstellungen die Schwimmgruppen übernommen werden.")
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(d.allePersonen, key = { it.id }) { p ->
                val n = d.nachweiseVon(p.id)
                val abgelaufen = n.count { it.gueltigBis?.isBefore(heute) == true }
                val bald = n.count { it.gueltigBis?.let { b -> !b.isBefore(heute) && !b.isAfter(grenze) } == true }
                Row(Modifier.fillMaxWidth().clickable { vm.oeffnen(Ansicht.PersonBearbeiten(p.id)) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name + if (!p.aktiv) " (nicht aktiv)" else "")
                        Text(
                            listOfNotNull(p.rolle.ifBlank { null }, n.joinToString(" ") { it.art.kurz }.ifBlank { null }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (abgelaufen > 0) Text("⚠ $abgelaufen abgelaufen", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    else if (bald > 0) Text("⏳ $bald läuft ab", style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun PersonScreen(id: String?, state: UiState, vm: LeitungViewModel) {
    val d = state.daten
    val alt = id?.let { d.person(it) }
    var name by rememberSaveable { mutableStateOf(alt?.name.orEmpty()) }
    var rolle by rememberSaveable { mutableStateOf(alt?.rolle.orEmpty()) }
    var telefon by rememberSaveable { mutableStateOf(alt?.telefon.orEmpty()) }
    var aktiv by rememberSaveable { mutableStateOf(alt?.aktiv ?: true) }
    var loeschen by remember { mutableStateOf(false) }
    val heute = LocalDate.now()
    fun person() = (alt ?: Person(vm.neueId(name), name)).copy(name = name, rolle = rolle.trim(), telefon = telefon.trim(), aktiv = aktiv)

    FormPage(
        title = if (alt == null) "Person anlegen" else alt.name,
        onBack = { vm.zurueck() },
        actions = { if (alt != null) TextButton(onClick = { loeschen = true }) { Text("Löschen") } },
        bottom = { BigButton("Speichern", { vm.speichern(person()); vm.zurueck() }, enabled = name.isNotBlank()) },
    ) {
        Eingabe("Name", name, { name = it }, pflicht = true)
        Eingabe("Rolle", rolle, { rolle = it }, hilfe = "Z. B. Abteilungsleitung, Trainer, Jungtrainer, Kassenwart")
        Eingabe("Telefon", telefon, { telefon = it }, typ = KeyboardType.Phone)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Aktiv", Modifier.weight(1f))
            Switch(checked = aktiv, onCheckedChange = { aktiv = it })
        }
        Abschnitt("Scheine und Nachweise") {
            val liste = alt?.let { d.nachweiseVon(it.id) }.orEmpty()
            if (liste.isEmpty()) Text("Noch keine.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            liste.forEach { n ->
                val bis = n.gueltigBis
                Column(Modifier.fillMaxWidth().clickable { vm.oeffnen(Ansicht.NachweisBearbeiten(n.id, n.personId)) }.padding(vertical = 6.dp)) {
                    Text(n.art.text + n.bezeichnung.ifBlank { null }?.let { " – $it" }.orEmpty())
                    Text(
                        bis?.let { "gültig bis ${Format.datum(it)}" + if (it.isBefore(heute)) " – abgelaufen" else "" } ?: "unbefristet",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (bis?.isBefore(heute) == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    // Neue Person erst speichern, damit der Nachweis zu ihr gehört.
                    val p = person()
                    if (alt == null || p != alt) vm.speichern(p)
                    if (alt == null) vm.ersetzen(Ansicht.PersonBearbeiten(p.id))
                    vm.oeffnen(Ansicht.NachweisBearbeiten(null, p.id))
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Nachweis hinzufügen") }
        }
        if (alt?.trainerId != null) Text("Aus der Trainerliste der Schwimmgruppen.", style = MaterialTheme.typography.bodySmall)
    }
    if (loeschen && alt != null) ConfirmDialog("${alt.name} mit allen Nachweisen löschen? Besser „Aktiv“ ausschalten, dann bleibt der Verlauf.", "Löschen", { vm.personLoeschen(alt.id) }, { loeschen = false })
}

@Composable
fun NachweisScreen(id: String?, personId: String, state: UiState, vm: LeitungViewModel) {
    val context = LocalContext.current
    val alt = id?.let { state.daten.nachweis(it) }
    var art by rememberSaveable { mutableStateOf(alt?.art ?: NachweisArt.UEBUNGSLEITER) }
    var bezeichnung by rememberSaveable { mutableStateOf(alt?.bezeichnung.orEmpty()) }
    var ausgestellt by rememberSaveable { mutableStateOf(Format.datum(alt?.ausgestellt)) }
    var bis by rememberSaveable { mutableStateOf(Format.datum(alt?.gueltigBis)) }
    var notiz by rememberSaveable { mutableStateOf(alt?.notiz.orEmpty()) }
    var loeschen by remember { mutableStateOf(false) }
    val von = datumOderNull(ausgestellt)
    val ende = datumOderNull(bis)
    val ok = (ausgestellt.isBlank() || von != null) && (bis.isBlank() || ende != null)
    FormPage(
        title = state.daten.person(personId)?.name ?: "Nachweis",
        onBack = { vm.zurueck() },
        actions = { if (alt != null) TextButton(onClick = { loeschen = true }) { Text("Löschen") } },
        bottom = {
            BigButton("Speichern", {
                vm.speichern(Nachweis(alt?.id ?: vm.neueId(art.kurz), personId, art, bezeichnung.trim(), von, ende, notiz.trim()))
                vm.zurueck()
            }, enabled = ok)
        },
    ) {
        NachweisArt.entries.chunked(3).forEach { reihe ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reihe.forEach { a -> FilterChip(selected = art == a, onClick = { art = a }, label = { Text(a.text) }) }
            }
        }
        Eingabe(
            "Bezeichnung / Stufe", bezeichnung, { bezeichnung = it },
            hilfe = when (art) {
                NachweisArt.UEBUNGSLEITER -> "Z. B. Trainer C Schwimmen, Übungsleiter C Breitensport"
                NachweisArt.RETTUNGSSCHWIMMER -> "Z. B. Bronze, Silber, Gold"
                NachweisArt.ERSTE_HILFE -> "Z. B. Erste-Hilfe-Kurs 9 UE"
                NachweisArt.FUEHRUNGSZEUGNIS -> "Erweitertes Führungszeugnis, eingesehen von …"
                NachweisArt.KAMPFRICHTER -> "Z. B. Kampfrichter Schwimmen, Schiedsrichter"
            },
        )
        DatumEingabe("Ausgestellt / erworben", ausgestellt, { ausgestellt = it }, context, fehler = if (ausgestellt.isNotBlank() && von == null) "Datum wie 15.03.2025" else null)
        DatumEingabe("Gültig bis (leer = unbefristet)", bis, { bis = it }, context, fehler = if (bis.isNotBlank() && ende == null) "Datum wie 15.03.2027" else null)
        val jahre = art.gueltigJahre
        if (von != null && jahre != null) {
            OutlinedButton(onClick = { bis = Format.datum(von.plusYears(jahre.toLong())) }) {
                Text("Üblich: $jahre Jahre → ${Format.datum(von.plusYears(jahre.toLong()))}")
            }
        }
        Eingabe("Notiz", notiz, { notiz = it })
    }
    if (loeschen && alt != null) ConfirmDialog("Diesen Nachweis löschen?", "Löschen", { vm.nachweisLoeschen(alt.id) }, { loeschen = false })
}

// ---------- Einstellungen ----------

private fun Context.activity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun EinstellungenScreen(state: UiState, vm: LeitungViewModel) {
    val context = LocalContext.current
    val e = state.e
    val g = state.geraet
    var pinNeu by remember { mutableStateOf(false) }
    var trennenFragen by remember { mutableStateOf(false) }
    var fixiert by remember { mutableStateOf(fixiert(context)) }
    val waehlen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::dateiGewaehlt) }
    val anlegen = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::dateiGewaehlt) }
    val importieren = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importieren) }

    FormPage(title = "Einstellungen", onBack = { vm.zurueck() }) {
        Abschnitt("Gemeinsame Datei in Google Drive") {
            Text(
                when (val s = state.sync) {
                    SyncZustand.KeineDatei -> "Keine Datei gewählt: Die Daten liegen nur auf diesem Gerät."
                    SyncZustand.Laeuft -> "Abgleich läuft …"
                    is SyncZustand.Ok -> "Zuletzt abgeglichen: ${zeitText(s.zeit)}"
                    is SyncZustand.Fehler -> "⚠ ${s.text}"
                },
            )
            Text(
                "Einmal eine Datei in deinem Drive-Ordner anlegen und den Ordner per Link (Bearbeiter) mit den Trainern teilen. " +
                    "Auf den anderen Geräten den Link einmal in der Drive-App öffnen, „Verknüpfung zu Meine Ablage hinzufügen“, " +
                    "dann hier „Vorhandene wählen“. Änderungen kommen mit etwas Verzögerung an, je nachdem wie schnell Drive abgleicht.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { anlegen.launch("schwimmleitung.json") }, modifier = Modifier.weight(1f)) { Text("Neu anlegen") }
                OutlinedButton(onClick = { waehlen.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, modifier = Modifier.weight(1f)) { Text("Vorhandene wählen") }
            }
            if (g.dateiUri != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::jetztAbgleichen, modifier = Modifier.weight(1f)) { Text("Jetzt abgleichen") }
                    OutlinedButton(onClick = { trennenFragen = true }, modifier = Modifier.weight(1f)) { Text("Trennen") }
                }
            }
            Text("Größe des gemeinsamen Stands: etwa ${vm.groesseKb()} KB (Fotos machen den Großteil aus).", style = MaterialTheme.typography.bodySmall)
        }

        Abschnitt("Daten übernehmen") {
            Text(
                "Sicherung aus der Schwimmkurs- oder Schwimmgruppen-App wählen (dort unter Einstellungen → Sichern). Die Leitung " +
                    "liest sie nur; übernommen werden Zahlen für die Auswertung, keine Namen von Kindern, keine Adressen.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Schwimmkurs: " + (state.daten.kurs?.let { "Stand ${zeitText(it.importiert)}, ${it.kurse.size} Kurse" } ?: "noch nicht übernommen"))
            Text("Schwimmgruppen: " + (state.daten.gruppen?.let { "Stand ${zeitText(it.importiert)}, ${it.einheiten.size} Trainings" } ?: "noch nicht übernommen"))
            OutlinedButton(onClick = { importieren.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, modifier = Modifier.fillMaxWidth()) {
                Text("Sicherung wählen")
            }
        }

        Abschnitt("Leitungs-PIN") {
            Text(
                if (e.pinGesetzt) "Gesetzt. Ohne PIN sehen Trainer nur den Hallenbericht. Die PIN gilt auf allen Geräten mit dieser Datei."
                else "Noch keine PIN: Der Leitungsbereich ist für alle offen.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { pinNeu = true }) { Text(if (e.pinGesetzt) "PIN ändern" else "PIN festlegen") }
        }

        Abschnitt("Tablet in der Halle fixieren") {
            Text(
                "Hält die App im Vordergrund (Android-App-Fixierung): Startbildschirm und andere Apps sind gesperrt. " +
                    "Lösen geht hier mit der Leitungs-PIN oder über die Geräte-PIN. Tipp: In den Android-Einstellungen unter Sicherheit " +
                    "„App-Fixierung“ einschalten und „Vor dem Loslösen nach PIN fragen“ aktivieren.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = {
                val a = context.activity()
                if (a != null) {
                    runCatching { if (fixiert) a.stopLockTask() else a.startLockTask() }
                        .onFailure { vm.showMessage("Fixieren ging nicht: ${it.message}") }
                }
                fixiert = !fixiert
            }) { Text(if (fixiert) "Fixierung lösen" else "Dieses Gerät fixieren") }
        }

        Abschnitt("Dieses Gerät") {
            Eingabe("Name für Meldungen", g.name, { vm.geraet(g.copy(name = it)) }, hilfe = "Z. B. „Tablet Halle“; erscheint bei Meldungen, wenn unter „Ich bin“ niemand gewählt ist")
            Auswahl(
                "Abgleich alle",
                listOf(30, 60, 120, 300),
                g.intervallSekunden,
                { if (it < 60) "$it Sekunden" else "${it / 60} Minute" + if (it >= 120) "n" else "" },
                { vm.geraet(g.copy(intervallSekunden = it)) },
                Modifier.fillMaxWidth(),
            )
        }

        Abschnitt("Verein und Auswertung") {
            Eingabe("Verein", e.verein, { vm.einstellungen(e.copy(verein = it)) })
            Auswahl("Saison beginnt im", (1..12).toList(), e.saisonBeginn, { Format.monatLang(java.time.Month.of(it)) }, { vm.einstellungen(e.copy(saisonBeginn = it)) }, Modifier.fillMaxWidth())
            Auswahl("Scheine warnen", listOf(30, 60, 90, 180), e.warnTage, { "$it Tage vorher" }, { vm.einstellungen(e.copy(warnTage = it)) }, Modifier.fillMaxWidth())
        }
    }
    if (pinNeu) PinSetzenDialog(onPin = vm::pinSetzen, onDismiss = { pinNeu = false })
    if (trennenFragen) {
        ConfirmDialog(
            "Dieses Gerät von der gemeinsamen Datei trennen? Die Daten bleiben auf dem Gerät, gleichen sich aber nicht mehr ab.",
            "Trennen",
            vm::dateiTrennen,
            { trennenFragen = false },
        )
    }
}

private fun fixiert(context: Context): Boolean =
    context.getSystemService(ActivityManager::class.java)?.lockTaskModeState?.let { it != ActivityManager.LOCK_TASK_MODE_NONE } == true

@Composable
private fun PinSetzenDialog(onPin: (String) -> Unit, onDismiss: () -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val ok = Pin.gueltig(a) && a == b
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Leitungs-PIN festlegen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Neue PIN (mind. ${Pin.MIN_LAENGE} Ziffern)" to a, "Wiederholen" to b).forEachIndexed { i, (label, wert) ->
                    OutlinedTextField(
                        value = wert,
                        onValueChange = { v -> val f = v.filter { it.isDigit() }.take(12); if (i == 0) a = f else b = f },
                        label = { Text(label) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                }
                if (b.isNotEmpty() && a != b) Text("Die beiden Eingaben stimmen nicht überein.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { onPin(a); onDismiss() }, enabled = ok) { Text("Festlegen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
