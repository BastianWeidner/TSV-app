package de.dachapp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.dachapp.module.SubApp

/**
 * Freiwilliges Feedback (Wunsch Bastian 2026-11-19): öffnet nur das Mailprogramm mit einer
 * vorausgefüllten Mail. Kein Tracking, keine Netzwerkaufrufe, keine Berechtigung, nichts wird
 * automatisch verschickt. Persönliche Daten werden nie angehängt; Gerät und Android-Version
 * nur, wenn der Haken gesetzt bleibt, und sie stehen sichtbar im Text.
 */
object Feedback {
    const val ADRESSE = "bastian.weidner.bw+feedback@gmail.com"

    /** „Feedback Gym · Food & Boost 1.26.1“, ohne Unter-App „Feedback Gym 1.26.1“. */
    fun betreff(appName: String, unterApp: String?, version: String?): String =
        listOfNotNull("Feedback $appName", unterApp).joinToString(" · ") + (version?.let { " $it" } ?: "")

    fun text(appName: String, unterApp: String?, version: String?, geraet: String?): String = buildString {
        append("Hallo Bastian,\n\n\n\n")
        append("---\n")
        append("App: $appName")
        if (unterApp != null) append(" · $unterApp")
        if (version != null) append(", Version $version")
        append('\n')
        if (geraet != null) append("Gerät: $geraet\n")
    }

    /** Nur Hersteller, Modell und Android-Version, keine Kennungen. */
    fun geraet(): String = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    /** mailto-Link; Betreff und Text stehen zusätzlich als Extras drin, weil manche Mail-Apps nur eins davon lesen. */
    fun oeffnen(context: Context, betreff: String, text: String) {
        val uri = Uri.parse("mailto:$ADRESSE?subject=${Uri.encode(betreff)}&body=${Uri.encode(text)}")
        val intent = Intent(Intent.ACTION_SENDTO, uri)
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(ADRESSE))
            .putExtra(Intent.EXTRA_SUBJECT, betreff)
            .putExtra(Intent.EXTRA_TEXT, text)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Keine Mail-App gefunden. Feedback gern an $ADRESSE", Toast.LENGTH_LONG).show()
        }
    }
}

/** Kleiner Knopf für die Fußzeile der Übersicht. */
@Composable
fun FeedbackKnopf(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Outlined.Email, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("Feedback geben", modifier = Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge)
    }
}

/** Fragt, worum es geht (ganze App oder eine Unter-App), und öffnet dann die Mail. */
@Composable
fun FeedbackDialog(appName: String, version: String?, module: List<SubApp>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var mitGeraet by rememberSaveable { mutableStateOf(true) }
    fun senden(unterApp: String?) {
        Feedback.oeffnen(
            context,
            Feedback.betreff(appName, unterApp, version),
            Feedback.text(appName, unterApp, version, if (mitGeraet) Feedback.geraet() else null),
        )
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Email, contentDescription = null) },
        title = { Text("Feedback geben") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Öffnet deine Mail-App mit einer vorbereiteten Mail an Bastian. Du siehst alles vor dem Absenden, " +
                        "die App selbst schickt nichts.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Worum geht es?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = { senden(null) }) { Text("Die App allgemein") }
                if (module.size > 1) module.forEach { m ->
                    TextButton(onClick = { senden(m.title) }) { Text(m.title) }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { mitGeraet = !mitGeraet },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = mitGeraet, onCheckedChange = { mitGeraet = it })
                    Text("Handymodell und Android-Version mitschicken", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
