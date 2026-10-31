package de.dachapp

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import de.dachapp.module.Abschnitt
import de.dachapp.module.BigButton
import de.dachapp.module.FormPage
import de.dachapp.module.Gesamtsicherung
import de.dachapp.module.MessageDialog
import de.dachapp.module.Schalter
import de.dachapp.module.Sicherungsarchiv
import de.dachapp.module.SubApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.system.exitProcess

/** Welche gesicherten Dateien zu welcher Unter-App gehören (für „nur diese Unter-App einspielen“). */
object Sicherungsgruppen {
    const val GEMEINSAM = "gemeinsam"

    /** Datei- oder Ordnername im App-Speicher bzw. Einstellungsdatei → Unter-App. */
    private val namen: Map<String, String> = mapOf(
        "werwolf_spielstand.json" to "werwolf", "werwolf.xml" to "werwolf",
        "schwimmkurs.json" to "schwimmkurs",
        "schwimmgruppen.json" to "schwimmgruppen", "schwimmgruppen-abgleich.json" to "schwimmgruppen",
        "schwimmleitung.json" to "schwimmleitung",
        "cafe-bastian.json" to "cafe",
        "einkauf.json" to "einkauf", "einkauf-intern.xml" to "einkauf",
        "tracker.json" to "tracker", "tracker.xml" to "tracker",
        "brennholz.json" to "brennholz", "brennholz-fotos" to "brennholz",
        "krafttraining.json" to "krafttraining",
        "nachlegen.json" to "nachlegen",
        "camper.json" to "camper", "camper-fotos" to "camper",
        "wartung.json" to "wartung", "wartung-belege" to "wartung",
        "karten.json" to "karten",
        "rezepte.json" to "rezepte", "rezepte-fotos" to "rezepte",
        "preise.json" to "preise", "preise-zettel" to "preise",
    )

    fun gruppe(zipName: String): String {
        val rest = zipName.removePrefix(Sicherungsarchiv.DATEIEN).removePrefix(Sicherungsarchiv.PREFS)
        return namen[rest.substringBefore('/')] ?: GEMEINSAM
    }
}

private data class Einspielen(val uri: Uri, val zeit: String, val gruppen: Map<String, Int>, val gewaehlt: Set<String>)

private fun zeitText(ms: Long) = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

@Composable
fun SicherungScreen(modules: List<SubApp>, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    var stand by remember { mutableStateOf(Gesamtsicherung.stand(app)) }
    var meldung by remember { mutableStateOf<String?>(null) }
    var laeuft by remember { mutableStateOf(false) }
    var einspielen by remember { mutableStateOf<Einspielen?>(null) }
    var fertig by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)

    fun arbeit(text: String, block: suspend () -> String) {
        laeuft = true
        scope.launch {
            meldung = runCatching { withContext(Dispatchers.IO) { block() } }.getOrElse { "$text fehlgeschlagen: ${it.message ?: it.javaClass.simpleName}" }
            stand = Gesamtsicherung.stand(app)
            laeuft = false
        }
    }

    val jetzt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) arbeit("Sicherung") { "Gesichert: ${Gesamtsicherung.inDatei(app, uri)} Dateien." }
    }
    val dateiZiel = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) { Gesamtsicherung.zielSetzen(app, datei = uri); stand = Gesamtsicherung.stand(app) }
    }
    val ordnerZiel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { Gesamtsicherung.zielSetzen(app, ordner = uri); stand = Gesamtsicherung.stand(app) }
    }
    val oeffnen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val inhalt = runCatching { withContext(Dispatchers.IO) { app.contentResolver.openInputStream(uri)?.use { Sicherungsarchiv.inhalt(it) } } }.getOrNull()
            if (inhalt == null || !inhalt.gueltig) {
                meldung = "Das ist keine Sicherung der Dach-App."
                return@launch
            }
            val vorhanden = modules.map { it.id }.toSet() + Sicherungsgruppen.GEMEINSAM
            val gruppen = inhalt.eintraege.groupBy(Sicherungsgruppen::gruppe).filterKeys { it in vorhanden }.mapValues { it.value.size }
            einspielen = Einspielen(uri, inhalt.kopf["zeit"].orEmpty().replace('T', ' ').take(16), gruppen, gruppen.keys)
        }
    }

    FormPage(title = "Sicherung", onBack = onBack) {
        Text(
            "Sichert die Daten aller Unter-Apps samt Fotos in eine ZIP-Datei. Produktdatenbanken (Einkauf, Tracker) " +
                "sind nicht dabei, sie lassen sich neu einlesen.",
            style = MaterialTheme.typography.bodyMedium,
        )
        BigButton(if (laeuft) "Läuft …" else "Jetzt sichern …", {
            jetzt.launch(Gesamtsicherung.dateiname())
        }, enabled = !laeuft)
        Abschnitt("Automatisch, etwa einmal am Tag") {
            Schalter("Automatisch sichern", stand.automatisch, {
                Gesamtsicherung.automatischSetzen(app, it); stand = Gesamtsicherung.stand(app)
            }, "Läuft, wenn der Akku nicht knapp ist, meist nachts.")
            Text("In eine Datei (z. B. in Google Drive), wird jedes Mal überschrieben:", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stand.datei?.let { name(context, it) } ?: "keine", Modifier.weight(1f))
                TextButton(onClick = { dateiZiel.launch("dach-sicherung.zip") }) { Text(if (stand.datei == null) "Anlegen" else "Ändern") }
                if (stand.datei != null) TextButton(onClick = { Gesamtsicherung.zielSetzen(app, datei = null); stand = Gesamtsicherung.stand(app) }) { Text("✕") }
            }
            Text("In einen Ordner auf dem Gerät, die letzten ${Gesamtsicherung.BEHALTEN} bleiben:", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stand.ordner?.lastPathSegment?.substringAfterLast(':')?.ifEmpty { "Ordner" } ?: "keiner", Modifier.weight(1f))
                TextButton(onClick = { ordnerZiel.launch(null) }) { Text(if (stand.ordner == null) "Wählen" else "Ändern") }
                if (stand.ordner != null) TextButton(onClick = { Gesamtsicherung.zielSetzen(app, ordner = null); stand = Gesamtsicherung.stand(app) }) { Text("✕") }
            }
            if (stand.letzteZeit > 0) {
                Text(
                    "Zuletzt ${zeitText(stand.letzteZeit)}: ${stand.letzteMeldung}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (stand.letzteOk) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            OutlinedButton(onClick = { arbeit("Sicherung") { Gesamtsicherung.automatisch(app); Gesamtsicherung.stand(app).letzteMeldung } },
                enabled = !laeuft && (stand.datei != null || stand.ordner != null)) { Text("Jetzt ausprobieren") }
        }
        Abschnitt("Einspielen") {
            Text("Sicherung wählen, dann festlegen, welche Unter-Apps zurückgeholt werden. Deren jetzige Daten werden ersetzt.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { oeffnen.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !laeuft) { Text("Sicherung öffnen …") }
        }
    }

    einspielen?.let { e ->
        AlertDialog(
            onDismissRequest = { einspielen = null },
            title = { Text("Sicherung vom ${e.zeit}") },
            text = {
                androidx.compose.foundation.layout.Column {
                    e.gruppen.keys.sortedBy { id -> modules.indexOfFirst { it.id == id }.let { if (it < 0) 999 else it } }.forEach { id ->
                        val titel = modules.firstOrNull { it.id == id }?.title ?: "Gemeinsames (Übersicht, Holzbuch, Trainingsbuch …)"
                        val an = id in e.gewaehlt
                        Row(Modifier.fillMaxWidth().clickable { einspielen = e.copy(gewaehlt = if (an) e.gewaehlt - id else e.gewaehlt + id) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = an, onCheckedChange = { einspielen = e.copy(gewaehlt = if (it) e.gewaehlt + id else e.gewaehlt - id) })
                            Text("$titel (${e.gruppen[id]})")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    einspielen = null
                    laeuft = true
                    scope.launch {
                        val r = runCatching { withContext(Dispatchers.IO) { Gesamtsicherung.einspielen(app, e.uri) { Sicherungsgruppen.gruppe(it) in e.gewaehlt } } }
                        laeuft = false
                        r.onSuccess { fertig = "$it Dateien eingespielt. Die App schließt sich jetzt, bitte neu öffnen." }
                            .onFailure { meldung = "Einspielen fehlgeschlagen: ${it.message}" }
                    }
                }, enabled = e.gewaehlt.isNotEmpty()) { Text("Einspielen") }
            },
            dismissButton = { TextButton(onClick = { einspielen = null }) { Text("Abbrechen") } },
        )
    }
    MessageDialog(meldung) { meldung = null }
    // Nach dem Einspielen hält jede Unter-App noch den alten Stand im Speicher: App beenden, damit nichts überschrieben wird.
    fertig?.let { t ->
        AlertDialog(
            onDismissRequest = {},
            text = { Text(t) },
            confirmButton = { TextButton(onClick = { (context as? Activity)?.finishAffinity(); exitProcess(0) }) { Text("OK") } },
        )
    }
}

private fun name(c: Context, uri: Uri): String = runCatching {
    c.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
}.getOrNull() ?: "gewählte Datei"
