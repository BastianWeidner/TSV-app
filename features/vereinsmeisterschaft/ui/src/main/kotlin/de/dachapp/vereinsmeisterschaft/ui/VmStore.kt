package de.dachapp.vereinsmeisterschaft.ui

import android.content.Context
import androidx.core.content.FileProvider
import de.dachapp.module.Speicher
import de.dachapp.vereinsmeisterschaft.logic.Daten
import de.dachapp.vereinsmeisterschaft.logic.Datei
import de.dachapp.vereinsmeisterschaft.logic.Veranstaltung
import de.dachapp.vereinsmeisterschaft.logic.Vorgaben
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.time.LocalDate

/** Eigene Unterklasse, damit der Manifest-Merger sie neben den Providern anderer Module akzeptiert. */
class VmDateiProvider : FileProvider()

/** Stand aller Veranstaltungen, gespeichert in filesDir/vereinsmeisterschaft.json. */
object VmStore {
    const val DATEI = "vereinsmeisterschaft.json"

    private val _daten = MutableStateFlow(Daten())
    val daten: StateFlow<Daten> = _daten.asStateFlow()
    private var speicher: Speicher? = null
    var ladefehler: String? = null
        private set

    fun laden(context: Context) {
        if (speicher != null) return
        val s = Speicher(context, DATEI)
        speicher = s
        val text = s.lesen()
        _daten.value = if (text == null) Daten() else try {
            Datei.lesen(text)
        } catch (e: Exception) {
            s.beiseitelegen()
            ladefehler = "Die gespeicherten Daten waren nicht lesbar und wurden beiseitegelegt."
            Daten()
        }
    }

    /** Für den Hintergrund (Heute-Zeile): nur lesen, nichts merken. */
    fun lesenOhneMerken(context: Context): Daten? = runCatching {
        File(context.filesDir, DATEI).takeIf { it.exists() }?.readText()?.let { Datei.lesen(it) }
    }.getOrNull()

    fun fehlerGesehen() { ladefehler = null }

    private fun speichern(d: Daten) { speicher?.schreiben(Datei.schreiben(d)) }

    fun setzen(f: (Daten) -> Daten) {
        _daten.update(f)
        speichern(_daten.value)
    }

    /** Ändert die aktuelle Veranstaltung. */
    fun aendern(f: (Veranstaltung) -> Veranstaltung) = setzen { d ->
        val akt = d.aktuelle ?: return@setzen d
        d.copy(veranstaltungen = d.veranstaltungen.map { if (it.id == akt.id) f(it) else it })
    }

    fun neu(jahr: Int = LocalDate.now().year, ausVorjahr: Veranstaltung? = null, teilnehmerMit: Boolean = false) = setzen { d ->
        val id = d.naechsteId
        val v = if (ausVorjahr != null) Vorgaben.folgejahr(ausVorjahr, id, jahr, teilnehmerMit) else Vorgaben.veranstaltung(id, jahr)
        d.copy(veranstaltungen = d.veranstaltungen + v, aktiv = id, naechsteId = id + 1)
    }

    fun waehlen(id: Long) = setzen { it.copy(aktiv = id) }

    fun loeschen(id: Long) = setzen { d ->
        val rest = d.veranstaltungen.filterNot { it.id == id }
        d.copy(veranstaltungen = rest, aktiv = if (d.aktiv == id) rest.lastOrNull()?.id else d.aktiv)
    }
}
