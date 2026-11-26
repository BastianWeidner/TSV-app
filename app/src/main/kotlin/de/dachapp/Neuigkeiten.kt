package de.dachapp

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.dachapp.module.SubApp
import de.dachapp.module.WasIstNeu
import de.dachapp.module.WasIstNeu.Punkt
import de.dachapp.module.WasIstNeu.Version

/**
 * „Was ist neu“ (Wunsch Bastian 2026-11-19): nach einem Update einmal die Änderungen zeigen, danach jederzeit
 * über „Was ist neu“ unten in der Übersicht. Strikt lokal: gemerkt wird nur der zuletzt gesehene versionCode
 * in den SharedPreferences, keine Berechtigung, kein Netz. Logik und Filter siehe core/module-api WasIstNeu.kt.
 *
 * Neue Version eintragen: oben in [LISTE] einen Eintrag mit dem neuen versionCode aus app/build.gradle.kts.
 * Punkte, die nur eine Variante betreffen, bekommen `varianten = setOf("dach-app" | "schwimmen" | "gym" | "ela")`
 * (Gym-Punkte meist auch für „ela“, die Ela-Variante hat alle Gym-Module plus Wartung),
 * Punkte einer Unter-App `module = setOf("<id>")`, dann sieht die Gym-App z. B. keine Café-Punkte.
 * Kurz und in Alltagssprache schreiben, die Leser sind Tester und Freunde.
 */
object Neuigkeiten {
    private const val DACH = "dach-app"

    val LISTE: List<Version> = listOf(
        Version(
            60, "1.30.0",
            listOf(
                Punkt(
                    "Brennholz, Feuer: Reagierst du nicht auf die Nachlegen-Erinnerung, kommt nach 5 Minuten eine zweite. " +
                        "Kommt dann 10 Minuten lang wieder nichts, gilt das Feuer als aus und die Erinnerungen hören auf. " +
                        "Brennt es doch noch, setzt „Brennt noch“ es fort. Abschalten kannst du das unter „Zeiten“.",
                    module = setOf("brennholz"),
                ),
                Punkt(
                    "Brennholz, Feuer: Neues Nachlegeprotokoll unten im Reiter. Es zeigt nach Tagen, wann angezündet, " +
                        "nachgelegt (mit Scheiten oder Litern), erinnert und gelöscht wurde.",
                    module = setOf("brennholz"),
                ),
            ),
        ),
        Version(
            59, "1.29.0",
            listOf(
                Punkt(
                    "Krafttraining: Wenn du im Training eine Übung öffnest oder hinzufügst und zurückgehst, " +
                        "bleibst du an derselben Stelle in der Liste, statt wieder ganz oben zu landen.",
                    module = setOf("krafttraining"),
                ),
                Punkt(
                    "Food & Boost: Tipp auf einen Nährwert (Kalorien, Eiweiß, Kohlenhydrate, Fett, Vitamine und mehr) zeigt, " +
                        "woher er kommt: jede Mahlzeit und jedes Produkt des Tages mit Menge und Anteil in Prozent, die größte Quelle zuerst.",
                    module = setOf("tracker"),
                ),
                Punkt(
                    "Krafttraining: Während ein Training läuft, bleibt der Bildschirm an. Und wenn du aus Versehen eine Übung " +
                        "oder einen Satz entfernst, holt „Rückgängig“ ihn ein paar Sekunden lang zurück.",
                    module = setOf("krafttraining"),
                ),
                Punkt(
                    "Krafttraining: Bei Langhantel-Übungen zeigt „🏋 Scheiben“, welche Scheiben auf jede Seite gehören, " +
                        "mit wählbarer Stange (20, 15 oder 10 kg).",
                    module = setOf("krafttraining"),
                ),
                Punkt(
                    "Food & Boost: Uhrzeit eines Eintrags direkt wählen, „Nochmal“ trägt einen Eintrag jetzt noch einmal ein, " +
                        "deine ★-Favoriten stehen als Schnellknöpfe im Essen-Tab, und 📅 springt im Kalender zu jedem Tag.",
                    module = setOf("tracker"),
                ),
                Punkt(
                    "Motorsägen und Wartung: Suchfeld, sobald die Liste länger wird.",
                    module = setOf("motorsaegen", "wartung"),
                ),
            ),
        ),
        Version(
            58, "1.28.0",
            listOf(
                Punkt(
                    "Neu für dich: Wartung & Fristen. Trag wiederkehrende Termine ein (Schornsteinfeger, Rauchmelder, " +
                        "Reifenwechsel, TÜV und mehr). Die App rechnet aus, wann es wieder fällig ist, erinnert rechtzeitig " +
                        "und merkt sich Kosten und Beleg-Fotos. Deine bisherigen Gym-Daten sind alle noch da.",
                    varianten = setOf("ela"),
                ),
            ),
        ),
        Version(
            57, "1.27.2",
            listOf(
                Punkt(
                    "Motorsägen-Import führt jetzt zusammen: Tabelle exportieren, ergänzen, wieder importieren. " +
                        "Vorhandenes wird aktualisiert statt doppelt angelegt, leere Zellen lassen den bisherigen Wert stehen.",
                    module = setOf("motorsaegen"),
                ),
            ),
        ),
        Version(
            56, "1.27.1",
            listOf(
                Punkt(
                    "Motorsägen: Sägen, Schwerter, Ketten, Ersatzteile und mehr lassen sich jetzt als CSV oder Excel-Datei exportieren und einlesen (Import ergänzt nur). " +
                        "Dazu gibt es einen Katalog des ganzen Bestands als PDF, Ketten und Schwerter kannst du duplizieren, und im Lager merkt sich die Soon™-Box, was du dir noch besorgen willst.",
                    module = setOf("motorsaegen"),
                ),
            ),
        ),
        Version(
            55, "1.27.0",
            listOf(
                Punkt("Neu: „Was ist neu“. Nach einem Update siehst du hier einmal, was sich geändert hat. Später findest du es unten in der Übersicht."),
                Punkt("Feedback geben: Unten in der Übersicht öffnet ein Knopf eine vorbereitete Mail an Bastian. Du siehst alles vor dem Absenden, die App selbst schickt nichts."),
                Punkt(
                    "Neue Unter-App Motorsägen: Timer für jeden Einsatz, Wartung nach Stunden, Tankfüllungen oder Tagen mit Erinnerung, " +
                        "offene Baustellen je Säge. Schwerter und Ketten mit Teilung, Nut und Treibgliedern, Kette wechseln mit einem Tipp, " +
                        "Schärf-Liste, Gemisch und Kettenöl, Ersatzteile und Schutzausrüstung.",
                    module = setOf("motorsaegen"),
                ),
                Punkt(
                    "Updates: Über das Zahnrad und „Updates“ wählst du einmal den Update-Ordner. Liegt dort eine neuere Version, " +
                        "zeigt die App oben „Neue Version da“ und meldet sich etwa einmal am Tag. „Installieren“ startet die Installation direkt.",
                    varianten = setOf(DACH, "gym"),
                ),
                Punkt(
                    "Updates: Über das Zahnrad und „Updates“ wählst du einmal den Update-Ordner. Liegt dort eine neuere Version, " +
                        "zeigt die App oben „Neue Version da“. Der Knopf öffnet die Datei in der Drive-App zum Installieren.",
                    varianten = setOf("schwimmen"),
                ),
                Punkt(
                    "Profil: Gewicht, Größe und Geschlecht pflegst du an einer Stelle (👤 oben in der Übersicht). Hattest du dein Gewicht " +
                        "bisher nur im Training eingetragen, übernimmt die App es jetzt ins Profil, Training und Food & Boost rechnen mit denselben Werten.",
                    module = setOf("krafttraining", "tracker"),
                ),
                Punkt(
                    "„Update-Ordner öffnen“ unter Zahnrad → „Updates“ bringt dich direkt zu Bastians Ordner in Drive.",
                    varianten = setOf("gym"),
                ),
            ),
        ),
    )

    private const val PREFS = "dach_neuigkeiten"
    private const val GESEHEN = "gesehen_version_code"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun paket(c: Context) = runCatching { c.packageManager.getPackageInfo(c.packageName, 0) }.getOrNull()

    fun variante(c: Context) = Aktualisierung.variante(c)

    /** Alle Einträge dieser Variante (für „Was ist neu“ in der Übersicht), neueste zuerst. */
    fun alle(c: Context, module: List<SubApp>): List<Version> =
        WasIstNeu.filtern(LISTE, variante(c), module.map { it.id }.toSet())

    /** Beim Start: was einmal gezeigt werden soll. Leere Liste = nichts zeigen. Merkt sich nichts, das macht [gesehen]. */
    fun beimStart(c: Context, module: List<SubApp>): List<Version> {
        val p = paket(c) ?: return emptyList()
        val prefs = prefs(c)
        val gesehen = if (prefs.contains(GESEHEN)) prefs.getLong(GESEHEN, 0) else null
        val neu = p.firstInstallTime == p.lastUpdateTime
        val r = WasIstNeu.zuZeigen(LISTE, p.longVersionCode, gesehen, neu, variante(c), module.map { it.id }.toSet())
        // Frisch installiert oder nichts Passendes: gleich als gesehen merken, damit erst das nächste Update etwas zeigt.
        if (r.isEmpty()) gesehen(c)
        return r
    }

    /** Aktuelle Version als gesehen merken. */
    fun gesehen(c: Context) {
        val code = paket(c)?.longVersionCode ?: return
        prefs(c).edit().putLong(GESEHEN, code).apply()
    }
}

/** Kleiner Knopf für die Fußzeile der Übersicht, neben „Feedback geben“. */
@Composable
fun WasIstNeuKnopf(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Outlined.NewReleases, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("Was ist neu", modifier = Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge)
    }
}

/** Dialog mit den Änderungen, neueste Version zuerst. [nachUpdate] = automatisch nach einem Update geöffnet. */
@Composable
fun WasIstNeuDialog(versionen: List<Version>, nachUpdate: Boolean, onSchliessen: () -> Unit) {
    AlertDialog(
        onDismissRequest = onSchliessen,
        icon = { Icon(Icons.Outlined.NewReleases, contentDescription = null) },
        title = { Text(if (nachUpdate && versionen.isNotEmpty()) "Neu in Version ${versionen.first().name}" else "Was ist neu") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (versionen.isEmpty()) {
                    Text("Noch keine Einträge.", style = MaterialTheme.typography.bodyMedium)
                }
                versionen.forEachIndexed { i, v ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!nachUpdate || versionen.size > 1 || i > 0) {
                            Text("Version ${v.name}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        v.punkte.forEach { p ->
                            Row {
                                Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                                Text(p.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSchliessen) { Text(if (nachUpdate) "Alles klar" else "Schließen") } },
    )
}
