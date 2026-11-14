package de.dachapp.vereinsmeisterschaft.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dachapp.module.Header
import de.dachapp.module.MessageDialog
import de.dachapp.module.SubApp
import de.dachapp.vereinsmeisterschaft.logic.ScanTreffer
import java.time.LocalDate

/** Navigation innerhalb der Unter-App. */
sealed interface Ansicht {
    data object Haupt : Ansicht
    data class Person(val id: Long?) : Ansicht
    data object Mitgliederwahl : Ansicht
    data class Erfassen(val lauf: Int) : Ansicht
    data class ScanPruefen(val treffer: List<ScanTreffer>, val foto: String) : Ansicht
    data object Regeln : Ansicht
    data object Veranstaltungen : Ansicht
    data object Staffeln : Ansicht
}

enum class Reiter(val text: String) { TEILNEHMER("Teilnehmer"), LAEUFE("Läufe"), ERGEBNISSE("Ergebnisse"), MEHR("Mehr") }

object VereinsmeisterschaftSubApp : SubApp {
    override val id = "vereinsmeisterschaft"
    override val title = "Vereinsmeisterschaft"
    override val description = "Vereinsmeisterschaft auswerten: Meldungen, Läufe, Zeiten, Urkunden"

    override fun heute(context: Context): String? {
        val v = VmStore.lesenOhneMerken(context)?.aktuelle ?: return null
        if (v.datum != LocalDate.now().toString()) return null
        val (starts, erfasst) = v.fortschritt()
        return "Vereinsmeisterschaft heute · $erfasst/$starts Zeiten"
    }

    @Composable
    override fun Content(onExit: () -> Unit) {
        val context = LocalContext.current
        LaunchedEffect(Unit) { VmStore.laden(context.applicationContext) }
        val daten by VmStore.daten.collectAsStateWithLifecycle()
        var ansicht by remember { mutableStateOf<Ansicht>(Ansicht.Haupt) }
        var reiter by rememberSaveable { mutableStateOf(Reiter.TEILNEHMER) }
        var meldung by remember { mutableStateOf(VmStore.ladefehler) }
        val zurueck = { ansicht = Ansicht.Haupt }

        BackHandler { if (ansicht == Ansicht.Haupt) onExit() else zurueck() }
        MessageDialog(meldung) { meldung = null; VmStore.fehlerGesehen() }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val v = daten.aktuelle
            if (v == null) {
                ErsteVeranstaltung(onExit)
                return@Surface
            }
            when (val a = ansicht) {
                Ansicht.Haupt -> Column(Modifier.fillMaxSize()) {
                    Header(v.titel, onExit)
                    TabRow(selectedTabIndex = reiter.ordinal) {
                        Reiter.entries.forEach { r ->
                            Tab(selected = r == reiter, onClick = { reiter = r }, text = { Text(r.text, maxLines = 1) })
                        }
                    }
                    when (reiter) {
                        Reiter.TEILNEHMER -> TeilnehmerListe(v, { ansicht = it }, { meldung = it })
                        Reiter.LAEUFE -> LaeufeListe(v, { ansicht = it }, { meldung = it })
                        Reiter.ERGEBNISSE -> ErgebnisseSeite(v, daten.logo) { meldung = it }
                        Reiter.MEHR -> MehrSeite(v, daten, { ansicht = it }, { meldung = it })
                    }
                }
                is Ansicht.Person -> PersonBearbeiten(v, a.id, zurueck)
                Ansicht.Mitgliederwahl -> MitgliederWahl(v, zurueck) { meldung = it }
                is Ansicht.Erfassen -> LaufErfassen(v, a.lauf, { ansicht = Ansicht.Erfassen(it) }, zurueck)
                is Ansicht.ScanPruefen -> ScanPruefen(v, a.treffer, a.foto, zurueck) { meldung = it }
                Ansicht.Regeln -> RegelnSeite(v, zurueck)
                Ansicht.Veranstaltungen -> VeranstaltungenSeite(daten, zurueck)
                Ansicht.Staffeln -> StaffelSeite(v, zurueck)
            }
        }
    }
}
