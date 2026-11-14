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
import de.dachapp.module.Schalter
import de.dachapp.module.SubApp
import de.dachapp.module.Wochenbeitrag
import de.dachapp.module.Wochenrueckblick
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Rückblick-Seite der Übersicht: eine Woche (Montag bis Sonntag) mit den Beiträgen aller Unter-Apps, blätterbar.
 * Montags startet sie mit der gerade beendeten Woche, sonst mit der laufenden. Unten die Sonntags-Erinnerung.
 */
@Composable
fun WochenrueckblickScreen(modules: List<SubApp>, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val app = context.applicationContext
    val heute = remember { LocalDate.now() }
    val diese = remember { Wochenrueckblick.montag(heute) }
    // 0 = laufende Woche, 1 = Vorwoche …
    var zurueck by rememberSaveable { mutableIntStateOf(if (heute.dayOfWeek == DayOfWeek.MONDAY) 1 else 0) }
    val montag = diese.minusWeeks(zurueck.toLong())
    var beitraege by remember { mutableStateOf<List<Wochenbeitrag>?>(null) }
    LaunchedEffect(montag) {
        beitraege = null
        beitraege = withContext(Dispatchers.IO) { WochenWecker.beitraege(app, modules, montag) }
    }

    Column(Modifier.fillMaxWidth()) {
        Header("Wochenrückblick", onBack)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { zurueck++ }, enabled = zurueck < 104, modifier = Modifier.semantics { contentDescription = "Woche davor" }) {
                    Text("‹", style = MaterialTheme.typography.headlineSmall)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when (zurueck) { 0 -> "Diese Woche"; 1 -> "Letzte Woche"; else -> "Vor $zurueck Wochen" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(Wochenrueckblick.titel(montag), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { zurueck-- }, enabled = zurueck > 0, modifier = Modifier.semantics { contentDescription = "Woche danach" }) {
                    Text("›", style = MaterialTheme.typography.headlineSmall)
                }
            }

            val b = beitraege
            when {
                b == null -> Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                b.isEmpty() -> Text(
                    "In dieser Woche ist noch nichts eingetragen. Trainings, Essen und Supplements erscheinen hier, sobald du sie erfasst.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                else -> {
                    Kopf(b, montag)
                    b.forEach { Abschnitt(it) }
                }
            }

            ErinnerungKarte()
        }
    }
}

@Composable
private fun Kopf(b: List<Wochenbeitrag>, montag: LocalDate) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Wochenrueckblick.ueberschrift(b), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            val n = b.sumOf { it.lob.size }
            if (n > 0) Text(if (n == 1) "1 Erfolg diese Woche" else "$n Erfolge diese Woche", style = MaterialTheme.typography.titleSmall)
            Text("„${Wochenrueckblick.spruch(montag)}“", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
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
    var an by remember { mutableStateOf(WochenWecker.an(app)) }
    var stunde by remember { mutableIntStateOf(WochenWecker.stunde(app)) }
    var erlaubt by remember { mutableStateOf(Erinnerungen.erlaubt(app)) }
    val frage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { erlaubt = Erinnerungen.erlaubt(app) }
    fun speichern() = WochenWecker.einstellen(app, an, stunde)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🔔  Sonntags erinnern", style = MaterialTheme.typography.titleMedium)
            Schalter(
                "Wochenrückblick als Benachrichtigung", an,
                { an = it; speichern(); if (it && !erlaubt && Build.VERSION.SDK_INT >= 33) frage.launch(Manifest.permission.POST_NOTIFICATIONS) },
                hilfe = "Jeden Sonntag eine kurze Zusammenfassung deiner Woche. Alles bleibt auf dem Handy.",
            )
            if (an) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WochenWecker.STUNDEN.forEach { h ->
                        FilterChip(selected = stunde == h, onClick = { stunde = h; speichern() }, label = { Text("$h Uhr") })
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
