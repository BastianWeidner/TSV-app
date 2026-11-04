package de.dachapp.module

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.time.LocalDate

/**
 * Gemeinsame Personendaten für alle Unter-Apps (Wunsch Bastian 2026-11-04): Gewicht, Größe, Geburtsjahr, Geschlecht
 * und Körperfettanteil werden einmal in der Übersicht gepflegt, Food & Boost (Kalorien, Alkohol) und Krafttraining
 * (Körpergewicht-Übungen, Verbrauch) lesen sie beim Öffnen. Eine kleine Textdatei „schluessel=wert“ im App-Speicher;
 * ein fehlender Wert heißt „nicht angegeben“, dann nimmt jede Unter-App ihren eigenen.
 */
data class Personendaten(
    val gewichtKg: Double? = null,
    val groesseCm: Double? = null,
    val geburtsjahr: Int? = null,
    /** "m" oder "w". */
    val geschlecht: String? = null,
    val kfaProzent: Double? = null,
) {
    val leer: Boolean get() = gewichtKg == null && groesseCm == null && geburtsjahr == null && geschlecht == null && kfaProzent == null

    /** Kurz für Anzeigen in den Unter-Apps, z. B. „82 kg · 194 cm · männlich · Jahrgang 2003“. */
    fun kurz(): String = listOfNotNull(
        gewichtKg?.let { "${zahl(it)} kg" },
        groesseCm?.let { "${zahl(it)} cm" },
        geschlecht?.let { if (it == "w") "weiblich" else "männlich" },
        geburtsjahr?.let { "Jahrgang $it" },
        kfaProzent?.let { "KFA ${zahl(it)} %" },
    ).joinToString(" · ").ifEmpty { "noch nichts eingetragen" }

    companion object {
        private const val DATEI = "personendaten.txt"
        private val sperre = Any()
        private fun zahl(d: Double) = if (d == Math.rint(d)) d.toLong().toString() else d.toString().replace('.', ',')
        private fun datei(context: Context) = File(context.applicationContext.filesDir, DATEI)

        /** null, solange noch nie etwas gespeichert wurde. */
        fun lesen(context: Context): Personendaten? = synchronized(sperre) {
            val f = datei(context)
            if (!f.exists()) null else runCatching { aus(f.readText()) }.getOrNull()
        }

        fun schreiben(context: Context, p: Personendaten) = synchronized(sperre) {
            val f = datei(context)
            val tmp = File(f.parentFile, "$DATEI.tmp")
            tmp.writeText(text(p))
            tmp.renameTo(f)
        }

        fun text(p: Personendaten): String = buildString {
            p.gewichtKg?.let { append("gewicht=$it\n") }
            p.groesseCm?.let { append("groesse=$it\n") }
            p.geburtsjahr?.let { append("geburtsjahr=$it\n") }
            p.geschlecht?.let { append("geschlecht=$it\n") }
            p.kfaProzent?.let { append("kfa=$it\n") }
        }

        fun aus(text: String): Personendaten {
            val m = text.lineSequence().mapNotNull { z -> z.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()
            return Personendaten(
                gewichtKg = m["gewicht"]?.toDoubleOrNull()?.takeIf { it > 0 },
                groesseCm = m["groesse"]?.toDoubleOrNull()?.takeIf { it > 0 },
                geburtsjahr = m["geburtsjahr"]?.toIntOrNull()?.takeIf { it in 1900..2100 },
                geschlecht = m["geschlecht"]?.takeIf { it == "m" || it == "w" },
                kfaProzent = m["kfa"]?.toDoubleOrNull()?.takeIf { it > 0 },
            )
        }

        /** Nur fehlende Werte aus [vorschlag] ergänzen (für den ersten Start: Daten einer Unter-App übernehmen). */
        fun ergaenzen(context: Context, vorschlag: Personendaten) = synchronized(sperre) {
            val alt = lesen(context) ?: Personendaten()
            val neu = Personendaten(
                alt.gewichtKg ?: vorschlag.gewichtKg, alt.groesseCm ?: vorschlag.groesseCm, alt.geburtsjahr ?: vorschlag.geburtsjahr,
                alt.geschlecht ?: vorschlag.geschlecht, alt.kfaProzent ?: vorschlag.kfaProzent,
            )
            if (neu != alt || !datei(context).exists()) schreiben(context, neu)
        }
    }
}

/** Seite „Profil“ in der Übersicht: Personendaten für alle Unter-Apps. */
@Composable
fun PersonendatenScreen(onBack: () -> Unit, nutzer: String) {
    val context = LocalContext.current
    var p by remember { mutableStateOf(Personendaten.lesen(context) ?: Personendaten()) }
    var gespeichert by remember { mutableStateOf(false) }
    fun aendern(neu: Personendaten) { p = neu; gespeichert = false }
    FormPage(
        "Profil",
        onBack,
        bottom = { BigButton(if (gespeichert) "Gespeichert ✓" else "Speichern", { Personendaten.schreiben(context, p); gespeichert = true }) },
    ) {
        Text(
            "Deine Körperdaten an einer Stelle. Sie gelten für $nutzer. Daten bleiben auf dem Gerät.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Abschnitt("Körper") {
            val fmt: (Double) -> String = { if (it == Math.rint(it)) it.toLong().toString() else it.toString().replace('.', ',') }
            ZahlEingabe("Gewicht (kg)", p.gewichtKg ?: 0.0, { aendern(p.copy(gewichtKg = it.takeIf { v -> v > 0 })) }, fmt, pflicht = true)
            ZahlEingabe("Größe (cm)", p.groesseCm ?: 0.0, { aendern(p.copy(groesseCm = it.takeIf { v -> v > 0 })) }, fmt, pflicht = true)
            ZahlEingabe("Geburtsjahr", (p.geburtsjahr ?: 0).toDouble(), { aendern(p.copy(geburtsjahr = it.toInt().takeIf { v -> v in 1900..2100 })) }, { it.toLong().toString() },
                hilfe = p.geburtsjahr?.let { "Alter: ${LocalDate.now().year - it}" }, pflicht = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Auswahl("Geschlecht", listOf<String?>(null, "m", "w"), p.geschlecht, {
                    when (it) { "m" -> "männlich"; "w" -> "weiblich"; else -> "wählen" }
                }, { aendern(p.copy(geschlecht = it)) })
            }
            ZahlEingabe("Körperfettanteil (%)", p.kfaProzent ?: 0.0, { aendern(p.copy(kfaProzent = it.takeIf { v -> v > 0 })) }, fmt,
                hilfe = "Freiwillig. Nur für die Alkohol-Rechnung „aus Körperfettanteil“.")
        }
    }
}
