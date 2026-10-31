# TSV-App „Schwimmen“

Android-App der Schwimmabteilung mit drei Teilen: **Schwimmkurs**, **Schwimmgruppen** und **Schwimmleitung**.
Sie läuft komplett offline (keine Internet-Berechtigung, keine weiteren Berechtigungen) und speichert alles nur auf dem Gerät.

- App-ID `de.dachapp.schwimmen`, Name „Schwimmen“
- Aktuelle Version: **1.12.1** (versionCode 33)
- Fertige APKs: Ordner [`apk/`](apk/), neueste Datei installieren (Updates lassen sich direkt über die alte Version installieren)

## Aufbau

| Ordner | Inhalt |
|---|---|
| `app/` | App-Rahmen (Übersicht, Design, Varianten-Infos), Variante `schwimmen` |
| `core/module-api/` | gemeinsame Schnittstelle der Unter-Apps und Pflichtfeld-Bausteine |
| `features/schwimmkurs/` | Schwimmkurs: Anmeldungen, Einladungen, Teilnehmer |
| `features/schwimmgruppen/` | Schwimmgruppen: Gruppen, Zeiten, Mitgliederliste |
| `features/schwimmleitung/` | Schwimmleitung |
| `testdaten/` | Beispieldaten zum Ausprobieren |

Jede Unter-App hat `logic/` (reines Kotlin mit Tests) und `ui/` (Jetpack Compose).

## Bauen

Voraussetzungen: JDK 17, Android SDK (Plattform 36, Build-Tools 36.0.0).

```
echo "sdk.dir=/pfad/zum/android-sdk" > local.properties
./gradlew test lint assembleRelease
```

Die APK liegt danach in `app/build/outputs/apk/schwimmen/release/`.
Für eine signierte Release-APK wird `keystore.properties` mit dem Release-Schlüssel gebraucht; beides liegt bewusst **nicht** im Repo.
Ohne diese Datei entsteht eine unsignierte Release-APK.

`gradle/maven-mirror.init.gradle` ist nur nötig, wenn Maven Central nicht erreichbar ist (`./gradlew -I gradle/maven-mirror.init.gradle …`).

## Herkunft

Der Code stammt aus der größeren „Dach-App“ und wird bei jedem neuen Build hierher übernommen
(nur die Schwimm-Teile, ohne die übrigen Unter-Apps).

Schriftart Nunito: SIL Open Font License, siehe `app/NUNITO-LIZENZ-OFL.txt`.
