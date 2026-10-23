package app.earcue.data

import app.earcue.R
import org.json.JSONArray
import org.json.JSONObject

/** A parse error carrying a string resource, so the UI shows it in the user's language. */
class ScriptFormatException(val res: Int, vararg val args: Any) : Exception("format error $res")

/**
 * Reads the two EarCue script formats:
 *  - JSON  (`"format": "earcue/1"`), what the AI conversion skill produces.
 *  - Plain text, for quick hand-typed or pasted scripts:
 *      title: My talk          (header lines before the first cue)
 *      speaker: Me
 *      lang: en-US
 *      tags: class, ai
 *      # Section title | P2 | 3      (title | slide | minutes)
 *      > P1 Host: a partner line
 *      A line I will hear.
 *      // a note shown on screen only, attached to the cue above
 */
object ScriptParser {

    fun parse(raw: String, fallbackTitle: String): ParsedScript {
        val text = raw.trim().removePrefix("﻿")
        if (text.isEmpty()) throw ScriptFormatException(R.string.err_empty)
        val script = if (text.startsWith("{")) parseJson(text, fallbackTitle) else parseText(text, fallbackTitle)
        if (script.cues.all { it.isPartner }) throw ScriptFormatException(R.string.err_no_cues)
        return script
    }

    private fun parseJson(text: String, fallbackTitle: String): ParsedScript {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw ScriptFormatException(R.string.err_json, e.message.orEmpty())
        }
        val format = root.optString("format", "earcue/1")
        if (!format.startsWith("earcue/")) throw ScriptFormatException(R.string.err_format, format)

        val cues = mutableListOf<ParsedCue>()
        val sections = root.optJSONArray("sections")
        if (sections != null) {
            for (i in 0 until sections.length()) {
                val s = sections.optJSONObject(i) ?: throw ScriptFormatException(R.string.err_field, "sections[$i]")
                val title = s.optString("title")
                val slide = s.optString("slide")
                val minutes = s.optDouble("minutes", 0.0).takeUnless { it.isNaN() } ?: 0.0
                readCues(s.optJSONArray("cues"), "sections[$i].cues") { t, n, p ->
                    cues += ParsedCue(title, slide, minutes, t, n, p)
                }
            }
        } else {
            readCues(root.optJSONArray("cues"), "cues") { t, n, p -> cues += ParsedCue("", "", 0.0, t, n, p) }
        }
        val tags = root.optJSONArray("tags")?.let { arr -> (0 until arr.length()).map { arr.optString(it) } }.orEmpty()
        return ParsedScript(
            title = root.optString("title").ifBlank { fallbackTitle },
            speaker = root.optString("speaker"),
            lang = root.optString("lang").ifBlank { "en-US" },
            tags = normalizeTags(tags),
            cues = cues,
        )
    }

    private inline fun readCues(arr: JSONArray?, path: String, add: (String, String, String) -> Unit) {
        if (arr == null) throw ScriptFormatException(R.string.err_missing, path)
        for (j in 0 until arr.length()) {
            when (val c = arr.opt(j)) {
                is String -> if (c.isNotBlank()) add(c.trim(), "", "")
                is JSONObject -> {
                    val t = c.optString("text").trim()
                    if (t.isEmpty()) throw ScriptFormatException(R.string.err_missing, "$path[$j].text")
                    add(t, c.optString("note").trim(), c.optString("partner").trim())
                }
                else -> throw ScriptFormatException(R.string.err_field, "$path[$j]")
            }
        }
    }

    private val header = Regex("""^(title|speaker|lang|tags)\s*[:：]\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val partnerLine = Regex("""^>\s*([^:：]{1,40})[:：]\s*(.+)$""")

    private fun parseText(text: String, fallbackTitle: String): ParsedScript {
        var title = ""
        var speaker = ""
        var lang = "en-US"
        var tags = emptyList<String>()
        var section = ""
        var slide = ""
        var minutes = 0.0
        val cues = mutableListOf<ParsedCue>()

        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val h = header.find(line)
            if (h != null && cues.isEmpty()) {
                val v = h.groupValues[2].trim()
                when (h.groupValues[1].lowercase()) {
                    "title" -> title = v
                    "speaker" -> speaker = v
                    "lang" -> lang = v.ifBlank { "en-US" }
                    "tags" -> tags = normalizeTags(v.split(',', '，', '、'))
                }
                continue
            }
            when {
                line.startsWith("#") -> {
                    val parts = line.trimStart('#').split("|").map { it.trim() }
                    section = parts.getOrElse(0) { "" }
                    slide = parts.getOrElse(1) { "" }
                    minutes = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0
                }
                line.startsWith("//") -> {
                    val note = line.removePrefix("//").trim()
                    if (cues.isNotEmpty()) {
                        val last = cues.removeAt(cues.lastIndex)
                        cues += last.copy(note = listOf(last.note, note).filter { it.isNotBlank() }.joinToString("\n"))
                    }
                }
                line.startsWith(">") -> {
                    val m = partnerLine.find(line)
                    val who = m?.groupValues?.get(1)?.trim() ?: "Partner"
                    val said = m?.groupValues?.get(2)?.trim() ?: line.removePrefix(">").trim()
                    cues += ParsedCue(section, slide, minutes, said, "", who)
                }
                else -> cues += ParsedCue(section, slide, minutes, line, "", "")
            }
        }
        return ParsedScript(title.ifBlank { fallbackTitle }, speaker, lang, tags, cues)
    }
}
