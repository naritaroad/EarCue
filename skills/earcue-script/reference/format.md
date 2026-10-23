# EarCue script format (earcue/1)

## JSON (preferred)

```json
{
  "format": "earcue/1",
  "title": "Talk title · role",
  "speaker": "P2 Mina Park",
  "lang": "en-US",
  "tags": ["paper-review", "urban-heat", "co-presenting"],
  "source": "original transcript file name (optional, informational)",
  "sections": [
    {
      "title": "Slide or part title",
      "slide": "P2",
      "minutes": 2.5,
      "cues": [
        { "partner": "P1 Alex Rivera", "text": "Line the co-presenter says first." },
        { "text": "My line, verbatim.", "note": "Optional on-screen hint" },
        "A plain string is also a valid cue (text only)."
      ]
    }
  ]
}
```

| Field | Required | Meaning |
|---|---|---|
| `format` | yes | Must start with `earcue/`. Use `earcue/1`. |
| `title` | yes | Library title. Also the key for revisions: importing a file with an existing title offers "new revision". |
| `speaker` | no | The presenter this file is for. |
| `lang` | yes | BCP-47 tag of the spoken language; picks the TTS voice. |
| `tags` | no | 2–6 short strings. Shown as filter chips in the library; merged with the user's own tags on update. |
| `sections[].title` | no | Shown above the cue. |
| `sections[].slide` | no | Short slide label, shown as an amber tag. |
| `sections[].minutes` | no | Planned duration; the app vibrates when the section runs over. |
| `cues[].text` | yes | What the earphones say. Verbatim. |
| `cues[].note` | no | On-screen only. Use `\n` for line breaks. |
| `cues[].partner` | no | Name of another speaker. Marks a partner cue: shown as "wait for <name>", skipped by "next" unless the user enables reading partner lines. |

A top-level `"cues": [...]` without `sections` is accepted for very short scripts.

## Revisions

The app keeps a git-like history per script. Updating a script (same title, or "Update from file" on the script page) appends a revision; restoring an old revision appends a copy of it, so history is never rewritten. Keep the `title` stable across versions of the same talk, and change it for a different talk or role.

## Plain-text alternative

For quick hand-typed scripts pasted into the app:

```
title: My talk
speaker: P2
lang: en-US
tags: class-report, demo

# Section title | P2 | 3
> P1 Host: partner line
My own line.
// note attached to the line above
```

- Header lines (`title:`, `speaker:`, `lang:`, `tags:`) come before the first cue. Tags are comma-separated.
- `# title | slide | minutes` starts a section; slide and minutes are optional.
- `> Name: text` is a partner cue. `//` adds a note to the previous cue.
- Every other non-empty line is one cue.

## Edge cases
- Two consecutive lines by the same partner: keep two partner cues (each verbatim). The home screen shows the last two before your turn.
- A section where the presenter only answers questions: still one section; partner cues carry the questions.
- Quotes inside text: keep the transcript's typographic quotes (“ ” ’ 「 」); they are valid JSON without escaping.
- Abbreviations the TTS may misread (e.g. "RQ1"): keep verbatim; the user can record their own voice for that cue in the app.
- Numbers, citations, URLs: keep as written; do not expand.
- Bilingual transcripts: `text` = the language spoken on stage; the other language goes to `note`.
- CJK transcripts: sentence ends are `。！？`; do not split inside `「」` quotes.
