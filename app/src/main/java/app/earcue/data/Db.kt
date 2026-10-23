package app.earcue.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Plain SQLite through the platform helper, no ORM.
 *
 * Scripts keep a git-like history: every import, update or revert appends a row to `revisions`
 * holding the full source; `cues` is the materialized head. Reverting never deletes history,
 * it appends a new revision with the old content. Recordings are keyed by cue text, so they
 * follow a line across revisions.
 */
class Db(context: Context) : SQLiteOpenHelper(context, "earcue.db", null, 2) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE scripts(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                speaker TEXT NOT NULL DEFAULT '',
                lang TEXT NOT NULL DEFAULT 'en-US',
                source TEXT NOT NULL,
                position INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                tags TEXT NOT NULL DEFAULT '',
                head_rev INTEGER NOT NULL DEFAULT 1)"""
        )
        createCues(db)
        createRevisions(db)
        createRecordings(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE scripts ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE scripts ADD COLUMN head_rev INTEGER NOT NULL DEFAULT 1")
            createRevisions(db)
            createRecordings(db)
            db.execSQL(
                """INSERT INTO revisions(script_id, rev, title, source, kind, arg, cue_count, added, removed, created_at)
                   SELECT s.id, 1, s.title, s.source, 'IMPORT', '',
                          (SELECT COUNT(*) FROM cues c WHERE c.script_id = s.id),
                          (SELECT COUNT(*) FROM cues c WHERE c.script_id = s.id), 0, s.created_at
                   FROM scripts s"""
            )
            db.execSQL(
                """INSERT OR REPLACE INTO recordings(script_id, text, path)
                   SELECT script_id, text, recording FROM cues WHERE recording IS NOT NULL"""
            )
            // Old cues keep their now-unused recording column; SQLite cannot drop it cheaply and it is ignored.
        }
    }

    private fun createCues(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE cues(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                script_id INTEGER NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
                ord INTEGER NOT NULL,
                section TEXT NOT NULL DEFAULT '',
                slide TEXT NOT NULL DEFAULT '',
                minutes REAL NOT NULL DEFAULT 0,
                text TEXT NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                partner TEXT NOT NULL DEFAULT '',
                recording TEXT)"""
        )
        db.execSQL("CREATE INDEX cues_script ON cues(script_id, ord)")
    }

    private fun createRevisions(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE revisions(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                script_id INTEGER NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
                rev INTEGER NOT NULL,
                title TEXT NOT NULL,
                source TEXT NOT NULL,
                kind TEXT NOT NULL,
                arg TEXT NOT NULL DEFAULT '',
                cue_count INTEGER NOT NULL,
                added INTEGER NOT NULL DEFAULT 0,
                removed INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                UNIQUE(script_id, rev))"""
        )
    }

    private fun createRecordings(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE recordings(
                script_id INTEGER NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
                text TEXT NOT NULL,
                path TEXT NOT NULL,
                PRIMARY KEY(script_id, text))"""
        )
    }

    // ---- scripts ----

    fun createScript(script: ParsedScript, source: String, fileName: String): Long = tx { db ->
        val now = System.currentTimeMillis()
        val id = db.insertOrThrow("scripts", null, ContentValues().apply {
            put("title", script.title)
            put("speaker", script.speaker)
            put("lang", script.lang)
            put("source", source)
            put("created_at", now)
            put("updated_at", now)
            put("tags", script.tags.joinToString("\n"))
            put("head_rev", 1)
        })
        insertRevision(db, id, 1, script, source, RevisionKind.IMPORT, fileName, script.cues.size, 0, now)
        writeCues(db, id, script)
        id
    }

    /** Appends a revision and makes it the head. Returns the new revision number. */
    fun addRevision(scriptId: Long, script: ParsedScript, source: String, kind: RevisionKind, arg: String): Int = tx { db ->
        val before = cueTexts(db, scriptId)
        val after = script.cues.map { it.text }
        val added = after.count { it !in before }
        val removed = before.count { it !in after }
        val rev = (db.longQuery("SELECT MAX(rev) FROM revisions WHERE script_id = ?", scriptId) ?: 0L).toInt() + 1
        val now = System.currentTimeMillis()
        insertRevision(db, scriptId, rev, script, source, kind, arg, added, removed, now)
        val tags = normalizeTags(tags(db, scriptId) + script.tags)
        db.update("scripts", ContentValues().apply {
            put("title", script.title)
            put("speaker", script.speaker)
            put("lang", script.lang)
            put("source", source)
            put("updated_at", now)
            put("tags", tags.joinToString("\n"))
            put("head_rev", rev)
        }, "id = ?", arrayOf(scriptId.toString()))
        db.delete("cues", "script_id = ?", arrayOf(scriptId.toString()))
        writeCues(db, scriptId, script)
        rev
    }

    fun revisions(scriptId: Long): List<Revision> = readableDatabase.rawQuery(
        "SELECT rev, title, kind, arg, cue_count, added, removed, created_at FROM revisions WHERE script_id = ? ORDER BY rev DESC",
        arrayOf(scriptId.toString())
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                Revision(
                    c.getInt(0), c.getString(1), runCatching { RevisionKind.valueOf(c.getString(2)) }.getOrDefault(RevisionKind.IMPORT),
                    c.getString(3), c.getInt(4), c.getInt(5), c.getInt(6), c.getLong(7),
                )
            )
        }
    }

    fun revisionSource(scriptId: Long, rev: Int): String? = readableDatabase.rawQuery(
        "SELECT source FROM revisions WHERE script_id = ? AND rev = ?", arrayOf(scriptId.toString(), rev.toString())
    ).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    fun listScripts(): List<ScriptMeta> = readableDatabase.rawQuery(
        """SELECT s.id, s.title, s.speaker, s.lang, s.updated_at, s.position,
                  (SELECT COUNT(*) FROM cues c WHERE c.script_id = s.id), s.tags, s.head_rev
           FROM scripts s ORDER BY s.updated_at DESC""", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                ScriptMeta(
                    c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                    c.getString(7).split("\n").filter { it.isNotBlank() },
                    c.getInt(6), c.getLong(4), c.getInt(5), c.getInt(8),
                )
            )
        }
    }

    fun script(id: Long): ScriptMeta? = listScripts().firstOrNull { it.id == id }

    fun setTags(scriptId: Long, tags: List<String>) {
        writableDatabase.update("scripts", ContentValues().apply { put("tags", normalizeTags(tags).joinToString("\n")) },
            "id = ?", arrayOf(scriptId.toString()))
    }

    fun cues(scriptId: Long): List<Cue> = readableDatabase.rawQuery(
        """SELECT c.id, c.ord, c.section, c.slide, c.minutes, c.text, c.note, c.partner, r.path
           FROM cues c LEFT JOIN recordings r ON r.script_id = c.script_id AND r.text = c.text
           WHERE c.script_id = ? ORDER BY c.ord""",
        arrayOf(scriptId.toString())
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                Cue(c.getLong(0), c.getInt(1), c.getString(2), c.getString(3), c.getDouble(4),
                    c.getString(5), c.getString(6), c.getString(7), c.getString(8))
            )
        }
    }

    /** Scripts whose cue text or notes contain [query] (case-insensitive for ASCII). */
    fun scriptsWithText(query: String): Set<Long> = readableDatabase.rawQuery(
        "SELECT DISTINCT script_id FROM cues WHERE text LIKE ? ESCAPE '!' OR note LIKE ? ESCAPE '!'",
        arrayOf(likeArg(query), likeArg(query))
    ).use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }

    private fun likeArg(q: String) = "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"

    fun savePosition(scriptId: Long, position: Int) {
        writableDatabase.update("scripts", ContentValues().apply { put("position", position) }, "id = ?", arrayOf(scriptId.toString()))
    }

    fun setRecording(scriptId: Long, text: String, path: String?) {
        if (path == null) {
            writableDatabase.delete("recordings", "script_id = ? AND text = ?", arrayOf(scriptId.toString(), text))
        } else {
            writableDatabase.insertWithOnConflict("recordings", null, ContentValues().apply {
                put("script_id", scriptId)
                put("text", text)
                put("path", path)
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun recordings(scriptId: Long): List<String> = readableDatabase.rawQuery(
        "SELECT path FROM recordings WHERE script_id = ?", arrayOf(scriptId.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    fun deleteScript(id: Long) {
        writableDatabase.delete("scripts", "id = ?", arrayOf(id.toString()))
    }

    // ---- helpers ----

    private fun insertRevision(
        db: SQLiteDatabase, scriptId: Long, rev: Int, script: ParsedScript, source: String,
        kind: RevisionKind, arg: String, added: Int, removed: Int, now: Long,
    ) {
        db.insertOrThrow("revisions", null, ContentValues().apply {
            put("script_id", scriptId)
            put("rev", rev)
            put("title", script.title)
            put("source", source)
            put("kind", kind.name)
            put("arg", arg)
            put("cue_count", script.cues.size)
            put("added", added)
            put("removed", removed)
            put("created_at", now)
        })
    }

    private fun writeCues(db: SQLiteDatabase, scriptId: Long, script: ParsedScript) {
        script.cues.forEachIndexed { i, c ->
            db.insertOrThrow("cues", null, ContentValues().apply {
                put("script_id", scriptId)
                put("ord", i)
                put("section", c.section)
                put("slide", c.slide)
                put("minutes", c.minutes)
                put("text", c.text)
                put("note", c.note)
                put("partner", c.partner)
            })
        }
    }

    private fun cueTexts(db: SQLiteDatabase, scriptId: Long): Set<String> =
        db.rawQuery("SELECT text FROM cues WHERE script_id = ?", arrayOf(scriptId.toString())).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }

    private fun tags(db: SQLiteDatabase, scriptId: Long): List<String> =
        db.rawQuery("SELECT tags FROM scripts WHERE id = ?", arrayOf(scriptId.toString())).use { c ->
            if (c.moveToFirst()) c.getString(0).split("\n").filter { it.isNotBlank() } else emptyList()
        }

    private fun SQLiteDatabase.longQuery(sql: String, arg: Long): Long? =
        rawQuery(sql, arrayOf(arg.toString())).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }

    private inline fun <T> tx(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            return block(db).also { db.setTransactionSuccessful() }
        } finally {
            db.endTransaction()
        }
    }
}
