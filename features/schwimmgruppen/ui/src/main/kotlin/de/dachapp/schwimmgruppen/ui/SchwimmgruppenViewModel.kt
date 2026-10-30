package de.dachapp.schwimmgruppen.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.dachapp.schwimmgruppen.logic.Bestand
import de.dachapp.schwimmgruppen.logic.BestandCodec
import de.dachapp.schwimmgruppen.logic.Einheit
import de.dachapp.schwimmgruppen.logic.Einstellungen
import de.dachapp.schwimmgruppen.logic.Gruppe
import de.dachapp.schwimmgruppen.logic.Kalender
import de.dachapp.schwimmgruppen.logic.Listen
import de.dachapp.schwimmgruppen.logic.Mitglied
import de.dachapp.schwimmgruppen.logic.MitgliedFeld
import de.dachapp.schwimmgruppen.logic.MitgliederImport
import de.dachapp.schwimmgruppen.logic.Tabelle
import de.dachapp.schwimmgruppen.logic.Trainer
import de.dachapp.schwimmgruppen.logic.TabellenFehler
import de.dachapp.schwimmgruppen.logic.TabellenLeser
import de.dachapp.schwimmgruppen.logic.Zuordnung
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class Tab(val titel: String, val symbol: String) {
    SCHWIMMER("Schwimmer", "🏊"),
    GRUPPEN("Gruppen", "👥"),
    TRAINING("Training", "📅"),
    MEHR("Mehr", "⚙"),
}

/** Bildschirme über der Tab-Übersicht. */
sealed interface Ansicht {
    data class Schwimmer(val id: String) : Ansicht
    data class SchwimmerBearbeiten(val id: String?) : Ansicht
    data class GruppeBearbeiten(val id: String?) : Ansicht
    /** [vorlage] ist die Einheit aus dem Kalender; gespeichert wird unter ihrem Schlüssel. */
    data class Training(val vorlage: Einheit) : Ansicht
    data object Halle : Ansicht
    data object Statistik : Ansicht
    data object Trainer : Ansicht
    data object Import : Ansicht
}

data class ImportStand(val dateiname: String, val tabelle: Tabelle, val zuordnung: List<Zuordnung>)

data class UiState(
    val bestand: Bestand = Bestand(),
    val tab: Tab = Tab.SCHWIMMER,
    /** Geöffnete Bildschirme, der letzte ist sichtbar. Leer = Tab-Übersicht. */
    val stapel: List<Ansicht> = emptyList(),
    /** Auf dem Tablet rechts angezeigter Schwimmer. */
    val auswahl: String? = null,
    /** Ein Tag der im Kalender gezeigten Woche. */
    val woche: LocalDate = LocalDate.now(),
    val import: ImportStand? = null,
    /** Nach dem Import: aktive Mitglieder, die in der Liste fehlen. */
    val nichtInListe: List<Mitglied> = emptyList(),
    val beschaeftigt: Boolean = false,
    /** Zählt hoch, wenn der Bestand von außen ersetzt wurde (Sicherung, Löschen); Formulare laden dann neu. */
    val ladeNummer: Int = 0,
    val message: String? = null,
) {
    val ansicht: Ansicht? get() = stapel.lastOrNull()
}

class SchwimmgruppenViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SchwimmgruppenStore(app)
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
    fun auswaehlen(id: String?) = _state.update { it.copy(auswahl = id) }

    /** false = nichts mehr zu schließen, die Dach-App darf die Unter-App verlassen. */
    fun zurueck(): Boolean {
        if (s.stapel.isEmpty()) return false
        val oben = s.stapel.last()
        _state.update { it.copy(stapel = it.stapel.dropLast(1), import = if (oben is Ansicht.Import) null else it.import) }
        return true
    }

    // ---------- Schwimmer ----------

    fun mitgliedSpeichern(m: Mitglied) {
        aendern { it.mitgliedSetzen(m) }
        zurueck()
    }

    fun mitgliedAendern(id: String, f: (Mitglied) -> Mitglied) = aendern { b -> b.mitglied(id)?.let { b.mitgliedSetzen(f(it)) } ?: b }

    fun mitgliedLoeschen(id: String) {
        aendern { it.mitgliedEntfernen(id) }
        _state.update { st ->
            st.copy(
                stapel = st.stapel.filterNot { it == Ansicht.Schwimmer(id) || it == Ansicht.SchwimmerBearbeiten(id) },
                auswahl = st.auswahl.takeUnless { it == id },
            )
        }
    }

    fun einteilen(mitgliedId: String, gruppeId: String, drin: Boolean) = aendern { it.einteilen(mitgliedId, gruppeId, drin) }

    // ---------- Gruppen ----------

    fun gruppeSpeichern(g: Gruppe) {
        aendern { it.gruppeSetzen(g) }
        zurueck()
    }

    fun gruppeAufteilen(id: String) {
        val alt = bestand.gruppe(id) ?: return
        aendern { it.gruppeAufteilen(id) }
        _state.update { st -> st.copy(stapel = st.stapel.filterNot { it == Ansicht.GruppeBearbeiten(id) }) }
        showMessage("„${alt.name}“ ist jetzt in ${alt.zeiten.size} Gruppen aufgeteilt, je Trainingstag eine. Die bisher eingeteilten Schwimmer stehen in allen davon; bitte unter „Gruppen“ anpassen.")
    }

    // ---------- Trainer ----------

    fun trainerSpeichern(t: Trainer) = aendern { it.trainerSetzen(t) }

    fun trainerLoeschen(id: String) = aendern { it.trainerEntfernen(id) }

    /** Trainer für eine einzelne Stunde an- oder abwählen. */
    fun trainerUmschalten(vorlage: Einheit, trainerId: String) =
        aendern { b -> b.einheitSetzen(b.trainerUmschalten(b.einheit(vorlage.schluessel) ?: vorlage, trainerId)) }

    /** „Vertreten durch“ für einen eingeteilten Trainer in einer Stunde; null nimmt die Vertretung zurück. */
    fun vertreten(vorlage: Einheit, trainerId: String, vertreterId: String?) =
        aendern { b -> b.einheitSetzen(b.vertreten(b.einheit(vorlage.schluessel) ?: vorlage, trainerId, vertreterId)) }

    /** Ändert eine Gruppe direkt, ohne den Bildschirm zu schließen (z. B. „nur zählen“ in der Halle). */
    fun gruppeAendern(g: Gruppe) = aendern { it.gruppeSetzen(g) }

    fun gruppeLoeschen(id: String) {
        aendern { it.gruppeEntfernen(id) }
        _state.update { st -> st.copy(stapel = st.stapel.filterNot { it == Ansicht.GruppeBearbeiten(id) }) }
    }

    // ---------- Training ----------

    fun woche(tag: LocalDate) = _state.update { it.copy(woche = tag) }

    /** Ändert die gespeicherte Fassung der Einheit (oder die Vorlage aus dem Plan, wenn noch nichts erfasst ist). */
    fun einheitAendern(vorlage: Einheit, f: (Einheit) -> Einheit) =
        aendern { b -> b.einheitSetzen(f(b.einheit(vorlage.schluessel) ?: vorlage)) }

    fun anwesend(vorlage: Einheit, mitgliedId: String) = einheitAendern(vorlage) { e ->
        e.copy(anwesend = if (mitgliedId in e.anwesend) e.anwesend - mitgliedId else e.anwesend + mitgliedId)
    }

    fun weitere(vorlage: Einheit, delta: Int) = einheitAendern(vorlage) { it.copy(weitere = (it.weitere + delta).coerceAtLeast(0)) }

    fun zusatztermin(e: Einheit) {
        if (bestand.einheit(e.schluessel) != null) return showMessage("Zu dieser Zeit gibt es für die Gruppe schon ein Training.")
        aendern { it.einheitSetzen(e.copy(zusatz = true)) }
        _state.update { it.copy(woche = e.datum) }
    }

    fun einheitLoeschen(e: Einheit) {
        aendern { it.einheitEntfernen(e.schluessel) }
        zurueck()
    }

    // ---------- Import ----------

    fun importLesen(uri: Uri) = imHintergrund {
        val (name, bytes) = lesen(uri)
        val tabelle = try {
            withContext(Dispatchers.Default) { TabellenLeser.lesen(bytes) }
        } catch (e: TabellenFehler) {
            return@imHintergrund showMessage(e.message ?: "Datei nicht lesbar.")
        }
        val zuordnung = MitgliederImport.vorschlagen(tabelle.kopf)
        _state.update { it.copy(import = ImportStand(name, tabelle, zuordnung), stapel = it.stapel + Ansicht.Import) }
    }

    fun zuordnungAendern(spalte: Int, z: Zuordnung) = _state.update { st ->
        val imp = st.import ?: return@update st
        st.copy(import = imp.copy(zuordnung = imp.zuordnung.mapIndexed { i, alt -> if (i == spalte) z else alt }))
    }

    fun importAusfuehren() {
        val imp = s.import ?: return
        if (imp.zuordnung.none { it == MitgliedFeld.VORNAME || it == MitgliedFeld.NACHNAME || it == MitgliedFeld.NAME }) {
            return showMessage("Bitte mindestens eine Spalte als Vorname, Nachname oder Name zuordnen.")
        }
        val e = MitgliederImport.importieren(imp.tabelle, imp.zuordnung, bestand)
        aendern { it.copy(mitglieder = e.mitglieder) }
        zurueck()
        val andere = if (e.andereAbteilung > 0) " ${e.andereAbteilung} Zeilen anderer Abteilungen übersprungen." else ""
        val hinweise = if (e.hinweise.isEmpty()) "" else "\n\nHinweise:\n" + e.hinweise.take(20).joinToString("\n") +
            if (e.hinweise.size > 20) "\n… und ${e.hinweise.size - 20} weitere" else ""
        _state.update { it.copy(nichtInListe = e.nichtInListe) }
        showMessage("${e.neu} Mitglieder neu angelegt, ${e.aktualisiert} vorhandene aktualisiert.$andere$hinweise")
    }

    /** Markiert die Mitglieder, die in der importierten Liste fehlten, als inaktiv. */
    fun fehlendeInaktiv() {
        val ids = s.nichtInListe.map { it.id }.toSet()
        aendern { b -> b.copy(mitglieder = b.mitglieder.map { if (it.id in ids) it.copy(aktiv = false) else it }) }
        _state.update { it.copy(nichtInListe = emptyList()) }
    }

    fun fehlendeBehalten() = _state.update { it.copy(nichtInListe = emptyList()) }

    // ---------- Dateien ----------

    fun einstellungen(e: Einstellungen) = aendern { it.copy(einstellungen = e) }

    fun anwesenheitSchreiben(uri: Uri, von: LocalDate, bis: LocalDate) = schreiben(uri, Listen.anwesenheit(bestand, von, bis), "Anwesenheitsliste gespeichert.")

    fun gruppenlisteSchreiben(uri: Uri, gruppeId: String) = schreiben(uri, Listen.gruppe(bestand, gruppeId), "Gruppenliste gespeichert.")

    fun sicherungSchreiben(uri: Uri) =
        schreiben(uri, BestandCodec.encode(bestand), "Sicherung gespeichert: ${bestand.mitglieder.size} Mitglieder, ${bestand.einheiten.size} erfasste Trainings.")

    private fun schreiben(uri: Uri, text: String, meldung: String) = imHintergrund {
        withContext(Dispatchers.IO) {
            resolver.openOutputStream(uri, "wt")?.use { it.write(text.encodeToByteArray()) } ?: error("Datei lässt sich nicht schreiben.")
        }
        showMessage(meldung)
    }

    fun sicherungLesen(uri: Uri) = imHintergrund {
        val neu = try {
            BestandCodec.decode(lesen(uri).second.decodeToString())
        } catch (e: IllegalArgumentException) {
            return@imHintergrund showMessage(e.message ?: "Datei nicht lesbar.")
        }
        aendern { neu }
        _state.update { it.copy(ladeNummer = it.ladeNummer + 1, auswahl = null) }
        showMessage("Sicherung eingespielt: ${neu.mitglieder.size} Mitglieder, ${neu.gruppen.size} Gruppen.")
    }

    fun allesLoeschen() {
        aendern { Bestand(einstellungen = it.einstellungen) }
        _state.update { it.copy(ladeNummer = it.ladeNummer + 1, auswahl = null) }
    }

    /** Dateiname mit Datum, z. B. schwimmgruppen-2026-10-20.json. */
    fun dateiname(art: String, endung: String): String = "$art-${LocalDate.now()}.$endung"

    /** Das Training, das gerade läuft oder heute als nächstes kommt. */
    fun jetzt(): Einheit? = Kalender.jetzt(bestand, java.time.LocalDateTime.now())

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
