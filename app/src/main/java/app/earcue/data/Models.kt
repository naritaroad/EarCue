package app.earcue.data

/** One playable segment of a script. Partner cues are lines spoken by a co-presenter. */
data class Cue(
    val id: Long,
    val ord: Int,
    val section: String,
    val slide: String,
    val sectionMinutes: Double,
    val text: String,
    val note: String,
    val partner: String,
    val recording: String?,
) {
    val isPartner: Boolean get() = partner.isNotBlank()
}

data class ScriptMeta(
    val id: Long,
    val title: String,
    val speaker: String,
    val lang: String,
    val tags: List<String>,
    val cueCount: Int,
    val updatedAt: Long,
    val position: Int,
    val headRev: Int,
)

/** How a revision came to be; rendered as a localized history line. */
enum class RevisionKind { IMPORT, UPDATE, REVERT }

data class Revision(
    val rev: Int,
    val title: String,
    val kind: RevisionKind,
    /** File name for IMPORT/UPDATE, the reverted-to revision number for REVERT. */
    val arg: String,
    val cueCount: Int,
    val added: Int,
    val removed: Int,
    val createdAt: Long,
)

data class ParsedCue(
    val section: String,
    val slide: String,
    val minutes: Double,
    val text: String,
    val note: String,
    val partner: String,
) {
    val isPartner: Boolean get() = partner.isNotBlank()
}

data class ParsedScript(
    val title: String,
    val speaker: String,
    val lang: String,
    val tags: List<String>,
    val cues: List<ParsedCue>,
)

fun normalizeTags(raw: Iterable<String>): List<String> =
    raw.map { it.trim().trimStart('#') }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(12)
