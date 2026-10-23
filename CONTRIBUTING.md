# Contributing

Thanks for helping with EarCue.

- **Bugs and ideas**: open an issue with the phone model, Android version, speech engine and the steps to reproduce.
- **Code**: keep the app dependency-free beyond AndroidX Compose. Match the existing style: one file per screen, colors from `Ink`, strings from resources.
- **UI text**: edit `tools/i18n/en.py` and every other locale table, then run `python tools/i18n/gen_strings.py`. The script fails on missing keys or mismatched placeholders. Never edit `res/values*/strings.xml` by hand.
- **Script format**: changes to `earcue/1` must update `ScriptParser.kt`, `skills/earcue-script/reference/format.md` and `scripts/validate.py` together.
- **Sample content**: only fictional material goes into `examples/`. Do not commit real transcripts or names.

Contributions are accepted under the project [LICENSE](LICENSE).
