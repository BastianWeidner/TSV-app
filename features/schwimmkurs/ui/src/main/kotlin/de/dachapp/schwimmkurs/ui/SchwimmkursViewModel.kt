package de.dachapp.schwimmkurs.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.dachapp.schwimmkurs.logic.Bestand
import de.dachapp.schwimmkurs.logic.BestandCodec
import de.dachapp.schwimmkurs.logic.Einstellungen
import de.dachapp.schwimmkurs.logic.KindFeld
import de.dachapp.schwimmkurs.logic.KinderImport
import de.dachapp.schwimmkurs.logic.Kind
import de.dachapp.schwimmkurs.logic.Kurs
import de.dachapp.schwimmkurs.logic.Kursplaner
import de.dachapp.schwimmkurs.logic.Tabelle
import de.dachapp.schwimmkurs.logic.TabellenFehler
import de.dachapp.schwimmkurs.logic.TabellenLeser
import de.dachapp.schwimmkurs.logic.Vorschlag
import de.dachapp.schwimmkurs.logic.Zuordnung
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class Tab(val titel: String, val symbol: String) {
    KINDER("Kinder", "👧"),
    KURSE("Kurse", "🏊"),
    EINLADUNGEN("Einladungen", "✉"),
    EINSTELLUNGEN("Einstellungen", "⚙"),
}

/** Bildschirme über der Tab-Übersicht. */
sealed interface Ansicht {
    data class KindBearbeiten(val id: String?) : Ansicht
    data class KursBearbeiten(val id: String?) : Ansicht
    data class KursDetail(val id: String) : Ansicht
    data object Import : Ansicht
    data object Vorschlag : Ansicht
    data class EinladungVorschau(val familienSchluessel: String) : Ansicht
}

data class ImportStand(val dateiname: String, val tabelle: Tabelle, val zuordnung: List<Zuordnung>)

data class UiState(
    val bestand: Bestand = Bestand(),
    val tab: Tab = Tab.KINDER,
    /** Geöffnete Bildschirme, der letzte ist sichtbar. Leer = Tab-Übersicht. */
    val stapel: List<Ansicht> = emptyList(),
    val import: ImportStand? = null,
    val vorschlag: Vorschlag? = null,
    val beschaeftigt: Boolean = false,
    /** Zählt hoch, wenn der Bestand von außen ersetzt wurde (Sicherung, Löschen); Formulare laden dann neu. */
    val ladeNummer: Int = 0,
    val message: String? = null,
) {
    val ansicht: Ansicht? get() = stapel.lastOrNull()
}

class SchwimmkursViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SchwimmkursStore(app)
    private val resolver: ContentResolver = app.contentResolver
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var speichernJob: Job? = null

    init {
        val geladen = store.load()
        if (geladen == null) {
            store.beiseitelegen()
            _state.value = UiState(message = "Die gespeicherten Daten konnten nicht gelesen werden. Die Datei wurde aufgehoben; bitte eine Sicherung einspielen.")
        } else {
            _state.value = UiState(bestand = geladen)
        }
    }

    private val s get() = _state.value
    val bestand get() = s.bestand

    /** Ändert den Bestand und speichert kurz danach (beim Tippen nicht bei jedem Buchstaben). */
    private fun aendern(f: (Bestand) -> Bestand) {
        _state.update { it.copy(bestand = f(it.bestand)) }
        speichernJob?.cancel()
        speichernJob = viewModelScope.launch {
            delay(400)
            store.save(s.bestand)
        }
    }

    override fun onCleared() {
        speichernJob?.cancel()
        store.save(s.bestand)
        store.close()
    }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    // ---------- Navigation ----------

    fun tab(tab: Tab) = _state.update { it.copy(tab = tab, stapel = emptyList()) }
    fun oeffnen(a: Ansicht) = _state.update { it.copy(stapel = it.stapel + a) }

    /** false = nichts mehr zu schließen, die Dach-App darf die Unter-App verlassen. */
    fun zurueck(): Boolean {
        if (s.stapel.isEmpty()) return false
        val oben = s.stapel.last()
        _state.update {
            it.copy(
                stapel = it.stapel.dropLast(1),
                import = if (oben is Ansicht.Import) null else it.import,
                vorschlag = if (oben is Ansicht.Vorschlag) null else it.vorschlag,
            )
        }
        return true
    }

    // ---------- Kinder ----------

    fun kindSpeichern(kind: Kind, kursId: String?) {
        aendern { it.kindSetzen(kind).zuteilen(kind.id, kursId) }
        zurueck()
    }

    fun kindLoeschen(id: String) {
        aendern { it.kindEntfernen(id) }
        zurueck()
    }

    fun zuteilen(kindId: String, kursId: String?) = aendern { it.zuteilen(kindId, kursId) }

    // ---------- Kurse ----------

    fun kursSpeichern(kurs: Kurs) {
        aendern { it.kursSetzen(kurs) }
        zurueck()
    }

    fun kursLoeschen(id: String) {
        aendern { it.kursEntfernen(id) }
        _state.update { st -> st.copy(stapel = st.stapel.filterNot { it == Ansicht.KursDetail(id) || it == Ansicht.KursBearbeiten(id) }) }
    }

    fun planen() {
        if (bestand.kurse.isEmpty()) return showMessage("Bitte zuerst Kurse anlegen.")
        _state.update { it.copy(vorschlag = Kursplaner.vorschlagen(it.bestand), stapel = it.stapel + Ansicht.Vorschlag) }
    }

    fun vorschlagUebernehmen() {
        val v = s.vorschlag ?: return
        aendern { Kursplaner.uebernehmen(it, v) }
        zurueck()
        showMessage("${v.anzahl} Kinder eingeteilt." + if (v.warteliste.isNotEmpty()) " ${v.warteliste.size} stehen auf der Warteliste." else "")
    }

    // ---------- Import ----------

    fun importLesen(uri: Uri) = imHintergrund {
        val (name, bytes) = lesen(uri)
        val tabelle = try {
            withContext(Dispatchers.Default) { TabellenLeser.lesen(bytes) }
        } catch (e: TabellenFehler) {
            return@imHintergrund showMessage(e.message ?: "Datei nicht lesbar.")
        }
        val zuordnung = KinderImport.vorschlagen(tabelle.kopf, bestand.einstellungen.abzeichen)
        _state.update { it.copy(import = ImportStand(name, tabelle, zuordnung), stapel = it.stapel + Ansicht.Import) }
    }

    fun zuordnungAendern(spalte: Int, z: Zuordnung) = _state.update { st ->
        val imp = st.import ?: return@update st
        st.copy(import = imp.copy(zuordnung = imp.zuordnung.mapIndexed { i, alt -> if (i == spalte) z else alt }))
    }

    fun importAusfuehren() {
        val imp = s.import ?: return
        val zugeordnet = imp.zuordnung.filterIsInstance<Zuordnung.Feld>().map { it.feld }
        if (zugeordnet.none { it == KindFeld.VORNAME || it == KindFeld.NACHNAME || it == KindFeld.NAME }) {
            return showMessage("Bitte mindestens eine Spalte als Vorname, Nachname oder Name zuordnen.")
        }
        val e = KinderImport.importieren(imp.tabelle, imp.zuordnung, bestand.kinder, bestand.einstellungen.abzeichen)
        aendern { it.copy(kinder = e.kinder) }
        zurueck()
        val hinweise = if (e.hinweise.isEmpty()) "" else "\n\nHinweise:\n" + e.hinweise.take(20).joinToString("\n") +
            if (e.hinweise.size > 20) "\n… und ${e.hinweise.size - 20} weitere" else ""
        showMessage("${e.neu} Kinder neu angelegt, ${e.aktualisiert} vorhandene ergänzt.$hinweise")
    }

    // ---------- Einladungen ----------

    fun alsGeoeffnetMarkieren(familienSchluessel: String) = aendern {
        it.copy(versendet = it.versendet + (familienSchluessel to LocalDateTime.now().withNano(0).toString()))
    }

    fun versandZuruecksetzen(familienSchluessel: String) = aendern { it.copy(versendet = it.versendet - familienSchluessel) }

    // ---------- Einstellungen & Sicherung ----------

    fun einstellungen(e: Einstellungen) = aendern { it.copy(einstellungen = e) }

    fun sicherungSchreiben(uri: Uri) = imHintergrund {
        val text = BestandCodec.encode(bestand)
        withContext(Dispatchers.IO) {
            resolver.openOutputStream(uri, "wt")?.use { it.write(text.encodeToByteArray()) } ?: error("Datei lässt sich nicht schreiben.")
        }
        showMessage("Sicherung gespeichert: ${bestand.kinder.size} Kinder, ${bestand.kurse.size} Kurse.")
    }

    fun sicherungLesen(uri: Uri) = imHintergrund {
        val neu = try {
            BestandCodec.decode(lesen(uri).second.decodeToString())
        } catch (e: IllegalArgumentException) {
            return@imHintergrund showMessage(e.message ?: "Datei nicht lesbar.")
        }
        aendern { neu }
        _state.update { it.copy(ladeNummer = it.ladeNummer + 1) }
        showMessage("Sicherung eingespielt: ${neu.kinder.size} Kinder, ${neu.kurse.size} Kurse.")
    }

    /** Nimmt alle Kinder aus allen Kursen, die Kurse selbst bleiben. */
    fun einteilungLeeren() {
        aendern { it.copy(kurse = it.kurse.map { k -> k.copy(teilnehmer = emptyList()) }) }
        showMessage("Alle Kurse sind wieder leer. Die Kinder stehen wieder auf der Warteliste.")
    }

    fun allesLoeschen() {
        aendern { Bestand(einstellungen = it.einstellungen) }
        _state.update { it.copy(ladeNummer = it.ladeNummer + 1) }
    }

    /** Name der Sicherungsdatei mit Datum, z. B. schwimmkurs-2026-10-20.json. */
    fun sicherungsName(): String = "schwimmkurs-" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".json"

    private fun imHintergrund(block: suspend () -> Unit) {
        _state.update { it.copy(beschaeftigt = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                showMessage("Das hat nicht geklappt: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _state.update { it.copy(beschaeftigt = false) }
            }
        }
    }

    /**
     * Liest eine gewählte Datei. Google-Tabellen in Drive sind keine echten Dateien; die lassen sich
     * oft trotzdem als Excel abholen, wenn der Anbieter eine Umwandlung anbietet.
     */
    private suspend fun lesen(uri: Uri): Pair<String, ByteArray> = withContext(Dispatchers.IO) {
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            ?: run {
                val typ = resolver.getStreamTypes(uri, "*/*")?.firstOrNull { it == XLSX || it == "text/csv" || it.startsWith("text/") }
                    ?: error("Datei lässt sich nicht öffnen. Google-Tabellen bitte in Drive als .xlsx oder .csv herunterladen.")
                resolver.openTypedAssetFileDescriptor(uri, typ, null)?.createInputStream()?.use { it.readBytes() }
                    ?: error("Datei lässt sich nicht öffnen.")
            }
        name to bytes
    }

    companion object {
        const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}
