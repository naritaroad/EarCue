package app.earcue

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.earcue.data.DeckButton
import app.earcue.ui.AppRoot

class MainActivity : ComponentActivity() {
    private val app get() = application as EarCueApp

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleShare(intent)
        setContent { AppRoot(app, this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    override fun onStart() {
        super.onStart()
        app.engine.setSessionActive(true)
        app.engine.refreshOutput()
    }

    override fun onDestroy() {
        if (isFinishing) app.engine.setSessionActive(false)
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = handleKey(event) || super.dispatchKeyEvent(event)

    private fun handleKey(e: KeyEvent): Boolean {
        val settings = app.prefs.value
        val engine = app.engine
        val down = e.action == KeyEvent.ACTION_DOWN
        val first = down && e.repeatCount == 0
        when (e.keyCode) {
            KeyEvent.KEYCODE_CAMERA -> {
                if (!settings.shutterKey) return false
                // Sony shutter: press = next at once, hold = keep skipping forward (same as holding the Next button).
                if (first) engine.press(DeckButton.NEXT)
                else if (e.action == KeyEvent.ACTION_UP) engine.release()
                return true
            }
            // Half-press of the shutter: swallow it so the camera app never wakes up.
            KeyEvent.KEYCODE_FOCUS -> return settings.shutterKey
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!settings.volumeKeys || engine.state.value.cues.isEmpty()) return false
                if (first) {
                    if (e.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) engine.next() else engine.prev()
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> if (settings.headsetKeys) { if (first) engine.next(); return true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> if (settings.headsetKeys) { if (first) engine.prev(); return true }
            KeyEvent.KEYCODE_MEDIA_STOP -> if (settings.headsetKeys) { if (first) engine.stop(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> if (settings.headsetKeys) {
                if (first) engine.toggle()
                return true
            }
        }
        return false
    }

    /** Accepts scripts shared from other apps (text or a file) and opened .json/.txt files. */
    private fun handleShare(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null -> app.importUri(stream)
                    text != null -> app.stageImport(text, intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty())
                }
            }
            Intent.ACTION_VIEW -> intent.data?.let { app.importUri(it) }
        }
    }

    /** Before Android 13 there is no per-app language API, so the chosen locale is applied here. */
    override fun attachBaseContext(newBase: Context) {
        val tag = (newBase.applicationContext as? EarCueApp)?.prefs?.value?.language.orEmpty()
        if (Build.VERSION.SDK_INT >= 33 || tag.isEmpty()) {
            super.attachBaseContext(newBase)
            return
        }
        val config = Configuration(newBase.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(tag))
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

}
