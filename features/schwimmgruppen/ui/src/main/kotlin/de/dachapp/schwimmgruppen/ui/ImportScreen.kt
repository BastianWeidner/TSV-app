package de.dachapp.schwimmgruppen.ui

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
import de.dachapp.module.Auswahl
import de.dachapp.module.BigButton
import de.dachapp.module.Page
import de.dachapp.schwimmgruppen.logic.MitgliedFeld
import de.dachapp.schwimmgruppen.logic.Zuordnung

/** Vorschau der Mitgliederliste: Für jede Spalte wählen, wohin sie gehört. Die Vorschläge kommen aus den Überschriften. */
@Composable
fun ImportScreen(state: UiState, vm: SchwimmgruppenViewModel) {
    val imp = state.import ?: return
    val optionen = listOf<Zuordnung>(null) + MitgliedFeld.entries
    val filter = state.bestand.einstellungen.abteilungFilter

    Page(
        title = "Import: ${imp.dateiname}",
        onBack = { vm.zurueck() },
        bottom = { BigButton("${imp.tabelle.zeilen.size} Zeilen übernehmen", vm::importAusfuehren) },
    ) {
        Text(
            "Prüfe, wohin jede Spalte gehört. Wer schon da ist (gleiche Mitgliedsnummer oder gleicher Name und " +
                "Geburtsdatum), wird aktualisiert; Gruppen, Zeiten und Erfolge bleiben." +
                if (MitgliedFeld.ABTEILUNG in imp.zuordnung && filter.isNotBlank()) " Nur Zeilen mit Abteilung „$filter…“ werden übernommen." else "",
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
                        Auswahl("Gehört zu", optionen, imp.zuordnung[spalte], { it?.anzeige ?: "nicht übernehmen" }, { vm.zuordnungAendern(spalte, it) })
                    }
                }
            }
        }
    }
}
