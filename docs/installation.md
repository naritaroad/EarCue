# Installation

## Install the app

1. Open [Releases](https://github.com/naritaroad/EarCue/releases/latest) on the phone and download `EarCue-<version>.apk`.
2. Open the file. Android asks to allow installs from the browser or file manager; allow it once.
3. Launch EarCue. Android 8.0 (API 26) or later is required.

Updating: install the newer APK over the old one. Scripts, history and settings are kept.

## Prepare a script

### With the AI skill (recommended)

`skills/earcue-script/` is an agent skill. Download `earcue-script.zip` from the release, add it to your AI agent's skills, then ask it, for example:

> Convert `Green Streets Transcript (Bilingual).docx` into an EarCue script for P2 Mina Park.

The agent writes `<talk>-<role>.earcue.json` with tags and checks it word for word with `scripts/validate.py`.

### By hand

Paste plain text in **Scripts → Paste text**:

```
title: My talk
lang: en-US
tags: class-report

# Opening | P1 | 2
> Host: Over to you.
Hello everyone.
// note shown on screen
```

## Load it on the phone

- **Scripts → Import file** and pick the `.json` or `.txt`, or
- share the file to EarCue from a file manager or chat app.

A file whose title matches an existing script can be saved as a new revision of it.

## First run checklist

- Connect earphones. The pill at the top right turns green with the device name.
- In **Settings → Voice**, pick an offline voice for the script language.
- In **Buttons**, choose a layout, or drag the buttons to fit your hand.
- On Sony Xperia phones, the shutter button works as Next (press) and Previous (hold).

## Build from source

Requirements: JDK 17 and Android SDK Platform 35 with Build-tools 35. Create `local.properties` with `sdk.dir=<path to the SDK>`.

```bash
./gradlew assembleDebug
```

```bash
./gradlew assembleRelease
```

The release build is minified with R8 and, in this repository, signed with the debug key. Use your own signing config before publishing to a store.

After editing `tools/i18n/*.py`:

```bash
python tools/i18n/gen_strings.py
```

To rebuild the demo files in `examples/green-streets/` (needs `python-docx` and `python-pptx`):

```bash
python tools/demo/build_demo.py
```
