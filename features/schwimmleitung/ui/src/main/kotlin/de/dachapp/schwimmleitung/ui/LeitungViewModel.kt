package de.dachapp.schwimmleitung.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.dachapp.schwimmleitung.logic.Abgleich
import de.dachapp.schwimmleitung.logic.Aufgabe
import de.dachapp.schwimmleitung.logic.AufgabeStatus
import de.dachapp.schwimmleitung.logic.Beleg
import de.dachapp.schwimmleitung.logic.Berichte
import de.dachapp.schwimmleitung.logic.Dokument
import de.dachapp.schwimmleitung.logic.Einstellungen
import de.dachapp.schwimmleitung.logic.GeraetEinstellung
import de.dachapp.schwimmleitung.logic.Import
import de.dachapp.schwimmleitung.logic.LeitungCodec
import de.dachapp.schwimmleitung.logic.LeitungDaten
import de.dachapp.schwimmleitung.logic.LokalerStand
import de.dachapp.schwimmleitung.logic.Material
import de.dachapp.schwimmleitung.logic.Meldung
import de.dachapp.schwimmleitung.logic.MeldungArt
import de.dachapp.schwimmleitung.logic.Nachweis
import de.dachapp.schwimmleitung.logic.Person
import de.dachapp.schwimmleitung.logic.Pin
import de.dachapp.schwimmleitung.logic.Sitzung
import de.dachapp.schwimmleitung.logic.SitzungArt
import de.dachapp.schwimmleitung.logic.Zeitraum
import de.dachapp.schwimmleitung.logic.aufgabeErledigt
import de.dachapp.schwimmleitung.logic.aufgabeLoeschen
import de.dachapp.schwimmleitung.logic.aufgabeStatus
import de.dachapp.schwimmleitung.logic.belegLoeschen
import de.dachapp.schwimmleitung.logic.dokumentLoeschen
import de.dachapp.schwimmleitung.logic.materialLoeschen
import de.dachapp.schwimmleitung.logic.meldungErledigt
import de.dachapp.schwimmleitung.logic.meldungLoeschen
import de.dachapp.schwimmleitung.logic.melden
import de.dachapp.schwimmleitung.logic.nachweisLoeschen
import de.dachapp.schwimmleitung.logic.personLoeschen
import de.dachapp.schwimmleitung.logic.setzen
import de.dachapp.schwimmleitung.logic.sitzungLoeschen
import de.dachapp.schwimmleitung.logic.trainerUebernehmen
import de.dachapp.schwimmleitung.logic.uebergeben
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

/** [leitung] = nur mit Leitungs-PIN sichtbar. */
enum class Tab(val titel: String, val symbol: String, val leitung: Boolean) {
    HALLE("Halle", "🏊", false),
    UEBERSICHT("Übersicht", "📊", true),
    AUFGABEN("Aufgaben", "✅", true),
    SITZUNGEN("Sitzungen", "📝", true),
    MEHR("Mehr", "☰", true),
}

/** Bildschirme über der Tab-Übersicht. `id == null` heißt neu anlegen. */
sealed interface Ansicht {
    data class MeldungNeu(val art: MeldungArt) : Ansicht
    data class MeldungZeigen(val id: String) : Ansicht
    data object Meldungen : Ansicht
    data class BelegBearbeiten(val id: String?) : Ansicht
    data object Belege : Ansicht
    data object Bericht : Ansicht
    data class AufgabeBearbeiten(val id: String?, val sitzungId: String? = null) : Ansicht
    data class SitzungBearbeiten(val id: String) : Ansicht
    data object MaterialListe : Ansicht
    data class MaterialBearbeiten(val id: String?) : Ansicht
    data object Ablage : Ansicht
    data class DokumentBearbeiten(val id: String?) : Ansicht
    data object Personen : Ansicht
    data class PersonBearbeiten(val id: String?) : Ansicht
    data class NachweisBearbeiten(val id: String?, val personId: String) : Ansicht
    data object Einstellungen : Ansicht

    /** Nur mit Leitungs-PIN. Meldung erfassen, Beleg erfassen und Meldungen ansehen gehen auch ohne. */
    val leitung: Boolean get() = this !is MeldungNeu && this !is MeldungZeigen && this !is Meldungen && !(this is BelegBearbeiten && id == null)
}

enum class BerichtArt(val text: String) { SEIT_SITZUNG("Seit letzter Sitzung"), SAISON("Saison"), QUARTAL("Quartal"), MONAT("Monat"), JAHR("Kalenderjahr") }

sealed interface SyncZustand {
    data object KeineDatei : SyncZustand
    data object Laeuft : SyncZustand
    data class Ok(val zeit: Long) : SyncZustand
    /** [angehalten]: Abgleich ruht, bis eine andere Datei gewählt wird (z. B. fremde Datei). */
    data class Fehler(val text: String, val angehalten: Boolean = false) : SyncZustand
}

data class UiState(
    val stand: LokalerStand = LokalerStand(),
    val tab: Tab = Tab.HALLE,
    val stapel: List<Ansicht> = emptyList(),
    val sync: SyncZustand = SyncZustand.KeineDatei,
    /** Leitungsbereich offen (PIN eingegeben oder noch keine PIN gesetzt). */
    val entsperrt: Boolean = false,
    val berichtArt: BerichtArt = BerichtArt.SAISON,
    /** Stichtag des Berichts; verschiebt Monat, Quartal, Saison und Jahr. */
    val berichtTag: LocalDate = LocalDate.now(),
    val beschaeftigt: Boolean = false,
    val message: String? = null,
) {
    val ansicht: Ansicht? get() = stapel.lastOrNull()
    val daten: LeitungDaten get() = stand.daten
    val geraet: GeraetEinstellung get() = stand.geraet
    val e: Einstellungen get() = daten.e
    val leitungOffen: Boolean get() = entsperrt || !e.pinGesetzt

    /** Wer an diesem Gerät meldet: gewählte Person oder Gerätename. */
    val melder: String get() = daten.person(geraet.ichPersonId)?.name ?: geraet.name

    fun zeitraum(heute: LocalDate = LocalDate.now()): Zeitraum = when (berichtArt) {
        BerichtArt.SEIT_SITZUNG -> daten.sichtbareSitzungen.firstOrNull { it.datum.isBefore(heute) }?.let { Zeitraum.seit(it.datum, heute) }
            ?: Zeitraum.saison(heute, e.saisonBeginn)
        BerichtArt.SAISON -> Zeitraum.saison(berichtTag, e.saisonBeginn)
        BerichtArt.QUARTAL -> Zeitraum.quartal(berichtTag)
        BerichtArt.MONAT -> Zeitraum.monat(berichtTag)
        BerichtArt.JAHR -> Zeitraum.jahr(berichtTag)
    }
}

class LeitungViewModel(app: Application) : AndroidViewModel(app) {

    private val store = LeitungStore(app)
    private val datei = GeteilteDatei(app.contentResolver)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var speichernJob: Job? = null
    private val abgleichSperre = Mutex()

    init {
        val geladen = store.load()
        val stand = (geladen ?: LokalerStand()).let { s ->
            if (s.geraet.id.isNotEmpty()) s
            else s.copy(geraet = s.geraet.copy(id = UUID.randomUUID().toString().take(8), name = Build.MODEL ?: "Gerät"))
        }
        _state.value = UiState(
            stand = stand,
            sync = when {
                stand.geraet.dateiUri == null -> SyncZustand.KeineDatei
                stand.letzterAbgleich > 0 -> SyncZustand.Ok(stand.letzterAbgleich)
                else -> SyncZustand.Laeuft
            },
            message = if (geladen == null) "Die gespeicherten Daten konnten nicht gelesen werden. Die Datei wurde aufgehoben. " +
                "Mit der geteilten Datei kommt der Stand der anderen Geräte zurück." else null,
        )
        if (geladen == null) store.beiseitelegen()
        if (stand !== geladen) store.save(stand)
    }

    private val s get() = _state.value
    private fun jetzt() = System.currentTimeMillis()
    private fun heute() = LocalDate.now()

    /** Ändert den Stand, speichert kurz danach und gleicht bei Bedarf gleich ab. */
    private fun aendern(sofortAbgleichen: Boolean = false, f: (LokalerStand) -> LokalerStand) {
        _state.update { it.copy(stand = f(it.stand)) }
        speichernJob?.cancel()
        speichernJob = viewModelScope.launch {
            delay(400)
            store.save(s.stand)
        }
        if (sofortAbgleichen) jetztAbgleichen()
    }

    private fun daten(f: (LeitungDaten) -> LeitungDaten) = aendern(sofortAbgleichen = true) { it.copy(daten = f(it.daten)) }

    override fun onCleared() {
        speichernJob?.cancel()
        store.save(s.stand)
        store.close()
    }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun neueId(name: String): String {
        val basis = name.lowercase().replace('ä', 'a').replace('ö', 'o').replace('ü', 'u').replace('ß', 's')
            .filter { it.isLetterOrDigit() }.take(16).ifEmpty { "eintrag" }
        return "$basis-${s.geraet.id.take(4)}${UUID.randomUUID().toString().take(6)}"
    }

    // ---------- Navigation und PIN ----------

    fun tab(tab: Tab) {
        if (tab.leitung && !s.leitungOffen) return
        _state.update { it.copy(tab = tab, stapel = emptyList()) }
    }

    fun oeffnen(a: Ansicht) {
        if (a.leitung && !s.leitungOffen) return
        _state.update { it.copy(stapel = it.stapel + a) }
    }

    /** Ersetzt den obersten Bildschirm (z. B. nach dem Anlegen den Bearbeiten-Bildschirm). */
    fun ersetzen(a: Ansicht) = _state.update { it.copy(stapel = it.stapel.dropLast(1) + a) }

    fun zurueck(): Boolean {
        if (s.stapel.isEmpty()) return false
        _state.update { it.copy(stapel = it.stapel.dropLast(1)) }
        return true
    }

    /** true = richtig, Leitungsbereich offen. */
    fun entsperren(pin: String): Boolean {
        if (!Pin.pruefen(s.e, pin)) return false
        _state.update { it.copy(entsperrt = true) }
        return true
    }

    /** Leitungsbereich schließen und zurück in den Hallenbericht (auch beim Verlassen der App). */
    fun sperren() {
        if (!s.e.pinGesetzt) return
        _state.update { st ->
            st.copy(
                entsperrt = false,
                tab = Tab.HALLE,
                stapel = st.stapel.filter { !it.leitung },
            )
        }
    }

    fun pinSetzen(pin: String) {
        daten { it.setzen(Pin.setzen(it.e, pin), jetzt()) }
        _state.update { it.copy(entsperrt = true) }
        showMessage("Die Leitungs-PIN ist gesetzt und gilt nach dem Abgleich auf allen Geräten mit dieser Datei.")
    }

    // ---------- Hallenbericht ----------

    fun melden(art: MeldungArt, text: String, materialId: String?, foto: String?) {
        val m = Meldung(neueId(art.name), art, text.trim(), materialId, s.melder, jetzt(), foto)
        daten { it.melden(m, heute(), jetzt(), neueId("defekt")) }
        zurueck()
        showMessage(if (art == MeldungArt.DEFEKT) "Danke, der Defekt ist gemeldet. Die Leitung bekommt ihn als Aufgabe." else "Danke, das Vorkommnis ist festgehalten.")
    }

    fun meldungErledigt(id: String, erledigt: Boolean) = daten { it.meldungErledigt(id, erledigt, heute(), jetzt()) }
    fun meldungLoeschen(id: String) { daten { it.meldungLoeschen(id, jetzt()) }; zurueck() }

    fun ich(personId: String?) = aendern { it.copy(geraet = it.geraet.copy(ichPersonId = personId)) }

    // ---------- Aufgaben ----------

    fun speichern(a: Aufgabe) = daten { it.setzen(a.copy(titel = a.titel.trim(), erstellt = a.erstellt ?: heute()), jetzt()) }
    fun aufgabeStatus(id: String, status: AufgabeStatus) = daten {
        if (status == AufgabeStatus.ERLEDIGT) it.aufgabeErledigt(id, heute(), jetzt()) else it.aufgabeStatus(id, status, heute(), jetzt())
    }
    fun aufgabeLoeschen(id: String) { daten { it.aufgabeLoeschen(id, jetzt()) }; zurueck() }

    // ---------- Sitzungen ----------

    fun sitzungNeu(art: SitzungArt) {
        val id = neueId("sitzung")
        val letzte = s.daten.sichtbareSitzungen.firstOrNull { it.art == art }
        daten { it.setzen(Sitzung(id, art, heute(), ort = letzte?.ort.orEmpty()), jetzt()) }
        oeffnen(Ansicht.SitzungBearbeiten(id))
    }

    fun speichern(x: Sitzung) = daten { it.setzen(x, jetzt()) }
    fun sitzungLoeschen(id: String) { daten { it.sitzungLoeschen(id, jetzt()) }; zurueck() }

    /** Kennzahlen seit der vorigen Sitzung (sonst der laufenden Saison) bis zum Sitzungstag, als Text. */
    fun kennzahlenText(sitzungId: String, datum: LocalDate): String {
        val vorige = s.daten.sichtbareSitzungen.firstOrNull { it.id != sitzungId && it.datum.isBefore(datum) }
        val z = vorige?.let { Zeitraum.seit(it.datum, datum) } ?: Zeitraum.saison(datum, s.e.saisonBeginn)
        return Berichte.text(Berichte.erstellen(s.daten, z, datum))
    }

    fun berichtFuerSitzung(sitzungId: String) {
        val text = Berichte.text(Berichte.erstellen(s.daten, s.zeitraum(), heute()))
        s.daten.sitzung(sitzungId)?.let { speichern(it.copy(kennzahlen = text)) }
        showMessage("Der Bericht steht jetzt im Leitungsbericht der Sitzung.")
    }

    // ---------- Material, Belege, Ablage, Personen ----------

    fun speichern(x: Material) = daten { it.setzen(x.copy(name = x.name.trim()), jetzt()) }
    fun materialLoeschen(id: String) { daten { it.materialLoeschen(id, jetzt()) }; zurueck() }

    fun speichern(x: Beleg) = daten { it.setzen(x.copy(zweck = x.zweck.trim()), jetzt()) }
    fun belegLoeschen(id: String) { daten { it.belegLoeschen(id, jetzt()) }; zurueck() }
    fun uebergeben(ids: Collection<String>) = daten { it.uebergeben(ids, heute(), jetzt()) }

    fun speichern(x: Dokument) = daten { it.setzen(x.copy(titel = x.titel.trim(), link = x.link.trim()), jetzt()) }
    fun dokumentLoeschen(id: String) { daten { it.dokumentLoeschen(id, jetzt()) }; zurueck() }

    fun speichern(x: Person) = daten { it.setzen(x.copy(name = x.name.trim()), jetzt()) }
    fun personLoeschen(id: String) { daten { it.personLoeschen(id, jetzt()) }; zurueck() }
    fun speichern(x: Nachweis) = daten { it.setzen(x, jetzt()) }
    fun nachweisLoeschen(id: String) { daten { it.nachweisLoeschen(id, jetzt()) }; zurueck() }

    // ---------- Bericht ----------

    fun berichtArt(a: BerichtArt) = _state.update { it.copy(berichtArt = a, berichtTag = heute()) }

    /** Zeitraum um eine Einheit vor oder zurück. */
    fun berichtBlaettern(richtung: Int) = _state.update {
        val t = it.berichtTag
        it.copy(
            berichtTag = when (it.berichtArt) {
                BerichtArt.MONAT -> t.plusMonths(richtung.toLong())
                BerichtArt.QUARTAL -> t.plusMonths(3L * richtung)
                BerichtArt.SAISON, BerichtArt.JAHR -> t.plusYears(richtung.toLong())
                BerichtArt.SEIT_SITZUNG -> t
            },
        )
    }

    // ---------- Einstellungen und Import ----------

    fun einstellungen(x: Einstellungen) = daten { it.setzen(x, jetzt()) }
    fun geraet(g: GeraetEinstellung) = aendern { it.copy(geraet = g) }

    /** Sicherung von Schwimmkurs oder Schwimmgruppen einlesen. */
    fun importieren(uri: Uri) {
        _state.update { it.copy(beschaeftigt = true) }
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    (getApplication<Application>().contentResolver.openInputStream(uri) ?: error("leer")).use { it.readBytes().decodeToString() }
                }
                when (val r = withContext(Dispatchers.Default) { Import.lesen(text, jetzt()) }) {
                    is Import.Ergebnis.Kurs -> {
                        daten { it.setzen(r.auszug, jetzt()) }
                        showMessage("Schwimmkurs übernommen: ${r.auszug.kurse.size} Kurse, ${r.auszug.kinder.size} Kinder. Namen und Kontaktdaten bleiben in der Schwimmkurs-App.")
                    }
                    is Import.Ergebnis.Gruppen -> {
                        var neu = 0
                        daten { d ->
                            val (mit, n) = d.setzen(r.auszug, jetzt()).trainerUebernehmen(r.auszug, jetzt()) { neueId("person") }
                            neu = n
                            mit
                        }
                        showMessage(
                            "Schwimmgruppen übernommen: ${r.auszug.gruppen.size} Gruppen, ${r.auszug.mitglieder.count { it.aktiv }} aktive Schwimmer, " +
                                "${r.auszug.einheiten.size} erfasste Trainings." + if (neu > 0) " $neu Trainer stehen jetzt in der Personenliste." else "",
                        )
                    }
                }
            } catch (e: IllegalArgumentException) {
                showMessage(e.message ?: "Die Datei konnte nicht gelesen werden.")
            } catch (e: Exception) {
                showMessage("Die Datei konnte nicht gelesen werden.")
            } finally {
                _state.update { it.copy(beschaeftigt = false) }
            }
        }
    }

    /** Datei aus dem Dateidialog übernehmen: dauerhafte Berechtigung holen und sofort abgleichen. */
    fun dateiGewaehlt(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: SecurityException) {
            showMessage("Für diese Datei gibt es keine dauerhafte Schreibberechtigung. Bitte eine Datei in Google Drive oder im Gerätespeicher wählen.")
            return
        }
        s.geraet.dateiUri?.let { alt ->
            if (alt != uri.toString()) runCatching {
                resolver.releasePersistableUriPermission(alt.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
        aendern { it.copy(geraet = it.geraet.copy(dateiUri = uri.toString()), letzterAbgleich = 0) }
        _state.update { it.copy(sync = SyncZustand.Laeuft) }
        jetztAbgleichen()
    }

    fun dateiTrennen() {
        aendern { it.copy(geraet = it.geraet.copy(dateiUri = null), letzterAbgleich = 0) }
        _state.update { it.copy(sync = SyncZustand.KeineDatei) }
    }

    /** Größe der geteilten Datei in KB (Fotos machen den Großteil aus). */
    fun groesseKb(): Int = LeitungCodec.encode(s.daten).length / 1024

    // ---------- Abgleich ----------

    private var schleife: Job? = null

    /** Regelmäßiger Abgleich, solange die Unter-App sichtbar ist. */
    fun sichtbar(an: Boolean) {
        schleife?.cancel()
        schleife = if (!an) null else viewModelScope.launch {
            while (true) {
                abgleichen()
                delay(s.geraet.intervallSekunden.coerceIn(15, 600) * 1000L)
            }
        }
    }

    fun jetztAbgleichen() {
        viewModelScope.launch { abgleichen() }
    }

    private suspend fun abgleichen() = abgleichSperre.withLock {
        val uriText = s.geraet.dateiUri ?: return@withLock
        if ((s.sync as? SyncZustand.Fehler)?.angehalten == true) return@withLock
        val uri = uriText.toUri()
        val erstes = s.stand.letzterAbgleich == 0L
        val vorher = s.daten
        val ergebnis = try {
            withContext(Dispatchers.IO) {
                val (text, laenge) = datei.lesen(uri)
                val entfernt = LeitungCodec.decode(text)
                // Beim ersten Verbinden gilt die PIN aus der Datei, nicht eine auf diesem Gerät neu gesetzte.
                val lokal = if (erstes && entfernt?.e?.pinGesetzt == true) vorher.copy(einstellungen = emptyList()) else vorher
                val r = Abgleich.abgleich(lokal, entfernt)
                if (r.schreiben) datei.schreiben(uri, LeitungCodec.encode(r.daten), laenge)
                r
            }
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(sync = SyncZustand.Fehler(e.message ?: "Datei unlesbar.", angehalten = true)) }
            return@withLock
        } catch (e: SecurityException) {
            _state.update { it.copy(sync = SyncZustand.Fehler("Kein Zugriff mehr auf die Datei. Bitte unter Einstellungen neu wählen.", angehalten = true)) }
            return@withLock
        } catch (e: Exception) {
            // Kurz nicht erreichbar (z. B. Drive offline): beim nächsten Durchlauf wieder versuchen.
            _state.update { it.copy(sync = SyncZustand.Fehler("Datei gerade nicht erreichbar, neuer Versuch folgt.")) }
            return@withLock
        }
        val zeit = jetzt()
        // Während des Lesens/Schreibens Geändertes nicht verlieren: mit dem aktuellen Stand zusammenführen.
        aendern { st ->
            val aktuell = if (erstes && ergebnis.daten.e.pinGesetzt) st.daten.copy(einstellungen = emptyList()) else st.daten
            st.copy(daten = Abgleich.zusammenfuehren(aktuell, ergebnis.daten), letzterAbgleich = zeit)
        }
        _state.update { it.copy(sync = SyncZustand.Ok(zeit)) }
    }
}
