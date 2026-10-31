package de.dachapp.module

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val anzeige = DateTimeFormatter.ofPattern("EE, d.M.yyyy")

/**
 * Knopf mit Datum, öffnet den Kalender. [tag] ist ein Tag als epochDay (null = noch keins);
 * mit [leerText] lässt sich das Datum auch wieder entfernen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatumFeld(label: String, tag: Long?, onChange: (Long?) -> Unit, modifier: Modifier = Modifier, leerText: String? = null) {
    var offen by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { offen = true }, modifier = modifier.fillMaxWidth()) {
        Text("$label: " + (tag?.let { LocalDate.ofEpochDay(it).format(anzeige) } ?: (leerText ?: "wählen")))
    }
    if (offen) {
        val st = rememberDatePickerState(initialSelectedDateMillis = (tag ?: LocalDate.now().toEpochDay()) * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { offen = false },
            confirmButton = {
                TextButton(onClick = {
                    offen = false
                    st.selectedDateMillis?.let { onChange(Math.floorDiv(it, 86_400_000L)) }
                }) { Text("OK") }
            },
            dismissButton = {
                if (leerText != null) TextButton(onClick = { offen = false; onChange(null) }) { Text("Entfernen") }
                TextButton(onClick = { offen = false }) { Text("Abbrechen") }
            },
        ) { DatePicker(st) }
    }
}
