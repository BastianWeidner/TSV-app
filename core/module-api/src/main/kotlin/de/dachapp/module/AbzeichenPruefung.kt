package de.dachapp.module

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Wer geprüft wird: [stand] wie in [Schwimmabzeichen] beschrieben, [hat] = Namen der Abzeichen, die schon vorliegen. */
data class Pruefling(val id: String, val name: String, val stand: Map<String, Map<String, String>>, val hat: Set<String>)

private val TAG = DateTimeFormatter.ofPattern("dd.MM.")
private val DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/**
 * Abzeichen-Prüfung am Beckenrand: Abzeichen und Teilleistung wählen, dann Kinder antippen (abgehakt mit heutigem
 * Datum). Ohne gewählte Teilleistung zeigt die Liste den Stand je Kind; Antippen klappt alle Teile auf.
 * Die Unter-App trägt ein fertiges Abzeichen in [onSetzen] selbst beim Kind ein.
 */
@Composable
fun AbzeichenPruefung(
    titel: String,
    prueflinge: List<Pruefling>,
    onBack: () -> Unit,
    onSetzen: (id: String, abzeichen: String, teil: String, datum: LocalDate?) -> Unit,
    kopf: @Composable ColumnScope.() -> Unit = {},
) {
    var abzName by rememberSaveable { mutableStateOf(Schwimmabzeichen.ALLE.first().name) }
    val abz = Schwimmabzeichen.ALLE.first { it.name == abzName }
    var teilId by rememberSaveable(abzName) { mutableStateOf<String?>(null) }
    val teil = abz.teile.firstOrNull { it.id == teilId }
    var offen by rememberSaveable { mutableStateOf<String?>(null) }
    var bestellliste by remember { mutableStateOf(false) }
    val heute = LocalDate.now()

    fun hatSchon(p: Pruefling) = p.hat.any { Schwimmabzeichen.zu(it)?.let { a -> Schwimmabzeichen.ALLE.indexOf(a) >= Schwimmabzeichen.ALLE.indexOf(abz) } == true }
    val liste = prueflinge.sortedWith(compareBy({ hatSchon(it) }, { it.name }))

    Page(
        title = titel,
        onBack = onBack,
        bottom = { OutlinedButton(onClick = { bestellliste = true }, modifier = Modifier.fillMaxWidth()) { Text("Bestellliste Abzeichen") } },
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    kopf()
                    Auswahl("Abzeichen", Schwimmabzeichen.ALLE.map { it.name }, abzName, { it }, { abzName = it })
                    Auswahl<String?>("Prüfen", listOf<String?>(null) + abz.teile.map { it.id }, teilId, { id ->
                        abz.teile.firstOrNull { it.id == id }?.text ?: "Übersicht (alle Teile)"
                    }, { teilId = it })
                    Text(
                        if (teil == null) "Antippen zeigt alle Teilleistungen eines Kindes." else "Antippen hakt „${teil.text}“ mit heutigem Datum ab.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (liste.isEmpty()) item { Leer("Niemand in der Liste.") }
            items(liste, key = { it.id }) { p ->
                val erledigt = Schwimmabzeichen.erledigt(p.stand, abz)
                val schon = hatSchon(p)
                Card(Modifier.fillMaxWidth()) {
                    if (teil != null) {
                        val datum = erledigt[teil.id]
                        val drin = teil.id in erledigt
                        Row(
                            Modifier.fillMaxWidth().clickable { onSetzen(p.id, abz.name, teil.id, if (drin) null else heute) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = drin, onCheckedChange = { onSetzen(p.id, abz.name, teil.id, if (drin) null else heute) })
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    listOfNotNull(
                                        "${erledigt.size}/${abz.teile.size} Teile",
                                        datum?.let { "dieser am ${it.format(TAG)}" },
                                        if (schon) "hat ${abz.name} schon" else null,
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().clickable { offen = if (offen == p.id) null else p.id }.padding(12.dp)) {
                            Row {
                                Text(p.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text(
                                    when {
                                        Schwimmabzeichen.fertig(p.stand, abz) -> "✓ bestanden ${Schwimmabzeichen.bestandenAm(p.stand, abz)?.format(TAG).orEmpty()}"
                                        schon -> "hat es schon"
                                        else -> "${erledigt.size}/${abz.teile.size}"
                                    },
                                )
                            }
                            if (offen == p.id) {
                                abz.teile.forEach { t ->
                                    val drin = t.id in erledigt
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = drin, onCheckedChange = { onSetzen(p.id, abz.name, t.id, if (drin) null else heute) })
                                        Text(
                                            t.text + (erledigt[t.id]?.let { " (${it.format(TAG)})" } ?: ""),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (bestellliste) BestelllisteDialog(titel, prueflinge) { bestellliste = false }
}

/** Bestandene Abzeichen je Stufe mit Datum, zum Teilen (Mail, Messenger) für die Bestellung von Abzeichen und Ausweisen. */
@Composable
private fun BestelllisteDialog(titel: String, prueflinge: List<Pruefling>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = bestellliste(titel, prueflinge)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val senden = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Bestellliste Abzeichen").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(senden, "Bestellliste teilen"))
            }) { Text("Teilen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
        text = { Text(text, modifier = Modifier.verticalScroll(rememberScrollState())) },
    )
}

fun bestellliste(titel: String, prueflinge: List<Pruefling>): String = buildString {
    append("Bestandene Abzeichen – ").append(titel).append('\n')
    var leer = true
    Schwimmabzeichen.ALLE.forEach { a ->
        val fertig = prueflinge.mapNotNull { p -> Schwimmabzeichen.bestandenAm(p.stand, a)?.let { p to it } }.sortedBy { it.first.name }
        if (fertig.isEmpty()) return@forEach
        leer = false
        append("\n").append(a.name).append(": ").append(fertig.size).append('\n')
        fertig.forEach { (p, tag) -> append("• ").append(p.name).append(" (").append(tag.format(DATUM)).append(")\n") }
    }
    if (leer) append("\nNoch kein Abzeichen vollständig abgehakt.\n")
}
