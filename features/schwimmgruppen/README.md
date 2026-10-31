## Schwimmgruppen

Verwaltung der Trainingsgruppen der Abteilung Schwimmen, komplett offline auf dem Gerät. Auf dem Handy wie
auf dem Tablet in der Schwimmhalle nutzbar (ab 840 dp Breite: Navigation links, Liste und Unterseite nebeneinander,
Gruppen als Spalten).

- **Mitgliederliste übernehmen:** Excel (.xlsx) oder CSV (auch Tab-getrennt) wählen. Erkannt werden u. a. Name,
  Vorname, Straße, PLZ, Ort, Telefon, GebDat (auch 16/7/2015), E-Mail-Adresse, EJ (Eintrittsjahr), Fotorechte,
  Gesundheitserklärung, Mitgliedsnummer, Geschlecht, Abteilung, Gruppe, Austritt. Vor dem Übernehmen lässt sich jede
  Spalte umstellen. Wer schon da ist (gleiche Mitgliedsnummer, sonst gleicher Name und Geburtsdatum), wird
  aktualisiert; Gruppen, Zeiten und Erfolge bleiben. Hat die Liste eine Abteilungs-Spalte, kommen nur Zeilen mit
  „Schwimm…“ herein (einstellbar). Wer in der Liste fehlt, kann danach auf inaktiv gesetzt werden.
- **Gruppen:** Startwerte nach dem Trainingsplan: Gruppe I und II je Trainingstag als eigene Gruppe (I Mittwoch,
  I Samstag, II Dienstag, II Mittwoch, II Samstag), Gruppe III, IV, Kader (an beiden Tagen dieselben Schwimmer),
  Wassergymnastik und Erwachsenenschwimmen / Stiloptimierung. Eine Gruppe mit mehreren Tagen lässt sich mit
  „Je Trainingstag eine eigene Gruppe“ aufteilen. Name, Zeiten, Trainer, Plätze und Art (Verein oder Angebot für
  Externe) sind änderbar, Gruppen lassen sich anlegen und löschen. Gruppenliste als CSV.
- **Einteilung durch die Trainer:** Im Tab „Gruppen“ einen Namen antippen und die Gruppen anhaken. Mehrere Gruppen
  sind möglich (z. B. Kader und Gruppe II). „Ohne Gruppe“ steht oben.
- **Trainer:** Unter „Gruppen“ → „Trainer“ eine eigene Liste aus den Mitgliedern (oder ohne Mitgliedschaft) mit
  Rang (Trainer, Jungtrainer, Helfer), Schein, Telefon und Alterskennzeichen 18+ / U18. Je Gruppe Stammtrainer, die für
  jede Stunde gelten; in einer einzelnen Stunde (Training oder Hallenmodus) lassen sich Trainer an- und abwählen
  und je eingeteiltem Trainer „vertreten durch …“ setzen (Kalender und CSV zeigen dann „Tom Tief (für Lisa Lang)“,
  die Einsätze zählen beim Vertreter).
  Ist kein volljähriger Trainer eingeteilt, erscheint ⚠. Die Statistik zählt die Einsätze je Trainer, das CSV nennt
  die Trainer je Stunde.
- **Unterseite je Schwimmer:** Gruppen, Wettkampfinteresse (offen / nein / vielleicht / ja + Notiz), Erfolge
  (Abzeichen, Wettkampf-Platzierungen, Sonstiges mit Datum), Zeiten je Strecke mit Bestzeiten (Eingabe wie 38,52
  oder 1:05,32, Training oder Wettkampf), Anwesenheit der letzten 3 Monate, Kontakt. 📵 = keine Fotorechte,
  ⚠ = Gesundheitserklärung fehlt (Anzeige in den Einstellungen abschaltbar), 🏅 = will zu Wettkämpfen.
- **Training:** Wochenkalender aus den Trainingszeiten. Je Training: Schwimmer abhaken und/oder Teilnehmer mit
  +/− zählen (Schalter „nur zählen“ je Gruppe, auch direkt im Training; Externe zählen standardmäßig nur), „ausgefallen“ und ein Vermerk (z. B. „Bahn 3 gesperrt“).
  Zusatztermine sind möglich.
- **Hallenmodus:** Für das Tablet am Beckenrand. Nimmt automatisch das laufende bzw. nächste Training des Tages,
  große Kacheln zum Abhaken, Bildschirm bleibt an. Schwimmer aus anderen Gruppen lassen sich dazunehmen.
  „Noch ohne Gruppe“ zeigt Schwimmer ohne Einteilung; Antippen hakt sie ab und teilt sie in diese Gruppe ein.
- **Trainingsuhr:** Unter „Training“ bzw. im Hallenmodus oben „⏱ Uhr“. *Abgangsuhr:* Abgänge je Serie, Abstand
  (z. B. 1:45), Serien und Pause einstellen; großer Countdown bis zum nächsten Abgang, kurze Töne bei 3-2-1 und ein
  langer Ton bei jedem Abgang (Lautstärke = Medienlautstärke). *Stoppuhr Bahnen:* 1 bis 8 Bahnen, je Bahn Schwimmer
  und Strecke wählen, gemeinsamer Start, je Bahn „Zwischenzeit“ und „Anschlag“ (mit Teilzeiten). „Zeiten beim
  Schwimmer speichern“ trägt die Endzeiten als Trainingszeit von heute ein. Bildschirm bleibt an, Drehen hält die
  Uhr nicht an.
- **Wettkämpfe (DSV 7/8, LENEX):** Unter „Mehr“ › „Wettkampfmeldungen“ einmal Vereinskennzahl, Landesverband und
  meldende Person eintragen. „Ausschreibung melden, Ergebnisse einlesen“ öffnet eine Datei und erkennt die Art selbst:
  *Ausschreibung* (Wettkampfdefinitionsliste .DSV7/.DSV8/.DSV8z oder LENEX-Einladung .lef/.lxf): je Schwimmer mit
  Wettkampfinteresse „ja“ (Schalter für alle aktiven) die passenden Einzelstarts nach Geschlecht und Jahrgang antippen,
  Meldezeit ist die Bestzeit über die Strecke (sonst „ohne Zeit“), Pflichtzeiten werden geprüft. „Meldedatei
  speichern“ schreibt die Vereinsmeldeliste im Format der Ausschreibung mit Dateinamen nach Standard
  (JJJJ-MM-TT-Ort-Verein-Me.DSV8). *Ergebnisse* (Wettkampf-/Vereinsergebnisliste oder LENEX-Ergebnis): Starts des
  eigenen Vereins (über Kennzahl oder Vereinsname) werden über DSV-ID oder Name und Jahrgang zugeordnet; „übernehmen“
  speichert die Zeiten als Wettkampfzeiten, Platz 1–3 als Erfolg und ergänzt fehlende DSV-IDs. DSV-ID je Schwimmer
  im Bearbeiten-Formular. Staffeln werden nicht gemeldet. Logik in `Dsv.kt`, `Lenex.kt`, `Wettkampf.kt`.
- **Abzeichen prüfen:** Unter „Training“ unten „Abzeichen prüfen“ oder im Hallenmodus 🏅 (dann für die gewählte Gruppe); Gruppe oben umschaltbar. Abzeichen (Seepferdchen, Bronze, Silber, Gold nach Prüfungsordnung 2020) und
  Teilleistung wählen, dann die Schwimmer antippen: abgehakt mit heutigem Datum, auch über mehrere Termine. „Übersicht“ zeigt
  den Stand je Schwimmer, Antippen klappt alle Teile auf. Sind alle Teile da, wird es als Erfolg (Abzeichen) mit dem Tag der letzten Teilleistung eingetragen. „Bestellliste Abzeichen“ listet
  die bestandenen Abzeichen mit Datum zum Teilen. Katalog und Bildschirm liegen gemeinsam in core
  (`Schwimmabzeichen.kt`, `AbzeichenPruefung.kt`).
- **Statistik:** Je Gruppe Trainings, Teilnahmen, Durchschnitt, Höchstwert, Ausfälle und nicht erfasste Termine,
  dazu Monatsbalken. Zeitraum: Monat, 3 Monate, Saison (ab September), Kalenderjahr. Als CSV für Excel speichern.
- **Sicherung:** Alle Daten als eine Datei sichern und einspielen; im Dateidialog Google Drive wählen. So kommen die
  Daten auch vom Handy aufs Tablet. Keine Internet-Berechtigung.
