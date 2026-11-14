package de.dachapp.vereinsmeisterschaft.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.dachapp.module.Abschnitt
import de.dachapp.module.Auswahl
import de.dachapp.module.BigButton
import de.dachapp.module.ConfirmDialog
import de.dachapp.module.DatumFeld
import de.dachapp.module.Eingabe
import de.dachapp.module.FormPage
import de.dachapp.module.Page
import de.dachapp.module.Schalter
import de.dachapp.module.Zaehler
import de.dachapp.vereinsmeisterschaft.logic.Basiszeit
import de.dachapp.vereinsmeisterschaft.logic.Daten
import de.dachapp.vereinsmeisterschaft.logic.Folge
import de.dachapp.vereinsmeisterschaft.logic.Geschlecht
import de.dachapp.vereinsmeisterschaft.logic.Kombi
import de.dachapp.vereinsmeisterschaft.logic.Lage
import de.dachapp.vereinsmeisterschaft.logic.LaufOrdnung
import de.dachapp.vereinsmeisterschaft.logic.Pokal
import de.dachapp.vereinsmeisterschaft.logic.Regeln
import de.dachapp.vereinsmeisterschaft.logic.Staffel
import de.dachapp.vereinsmeisterschaft.logic.Strecke
import de.dachapp.vereinsmeisterschaft.logic.Stufe
import de.dachapp.vereinsmeisterschaft.logic.Veranstaltung
import de.dachapp.vereinsmeisterschaft.logic.Wertungsart
import de.dachapp.vereinsmeisterschaft.logic.Zeit
import java.time.LocalDate

@Composable
fun MehrSeite(v: Veranstaltung, daten: Daten, nav: (Ansicht) -> Unit, meldung: (String) -> Unit) {
    val context = LocalContext.current
    val logoWahl = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val b64 = Dateien.logoLesen(context, uri)
            if (b64 == null) meldung("Bild nicht lesbar.") else VmStore.setzen { it.copy(logo = b64) }
        }
    }
    val logo: Bitmap? = remember(daten.logo) { Dateien.logo(daten.logo) }
    fun aendern(f: (Veranstaltung) -> Veranstaltung) = VmStore.aendern(f)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Abschnitt("Veranstaltung") {
            Eingabe("Titel", v.titel, { t -> aendern { it.copy(titel = t) } })
            DatumFeld("Datum", runCatching { LocalDate.parse(v.datum).toEpochDay() }.getOrNull(), { d ->
                aendern { it.copy(datum = d?.let { x -> LocalDate.ofEpochDay(x).toString() }.orEmpty()) }
            }, leerText = "noch offen")
            Eingabe("Ort", v.ort, { t -> aendern { it.copy(ort = t) } })
            Eingabe("Verein (Kopf der Urkunde)", v.verein, { t -> aendern { it.copy(verein = t) } })
            Eingabe("Unterschrift (unter der Linie)", v.unterschrift, { t -> aendern { it.copy(unterschrift = t) } })
            Eingabe("Vereinskennzahl (nur DSV-Datei, optional)", v.vereinsnummer, { t -> aendern { it.copy(vereinsnummer = t.filter(Char::isDigit)) } }, typ = KeyboardType.Number)
        }
        Abschnitt("Logo für Urkunden") {
            if (logo != null) Image(logo.asImageBitmap(), "Logo", Modifier.height(80.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { logoWahl.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(if (logo == null) "Logo wählen" else "Anderes Logo")
                }
                if (logo != null) TextButton(onClick = { VmStore.setzen { it.copy(logo = null) } }) { Text("Entfernen") }
            }
        }
        BigButton("Regeln: Stufen, Pokale, Wertung, Läufe", { nav(Ansicht.Regeln) })
        OutlinedButton(onClick = { nav(Ansicht.Staffeln) }, modifier = Modifier.fillMaxWidth()) { Text("Familienstaffel (${v.staffeln.size} Teams)") }
        OutlinedButton(onClick = { nav(Ansicht.Veranstaltungen) }, modifier = Modifier.fillMaxWidth()) {
            Text("Veranstaltungen (${daten.veranstaltungen.size}) · neues Jahr")
        }
        Text(
            "Gesichert wird alles mit der Sicherung der Dach-App (Datei vereinsmeisterschaft.json).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Zahlenfeld für Jahrgänge u. Ä.; leer = null. */
@Composable
private fun JahrFeld(label: String, wert: Int?, modifier: Modifier = Modifier, onChange: (Int?) -> Unit) {
    var t by remember(wert) { mutableStateOf(wert?.toString().orEmpty()) }
    Eingabe(label, t, { neu ->
        t = neu.filter(Char::isDigit).take(4)
        val j = t.toIntOrNull()
        if (t.isEmpty()) onChange(null) else if (j != null && j in 1900..2100) onChange(j)
    }, modifier, typ = KeyboardType.Number)
}

@Composable
fun RegelnSeite(v: Veranstaltung, zurueck: () -> Unit) {
    val r = v.regeln
    fun regeln(f: (Regeln) -> Regeln) = VmStore.aendern { it.copy(regeln = f(it.regeln)) }
    fun stufe(id: String, f: (Stufe) -> Stufe) = regeln { rr -> rr.copy(stufen = rr.stufen.map { if (it.id == id) f(it) else it }) }

    FormPage("Regeln ${v.jahr}", zurueck) {
        Text(
            "Die Stufen richten sich nach dem Jahrgang (ältester … jüngster). Leer = offen. Bei einem neuen Jahr werden die " +
                "Grenzen automatisch verschoben.",
            style = MaterialTheme.typography.bodySmall,
        )
        r.stufen.forEach { s ->
            Abschnitt(s.name) {
                Eingabe("Name", s.name, { t -> stufe(s.id) { it.copy(name = t) } })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JahrFeld("ältester Jg.", s.abJahrgang, Modifier.weight(1f)) { j -> stufe(s.id) { it.copy(abJahrgang = j) } }
                    JahrFeld("jüngster Jg.", s.bisJahrgang, Modifier.weight(1f)) { j -> stufe(s.id) { it.copy(bisJahrgang = j) } }
                }
                Text(s.jahrgaenge, style = MaterialTheme.typography.bodySmall)
                Lage.entries.forEach { l ->
                    val st = s.strecke(l)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = st != null, onCheckedChange = { an ->
                            stufe(s.id) { x ->
                                val ohne = x.strecken.filterNot { it.lage == l }
                                x.copy(strecken = if (an) (ohne + Strecke(l, x.strecken.firstOrNull()?.bahnen ?: 1)).sortedBy { it.lage.ordinal } else ohne)
                            }
                        })
                        Text(l.text, Modifier.weight(1f))
                        if (st != null) (1..3).forEach { b ->
                            FilterChip(selected = st.bahnen == b, onClick = {
                                stufe(s.id) { x -> x.copy(strecken = x.strecken.map { if (it.lage == l) it.copy(bahnen = b) else it }) }
                            }, label = { Text("$b") }, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
                if (r.stufen.size > 1) TextButton(onClick = {
                    regeln { rr -> rr.copy(stufen = rr.stufen.filterNot { it.id == s.id }, pokale = rr.pokale.map { p -> p.copy(stufen = p.stufen - s.id) }) }
                }) { Text("Stufe entfernen") }
            }
        }
        OutlinedButton(onClick = {
            regeln { rr -> rr.copy(stufen = rr.stufen + Stufe("s" + System.currentTimeMillis(), "Neue Stufe", strecken = listOf(Strecke(Lage.BRUST, 1)))) }
        }) { Text("+ Stufe") }

        Abschnitt("Pokale (Meister, immer w/m getrennt)") {
            r.pokale.forEachIndexed { i, p ->
                Eingabe("Name", p.name, { t -> regeln { rr -> rr.copy(pokale = rr.pokale.mapIndexed { j, x -> if (j == i) x.copy(name = t) else x }) } })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.stufen.forEach { s ->
                        FilterChip(selected = s.id in p.stufen, onClick = {
                            regeln { rr -> rr.copy(pokale = rr.pokale.mapIndexed { j, x -> if (j == i) x.copy(stufen = if (s.id in x.stufen) x.stufen - s.id else x.stufen + s.id) else x }) }
                        }, label = { Text(s.name) })
                    }
                    TextButton(onClick = { regeln { rr -> rr.copy(pokale = rr.pokale.filterIndexed { j, _ -> j != i }) } }) { Text("entfernen") }
                }
                HorizontalDivider()
            }
            TextButton(onClick = { regeln { rr -> rr.copy(pokale = rr.pokale + Pokal("Neuer Pokal", emptyList())) } }) { Text("+ Pokal") }
        }

        Abschnitt("Jahrgänge zusammen werten") {
            Text("Gilt für Jahrgangswertung, Medaillen und Urkunden. Beispiel: 1900–${r.stufen.firstOrNull { it.id == "master" }?.bisJahrgang ?: (v.jahr - 18)} = alle Master zusammen.", style = MaterialTheme.typography.bodySmall)
            r.kombis.forEachIndexed { i, k ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    JahrFeld("von", k.von, Modifier.weight(1f)) { j -> if (j != null) regeln { rr -> rr.copy(kombis = rr.kombis.mapIndexed { x, kk -> if (x == i) kk.copy(von = j) else kk }) } }
                    JahrFeld("bis", k.bis, Modifier.weight(1f)) { j -> if (j != null) regeln { rr -> rr.copy(kombis = rr.kombis.mapIndexed { x, kk -> if (x == i) kk.copy(bis = j) else kk }) } }
                    Auswahl("für", listOf<Geschlecht?>(null) + Geschlecht.entries, k.geschlecht, { it?.kurz ?: "w+m" }, { g ->
                        regeln { rr -> rr.copy(kombis = rr.kombis.mapIndexed { x, kk -> if (x == i) kk.copy(geschlecht = g) else kk }) }
                    }, Modifier.weight(1f))
                    TextButton(onClick = { regeln { rr -> rr.copy(kombis = rr.kombis.filterIndexed { x, _ -> x != i }) } }) { Text("✕") }
                }
            }
            TextButton(onClick = { regeln { rr -> rr.copy(kombis = rr.kombis + Kombi(v.jahr - 10, v.jahr - 9)) } }) { Text("+ Jahrgänge zusammenlegen") }
        }

        Abschnitt("Wertung") {
            Auswahl("Gesamt/Pokale", Wertungsart.entries, r.wertung, { it.text }, { w -> regeln { it.copy(wertung = w) } })
            Zaehler("Hochrechnen auf Bahnen", "${r.normBahnen}", { regeln { it.copy(normBahnen = (it.normBahnen - 1).coerceAtLeast(1)) } }, { regeln { it.copy(normBahnen = it.normBahnen + 1) } })
            Zaehler("Medaillen für Platz 1 bis", "${r.medaillenPlaetze}", { regeln { it.copy(medaillenPlaetze = (it.medaillenPlaetze - 1).coerceAtLeast(0)) } }, { regeln { it.copy(medaillenPlaetze = it.medaillenPlaetze + 1) } })
            Zaehler("Punkteurkunde für Platz 1 bis", "${r.punkteurkundeBis}", { regeln { it.copy(punkteurkundeBis = (it.punkteurkundeBis - 1).coerceAtLeast(0)) } }, { regeln { it.copy(punkteurkundeBis = it.punkteurkundeBis + 1) } })
            if (r.wertung == Wertungsart.PUNKTE) {
                Text("Basiszeiten für 50 m (Punkte = 1000 × (Basis ÷ Zeit)³)", fontWeight = FontWeight.Bold)
                r.basiszeiten.forEach { b ->
                    var t by remember(b) { mutableStateOf(Zeit.text(b.hundertstel)) }
                    Eingabe("${b.lage.text} ${b.geschlecht.kurz}", t, { neu ->
                        t = neu
                        Zeit.lesen(neu)?.let { z -> regeln { rr -> rr.copy(basiszeiten = rr.basiszeiten.map { x -> if (x.lage == b.lage && x.geschlecht == b.geschlecht) Basiszeit(x.lage, x.geschlecht, z) else x }) } }
                    }, typ = KeyboardType.Decimal)
                }
            }
        }

        Abschnitt("Läufe") {
            Zaehler("Bahnen im Becken", "${r.bahnenImBecken}", { regeln { it.copy(bahnenImBecken = (it.bahnenImBecken - 1).coerceAtLeast(1)) } }, { regeln { it.copy(bahnenImBecken = (it.bahnenImBecken + 1).coerceAtMost(10)) } })
            Auswahl("Reihenfolge", LaufOrdnung.entries, r.laufOrdnung, { it.text }, { o -> regeln { it.copy(laufOrdnung = o) } })
            Auswahl("Wettkampffolge", Folge.entries, r.folge, { it.text }, { f -> regeln { it.copy(folge = f) } })
            Schalter("Läufe nach Geschlecht trennen", r.laeufeNachGeschlecht, { an -> regeln { it.copy(laeufeNachGeschlecht = an) } })
            Text("Änderungen wirken beim nächsten „Läufe einteilen“.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun StaffelSeite(v: Veranstaltung, zurueck: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var mitglieder by remember { mutableStateOf("") }
    FormPage("Familienstaffel", zurueck) {
        Text("Jedes Team bekommt eine Urkunde (bei „Alle Urkunden PDF“ am Ende).", style = MaterialTheme.typography.bodySmall)
        v.staffeln.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.name, fontWeight = FontWeight.Bold)
                    if (s.mitglieder.isNotBlank()) Text(s.mitglieder, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { VmStore.aendern { it.copy(staffeln = it.staffeln.filterNot { x -> x.id == s.id }) } }) { Text("✕") }
            }
            HorizontalDivider()
        }
        Abschnitt("Neues Team") {
            Eingabe("Teamname, z. B. Familie Huber", name, { name = it }, pflicht = true)
            Eingabe("Wer schwimmt mit? (z. B. Oma Rosi, Lena, Papa Tom)", mitglieder, { mitglieder = it })
            BigButton("Hinzufügen", {
                VmStore.aendern { it.copy(staffeln = it.staffeln + Staffel(it.naechsteId, name.trim(), mitglieder.trim()), naechsteId = it.naechsteId + 1) }
                name = ""; mitglieder = ""
            }, enabled = name.isNotBlank())
        }
    }
}

@Composable
fun VeranstaltungenSeite(daten: Daten, zurueck: () -> Unit) {
    var loeschen by remember { mutableStateOf<Veranstaltung?>(null) }
    var mitTeilnehmern by remember { mutableStateOf(true) }
    val akt = daten.aktuelle
    loeschen?.let { x ->
        ConfirmDialog("„${x.titel}“ mit allen Teilnehmern und Zeiten löschen?", "Löschen", { VmStore.loeschen(x.id) }) { loeschen = null }
    }
    Page("Veranstaltungen", zurueck) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            daten.veranstaltungen.sortedByDescending { it.jahr }.forEach { x ->
                Row(Modifier.fillMaxWidth().clickable { VmStore.waehlen(x.id); zurueck() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(x.titel + if (x.id == akt?.id) "  (geöffnet)" else "", fontWeight = if (x.id == akt?.id) FontWeight.Bold else FontWeight.Normal)
                        Text("${x.teilnehmer.size} Teilnehmer · ${x.fortschritt().second} Zeiten", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { loeschen = x }) { Text("Löschen") }
                }
                HorizontalDivider()
            }
            if (akt != null) Abschnitt("Nächstes Jahr aus „${akt.titel}“") {
                Text("Regeln werden übernommen, die Jahrgangsgrenzen wandern um ein Jahr mit.", style = MaterialTheme.typography.bodySmall)
                Schalter("Teilnehmer mitnehmen (Vorjahreszeiten als Meldezeit)", mitTeilnehmern, { mitTeilnehmern = it })
                BigButton("Vereinsmeisterschaft ${akt.jahr + 1} anlegen", { VmStore.neu(akt.jahr + 1, akt, mitTeilnehmern); zurueck() })
            }
            OutlinedButton(onClick = { VmStore.neu(LocalDate.now().year); zurueck() }, modifier = Modifier.fillMaxWidth()) {
                Text("Leere Veranstaltung ${LocalDate.now().year} (Vorgaben)")
            }
        }
    }
}
