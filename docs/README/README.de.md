<div align="center">

<img src="../assets/banner.png" alt="EarCue: dein Skript im Ohr, ein Daumendruck pro Satz" width="100%">

# EarCue

Ein Android-Prompter, der deinen Vortrag in die Kopfhörer liest, ein Satz pro Daumendruck.

[APK herunterladen](https://github.com/naritaroad/EarCue/releases/latest) · [Skriptformat](../../skills/earcue-script/reference/format.md) · [Issues](https://github.com/naritaroad/EarCue/issues)

![Release](https://img.shields.io/github/v/release/naritaroad/EarCue?include_prereleases&style=flat&color=FFB547)
![Downloads](https://img.shields.io/github/downloads/naritaroad/EarCue/total?style=flat&label=Downloads&color=3DDC84)
![Stars](https://img.shields.io/github/stars/naritaroad/EarCue?style=flat&color=FFB547)
![Last commit](https://img.shields.io/github/last-commit/naritaroad/EarCue?style=flat&color=1B1E26)
![Issues](https://img.shields.io/github/issues/naritaroad/EarCue?style=flat&color=1B1E26)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin 2.1](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-7F52FF)
![UI languages](https://img.shields.io/badge/UI-10_languages-1B1E26)
![License](https://img.shields.io/badge/license-MIT_%2B_non--commercial-lightgrey)

[English](../../README.md) | [繁體中文](README.zh-TW.md) | [简体中文](README.zh-CN.md) | [Español](README.es.md) | [Português (Brasil)](README.pt-BR.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Français](README.fr.md) | **Deutsch** | [Русский](README.ru.md)

</div>

## Funktionen

- **Sofortiger Wechsel**: jeder Druck auf Weiter oder Zurück bricht den aktuellen Satz ab und startet den neuen, nichts überlagert sich und du wartest nie auf das Satzende.
- **Kopfhörerschutz**: ohne Kopfhörer oder bei Medienlautstärke 0 vibrieren die Wiedergabetasten dreimal und bleiben stumm. Kopfhörer abziehen stoppt die Wiedergabe.
- **Bedienung mit einem Daumen**: drei Tastenlayouts, Verschieben und Größe ändern per Ziehen mit Zurücksetzen, Spiegelung für Links- und Rechtshänder und ein schwarzer Bühnenmodus für das Telefon hinter dem Rücken.
- **Hardwaretasten**: Sony-Auslöser (drücken = weiter, halten = zurück), optional Lautstärketasten sowie Tasten kabelgebundener oder Bluetooth-Kopfhörer.
- **Stimmen**: Android-Sprachausgabe mit Tempo 0,25×–2,00× (− oder + halten für stufenloses Ändern), Tonhöhe, Engine und Stimmenwahl nach Region und Offline-Nutzung gefiltert, wortweises Hervorheben oder eigene Aufnahme pro Satz.
- **Skriptbibliothek**: lokale SQLite-Speicherung mit Suche in Titeln, Tags und Text, Tag-Filtern und einem Verlauf wie bei git: Skript aus neuer Datei aktualisieren und jede frühere Revision wiederherstellen.
- **KI-Konvertierungs-Skill**: `skills/earcue-script` wandelt ein Word-, PDF- oder Folien-Transkript in eine `.earcue.json` mit Tags um und prüft sie Wort für Wort gegen die Quelle.
- **10 UI-Sprachen**: mit hellem und dunklem Design: English, 繁體中文, 简体中文, Español, Português (Brasil), 日本語, 한국어, Français, Deutsch, Русский.

## Demo

<p align="center">
  <img src="../assets/screen-home-light.png" width="19%" alt="Home, light theme">
  <img src="../assets/screen-home-dark.png" width="19%" alt="Home, dark theme">
  <img src="../assets/screen-library.png" width="19%" alt="Script library with tag filter">
  <img src="../assets/screen-history.png" width="19%" alt="Script history">
  <img src="../assets/screen-stage.png" width="19%" alt="Stage mode">
</p>

Probiere es mit dem fiktiven Vortrag in [`examples/green-streets/`](../../examples/green-streets/): Foliensatz, zweisprachiges Transkript und die konvertierte Datei für Person P2.

## Architektur

```mermaid
flowchart LR
  T["Transcript<br/>.docx / .pdf / .pptx"] -->|earcue-script skill| J[".earcue.json"]
  J -->|Import file · Paste · Share| P[ScriptParser]
  P --> DB[("SQLite<br/>scripts · revisions · cues · recordings")]
  DB --> E[CueEngine]
  K["Touch deck · Sony shutter<br/>volume keys · earphone buttons"] --> E
  E --> G{"Earphones connected<br/>and volume > 0?"}
  G -->|yes| S["TextToSpeech or own recording"] --> H((Earphones))
  G -->|no| V["Vibrate ×3, no sound"]
```

Jeder Import wird zu einer Revision des Skripts, die aktuelle Revision wird in Abschnitte aufgeteilt. `CueEngine` steuert die Wiedergabe: Sie verwirft den vorigen Satz vor dem nächsten und prüft bei jedem Druck die Audioausgabe. Details: [docs/architecture.md](../architecture.md)

## Technische Details

<details>
<summary>Technik</summary>

| Bereich | Wahl |
|---|---|
| Sprache | Kotlin 2.1, JDK 17 |
| UI | Jetpack Compose (BOM 2024.12.01), Material 3, selbst gezeichnete Icons |
| Speicher | Plattform-`SQLiteOpenHelper` (kein ORM), SharedPreferences für Einstellungen |
| Sprache/Audio | `TextToSpeech`, `MediaPlayer` für eigene Aufnahmen, `MediaSession` für Kopfhörertasten |
| Build | Android Gradle Plugin 8.7.3, Gradle 8.11.1, compileSdk 35, minSdk 26 |
| Release-APK | etwa 1,4 MB, mit R8 verkleinert |

</details>

<details>
<summary>Skriptformat</summary>

```json
{
  "format": "earcue/1",
  "title": "Green Streets at Night · P2 Background",
  "speaker": "P2 Mina Park",
  "lang": "en-US",
  "tags": ["demo", "paper-review"],
  "sections": [
    {
      "title": "Why are summer nights getting hotter?",
      "slide": "P2",
      "minutes": 2.5,
      "cues": [
        { "partner": "P1 Alex Rivera", "text": "Quick poll first. ..." },
        { "text": "I am one of them. ...", "note": "On-screen hint" }
      ]
    }
  ]
}
```

Ein einfaches Textformat zum Einfügen wird ebenfalls akzeptiert. Vollständige Spezifikation: [format.md](../../skills/earcue-script/reference/format.md)

</details>

<details>
<summary>Berechtigungen</summary>

| Berechtigung | Zweck |
|---|---|
| `VIBRATE` | Haptisches Feedback und Warnung des Kopfhörerschutzes |
| `RECORD_AUDIO` | Nur beim Aufnehmen der eigenen Stimme für einen Satz; wird dann abgefragt |

Die App deklariert keine Internetberechtigung. Online-TTS-Stimmen lädt die Sprachausgabe, nicht EarCue.

</details>

## Installation

Lade `EarCue-<version>.apk` unter [Releases](https://github.com/naritaroad/EarCue/releases/latest) herunter, öffne sie auf dem Telefon und erlaube die Installation aus dieser Quelle. Android 8.0 oder neuer ist nötig.

Aus dem Quellcode bauen (JDK 17 und Android SDK 35):

```bash
./gradlew assembleRelease
```

Vollständige Anleitung inklusive Einrichtung des KI-Skills: [docs/installation.md](../installation.md)

## Projektstruktur

```
EarCue/
├── app/src/main/
│   ├── java/app/earcue/   # audio/ data/ ui/
│   └── res/               # icons, strings × 10 languages (generated)
├── skills/earcue-script/  # AI conversion skill
├── examples/green-streets/# fictional demo
├── tools/
│   ├── i18n/              # UI string tables + generator
│   ├── readme/            # README template + translations
│   └── demo/              # demo file builder
├── design/                # logo source
└── docs/                  # installation, architecture, assets, README/ (translations)
```

## Mitwirken

Issues und Pull Requests sind willkommen. UI-Texte liegen in `tools/i18n/`; führe nach Änderungen `python tools/i18n/gen_strings.py` aus. [CONTRIBUTING.md](../../CONTRIBUTING.md)

## Lizenz

Kostenlos für private, schulische, wissenschaftliche und gemeinnützige Nutzung zu MIT-Bedingungen; kommerzielle Nutzung braucht die schriftliche Erlaubnis des Autors. [LICENSE](../../LICENSE)
