package app.earcue.audio

import app.earcue.R

/**
 * The two reminder tones, handed to the speech engine as earcons so they queue with speech,
 * flush with it, and go to the same earphones.
 *  - page: two rising notes before the first line of a new slide ("flip the slide").
 *  - turn: two falling notes after a line when the next one belongs to someone else ("hand over").
 *
 * The WAV files are app resources made by tools/audio/make_earcons.py. They must be resources:
 * the engine plays them from its own process, which cannot read EarCue's private files.
 */
object Earcons {
    const val PAGE = "[earcue-page]"
    const val TURN = "[earcue-turn]"

    val resources = mapOf(PAGE to R.raw.earcon_page, TURN to R.raw.earcon_turn)
}
