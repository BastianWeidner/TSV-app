package de.dachapp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Pool
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Symbol, Farbe und Infotext einer Unter-App für die Übersicht. */
data class ModulInfo(val icon: ImageVector, val farbe: Color, val info: String)

/**
 * Übersichts-Angaben je Unter-App-ID. Die Schwimm-Module stehen hier (beide Varianten),
 * alle übrigen in VariantenInfos der Variante "alle", damit "schwimmen" nichts davon enthält.
 */
object ModulInfos {
    private val schwimmen = mapOf(
        "schwimmkurs" to ModulInfo(
            Icons.Filled.Pool, Color(0xFF1E88E5),
            "Schwimmkurse für Kinder verwalten. Anmeldelisten aus Excel oder CSV übernehmen (Spalten werden erkannt und " +
                "lassen sich umstellen), Kinder passend auf Kurse verteilen, Abzeichen festhalten und Einladungen per " +
                "Mail verschicken. Alles bleibt auf dem Gerät.",
        ),
        "schwimmgruppen" to ModulInfo(
            Icons.Filled.Groups, Color(0xFF00897B),
            "Trainingsgruppen der Abteilung Schwimmen. Mitgliederliste aus Excel oder CSV übernehmen, Schwimmer in " +
                "Gruppen einteilen, Anwesenheit, Zeiten und Erfolge festhalten. Auf dem Tablet mit Gruppen als Spalten.",
        ),
        "schwimmleitung" to ModulInfo(
            Icons.Filled.AdminPanelSettings, Color(0xFF3949AB),
            "Werkzeug für die Abteilungsleitung. Ohne PIN können Trainer in der Halle Defekte, Vorkommnisse und Belege " +
                "melden. Mit Leitungs-PIN: Kennzahlen und Leitungsbericht, Protokolle, Aufgaben, Material, Belege und " +
                "Scheine. Mehrere Geräte gleichen sich über eine Datei in Google Drive ab.",
        ),
    )

    private val alle: Map<String, ModulInfo> = schwimmen + VariantenInfos.eintraege

    fun zu(id: String, description: String): ModulInfo =
        alle[id] ?: ModulInfo(Icons.Filled.Apps, Color(0xFF757575), description)
}
