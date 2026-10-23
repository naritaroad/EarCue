---
name: earcue-script
description: Convert a speech transcript (docx, pdf, txt, md, pptx notes) in any language into an EarCue `.earcue.json` cue file with tags for one presenter. Use when the user asks to prepare, convert, split or tag a script for the EarCue earpiece-prompter app.
---

# EarCue script conversion

Turn a transcript into the cue file the EarCue Android app imports. The app reads each cue aloud in the presenter's earphones; the presenter taps "next" to hear the following cue. The library groups and searches scripts by their tags.

## Steps
1. Get the source text. `.docx`: extract paragraphs (python `zipfile` + `word/document.xml`). `.pdf`: a PDF text tool. `.pptx`: slide text and speaker notes.
2. Ask (or infer from the request) the presenter's role/name, the spoken language and whether co-presenter lines become partner cues.
3. Find every section where that presenter speaks, in talk order. One section per slide or part.
4. Build cues following the rules below and the schema in `reference/format.md`.
5. Add 2–6 tags (rules below).
6. Save as `<talk>-<role>.earcue.json` (UTF-8, no BOM).
7. Validate: `python scripts/validate.py <file> --source <transcript>`. Fix every error; review warnings.
8. Tell the user how to load it: copy to the phone, then EarCue → Scripts → Import file, or share the file to EarCue. Importing a file whose title matches an existing script offers to save it as a new revision of that script.

## Cue rules
- **Verbatim**: the presenter's `text` is copied exactly from the transcript in the spoken language. Never paraphrase, translate or fix grammar.
- **Chunk for shadowing**: 1–2 sentences per cue; about 8–30 words, or 15–60 characters for Chinese, Japanese and Korean. Split only at sentence ends.
- **Partner cues**: the line another speaker says right before this presenter's turn becomes `{ "partner": "<name>", "text": ... }`, verbatim. Include hand-over questions and dialogue turns.
- **note**: optional on-screen hint, such as a translation or "→ hand over to P3". Keep it short. Split a translation so it matches the chunk.
- **lang**: BCP-47 tag of the spoken language (`en-US`, `zh-TW`, `zh-CN`, `es-ES`, `pt-BR`, `ja-JP`, `ko-KR`, `fr-FR`, `de-DE`, `ru-RU`, …). It selects the TTS voice.
- **Section metadata**: `slide` = label as written in the deck (e.g. `P4`); `minutes` = planned time when the transcript gives one.
- Drop headings, speaker labels, timing tables and slide bullets from `text`. Add nothing that is not in the source.

## Tag rules
- 2–6 tags, lowercase, kebab-case for English (`paper-review`), natural words for other languages.
- Write tags in the user's language unless they ask otherwise.
- Cover: occasion (`class-report`, `conference`, `pitch`), topic (1–3 subject words), format (`co-presenting`, `solo`), plus `demo` for sample material.
- No names of people, no dates, no tags that repeat the title.

## Files
- `reference/format.md`: full JSON schema, the plain-text alternative, revision behavior and edge cases. Read before writing the first file in a session.
- `examples/sample.earcue.json`: a short valid example with dialogue, partner cues and tags.
- `scripts/validate.py`: schema and tag check, chunk-length warnings, timing estimate, verbatim check against the source (`--source` accepts .docx/.txt/.md).
