# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/2.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- README badges now show downloads, Android, Kotlin, Jetpack Compose, UI languages, and the license.

## [1.0.0] - 2026-10-23

First release.

### Added

- Earphone-only playback with instant switching: every press of Next or Previous cuts the current cue and starts the new one, so cues never overlap.
- Earphone and mute guard: three strong vibrations and no sound when no earphones are connected or media volume is 0. Unplugging earphones mid-cue stops playback.
- Three button layouts with drag-to-move and drag-to-resize editing, reset to default, and a left/right-hand mirror.
- Stage mode: black screen at minimum brightness that needs two Back presses to leave.
- Sony shutter button support (press = next, hold = previous), optional volume keys, and wired or Bluetooth earphone buttons.
- Text-to-speech voices with speed 0.25×–2.00× (hold − or + for continuous change), pitch, engine selection, a voice picker with region chips and an offline-only filter, and word-by-word highlighting.
- Recording your own voice per cue; a recording is played instead of speech synthesis.
- Local SQLite script library with import from file, pasted text and share, search over titles, speakers, tags and cue text, tag filters and tag editing.
- Script history: update a script from a new file and restore any earlier revision. The history is append-only.
- Section timers with an overtime vibration, and partner cues shown as "wait for" hints.
- UI in 10 languages (English, 繁體中文, 简体中文, Español, Português (Brasil), 日本語, 한국어, Français, Deutsch, Русский) with an in-app language switch, and light and dark themes.
- `earcue-script` AI skill that converts a transcript into a tagged EarCue script file and checks it word for word against the source.
- Fictional demo talk in `examples/green-streets/` (deck, bilingual transcript, cue file).

[Unreleased]: https://github.com/naritaroad/EarCue/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/naritaroad/EarCue/releases/tag/v1.0.0
