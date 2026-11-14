package de.dachapp.vereinsmeisterschaft.ui

import android.graphics.Bitmap
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.dachapp.module.BigButton
import de.dachapp.module.Page
import de.dachapp.scanner.Texterkennung
import de.dachapp.vereinsmeisterschaft.logic.Ergebnis
import de.dachapp.vereinsmeisterschaft.logic.ScanTreffer
import de.dachapp.vereinsmeisterschaft.logic.Status
import de.dachapp.vereinsmeisterschaft.logic.Veranstaltung
import de.dachapp.vereinsmeisterschaft.logic.Zeit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class Zeile(val treffer: ScanTreffer, val text: String, val status: Status?, val an: Boolean)

/**
 * Korrektur-Ansicht nach dem Foto: je Zeile der Bildausschnitt mit der Handschrift, daneben die gelesene Zeit zum
 * Verbessern. Unsichere Zeilen sind farbig markiert. Übernommen wird erst nach Bestätigung.
 */
@Composable
fun ScanPruefen(v: Veranstaltung, treffer: List<ScanTreffer>, foto: String, zurueck: () -> Unit, meldung: (String) -> Unit) {
    val context = LocalContext.current
    val bild by produceState<Bitmap?>(null, foto) {
        value = runCatching { withContext(Dispatchers.IO) { Texterkennung.laden(context, foto.toUri()) } }.getOrNull()
    }
    val zeilen = remember(treffer) {
        mutableStateListOf<Zeile>().apply {
            treffer.sortedWith(compareBy({ it.lauf }, { it.bahn })).forEach { t ->
                val belegt = v.laeufe.firstOrNull { it.nr == t.lauf }?.auf(t.bahn) != null
                add(Zeile(t, t.zeit?.let { Zeit.text(it) }.orEmpty(), t.status, belegt && (t.zeit != null || t.status != null)))
            }
        }
    }
    val unsicher = zeilen.count { !it.treffer.sicher }
    Page("Zettel prüfen", zurueck, bottom = {
        BigButton("${zeilen.count { it.an }} übernehmen", {
            var n = 0
            VmStore.aendern { ver ->
                var neu = ver
                for (z in zeilen.filter { it.an }) {
                    val lauf = ver.laeufe.firstOrNull { it.nr == z.treffer.lauf } ?: continue
                    val tid = lauf.auf(z.treffer.bahn) ?: continue
                    val zeit = Zeit.lesen(z.text)
                    if (z.status == null && zeit == null) continue
                    neu = neu.mitErgebnis(tid, lauf.strecke.lage, Ergebnis(tid, lauf.strecke.lage, if (z.status == null) zeit else null, z.status ?: Status.OK))
                    n++
                }
                neu
            }
            meldung("$n Zeiten übernommen.")
            zurueck()
        }, enabled = zeilen.any { it.an })
    }) {
        Text(
            "${zeilen.size} Zeilen erkannt" + if (unsicher > 0) ", $unsicher bitte prüfen (markiert)." else ". Bitte trotzdem kurz mit dem Bild vergleichen.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(zeilen, key = { _, z -> "${z.treffer.lauf}-${z.treffer.bahn}" }) { i, z ->
                val t = z.treffer
                val lauf = v.laeufe.firstOrNull { it.nr == t.lauf }
                val person = lauf?.auf(t.bahn)?.let { v.person(it) }
                val bisher = person?.let { p -> lauf.let { v.ergebnis(p.id, it.strecke.lage) } }
                val farbe = if (!t.sicher) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                Card(colors = CardDefaults.cardColors(containerColor = farbe)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = z.an, onCheckedChange = { zeilen[i] = z.copy(an = it) }, enabled = person != null)
                            Column(Modifier.weight(1f)) {
                                Text("Lauf ${t.lauf} · Bahn ${t.bahn}", fontWeight = FontWeight.Bold)
                                Text(person?.let { "${it.name} · ${lauf.strecke.text}" } ?: "Bahn nicht belegt", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        val b = bild
                        if (b != null && t.unten > t.oben && t.oben < b.height) {
                            val ausschnitt = remember(b, t) {
                                val oben = t.oben.coerceIn(0, b.height - 1)
                                val h = (t.unten - oben).coerceIn(1, b.height - oben)
                                Bitmap.createBitmap(b, 0, oben, b.width, h).asImageBitmap()
                            }
                            Image(ausschnitt, "Ausschnitt vom Zettel", Modifier.fillMaxWidth().heightIn(max = 90.dp), contentScale = ContentScale.FillWidth)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = z.text,
                                onValueChange = { neu -> zeilen[i] = z.copy(text = neu.filter { c -> c.isDigit() || c in ",.:" }, status = null, an = person != null) },
                                label = { Text("Zeit") },
                                isError = z.status == null && z.an && Zeit.lesen(z.text) == null,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                            listOf(Status.NA, Status.DSQ).forEach { s ->
                                FilterChip(selected = z.status == s, onClick = { zeilen[i] = z.copy(status = if (z.status == s) null else s, an = person != null) }, label = { Text(s.kurz) })
                            }
                        }
                        val info = listOfNotNull(
                            t.grund.takeIf { it.isNotBlank() },
                            t.roh.takeIf { it.isNotBlank() }?.let { "gelesen: „$it“" },
                            bisher?.let { "bisher: " + if (it.gueltig) Zeit.text(it.zeit) else it.status.kurz },
                        )
                        if (info.isNotEmpty()) Text(info.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
