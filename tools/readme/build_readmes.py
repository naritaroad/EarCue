#!/usr/bin/env python3
"""Write README.md (English, repo root) and its 9 translations (docs/README/) from one template,
so every language has the same sections.

Usage: python tools/readme/build_readmes.py
Edit the text tables in tools/readme/text_*.py; code blocks and diagrams stay shared.
Every link is written relative to the file it lands in, so GitHub resolves it from either place.
"""
import importlib.util
import pathlib
import posixpath

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]

TRANSLATIONS = "docs/README"  # English stays at the repo root; the rest live here

LANGS = [  # (code, path from repo root, native name)
    ("en", "README.md", "English"),
    ("zh-TW", f"{TRANSLATIONS}/README.zh-TW.md", "繁體中文"),
    ("zh-CN", f"{TRANSLATIONS}/README.zh-CN.md", "简体中文"),
    ("es", f"{TRANSLATIONS}/README.es.md", "Español"),
    ("pt-BR", f"{TRANSLATIONS}/README.pt-BR.md", "Português (Brasil)"),
    ("ja", f"{TRANSLATIONS}/README.ja.md", "日本語"),
    ("ko", f"{TRANSLATIONS}/README.ko.md", "한국어"),
    ("fr", f"{TRANSLATIONS}/README.fr.md", "Français"),
    ("de", f"{TRANSLATIONS}/README.de.md", "Deutsch"),
    ("ru", f"{TRANSLATIONS}/README.ru.md", "Русский"),
]

REPO = "https://github.com/naritaroad/EarCue"

BADGES = f"""![Release](https://img.shields.io/github/v/release/naritaroad/EarCue?include_prereleases&style=flat&color=FFB547)
![Downloads](https://img.shields.io/github/downloads/naritaroad/EarCue/total?style=flat&label=Downloads&color=3DDC84)
![Stars](https://img.shields.io/github/stars/naritaroad/EarCue?style=flat&color=FFB547)
![Last commit](https://img.shields.io/github/last-commit/naritaroad/EarCue?style=flat&color=1B1E26)
![Issues](https://img.shields.io/github/issues/naritaroad/EarCue?style=flat&color=1B1E26)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin 2.1](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-7F52FF)
![UI languages](https://img.shields.io/badge/UI-10_languages-1B1E26)
![License](https://img.shields.io/badge/license-MIT_%2B_non--commercial-lightgrey)"""

MERMAID = """```mermaid
flowchart LR
  T["Transcript<br/>.docx / .pdf / .pptx"] -->|earcue-script skill| J[".earcue.json"]
  J -->|Import file · Paste · Share| P[ScriptParser]
  P --> DB[("SQLite<br/>scripts · revisions · cues · recordings")]
  DB --> E[CueEngine]
  K["Touch deck · Sony shutter<br/>volume keys · earphone buttons"] --> E
  E --> G{"Earphones connected<br/>and volume > 0?"}
  G -->|yes| S["TextToSpeech or own recording"] --> H((Earphones))
  G -->|no| V["Vibrate ×3, no sound"]
```"""

FORMAT = """```json
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
```"""

TREE = """```
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
```"""


def load(code):
    name = "text_" + code.replace("-", "_")
    spec = importlib.util.spec_from_file_location(name, HERE / f"{name}.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod.T


def location(code):
    return next(file for c, file, _ in LANGS if c == code)


def rel(target, current):
    """Path to repo-root-relative `target` as written inside the README of language `current`."""
    base = posixpath.dirname(location(current)) or "."
    out = posixpath.relpath(target, base)  # posixpath, so Windows never writes backslashes
    return out + "/" if target.endswith("/") else out


def switcher(current):
    parts = []
    for code, file, name in LANGS:
        parts.append(f"**{name}**" if code == current else f"[{name}]({rel(file, current)})")
    return " | ".join(parts)


def render(code, t):
    r = lambda target: rel(target, code)  # noqa: E731
    docs = "docs/zh-TW/" if code == "zh-TW" else "docs/"
    stack = "\n".join(f"| {a} | {b} |" for a, b in t["stack_rows"])
    perms = "\n".join(f"| `{a}` | {b} |" for a, b in t["perm_rows"])
    features = "\n".join(f"- **{a}**: {b}" for a, b in t["features"])
    return f"""<div align="center">

<img src="{r('docs/assets/banner.png')}" alt="{t['alt']}" width="100%">

# EarCue

{t['tagline']}

[{t['link_download']}]({REPO}/releases/latest) · [{t['link_format']}]({r('skills/earcue-script/reference/format.md')}) · [{t['link_issues']}]({REPO}/issues)

{BADGES}

{switcher(code)}

</div>

## {t['h_features']}

{features}

## {t['h_demo']}

<p align="center">
  <img src="{r('docs/assets/screen-home-light.png')}" width="19%" alt="Home, light theme">
  <img src="{r('docs/assets/screen-home-dark.png')}" width="19%" alt="Home, dark theme">
  <img src="{r('docs/assets/screen-library.png')}" width="19%" alt="Script library with tag filter">
  <img src="{r('docs/assets/screen-history.png')}" width="19%" alt="Script history">
  <img src="{r('docs/assets/screen-stage.png')}" width="19%" alt="Stage mode">
</p>

{t['demo_text'].replace('](examples/green-streets/)', '](' + r('examples/green-streets/') + ')')}

## {t['h_arch']}

{MERMAID}

{t['arch_text']} [{docs}architecture.md]({r(docs + 'architecture.md')})

## {t['h_tech']}

<details>
<summary>{t['sum_stack']}</summary>

| {t['col_part']} | {t['col_choice']} |
|---|---|
{stack}

</details>

<details>
<summary>{t['sum_format']}</summary>

{FORMAT}

{t['format_text']} [format.md]({r('skills/earcue-script/reference/format.md')})

</details>

<details>
<summary>{t['sum_perm']}</summary>

| {t['col_perm']} | {t['col_why']} |
|---|---|
{perms}

{t['perm_net']}

</details>

## {t['h_install']}

{t['install_text']}

{t['build_text']}

```bash
./gradlew assembleRelease
```

{t['guide_text']} [{docs}installation.md]({r(docs + 'installation.md')})

## {t['h_structure']}

{TREE}

## {t['h_contrib']}

{t['contrib_text']} [CONTRIBUTING.md]({r('CONTRIBUTING.md')})

## {t['h_license']}

{t['license_text']} [LICENSE]({r('LICENSE')})
"""


def main():
    for code, file, _ in LANGS:
        out = ROOT / file
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(render(code, load(code)), encoding="utf-8")
    print("wrote", ", ".join(f for _, f, _ in LANGS))


if __name__ == "__main__":
    main()
