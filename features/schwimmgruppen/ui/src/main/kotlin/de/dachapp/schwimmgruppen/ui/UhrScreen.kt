package de.dachapp.schwimmgruppen.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dachapp.module.Auswahl
import de.dachapp.module.Eingabe
import de.dachapp.module.Page
import de.dachapp.module.Zaehler
import de.dachapp.schwimmgruppen.logic.Abgangsuhr
import de.dachapp.schwimmgruppen.logic.Bahn
import de.dachapp.schwimmgruppen.logic.Serie
import de.dachapp.schwimmgruppen.logic.Stoppuhr
import de.dachapp.schwimmgruppen.logic.Zeiten
import kotlinx.coroutines.delay

/** Zustand der Trainingsuhr; liegt im ViewModel, damit Drehen des Tablets nichts anhält. */
data class UhrZustand(
    val stoppuhr: Boolean = false,
    val serie: Serie = Serie(),
    /** SystemClock.elapsedRealtime() beim Start der Abgangsuhr, null = steht. */
    val serieStart: Long? = null,
    val bahnen: List<Bahn> = List(4) { Bahn(it + 1) },
    val stoppStart: Long? = null,
)

/**
 * Trainingsuhr für die Halle: Abgangsuhr für Serien (großer Countdown, Töne 3-2-1 und beim Abgang) und Stoppuhr für
 * mehrere Bahnen, deren Zeiten direkt beim Schwimmer als Trainingszeit landen.
 */
@Composable
fun UhrScreen(state: UiState, vm: SchwimmgruppenViewModel) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val u by vm.uhr.collectAsStateWithLifecycle()
    var jetzt by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(u.serieStart, u.stoppStart) {
        while (u.serieStart != null || u.stoppStart != null) {
            jetzt = SystemClock.elapsedRealtime()
            delay(31)
        }
    }

    Page(title = "Trainingsuhr", onBack = { vm.zurueck() }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!u.stoppuhr, { vm.uhrAendern { it.copy(stoppuhr = false) } }, { Text("Abgangsuhr") })
            FilterChip(u.stoppuhr, { vm.uhrAendern { it.copy(stoppuhr = true) } }, { Text("Stoppuhr Bahnen") })
        }
        if (u.stoppuhr) StoppuhrTeil(u, jetzt, state, vm, Modifier.weight(1f)) else AbgangsuhrTeil(u, jetzt, vm, Modifier.weight(1f))
    }
}

@Composable
private fun AbgangsuhrTeil(u: UhrZustand, jetzt: Long, vm: SchwimmgruppenViewModel, modifier: Modifier) {
    val s = u.serie
    val start = u.serieStart
    val ton = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { ton?.release() } }
    var letzterTon by remember { mutableStateOf("") }

    Column(modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (start == null) {
            var abstand by remember { mutableStateOf(Abgangsuhr.text(s.abstandMs)) }
            var pause by remember { mutableStateOf(Abgangsuhr.text(s.pauseMs)) }
            Zaehler("Abgänge je Serie", "${s.anzahl}", { vm.uhrAendern { it.copy(serie = s.copy(anzahl = s.anzahl - 1)) } }, { vm.uhrAendern { it.copy(serie = s.copy(anzahl = s.anzahl + 1)) } }, s.anzahl > 1, s.anzahl < 50)
            Eingabe("Abgang alle (Min:Sek)", abstand, { t ->
                abstand = t
                sekundenText(t)?.let { ms -> vm.uhrAendern { it.copy(serie = s.copy(abstandMs = ms)) } }
            }, hilfe = "z. B. 1:45", fehler = "Bitte wie 1:45 eingeben".takeIf { sekundenText(abstand) == null })
            Zaehler("Serien", "${s.serien}", { vm.uhrAendern { it.copy(serie = s.copy(serien = s.serien - 1)) } }, { vm.uhrAendern { it.copy(serie = s.copy(serien = s.serien + 1)) } }, s.serien > 1, s.serien < 20)
            if (s.serien > 1) Eingabe("Pause zwischen den Serien (Min:Sek)", pause, { t ->
                pause = t
                sekundenText(t)?.let { ms -> vm.uhrAendern { it.copy(serie = s.copy(pauseMs = ms)) } }
            })
            Text(
                "${s.abgaengeGesamt} Abgänge, Dauer etwa ${Abgangsuhr.text(Abgangsuhr.abgaenge(s).last().first + s.abstandMs)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { vm.uhrAendern { it.copy(serieStart = SystemClock.elapsedRealtime()) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text("Start (erster Abgang sofort)", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            val st = Abgangsuhr.stand(s, jetzt - start)
            val rest = st.bisNaechsterMs
            // Töne: 3, 2, 1 kurz, beim Abgang lang; je Sekunde nur einmal.
            val sek = rest?.let { (it + 999) / 1000 } ?: -1
            val schluessel = "${st.serie}-${st.abgang}-$sek"
            LaunchedEffect(schluessel) {
                if (schluessel == letzterTon) return@LaunchedEffect
                letzterTon = schluessel
                when {
                    st.fertig -> ton?.startTone(ToneGenerator.TONE_PROP_ACK, 600)
                    sek in 1..3 -> ton?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                }
            }
            // Langer Ton bei jedem Abgang, auch beim ersten direkt nach dem Start.
            LaunchedEffect(st.serie, st.abgang) { ton?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500) }
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (st.inPause) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when {
                            st.fertig -> "Fertig"
                            st.inPause -> "Pause · gleich Serie ${st.serie + 1}"
                            else -> (if (s.serien > 1) "Serie ${st.serie} · " else "") + "Abgang ${st.abgang} von ${s.anzahl}"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        rest?.let { Abgangsuhr.text(it) } ?: "✓",
                        fontSize = 120.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text("bis zum nächsten Abgang · läuft seit ${Abgangsuhr.text(jetzt - start)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            OutlinedButton(onClick = { vm.uhrAendern { it.copy(serieStart = null) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Stopp") }
        }
    }
}

/** "1:45" oder "90" → ms. */
private fun sekundenText(t: String): Long? {
    val teile = t.trim().split(':')
    return when (teile.size) {
        1 -> teile[0].toLongOrNull()?.times(1000)
        2 -> {
            val m = teile[0].toLongOrNull() ?: return null
            val s = teile[1].toLongOrNull()?.takeIf { it < 60 } ?: return null
            (m * 60 + s) * 1000
        }
        else -> null
    }?.takeIf { it in 5_000..3_600_000 }
}

@Composable
private fun StoppuhrTeil(u: UhrZustand, jetzt: Long, state: UiState, vm: SchwimmgruppenViewModel, modifier: Modifier) {
    val b = state.bestand
    val schwimmer = remember(b) { b.mitglieder.filter { it.aktiv }.sortedBy { it.name.lowercase() } }
    val strecken = b.einstellungen.strecken
    val start = u.stoppStart
    val laufzeit = start?.let { jetzt - it } ?: 0L
    fun bahn(nr: Int, f: (Bahn) -> Bahn) = vm.uhrAendern { z -> z.copy(bahnen = z.bahnen.map { if (it.nr == nr) f(it) else it }) }

    Column(modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Zeiten.text(Stoppuhr.hundertstel(laufzeit)), Modifier.weight(1f), fontSize = 48.sp, fontWeight = FontWeight.Bold)
            if (start == null) {
                Button(onClick = { vm.uhrAendern { z -> z.copy(stoppStart = SystemClock.elapsedRealtime(), bahnen = z.bahnen.map { it.copy(zwischen = emptyList(), fertig = false) }) } }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text("Start")
                }
            } else {
                OutlinedButton(onClick = { vm.uhrAendern { it.copy(stoppStart = null) } }) { Text("Abbrechen") }
            }
        }
        if (start == null) {
            Zaehler("Bahnen", "${u.bahnen.size}", {
                vm.uhrAendern { it.copy(bahnen = it.bahnen.dropLast(1)) }
            }, {
                vm.uhrAendern { it.copy(bahnen = it.bahnen + Bahn(it.bahnen.size + 1)) }
            }, u.bahnen.size > 1, u.bahnen.size < 8)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(u.bahnen, key = { it.nr }) { bn ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Bahn ${bn.nr}", style = MaterialTheme.typography.titleMedium)
                            Auswahl(
                                "Wer", listOf<String?>(null) + schwimmer.map { it.id }, bn.schwimmerId,
                                { id -> id?.let { b.mitglied(it)?.name } ?: "–" }, { id -> bahn(bn.nr) { it.copy(schwimmerId = id) } },
                                Modifier.weight(1f),
                            )
                        }
                        Auswahl("Strecke", listOf("") + strecken, bn.strecke, { it.ifEmpty { "–" } }, { st -> bahn(bn.nr) { it.copy(strecke = st) } })
                        val zeiten = Stoppuhr.teilzeiten(bn)
                        if (bn.zwischen.isNotEmpty()) {
                            Text(
                                bn.zwischen.mapIndexed { i, t -> Zeiten.text(Stoppuhr.hundertstel(t)) + " (" + Zeiten.text(Stoppuhr.hundertstel(zeiten[i])) + ")" }.joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (bn.fertig) FontWeight.Bold else null,
                            )
                        }
                        if (start != null && !bn.fertig) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { bahn(bn.nr) { it.copy(zwischen = it.zwischen + (SystemClock.elapsedRealtime() - start)) } }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                                    Text("Zwischenzeit")
                                }
                                Button(onClick = { bahn(bn.nr) { it.copy(zwischen = it.zwischen + (SystemClock.elapsedRealtime() - start), fertig = true) } }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                                    Text("Anschlag")
                                }
                            }
                        }
                    }
                }
            }
        }
        val speicherbar = u.bahnen.filter { it.fertig && it.schwimmerId != null && it.strecke.isNotEmpty() }
        if (u.bahnen.any { it.fertig }) {
            Button(onClick = vm::uhrZeitenSpeichern, enabled = speicherbar.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(if (speicherbar.isEmpty()) "Schwimmer und Strecke wählen, dann speichern" else "${speicherbar.size} Zeiten beim Schwimmer speichern")
            }
        }
    }
}
