package de.dachapp.schwimmkurs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.dachapp.schwimmkurs.logic.KindFeld
import de.dachapp.schwimmkurs.logic.Zuordnung

/** Vorschau der Datei: Für jede Spalte wählen, wohin sie gehört. Die Vorschläge kommen aus den Überschriften. */
@Composable
fun ImportScreen(state: UiState, vm: SchwimmkursViewModel) {
    val imp = state.import ?: return
    val optionen = listOf<Zuordnung>(Zuordnung.Ignorieren) +
        KindFeld.entries.map { Zuordnung.Feld(it) } +
        state.bestand.einstellungen.abzeichen.map { Zuordnung.AbzeichenSpalte(it) }

    Page(
        title = "Import: ${imp.dateiname}",
        onBack = { vm.zurueck() },
        bottom = { BigButton("${imp.tabelle.zeilen.size} Zeilen übernehmen", vm::importAusfuehren) },
    ) {
        Text(
            "Prüfe, wohin jede Spalte gehört. Kinder, die es schon gibt (gleicher Name und Geburtsdatum), " +
                "werden ergänzt statt doppelt angelegt.",
            style = MaterialTheme.typography.bodyMedium,
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(imp.tabelle.kopf) { spalte, titel ->
                val beispiele = (0 until minOf(3, imp.tabelle.zeilen.size)).map { imp.tabelle.zelle(it, spalte) }.filter { it.isNotBlank() }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(titel.ifBlank { "Spalte ${spalte + 1}" }, style = MaterialTheme.typography.titleMedium)
                        Text(
                            beispiele.joinToString(" · ").ifBlank { "leer" },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Auswahl("Gehört zu", optionen, imp.zuordnung[spalte], { it.anzeige }, { vm.zuordnungAendern(spalte, it) })
                    }
                }
            }
        }
    }
}
