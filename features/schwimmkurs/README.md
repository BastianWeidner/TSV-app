## Schwimmkurse

Verwaltung von Schwimmkursen für Kinder, komplett offline auf dem Gerät.

- **Kinder importieren:** Excel (.xlsx) oder CSV wählen, z. B. die Anmeldeliste oder ein Google-Formular-Export.
  Die App erkennt übliche Spalten (Vorname, Name, Geburtsdatum, E-Mail, Telefon, Straße, PLZ/Ort, Elternteil,
  Abzeichen, Zeitstempel/Anmeldedatum, Bemerkung) und zeigt vor dem Übernehmen, welche Spalte wohin geht.
  Das lässt sich pro Spalte ändern. Eine Spalte, die wie ein Abzeichen heißt (z. B. „Seepferdchen“ mit „x“),
  wird als dieses Abzeichen gelesen. Wer schon da ist (gleicher Name und Geburtsdatum), wird ergänzt statt doppelt angelegt.
  Steht in einer Namensspalte mal „Vorname Nachname“, mal „Nachname Vorname“, erkennt die App die Reihenfolge über
  Geschwister, die E-Mail-Adresse und eine Liste häufiger Vornamen. Hinweise nach dem Import nennen fehlende
  Geburtsdaten oder E-Mails, mögliche Doppelte, Abmeldungen und erwähnte Abzeichen in der Bemerkung.
  „Nicht in die selbe Gruppe“ in der Bemerkung setzt „Geschwister getrennt“. Die Einschulung wird mit übernommen.
- **Abzeichen:** Standard Seepferdchen, Bronze, Silber, Gold (in den Einstellungen änderbar). Ein höheres Abzeichen
  erfüllt die niedrigeren. Alte Namen werden erkannt (Freischwimmer = Bronze, Fahrtenschwimmer = Silber,
  Jugendschwimmer = Gold). Freie Leistungen wie „Tauchring“ werden einfach mitgespeichert.
- **Kurse:** Name, Plätze, Preis, Schwimmbad, Kursleitung, Alter von/bis (am ersten Kurstag), „braucht schon“
  und „Kursziel“. Termine als Serie (z. B. 8 Samstage 9:00–9:45) oder einzeln; einzelne Termine lassen sich
  verschieben oder löschen (Ferien).
- **Automatisch befüllen:** Verteilt alle Kinder ohne Kurs auf freie Plätze, die nach Alter und Abzeichen passen.
  Altersgruppen: Mindestalter (Standard 5 Jahre, für Kurse ohne eigene Grenze) und höchstens 2 Jahre Unterschied
  zwischen jüngstem und ältestem Kind eines Kurses; beides in den Einstellungen änderbar. „Einteilung leeren“
  nimmt alle Kinder wieder aus den Kursen.
  Vorrang zuerst nach Einzugsgebiet: Fischach, Langenneufnach, Gessertshausen, Ustersbach, Mickhausen,
  Dinkelscherben, danach Kutzenhausen, Mittelneufnach, Walkertshofen, Rommelsried, in dieser Reihenfolge, jeweils mit ihren Ortsteilen (in den Einstellungen änderbar, eine Zeile je
  Gemeinde: „Fischach: Willmatshofen, Reitenbuch“, auch PLZ wie „123*“). Ohne Wohnort-Spalte wird der Ort in der
  Bemerkung gesucht, z. B. „KiGa Willmatshofen“. Danach kommen Kinder ohne erkennbaren Wohnort (neutral), zuletzt
  Kinder von außerhalb. Innerhalb davon zuerst ältere Kinder, dann frühere Einschulung (leer = aus dem Geburtsdatum
  geschätzt, Stichtag 30.09.), dann Platz in der Anmeldeliste.
  Geschwister (gleiche E-Mail oder gleiche Adresse) kommen in denselben Kurs, wenn alle hineinpassen, sonst
  möglichst an dieselben Tage. Es gibt erst einen Vorschlag mit Warteliste und Gründen, dann „Übernehmen“.
  Von Hand eingeteilte Kinder bleiben, wo sie sind.
- **Einladungen:** Eine Mail pro Familie im Stil der bisherigen Vereinsmails: Termine als Liste, ausfallende
  Samstage automatisch („Am 31.01. und 21.02. ist kein Schwimmkurs“), Kostenaufstellung (Termine × Kursgebühr
  + Eintritt, Geschwisterrabatt), Überweisungsdaten mit Kurskürzel und Kindernamen im Verwendungszweck,
  Zahlungsfrist pro Kurs und die Regeln zu Fristen und Warteliste. Als PDF hängt eine Kursübersicht mit
  Termintabelle und Überweisungskasten an, dahinter die Haftungserklärung (ohne Unterschrift; mit der Überweisung gilt sie als anerkannt). „Mail öffnen“ übergibt alles an Gmail oder Outlook, dort nur noch Senden tippen.
  Die App merkt sich, welche Mails schon geöffnet wurden. Texte mit Platzhaltern in den Einstellungen.
- **GiroCode:** Im PDF steht unter den Überweisungsdaten ein QR-Code (EPC-Standard). Eltern fotografieren ihn in
  der Banking-App, Empfänger, IBAN, Betrag und Verwendungszweck sind dann ausgefüllt. Die Vorschau zeigt ihn auch
  zum Vorzeigen. Ohne gültige IBAN (Prüfziffer) gibt es keinen Code und einen Hinweis.
- **Zahlungen:** Jede Einladung zeigt „Zahlung offen“ oder „Bezahlt am …“, von Hand abhakbar. „Kontoauszug
  abgleichen“ liest den CSV-Export der Umsätze (Sparkasse, VR-Bank u. a.; Kopfzeile mit „Betrag“ wird gesucht,
  Soll/Haben-Spalte wird beachtet) und ordnet Gutschriften über Kurskürzel, Nachname und Betrag den Familien zu.
  Sichere Treffer sind angehakt, abweichende Beträge und unsichere Treffer werden markiert, Gutschriften, die
  nach Schwimmkurs aussehen, aber nicht passen, stehen darunter. Gespeichert wird nur Datum und Betrag.
- **Abzeichen prüfen:** Im Kurs unten „Abzeichen prüfen“. Abzeichen (Seepferdchen, Bronze, Silber, Gold nach Prüfungsordnung 2020) und
  Teilleistung wählen, dann die Kinder antippen: abgehakt mit heutigem Datum, auch über mehrere Termine. „Übersicht“ zeigt
  den Stand je Kind, Antippen klappt alle Teile auf. Sind alle Teile da, steht das Abzeichen beim Kind (Zurücknehmen nimmt es wieder weg). „Bestellliste Abzeichen“ listet
  die bestandenen Abzeichen mit Datum zum Teilen. Katalog und Bildschirm liegen gemeinsam in core
  (`Schwimmabzeichen.kt`, `AbzeichenPruefung.kt`).
- **Teilnehmerliste** pro Kurs als CSV teilen (öffnet sich in Excel direkt richtig).
- **Sicherung:** Alle Daten als eine Datei sichern und wieder einspielen. Im Android-Dateidialog kann man
  direkt Google Drive wählen, ohne dass die App eine Internet-Berechtigung braucht. Darauf lässt sich später
  eine automatische Drive-Synchronisation aufsetzen.

Alte Excel-Dateien (.xls) bitte in Excel als .xlsx oder .csv speichern. Google-Tabellen öffnet die App,
wenn Drive sie als Excel anbietet; sonst in Drive als .xlsx herunterladen.
