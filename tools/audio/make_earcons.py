#!/usr/bin/env python3
"""Generate the two reminder tones as 16-bit mono WAV files in app/src/main/res/raw/.

They are app resources (not files written at run time) because the speech engine that plays
them runs in its own process and can read another app's resources but not its private files.

Usage: python tools/audio/make_earcons.py
"""
import math
import pathlib
import struct

RATE = 24_000
OUT = pathlib.Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "res" / "raw"

TONES = {
    # two rising notes (E5 -> B5) before the first line of a new slide
    "earcon_page.wav": [(659.25, 90), (987.77, 150)],
    # two falling notes (G5 -> C5) when the next line belongs to someone else
    "earcon_turn.wav": [(783.99, 110), (523.25, 170)],
}


def render(notes):
    gap = RATE * 25 // 1000
    fade = RATE * 12 // 1000
    out = []
    for n, (hz, ms) in enumerate(notes):
        length = RATE * ms // 1000
        for i in range(length):
            t = i / RATE
            env = min(1.0, i / fade, (length - i) / (fade * 4))
            v = (math.sin(2 * math.pi * hz * t) + 0.25 * math.sin(4 * math.pi * hz * t)) / 1.25 * env * 0.32
            out.append(int(v * 32767))
        if n < len(notes) - 1:
            out.extend([0] * gap)
    return out


def write(path, samples):
    data = struct.pack(f"<{len(samples)}h", *samples)
    header = b"RIFF" + struct.pack("<I", 36 + len(data)) + b"WAVE"
    header += b"fmt " + struct.pack("<IHHIIHH", 16, 1, 1, RATE, RATE * 2, 2, 16)
    header += b"data" + struct.pack("<I", len(data))
    path.write_bytes(header + data)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, notes in TONES.items():
        samples = render(notes)
        write(OUT / name, samples)
        print(f"{name}: {len(samples) / RATE * 1000:.0f} ms")


if __name__ == "__main__":
    main()
