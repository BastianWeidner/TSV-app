package de.dachapp.schwimmleitung.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.dachapp.module.Pflichtfeld
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

val DATUM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
val UHRZEIT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
val DATUM_KURZ: DateTimeFormatter = DateTimeFormatter.ofPattern("EE dd.MM.", java.util.Locale.GERMAN)

/** Kopfzeile mit Titel, optionalem Zurück und Aktionen rechts. */
@Composable
fun Header(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Zurück" }) {
                Text("‹", style = MaterialTheme.typography.headlineSmall)
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        actions()
    }
}

/** Seitenrahmen: Kopf, Inhalt, unten fest die Hauptknöpfe. */
@Composable
fun Page(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    bottom: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Header(title, onBack, actions)
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), content = content)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = bottom,
        )
    }
}

/** Wie [Page], der Inhalt scrollt als Ganzes (für Formulare). */
@Composable
fun FormPage(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    bottom: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) = Page(title, onBack, actions, bottom) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun Abschnitt(titel: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titel, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun Eingabe(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    typ: KeyboardType = KeyboardType.Text,
    mehrzeilig: Boolean = false,
    fehler: String? = null,
    hilfe: String? = null,
    pflicht: Boolean = false,
    pflichtFehlt: Boolean = pflicht && value.isBlank(),
    pflichtHinweis: String = Pflichtfeld.HINWEIS,
) {
    val leer = pflicht && pflichtFehlt && fehler == null
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(Pflichtfeld.label(label, pflicht)) },
        singleLine = !mehrzeilig,
        minLines = if (mehrzeilig) 3 else 1,
        isError = fehler != null,
        colors = Pflichtfeld.farben(leer),
        supportingText = (fehler ?: if (leer) listOfNotNull(pflichtHinweis, hilfe).joinToString(" · ") else hilfe)
            ?.let { t -> @Composable { Text(t) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = typ,
            capitalization = if (typ == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Auswahlknopf mit Aufklappliste. */
@Composable
fun <T> Auswahl(
    label: String,
    optionen: List<T>,
    gewaehlt: T,
    text: (T) -> String,
    onWahl: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var offen by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { offen = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${text(gewaehlt)}", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(" ▾")
        }
        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            optionen.forEach { o ->
                DropdownMenuItem(text = { Text(text(o)) }, onClick = { offen = false; onWahl(o) })
            }
        }
    }
}

/** Datumsfeld zum Tippen (TT.MM.JJJJ) mit Kalender-Knopf. [text] ist der Rohtext, ausgewertet wird beim Speichern. */
@Composable
fun DatumEingabe(label: String, text: String, onChange: (String) -> Unit, context: Context, fehler: String? = null, pflicht: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Eingabe(label, text, onChange, Modifier.weight(1f), KeyboardType.Number, fehler = fehler, hilfe = "TT.MM.JJJJ", pflicht = pflicht)
        TextButton(onClick = {
            val start = datumOderNull(text) ?: LocalDate.now()
            DatePickerDialog(context, { _, y, m, d -> onChange(LocalDate.of(y, m + 1, d).format(DATUM)) }, start.year, start.monthValue - 1, start.dayOfMonth).show()
        }) { Text("Kalender") }
    }
}

fun uhrzeitWaehlen(context: Context, start: LocalTime, onWahl: (LocalTime) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onWahl(LocalTime.of(h, m)) }, start.hour, start.minute, true).show()
}

fun datumWaehlen(context: Context, start: LocalDate, onWahl: (LocalDate) -> Unit) {
    DatePickerDialog(context, { _, y, m, d -> onWahl(LocalDate.of(y, m + 1, d)) }, start.year, start.monthValue - 1, start.dayOfMonth).show()
}

fun datumOderNull(text: String): LocalDate? =
    de.dachapp.schwimmleitung.logic.Format.datumLesen(text)

@Composable
fun MessageDialog(message: String?, onDismiss: () -> Unit) {
    if (message == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        text = { Text(message, modifier = Modifier.verticalScroll(rememberScrollState())) },
    )
}

@Composable
fun ConfirmDialog(text: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        text = { Text(text) },
    )
}

@Composable
fun Leer(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp),
    )
}
