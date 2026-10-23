package app.earcue

import android.app.Application
import android.app.LocaleManager
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.provider.OpenableColumns
import app.earcue.audio.CueEngine
import app.earcue.audio.Recorder
import app.earcue.data.Db
import app.earcue.data.ParsedScript
import app.earcue.data.Prefs
import app.earcue.data.RevisionKind
import app.earcue.data.ScriptFormatException
import app.earcue.data.ScriptMeta
import app.earcue.data.ScriptParser
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** A transient message; resolved to text in the UI so it follows the app language. */
data class Notice(val res: Int, val args: List<Any> = emptyList(), val at: Long = System.nanoTime())

/** An import whose title matches an existing script: the user picks "new revision" or "new script". */
data class PendingImport(val parsed: ParsedScript, val source: String, val fileName: String, val match: ScriptMeta)

class EarCueApp : Application() {
    lateinit var db: Db
    lateinit var prefs: Prefs
    lateinit var engine: CueEngine
    lateinit var recorder: Recorder
    val notice = MutableStateFlow<Notice?>(null)
    val pendingImport = MutableStateFlow<PendingImport?>(null)

    /** Bumped after an import creates or switches the active script, so the UI can return home. */
    val imported = MutableStateFlow(0)

    override fun onCreate() {
        super.onCreate()
        db = Db(this)
        prefs = Prefs(this)
        engine = CueEngine(this, db, prefs)
        recorder = Recorder(this)
        val id = db.script(prefs.value.lastScript)?.id ?: db.listScripts().firstOrNull()?.id
        if (id != null) engine.load(id)
    }

    fun say(res: Int, vararg args: Any) {
        notice.value = Notice(res, args.toList())
    }

    fun sayError(e: Throwable) {
        if (e is ScriptFormatException) notice.value = Notice(e.res, e.args.toList())
        else say(R.string.err_generic, e.message.orEmpty())
    }

    // ---- import & history ----

    /**
     * Every import path ends here. With [target] the file becomes a new revision of that script;
     * otherwise a script with the same title triggers a choice, and anything else becomes a new script.
     */
    fun stageImport(raw: String, fileName: String, target: Long? = null) {
        val parsed = runCatching { ScriptParser.parse(raw, fileName.ifBlank { getString(R.string.untitled) }) }
            .getOrElse { sayError(it); return }
        if (target != null) {
            commitUpdate(target, parsed, raw, fileName, goHome = false)
            return
        }
        val match = db.listScripts().firstOrNull { it.title.equals(parsed.title, ignoreCase = true) }
        if (match != null) pendingImport.value = PendingImport(parsed, raw, fileName, match)
        else commitNew(parsed, raw, fileName)
    }

    fun resolvePending(asRevision: Boolean) {
        val p = pendingImport.value ?: return
        pendingImport.value = null
        if (asRevision) commitUpdate(p.match.id, p.parsed, p.source, p.fileName, goHome = true)
        else commitNew(p.parsed, p.source, p.fileName)
    }

    fun importUri(uri: Uri, target: Long? = null) {
        runCatching {
            val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/') ?: ""
            val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error(getString(R.string.err_read))
            text to name
        }.onSuccess { (text, name) -> stageImport(text, name, target) }
            .onFailure { sayError(it) }
    }

    fun revert(scriptId: Long, rev: Int) {
        val source = db.revisionSource(scriptId, rev) ?: return
        runCatching {
            val parsed = ScriptParser.parse(source, getString(R.string.untitled))
            db.addRevision(scriptId, parsed, source, RevisionKind.REVERT, rev.toString())
        }.onSuccess { newRev ->
            if (engine.state.value.script?.id == scriptId) engine.load(scriptId)
            say(R.string.msg_reverted, rev, newRev)
        }.onFailure { sayError(it) }
    }

    fun setTags(scriptId: Long, tags: List<String>) {
        db.setTags(scriptId, tags)
        engine.refreshMeta()
    }

    fun deleteScript(id: Long) {
        db.recordings(id).forEach { File(it).delete() }
        db.deleteScript(id)
        if (engine.state.value.script?.id == id) {
            val other = db.listScripts().firstOrNull()
            if (other != null) engine.load(other.id) else engine.unload()
        }
    }

    private fun commitNew(parsed: ParsedScript, raw: String, fileName: String) {
        runCatching { db.createScript(parsed, raw, fileName) }
            .onSuccess {
                engine.load(it)
                say(R.string.msg_imported, parsed.title)
                imported.value++
            }.onFailure { sayError(it) }
    }

    private fun commitUpdate(id: Long, parsed: ParsedScript, raw: String, fileName: String, goHome: Boolean) {
        runCatching { db.addRevision(id, parsed, raw, RevisionKind.UPDATE, fileName) }
            .onSuccess { rev ->
                engine.load(id)
                say(R.string.msg_updated, rev)
                if (goHome) imported.value++
            }.onFailure { sayError(it) }
    }

    // ---- language ----

    /** Switches the UI language; "" follows the system. Android 13+ stores it per app, older versions recreate the activity. */
    fun setLanguage(tag: String) {
        prefs.update { it.copy(language = tag) }
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        }
    }

    fun currentLanguage(): String = if (Build.VERSION.SDK_INT >= 33) {
        getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
    } else {
        prefs.value.language
    }
}
