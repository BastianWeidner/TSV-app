package de.dachapp.schwimmgruppen.ui

import de.dachapp.module.Schwimmabzeichen
import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import de.dachapp.abgleich.Abgleich
import de.dachapp.module.GeteilteDatei
import de.dachapp.schwimmgruppen.logic.Teilen
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.dachapp.schwimmgruppen.logic.Ausschreibung
import de.dachapp.schwimmgruppen.logic.Bestand
import de.dachapp.schwimmgruppen.logic.Dsv
import de.dachapp.schwimmgruppen.logic.ErgebnisTreffer
import de.dachapp.schwimmgruppen.logic.Ergebnisliste
import de.dachapp.schwimmgruppen.logic.Meldender
import de.dachapp.schwimmgruppen.logic.Meldung
import de.dachapp.schwimmgruppen.logic.WettkampfDatei
import de.dachapp.schwimmgruppen.logic.WettkampfFehler
import de.dachapp.schwimmgruppen.logic.Wettkampfabgleich
import de.dachapp.schwimmgruppen.logic.WkDef
import de.dachapp.schwimmgruppen.logic.BestandCodec
import de.dachapp.schwimmgruppen.logic.Einheit
import de.dachapp.schwimmgruppen.logic.Erfolg
import de.dachapp.schwimmgruppen.logic.ErfolgArt
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
import de.dachapp.schwimmgruppen.logic.Stoppuhr
import de.dachapp.schwimmgruppen.logic.Zeit
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
import java.util.UUID

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
    data object Uhr : Ansicht
    data object Wettkampf : Ansicht
    /** Abzeichen-Prüfung, [gruppeId] null = alle aktiven Schwimmer. */
    data class Pruefung(val gruppeId: String?) : Ansicht
}

/** Offene Ausschreibung mit gewählten Starts und eingelesene Ergebnisse; bleibt bis zum Beenden der App (nicht gespeichert). */
data class WettkampfStand(
    val ausschreibung: Ausschreibung? = null,
    /** Mitglied → Wettkampfnummer → Meldezeit in Hundertsteln (0 = ohne Zeit). */
    val starts: Map<String, Map<Int, Int>> = emptyMap(),
    /** true = auch Schwimmer ohne „ja“ beim Wettkampfinteresse zeigen. */
    val alle: Boolean = false,
    val ergebnisse: Ergebnisliste? = null,
    val treffer: List<ErgebnisTreffer> = emptyList(),
) {
    val anzahlStarts: Int get() = starts.values.sumOf { it.size }
}

/** Abgleich mit anderen Trainern über eine geteilte Datei. */
data class TeilenStand(
    val verbunden: Boolean = false,
    /** Letzter erfolgreicher Abgleich, 0 = noch nie. */
    val letzter: Long = 0,
    val laeuft: Boolean = false,
    val fehler: String? = null,
    /** Abgleich ruht, bis eine andere Datei gewählt wird (fremde Datei, Zugriff weg). */
    val angehalten: Boolean = false,
)

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
    val wettkampf: WettkampfStand = WettkampfStand(),
    /** Nach dem Import: aktive Mitglieder, die in der Liste fehlen. */
    val nichtInListe: List<Mitglied> = emptyList(),
    val beschaeftigt: Boolean = false,
    /** Zählt hoch, wenn der Bestand von außen ersetzt wurde (Sicherung, Löschen); Formulare laden dann neu. */
    val ladeNummer: Int = 0,
    val teilen: TeilenStand = TeilenStand(),
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
    private val verbindungStore = VerbindungStore(app)
    private var verbindung = verbindungStore.load()
    private val datei = GeteilteDatei(app.contentResolver)
    private val abgleichSperre = Mutex()
    private var abgleichJob: Job? = null
    private var schleife: Job? = null

    init {
        val geladen = store.load()
        if (geladen == null) {
            store.beiseitelegen()
            _state.value = UiState(message = "Die gespeicherten Daten konnten nicht gelesen werden. Die Datei wurde aufgehoben; bitte eine Sicherung einspielen.")
        } else {
            _state.value = UiState(bestand = geladen)
        }
        _state.update { it.copy(teilen = TeilenStand(verbunden = verbindung.datei != null, letzter = verbindung.letzter)) }
    }

    private val s get() = _state.value
    val bestand get() = s.bestand

    private val _uhr = MutableStateFlow(UhrZustand())
    val uhr: StateFlow<UhrZustand> = _uhr.asStateFlow()

    fun uhrAendern(f: (UhrZustand) -> UhrZustand) = _uhr.update(f)

    /** Gestoppte Bahnen mit Schwimmer und Strecke als Trainingszeit von heute speichern. */
    fun uhrZeitenSpeichern() {
        val fertig = _uhr.value.bahnen.filter { it.fertig && it.schwimmerId != null && it.strecke.isNotEmpty() }
        aendern { b ->
            fertig.fold(b) { acc, bahn ->
                val m = acc.mitglied(bahn.schwimmerId!!) ?: return@fold acc
                val zeit = Zeit(UUID.randomUUID().toString(), bahn.strecke, Stoppuhr.hundertstel(bahn.endzeitMs ?: 0), LocalDate.now(), wettkampf = false, anlass = "Training")
                acc.mitgliedSetzen(m.copy(zeiten = m.zeiten + zeit))
            }
        }
        _uhr.update { z -> z.copy(stoppStart = null, bahnen = z.bahnen.map { it.copy(zwischen = emptyList(), fertig = false) }) }
        showMessage("${fertig.size} Zeiten gespeichert.")
    }

    /** Ändert den Bestand und speichert kurz danach (beim Tippen nicht bei jedem Buchstaben). */
    private fun aendern(f: (Bestand) -> Bestand) {
        _state.update { it.copy(bestand = f(it.bestand)) }
        speichernJob?.cancel()
        speichernJob = viewModelScope.launch {
            delay(400)
            store.save(s.bestand)
        }
        // Änderungen bald an die anderen Trainer weitergeben.
        if (verbindung.datei != null) {
            abgleichJob?.cancel()
            abgleichJob = viewModelScope.launch {
                delay(3000)
                abgleichen()
            }
        }
    }

    // ---------- Abgleich mit anderen Trainern ----------

    /** Regelmäßiger Abgleich, solange Schwimmgruppen im Vordergrund offen ist (siehe SchwimmgruppenSubApp). */
    fun sichtbar(an: Boolean) {
        schleife?.cancel()
        schleife = if (!an || verbindung.datei == null) null else viewModelScope.launch {
            while (true) {
                abgleichen()
                delay(60_000)
            }
        }
    }

    fun jetztAbgleichen() {
        viewModelScope.launch { abgleichen() }
    }

    /** Datei aus dem Dateidialog übernehmen: dauerhafte Berechtigung holen und sofort abgleichen. */
    fun dateiGewaehlt(uri: Uri) {
        val rechte = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            resolver.takePersistableUriPermission(uri, rechte)
        } catch (e: SecurityException) {
            showMessage("Für diese Datei gibt es keine dauerhafte Schreibberechtigung. Bitte eine Datei in Google Drive oder im Gerätespeicher wählen.")
            return
        }
        verbindung.datei?.let { alt -> if (alt != uri.toString()) runCatching { resolver.releasePersistableUriPermission(Uri.parse(alt), rechte) } }
        verbindungSetzen(Teilen.Verbindung(datei = uri.toString()))
        sichtbar(true)
    }

    /** Abgleich beenden; die Daten auf diesem Gerät bleiben. */
    fun dateiTrennen() {
        verbindung.datei?.let { alt ->
            runCatching { resolver.releasePersistableUriPermission(Uri.parse(alt), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
        schleife?.cancel()
        abgleichJob?.cancel()
        verbindungSetzen(Teilen.Verbindung())
    }

    private fun verbindungSetzen(v: Teilen.Verbindung) {
        verbindung = v
        viewModelScope.launch(Dispatchers.IO) { verbindungStore.save(v) }
        _state.update { it.copy(teilen = TeilenStand(verbunden = v.datei != null, letzter = v.letzter)) }
    }

    private suspend fun abgleichen() = abgleichSperre.withLock {
        val uriText = verbindung.datei ?: return@withLock
        if (s.teilen.angehalten) return@withLock
        val vorher = s.bestand
        val v = verbindung
        _state.update { it.copy(teilen = it.teilen.copy(laeuft = true)) }
        val r = try {
            withContext(Dispatchers.IO) {
                val uri = Uri.parse(uriText)
                val (text, laenge) = datei.lesen(uri)
                val r = Abgleich.abgleich(
                    Teilen.APP, Teilen.zerlegen(vorher), v.stand, Abgleich.decode(text, Teilen.APP),
                    System.currentTimeMillis(), Teilen.nurVorgaben(vorher),
                )
                if (r.schreiben) datei.schreiben(uri, Abgleich.encode(r.stand), laenge)
                r
            }
        } catch (e: IllegalArgumentException) {
            return@withLock teilenFehler(e.message ?: "Datei unlesbar.", angehalten = true)
        } catch (e: SecurityException) {
            return@withLock teilenFehler("Kein Zugriff mehr auf die Datei. Bitte unter „Mehr“ neu wählen.", angehalten = true)
        } catch (e: Exception) {
            // Kurz nicht erreichbar (z. B. Drive offline): beim nächsten Durchlauf wieder versuchen.
            return@withLock teilenFehler("Datei gerade nicht erreichbar, neuer Versuch folgt.", angehalten = false)
        }
        // Inzwischen wurde etwas geändert oder getrennt: nichts übernehmen, der nächste Durchlauf holt es nach.
        if (s.bestand != vorher || verbindung !== v) {
            _state.update { it.copy(teilen = it.teilen.copy(laeuft = false)) }
            return@withLock
        }
        if (r.uebernehmen) {
            _state.update { it.copy(bestand = Teilen.zusammensetzen(it.bestand, r.stand.werte())) }
            speichernJob?.cancel()
            store.save(s.bestand)
        }
        verbindungSetzen(v.copy(letzter = System.currentTimeMillis(), stand = r.stand))
    }

    private fun teilenFehler(text: String, angehalten: Boolean) =
        _state.update { it.copy(teilen = it.teilen.copy(laeuft = false, fehler = text, angehalten = angehalten)) }

    override fun onCleared() {
        speichernJob?.cancel()
        schleife?.cancel()
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
        _state.update {
            it.copy(
                stapel = it.stapel.dropLast(1),
                import = if (oben is Ansicht.Import) null else it.import,
            )
        }
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

    /** Teilleistung abhaken oder zurücknehmen; ein komplettes Abzeichen wird als Erfolg mit dem Tag der letzten Teilleistung eingetragen. */
    fun pruefungSetzen(mitgliedId: String, abzeichen: String, teil: String, datum: LocalDate?) = mitgliedAendern(mitgliedId) { m ->
        val a = Schwimmabzeichen.zu(abzeichen) ?: return@mitgliedAendern m
        val vorher = Schwimmabzeichen.bestandenAm(m.pruefung, a)
        val stand = Schwimmabzeichen.setzen(m.pruefung, a.name, teil, datum)
        val nachher = Schwimmabzeichen.bestandenAm(stand, a)
        var erfolge = m.erfolge
        if (vorher != null && nachher == null) erfolge = erfolge.filterNot { it.art == ErfolgArt.ABZEICHEN && it.text == a.name && it.datum == vorher }
        if (vorher == null && nachher != null) erfolge = erfolge + Erfolg(UUID.randomUUID().toString(), a.name, ErfolgArt.ABZEICHEN, nachher, "Abzeichen-Prüfung")
        m.copy(pruefung = stand, erfolge = erfolge)
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

    // ---------- Wettkämpfe ----------

    private fun wk(f: (WettkampfStand) -> WettkampfStand) = _state.update { it.copy(wettkampf = f(it.wettkampf)) }

    /** Ausschreibung (Meldung vorbereiten) oder Ergebnisliste (Zeiten übernehmen), DSV 7/8 oder LENEX. */
    fun wettkampfDateiLesen(uri: Uri) = imHintergrund {
        val bytes = lesen(uri).second
        val datei = try {
            withContext(Dispatchers.Default) { WettkampfDatei.lesen(bytes) }
        } catch (e: WettkampfFehler) {
            return@imHintergrund showMessage(e.message ?: "Datei nicht lesbar.")
        }
        when (datei) {
            is WettkampfDatei.Einladung -> {
                wk { it.copy(ausschreibung = datei.ausschreibung, starts = emptyMap()) }
                if (datei.ausschreibung.wettkaempfe.none { !it.staffel }) showMessage("Die Ausschreibung enthält keine Einzelwettkämpfe.")
            }
            is WettkampfDatei.Ergebnisse -> {
                val e = bestand.einstellungen
                val treffer = Wettkampfabgleich.zuordnen(datei.liste, bestand, e.vereinsnummer, e.verein)
                wk { it.copy(ergebnisse = datei.liste, treffer = treffer) }
                if (treffer.isEmpty()) {
                    showMessage("In der Ergebnisliste stehen keine Schwimmer des eigenen Vereins. Stimmen Vereinskennzahl und Vereinsname unter „Mehr“?")
                }
            }
        }
        if (s.ansicht != Ansicht.Wettkampf) oeffnen(Ansicht.Wettkampf)
    }

    fun ausschreibungSchliessen() = wk { it.copy(ausschreibung = null, starts = emptyMap()) }

    fun wettkampfAlle(an: Boolean) = wk { it.copy(alle = an) }

    /** Start an- oder abwählen; die Meldezeit ist die beste gespeicherte Zeit über die Strecke. */
    fun startUmschalten(mitgliedId: String, w: WkDef) = wk { st ->
        val m = bestand.mitglied(mitgliedId) ?: return@wk st
        val alt = st.starts[mitgliedId].orEmpty()
        val neu = if (w.nr in alt) alt - w.nr else alt + (w.nr to WettkampfDatei.meldezeit(m, w))
        st.copy(starts = if (neu.isEmpty()) st.starts - mitgliedId else st.starts + (mitgliedId to neu))
    }

    /** Was vor dem Speichern der Meldung noch fehlt; null = alles da. */
    fun meldungFehlt(): String? {
        val e = bestand.einstellungen
        val st = s.wettkampf
        val fehlt = buildList {
            if (e.vereinsnummer.isBlank()) add("DSV-Vereinskennzahl (unter „Mehr“)")
            if (e.meldenderName.isBlank() || e.meldenderEmail.isBlank()) add("meldende Person mit E-Mail (unter „Mehr“)")
            st.starts.keys.mapNotNull { bestand.mitglied(it) }.filter { Dsv.geschlecht(it.geschlecht).isEmpty() || it.jahrgang == null }
                .forEach { add("Geschlecht oder Geburtsdatum bei ${it.name}") }
        }
        return when {
            st.anzahlStarts == 0 -> "Noch kein Start gewählt."
            fehlt.isNotEmpty() -> "Für die Meldedatei fehlt noch:\n• " + fehlt.joinToString("\n• ")
            else -> null
        }
    }

    fun meldeDateiname(): String = s.wettkampf.ausschreibung?.let { Dsv.meldeDateiname(it, bestand.einstellungen.verein) } ?: "Meldung.DSV8"

    fun meldungSchreiben(uri: Uri) {
        val a = s.wettkampf.ausschreibung ?: return
        val e = bestand.einstellungen
        val wer = Meldender(e.verein, e.vereinsnummer, e.landesverband, e.meldenderName, e.meldenderEmail, e.meldenderTelefon)
        val meldungen = s.wettkampf.starts.entries
            .mapNotNull { (id, starts) -> bestand.mitglied(id)?.let { it to Meldung(id, starts) } }
            .sortedWith(compareBy({ it.first.nachname }, { it.first.vorname })).map { it.second }
        schreiben(uri, Dsv.meldeliste(a, wer, bestand, meldungen),
            "Meldedatei gespeichert: ${meldungen.size} Schwimmer, ${s.wettkampf.anzahlStarts} Starts. Jetzt per Mail an die Meldeadresse schicken oder im Meldeportal hochladen.")
    }

    /** Zeiten und Platz 1–3 aus der Ergebnisliste bei den zugeordneten Schwimmern speichern. */
    fun ergebnisseUebernehmen() {
        val liste = s.wettkampf.ergebnisse ?: return
        val neu = s.wettkampf.treffer.count { it.mitgliedId != null && !it.schonDa }
        aendern { Wettkampfabgleich.uebernehmen(it, s.wettkampf.treffer, liste) { UUID.randomUUID().toString() } }
        wk { it.copy(ergebnisse = null, treffer = emptyList()) }
        showMessage("$neu Ergebnisse übernommen.")
    }

    fun ergebnisseVerwerfen() = wk { it.copy(ergebnisse = null, treffer = emptyList()) }

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
        // Eine Sicherung ersetzt alles; geteilt würde sie sonst bei den anderen Trainern löschen. Daher trennen.
        val warVerbunden = verbindung.datei != null
        if (warVerbunden) dateiTrennen()
        aendern { neu }
        _state.update { it.copy(ladeNummer = it.ladeNummer + 1, auswahl = null) }
        if (warVerbunden) showMessage("Sicherung eingespielt: ${neu.mitglieder.size} Mitglieder. Der Abgleich mit den anderen Trainern wurde getrennt; bei Bedarf unter „Mehr“ neu verbinden.")
        else showMessage("Sicherung eingespielt: ${neu.mitglieder.size} Mitglieder, ${neu.gruppen.size} Gruppen.")
    }

    fun allesLoeschen() {
        // Sonst würde das Löschen bei allen Trainern ankommen.
        dateiTrennen()
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
