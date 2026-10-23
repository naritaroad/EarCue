#!/usr/bin/env python3
"""Validate an EarCue .earcue.json file.

Usage: python validate.py SCRIPT.earcue.json [--source TRANSCRIPT.(docx|txt|md)] [--wpm 140]

Exit code 1 when there are errors. Warnings do not fail the run.
"""
import argparse
import json
import re
import sys
import unicodedata
import zipfile


def source_text(path):
    if path.lower().endswith(".docx"):
        xml = zipfile.ZipFile(path).read("word/document.xml").decode("utf8")
        paras = re.findall(r"<w:p[ >].*?</w:p>", xml, re.S)
        return "\n".join("".join(re.findall(r"<w:t[^>]*>([^<]*)</w:t>", p)) for p in paras)
    with open(path, encoding="utf8") as f:
        return f.read()


def norm(s):
    s = unicodedata.normalize("NFKC", s)
    s = s.replace("&amp;", "&").replace("&quot;", '"').replace("&apos;", "'")
    s = re.sub(r"[“”„\"]", '"', s)
    s = re.sub(r"[‘’`']", "'", s)
    s = re.sub(r"[–—]", "-", s)
    return re.sub(r"\s+", " ", s).strip().lower()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("script")
    ap.add_argument("--source")
    ap.add_argument("--wpm", type=int, default=140, help="speaking rate for the timing estimate")
    a = ap.parse_args()

    errors, warnings = [], []
    try:
        with open(a.script, encoding="utf-8") as f:
            raw = f.read()
        if raw.startswith("﻿"):
            warnings.append("file starts with a BOM; save as UTF-8 without BOM")
            raw = raw[1:]
        doc = json.loads(raw)
    except Exception as e:  # noqa: BLE001
        print(f"ERROR: cannot read JSON: {e}")
        return 1

    if not str(doc.get("format", "")).startswith("earcue/"):
        errors.append('"format" must be "earcue/1"')
    for key in ("title", "lang"):
        if not str(doc.get(key, "")).strip():
            errors.append(f'missing "{key}"')

    tags = doc.get("tags")
    if tags is None:
        warnings.append('no "tags"; add 2-6 so the app can filter this script')
    elif not isinstance(tags, list) or not all(isinstance(t, str) and t.strip() for t in tags):
        errors.append('"tags" must be a list of non-empty strings')
    else:
        if not 2 <= len(tags) <= 6:
            warnings.append(f"{len(tags)} tags; 2-6 is recommended")
        if len({t.strip().lower() for t in tags}) != len(tags):
            warnings.append("duplicate tags")
        if any(len(t) > 24 for t in tags):
            warnings.append("tags longer than 24 characters are hard to read as chips")

    cjk = str(doc.get("lang", "")).lower().startswith(("zh", "ja", "ko"))

    sections = doc.get("sections")
    if sections is None and isinstance(doc.get("cues"), list):
        sections = [{"title": "", "cues": doc["cues"]}]
    if not isinstance(sections, list) or not sections:
        errors.append('"sections" must be a non-empty list')
        sections = []

    src = norm(source_text(a.source)) if a.source else None
    own = partner = 0
    for si, s in enumerate(sections):
        cues = s.get("cues")
        if not isinstance(cues, list) or not cues:
            errors.append(f"sections[{si}] has no cues")
            continue
        words = 0
        for ci, c in enumerate(cues):
            where = f"sections[{si}].cues[{ci}]"
            if isinstance(c, str):
                c = {"text": c}
            if not isinstance(c, dict) or not str(c.get("text", "")).strip():
                errors.append(f"{where}: missing text")
                continue
            unknown = set(c) - {"text", "note", "partner"}
            if unknown:
                warnings.append(f"{where}: unknown fields {sorted(unknown)} are ignored by the app")
            text = c["text"].strip()
            n = len(re.sub(r"\s", "", text)) if cjk else len(text.split())
            if c.get("partner"):
                partner += 1
            else:
                own += 1
                words += n
                if n > (80 if cjk else 40):
                    unit = "characters" if cjk else "words"
                    warnings.append(f"{where}: {n} {unit}; consider splitting at a sentence end")
            if src is not None and norm(text) not in src:
                errors.append(f"{where}: text not found verbatim in source: {text[:70]}...")
        minutes = s.get("minutes") or 0
        est = words / (a.wpm * 2.2 if cjk else a.wpm)
        label = s.get("slide") or s.get("title") or f"section {si}"
        line = f"  {label}: {words} {'chars' if cjk else 'words'} ≈ {est:.1f} min"
        if minutes:
            line += f" (planned {minutes} min)"
            if est > minutes * 1.15:
                warnings.append(f"{label}: estimated {est:.1f} min exceeds planned {minutes} min")
        print(line)

    if own == 0:
        errors.append("no presenter cues (all cues are partner cues)")
    print(f"cues: {own} own, {partner} partner, {len(sections)} sections")
    for w in warnings:
        print("WARN:", w)
    for e in errors:
        print("ERROR:", e)
    print("OK" if not errors else f"{len(errors)} error(s)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
