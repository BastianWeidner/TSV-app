package de.dachapp.schwimmkurs.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    if (text.isBlank()) null else de.dachapp.schwimmkurs.logic.KinderImport.datum(text)

/** GiroCode zum Vorzeigen, z. B. für Eltern in der Halle; immer schwarz auf weiß, damit Kameras ihn lesen. */
@Composable
fun GiroCodeBild(inhalt: String, modifier: Modifier = Modifier) {
    val m = remember(inhalt) { de.dachapp.schwimmkurs.logic.GiroCode.matrix(inhalt) }
    androidx.compose.foundation.Canvas(
        modifier.size(180.dp).background(androidx.compose.ui.graphics.Color.White).padding(12.dp),
    ) {
        val modul = size.minDimension / m.size
        for (y in m.indices) for (x in m[y].indices) if (m[y][x]) {
            drawRect(
                androidx.compose.ui.graphics.Color.Black,
                topLeft = androidx.compose.ui.geometry.Offset(x * modul, y * modul),
                size = androidx.compose.ui.geometry.Size(modul + 0.5f, modul + 0.5f),
            )
        }
    }
}
