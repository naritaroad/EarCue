package app.earcue.ui

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.earcue.EarCueApp
import app.earcue.R
import app.earcue.audio.EngineState
import app.earcue.data.DeckButton
import app.earcue.data.Settings

/**
 * For the phone hidden behind the back: black, near-zero brightness, system bars hidden,
 * the same button layout stretched over the whole screen. Leaving takes two back presses
 * so a stray edge swipe from the thumb cannot drop the presenter out mid-talk.
 */
@Composable
fun StageScreen(app: EarCueApp, st: EngineState, settings: Settings, onExit: () -> Unit) {
    val activity = LocalContext.current as Activity
    var lastBack by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        val attrs = window.attributes
        val previous = attrs.screenBrightness
        attrs.screenBrightness = 0.02f
        window.attributes = attrs
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            val a = window.attributes
            a.screenBrightness = previous
            window.attributes = a
        }
    }

    BackHandler {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBack < 2000) {
            onExit()
        } else {
            lastBack = now
            app.engine.haptics.edge()
            app.say(R.string.stage_back_again)
        }
    }

    // The stage is always dark, whatever the app theme: a lit screen behind the back draws eyes.
    EarCueTheme(dark = true) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                val dot = when {
                    st.output.headphones && !st.output.muted -> Ink.mint
                    st.output.headphones -> Ink.amber
                    else -> Ink.coral
                }
                Box(Modifier.size(7.dp).clip(CircleShape).background(dot.copy(alpha = 0.6f)))
                Spacer(Modifier.width(10.dp))
                Text("${st.index + 1} / ${st.cues.size}", style = MaterialTheme.typography.labelMedium, color = Ink.faint)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.stage_hint), style = MaterialTheme.typography.labelSmall, color = Ink.faint.copy(alpha = 0.6f))
            }
            Deck(
                settings.deck,
                DeckLook(st.playing, st.played, "", stage = true),
                Modifier.weight(1f).fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
            ) { button ->
                when (button) {
                    DeckButton.NEXT -> app.engine.next()
                    DeckButton.PREV -> app.engine.prev()
                    DeckButton.TOGGLE -> app.engine.toggle()
                }
            }
        }
    }
}
}
