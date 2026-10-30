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
- **Teilnehmerliste** pro Kurs als CSV teilen (öffnet sich in Excel direkt richtig).
- **Sicherung:** Alle Daten als eine Datei sichern und wieder einspielen. Im Android-Dateidialog kann man
  direkt Google Drive wählen, ohne dass die App eine Internet-Berechtigung braucht. Darauf lässt sich später
  eine automatische Drive-Synchronisation aufsetzen.

Alte Excel-Dateien (.xls) bitte in Excel als .xlsx oder .csv speichern. Google-Tabellen öffnet die App,
wenn Drive sie als Excel anbietet; sonst in Drive als .xlsx herunterladen.
