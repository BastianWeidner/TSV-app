package de.dachapp.module

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Einheitliche Markierung von Pflichtfeldern in allen Unter-Apps: Solange ein Pflichtfeld leer ist, sind Rahmen,
 * Beschriftung und Hinweis orange. Bewusst nicht Fehler-Rot, denn leer ist noch kein Fehler, nur „fehlt noch“.
 * Sobald etwas drinsteht, sieht das Feld aus wie jedes andere.
 */
object Pflichtfeld {
    /** Hinweistext unter einem leeren Pflichtfeld. */
    const val HINWEIS = "Pflichtfeld"

    /** Hinweis für Felder, von denen mindestens eines gefüllt sein muss (z. B. Vor- oder Nachname). */
    const val HINWEIS_EINES = "Pflichtfeld (eines davon)"

    /** Beschriftung mit Sternchen, damit Pflichtfelder auch gefüllt erkennbar bleiben. */
    fun label(text: String, pflicht: Boolean): String = if (pflicht) "$text *" else text

    /** Orange, passend zu hellem oder dunklem Design. */
    @Composable
    fun farbe(): Color =
        if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFFFB74D) else Color(0xFFC25E00)

    /** Farben für OutlinedTextField: orange, solange [leer], sonst Standard. */
    @Composable
    fun farben(leer: Boolean): TextFieldColors {
        if (!leer) return OutlinedTextFieldDefaults.colors()
        val f = farbe()
        return OutlinedTextFieldDefaults.colors(
            focusedBorderColor = f,
            unfocusedBorderColor = f,
            focusedLabelColor = f,
            unfocusedLabelColor = f,
            focusedSupportingTextColor = f,
            unfocusedSupportingTextColor = f,
            cursorColor = f,
        )
    }
}

/** Orange Hinweiszeile für Pflichtangaben ohne Textfeld (Auswahl, Tage, Datum), nur zeigen, solange sie fehlen. */
@Composable
fun PflichtHinweis(text: String = Pflichtfeld.HINWEIS, modifier: Modifier = Modifier) {
    Text(text, color = Pflichtfeld.farbe(), style = MaterialTheme.typography.bodySmall, modifier = modifier)
}
