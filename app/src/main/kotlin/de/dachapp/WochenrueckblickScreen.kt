package de.dachapp

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.dachapp.module.Erinnerungen
import de.dachapp.module.Header
import de.dachapp.module.Kennzahl
import de.dachapp.module.Rueckblickart
import de.dachapp.module.Schalter
import de.dachapp.module.SubApp
import de.dachapp.module.Wochenbeitrag
import de.dachapp.module.Wochenrueckblick
import de.dachapp.module.Zeitraum
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Rückblick-Seite der Übersicht: Woche, Monat, Quartal, Halbjahr oder Jahr mit den Beiträgen aller Unter-Apps,
 * blätterbar. Am ersten Tag eines Zeitraums (Woche: Montag) startet sie mit dem gerade beendeten. Unten die
 * Erinnerungen. [start]: Art, mit der die Seite aufgeht (aus der Benachrichtigung), sonst Woche.
 */
@Composable
fun WochenrueckblickScreen(modules: List<SubApp>, onBack: () -> Unit, start: Rueckblickart? = null) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val app = context.applicationContext
    val heute = remember { LocalDate.now() }
    var artName by rememberSaveable { mutableStateOf((start ?: Rueckblickart.WOCHE).name) }
    val art = Rueckblickart.valueOf(artName)
    fun anfang(a: Rueckblickart) = if (Zeitraum.um(a, heute).von == heute) 1 else 0
    // 0 = laufender Zeitraum, 1 = der davor …
    var zurueck by rememberSaveable { mutableIntStateOf(anfang(art)) }
    val z = Zeitraum.um(art, heute).um(-zurueck)
    var beitraege by remember { mutableStateOf<List<Wochenbeitrag>?>(null) }
    LaunchedEffect(z) {
        beitraege = null
        beitraege = withContext(Dispatchers.IO) { WochenWecker.beitraege(app, modules, z) }
    }

    Column(Modifier.fillMaxWidth()) {
        Header("Rückblick", onBack)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Rueckblickart.entries.forEach { a ->
                    FilterChip(
                        selected = a == art,
                        onClick = { if (a != art) { artName = a.name; zurueck = anfang(a) } },
                        label = { Text(a.titel) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { zurueck++ }, enabled = zurueck < 520, modifier = Modifier.semantics { contentDescription = "${art.titel} davor" }) {
                    Text("‹", style = MaterialTheme.typography.headlineSmall)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when (zurueck) {
                            0 -> art.diese.replaceFirstChar { it.uppercase() }
                            1 -> art.vorKurz
                            else -> "Vor $zurueck ${MEHRZAHL[art]}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(z.titel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { zurueck-- }, enabled = zurueck > 0, modifier = Modifier.semantics { contentDescription = "${art.titel} danach" }) {
                    Text("›", style = MaterialTheme.typography.headlineSmall)
                }
            }

            val b = beitraege
            when {
                b == null -> Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                b.isEmpty() -> Text(
                    "In diesem Zeitraum ist noch nichts eingetragen. Trainings, Essen und Supplements erscheinen hier, sobald du sie erfasst.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                else -> {
                    Kopf(b, z)
                    b.forEach { Abschnitt(it) }
                }
            }

            ErinnerungKarte()
        }
    }
}

/** „Vor 3 Wochen“, „Vor 3 Monaten“ … (Dativ Mehrzahl). */
private val MEHRZAHL = mapOf(
    Rueckblickart.WOCHE to "Wochen", Rueckblickart.MONAT to "Monaten", Rueckblickart.QUARTAL to "Quartalen",
    Rueckblickart.HALBJAHR to "Halbjahren", Rueckblickart.JAHR to "Jahren",
)

@Composable
private fun Kopf(b: List<Wochenbeitrag>, z: Zeitraum) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Wochenrueckblick.ueberschrift(b, z.art), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            val n = b.sumOf { it.lob.size }
            if (n > 0) Text(if (n == 1) "1 Erfolg ${z.art.diese}" else "$n Erfolge ${z.art.diese}", style = MaterialTheme.typography.titleSmall)
            Text("„${Wochenrueckblick.spruch(z)}“", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Abschnitt(b: Wochenbeitrag) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${b.symbol}  ${b.titel}", style = MaterialTheme.typography.titleMedium)
            b.kennzahlen.forEach { Zahl(it) }
            if (b.lob.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    b.lob.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                }
            }
            if (b.tipps.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    b.tipps.forEach { Text("💡 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun Zahl(k: Kennzahl) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(k.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            val pfeil = when (k.trend) { 1 -> " ↑"; -1 -> " ↓"; else -> "" }
            Text(
                k.wert + pfeil,
                style = MaterialTheme.typography.titleSmall,
                color = when (k.trend) { 1 -> MaterialTheme.colorScheme.primary; -1 -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurface },
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 220.dp),
            )
        }
        k.anteil?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
        k.unter?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ErinnerungKarte() {
    val context = LocalContext.current
    val app = context.applicationContext
    var arten by remember { mutableStateOf(WochenWecker.arten(app)) }
    var stunde by remember { mutableIntStateOf(WochenWecker.stunde(app)) }
    var erlaubt by remember { mutableStateOf(Erinnerungen.erlaubt(app)) }
    val frage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { erlaubt = Erinnerungen.erlaubt(app) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🔔  Erinnern", style = MaterialTheme.typography.titleMedium)
            Text(
                "Am letzten Tag des Zeitraums eine kurze Zusammenfassung (Woche: sonntags). Fallen mehrere auf einen Tag, kommt nur der größte. Alles bleibt auf dem Handy.",
                style = MaterialTheme.typography.bodySmall,
            )
            Rueckblickart.entries.forEach { a ->
                Schalter(a.rueckblick, a in arten, { an ->
                    WochenWecker.einstellen(app, a, an)
                    arten = WochenWecker.arten(app)
                    if (an && !erlaubt && Build.VERSION.SDK_INT >= 33) frage.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
            }
            if (arten.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WochenWecker.STUNDEN.forEach { h ->
                        FilterChip(selected = stunde == h, onClick = { stunde = h; WochenWecker.stundeSetzen(app, h) }, label = { Text("$h Uhr") })
                    }
                }
                if (!erlaubt) {
                    Text("Benachrichtigungen sind für die App noch aus.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    if (Build.VERSION.SDK_INT >= 33) {
                        OutlinedButton(onClick = { frage.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Benachrichtigungen erlauben") }
                    }
                }
            }
        }
    }
}
