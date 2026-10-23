package app.earcue.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Records the presenter's own voice for a cue; the engine prefers a recording over TTS. */
class Recorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var target: File? = null

    val isRecording: Boolean get() = recorder != null

    fun start(cueId: Long): Boolean {
        stop(keep = false)
        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(dir, "cue_${cueId}_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(96_000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            target = file
            true
        } catch (e: Exception) {
            r.release()
            false
        }
    }

    /** Stops recording; returns the saved file path, or null when nothing usable was captured. */
    fun stop(keep: Boolean = true): String? {
        val r = recorder ?: return null
        val file = target
        recorder = null
        target = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        if (!ok || !keep || file == null || file.length() == 0L) {
            file?.delete()
            return null
        }
        return file.absolutePath
    }
}
