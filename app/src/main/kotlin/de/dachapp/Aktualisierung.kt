package de.dachapp

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import de.dachapp.module.Erinnerungen
import de.dachapp.module.UpdateVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Updates aus einem Drive-Ordner (Wunsch Bastian 2026-11-19). Der Ordner wird einmal im Dateidialog gewählt (wie beim
 * Café-Abgleich); die Drive-App auf dem Gerät holt die Dateien. Die Dach-App selbst hat kein Internet und schickt
 * nichts weg, sie liest nur die Dateinamen im Ordner (siehe [UpdateVersion]). Beim Start und etwa einmal am Tag wird
 * nachgesehen; eine neue Version meldet sich einmal per Benachrichtigung und bleibt als Hinweis in der Übersicht.
 * Installieren startet den Dialog von Android (still installieren lässt Android nicht zu). Ohne die Berechtigung
 * REQUEST_INSTALL_PACKAGES im Manifest (Variante „schwimmen“) öffnet der Knopf die Datei in der Drive-App.
 */
object Aktualisierung {
    private const val PREFS = "dach_update"
    private const val ORDNER = "ordner"
    private const val AN = "an"
    private const val GEMELDET = "gemeldet"
    private const val KANAL = "dach_update"
    private const val NR = 77_001
    private const val WECKER_NR = 77_020
    private const val STATUS_NR = 77_030
    const val PRUEFEN = "de.dachapp.UPDATE_PRUEFEN"
    const val STATUS = "de.dachapp.UPDATE_STATUS"

    /**
     * Freigabe-Link des Drive-Ordners (Wunsch Bastian 2026-11-19): Knopf „Update-Ordner öffnen“ zeigt ihn in der
     * Drive-App oder im Browser, dort lädt und installiert man die neue APK von Hand. Leer = Knopf ausgeblendet.
     * Braucht keine Internet-Berechtigung, das Öffnen übernimmt die Drive-App bzw. der Browser. Je Variante:
     * bisher nur „gym“ (Bastians Ordner „Gym Updates“, per Link freigegeben).
     */
    fun ordnerLink(c: Context): String = when (UpdateVersion.praefix(c.packageName)) {
        "gym" -> "https://drive.google.com/drive/folders/1DXZHvHMxW5RoUeBqiF14tQVeUOBZXqeN"
        else -> ""
    }

    fun linkOeffnen(c: Context): Boolean = ordnerLink(c).isNotBlank() && runCatching {
        c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ordnerLink(c))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)

    /** Gefundene neuere APK der eigenen Variante. */
    data class Fund(val name: String, val version: String, val uri: Uri)

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ordner(c: Context): Uri? = prefs(c).getString(ORDNER, null)?.let(Uri::parse)
    fun an(c: Context): Boolean = prefs(c).getBoolean(AN, true)

    fun installiert(c: Context): String =
        runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull() ?: "0"

    /** Name des gewählten Ordners für die Anzeige. */
    fun ordnerName(c: Context): String? {
        val tree = ordner(c) ?: return null
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return runCatching {
            c.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: "gewählter Ordner"
    }

    fun ordnerSetzen(c: Context, tree: Uri) {
        val r = c.contentResolver
        ordner(c)?.takeIf { it != tree }?.let { alt -> runCatching { r.releasePersistableUriPermission(alt, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        r.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs(c).edit().putString(ORDNER, tree.toString()).remove(GEMELDET).apply()
        planen(c)
    }

    fun ordnerWeg(c: Context) {
        ordner(c)?.let { runCatching { c.contentResolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        prefs(c).edit().remove(ORDNER).remove(GEMELDET).apply()
        planen(c)
    }

    fun einschalten(c: Context, an: Boolean) {
        prefs(c).edit().putBoolean(AN, an).apply()
        planen(c)
    }

    /**
     * Sieht im Ordner nach (nur Dateinamen, keine Unterordner). Null, wenn nichts Neueres da ist; wirft, wenn der
     * Ordner nicht mehr lesbar ist (z. B. Freigabe entzogen).
     */
    fun suchen(c: Context): Fund? {
        val tree = ordner(c) ?: return null
        val kinder = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val dateien = mutableListOf<Pair<String, String>>()
        c.contentResolver.query(
            kinder, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { cur -> while (cur.moveToNext()) dateien += cur.getString(0) to (cur.getString(1) ?: "") }
        val (d, v) = UpdateVersion.neueste(dateien, { it.second }, UpdateVersion.praefix(c.packageName), installiert(c)) ?: return null
        return Fund(d.second, UpdateVersion.text(v), DocumentsContract.buildDocumentUriUsingTree(tree, d.first))
    }

    /** Suchen und eine neue Version einmal melden (Wecker und App-Start). Fehler bleiben still. */
    fun pruefenUndMelden(c: Context): Fund? {
        if (!an(c)) return null
        val f = runCatching { suchen(c) }.getOrNull() ?: return null
        if (prefs(c).getString(GEMELDET, null) != f.version) {
            Erinnerungen.kanal(c, KANAL, "Updates", "Meldet, wenn im Update-Ordner eine neue Version liegt")
            Erinnerungen.melden(c, KANAL, NR, "Neue Version ${f.version} da", "Antippen und in der Übersicht installieren.", unterApp = "")
            prefs(c).edit().putString(GEMELDET, f.version).apply()
        }
        return f
    }

    /** Beim App-Start: suchen, ohne Benachrichtigung (der Hinweis steht ja in der Übersicht). */
    fun beimStart(c: Context): Fund? {
        if (!an(c)) return null
        val f = runCatching { suchen(c) }.getOrNull() ?: return null
        prefs(c).edit().putString(GEMELDET, f.version).apply()
        Erinnerungen.weg(c, NR)
        return f
    }

    private fun wecker(c: Context): PendingIntent =
        Erinnerungen.anEmpfaenger(c, AktualisierungEmpfaenger::class.java, PRUEFEN, WECKER_NR)

    /** Täglichen Blick in den Ordner einplanen oder löschen (App-Start, Neustart, nach jeder Einstellung). */
    fun planen(c: Context) {
        if (an(c) && ordner(c) != null) Erinnerungen.taeglich(c, 11, wecker(c), minute = 17)
        else Erinnerungen.weckerWeg(c, wecker(c))
    }

    private fun hatBerechtigung(c: Context, name: String): Boolean = runCatching {
        c.packageManager.getPackageInfo(c.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions?.contains(name) == true
    }.getOrDefault(false)

    /** Darf diese Variante selbst installieren? („schwimmen“ hat die Berechtigung nicht.) */
    fun kannSelbst(c: Context) = hatBerechtigung(c, Manifest.permission.REQUEST_INSTALL_PACKAGES)
    fun kannMelden(c: Context) = hatBerechtigung(c, Manifest.permission.POST_NOTIFICATIONS)

    /**
     * Installation starten. Fehlt die Erlaubnis „Unbekannte Apps installieren“, öffnet sich zuerst die passende
     * Einstellung; danach den Knopf noch einmal tippen. Ohne Berechtigung im Manifest: Datei an die Drive-App geben.
     */
    fun installieren(c: Context, f: Fund): String? {
        if (!kannSelbst(c)) {
            val i = Intent(Intent.ACTION_VIEW).setDataAndType(f.uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching { c.startActivity(i); null }.getOrElse { "Bitte die Datei ${f.name} in der Drive-App antippen." }
        }
        if (!c.packageManager.canRequestPackageInstalls()) {
            c.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return "Bitte „Aus dieser Quelle zulassen“ einschalten und dann noch einmal „Installieren“ tippen."
        }
        return try {
            val pi = c.packageManager.packageInstaller
            val p = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(c.packageName)
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
            val id = pi.createSession(p)
            pi.openSession(id).use { s ->
                (c.contentResolver.openInputStream(f.uri) ?: error("Datei nicht lesbar")).use { ein ->
                    s.openWrite(f.name, 0, -1).use { aus -> ein.copyTo(aus); s.fsync(aus) }
                }
                // MUTABLE: Android trägt Status und Bestätigungs-Intent in den Intent ein.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val rueck = PendingIntent.getBroadcast(c, STATUS_NR, Intent(c, AktualisierungEmpfaenger::class.java).setAction(STATUS).setPackage(c.packageName), flags)
                s.commit(rueck.intentSender)
            }
            null
        } catch (e: Exception) {
            "Installieren ging nicht (${e.message ?: e.javaClass.simpleName}). Die Datei liegt in der Drive-App im Update-Ordner."
        }
    }

    internal fun status(c: Context, i: Intent) {
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val bestaetigen = (if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else i.getParcelableExtra(Intent.EXTRA_INTENT))
                bestaetigen?.let { runCatching { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
            PackageInstaller.STATUS_SUCCESS -> Erinnerungen.weg(c, NR)
            else -> Unit
        }
    }
}

/** Täglicher Blick in den Ordner und Rückmeldung der Installation. */
class AktualisierungEmpfaenger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Aktualisierung.STATUS -> Aktualisierung.status(context, intent)
            Aktualisierung.PRUEFEN -> {
                val p = goAsync()
                Thread {
                    try { Aktualisierung.pruefenUndMelden(context) } finally { p.finish() }
                }.start()
            }
        }
    }
}

/** Hinweis oben in der Übersicht, wie „Neue Version da“ in der Web-App. */
@Composable
fun UpdateHinweis(f: Aktualisierung.Fund, onInstallieren: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Neue Version ${f.version} da", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Liegt im Update-Ordner", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Button(onClick = onInstallieren) { Text("Installieren") }
        }
    }
}

/** Einstellungen: Ordner wählen, täglich nachsehen an/aus, jetzt suchen. [onFund] gibt das Ergebnis an die Übersicht. */
@Composable
fun UpdateDialog(onFund: (Aktualisierung.Fund?) -> Unit, onSchliessen: () -> Unit) {
    val c = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var ordnerName by remember { mutableStateOf<String?>(null) }
    var an by remember { mutableStateOf(Aktualisierung.an(c)) }
    var meldung by remember { mutableStateOf<String?>(null) }
    var stand by remember { mutableStateOf(0) }

    fun suchen() {
        meldung = "Sehe nach …"
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Aktualisierung.suchen(c) } }
            r.onSuccess { onFund(it) }
            meldung = r.fold(
                { f -> if (f == null) "Du hast die neueste Version (${Aktualisierung.installiert(c)})." else "Neue Version ${f.version} gefunden, Hinweis steht in der Übersicht." },
                { "Ordner nicht lesbar. Bitte neu wählen (Freigabe oder Drive-App prüfen)." },
            )
        }
    }

    androidx.compose.runtime.LaunchedEffect(stand) { ordnerName = withContext(Dispatchers.IO) { Aktualisierung.ordnerName(c) } }
    val benachrichtigung = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val waehlen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { Aktualisierung.ordnerSetzen(c, uri) }
                .onSuccess {
                    stand++
                    if (Build.VERSION.SDK_INT >= 33 && Aktualisierung.kannMelden(c) && !Erinnerungen.erlaubt(c)) {
                        benachrichtigung.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    suchen()
                }
                .onFailure { meldung = "Diesen Ordner kann die App nicht dauerhaft lesen." }
        }
    }

    AlertDialog(
        onDismissRequest = onSchliessen,
        icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
        title = { Text("Updates") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Wähle einmal den Drive-Ordner, in dem die neuen APKs liegen. Die App liest dort nur die Dateinamen, " +
                        "sie hat kein Internet und schickt nichts weg. Installiert ist Version ${Aktualisierung.installiert(c)}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (Aktualisierung.ordnerLink(c).isNotBlank()) {
                    Button(onClick = { Aktualisierung.linkOeffnen(c) }) { Text("Update-Ordner öffnen") }
                    Text(
                        "Öffnet den Ordner in Drive. Dort die neueste APK antippen und installieren. " +
                            "Wer möchte, kann den Ordner unten zusätzlich wählen, dann meldet die App neue Versionen selbst.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    ordnerName?.let { "Ordner: $it" } ?: "Noch kein Ordner gewählt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { waehlen.launch(null) }) { Text(if (ordnerName == null) "Ordner wählen" else "Anderer Ordner") }
                    if (ordnerName != null) {
                        TextButton(onClick = { Aktualisierung.ordnerWeg(c); onFund(null); stand++; meldung = null }) { Text("Entfernen") }
                    }
                }
                if (ordnerName != null) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Täglich nachsehen und melden", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = an, onCheckedChange = { an = it; Aktualisierung.einschalten(c, it) })
                    }
                    OutlinedButton(onClick = { suchen() }) { Text("Jetzt nachsehen") }
                }
                meldung?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onSchliessen) { Text("Fertig") } },
    )
}
