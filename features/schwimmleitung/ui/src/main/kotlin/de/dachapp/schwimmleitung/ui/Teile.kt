package de.dachapp.schwimmleitung.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.dachapp.module.Eingabe
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

val DATUM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
val UHRZEIT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
val DATUM_KURZ: DateTimeFormatter = DateTimeFormatter.ofPattern("EE dd.MM.", java.util.Locale.GERMAN)

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
