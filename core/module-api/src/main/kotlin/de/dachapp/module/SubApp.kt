package de.dachapp.module

import androidx.compose.runtime.Composable

/**
 * Vertrag zwischen Dach-App und Unter-App. Jede Unter-App liefert genau ein Objekt, das dieses Interface
 * implementiert, und wird damit in der Dach-App registriert. Unter-Apps kennen einander nicht.
 */
interface SubApp {
    /** Stabile, eindeutige Kennung, z. B. "werwolf". Wird zum Merken der geöffneten Unter-App genutzt. */
    val id: String

    /** Name in der Übersicht. */
    val title: String

    /** Ein Satz, wofür die Unter-App da ist. */
    val description: String

    /**
     * Oberfläche der Unter-App, bildschirmfüllend. [onExit] führt zurück zur Übersicht.
     * Die Unter-App darf die Zurück-Taste selbst abfangen; tut sie es nicht, schließt die Dach-App sie.
     */
    @Composable
    fun Content(onExit: () -> Unit)
}
