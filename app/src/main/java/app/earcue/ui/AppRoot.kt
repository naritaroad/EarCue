package app.earcue.ui

import android.graphics.Color as AndroidColor
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.earcue.EarCueApp
import app.earcue.R

enum class Screen { HOME, CUES, CUE, LIBRARY, SCRIPT, IMPORT, SETTINGS, VOICES, LAYOUT, STAGE }

@Composable
fun AppRoot(app: EarCueApp, activity: ComponentActivity) {
    val settings by app.prefs.settings.collectAsState()
    val dark = isDarkTheme(settings.theme)

    LaunchedEffect(dark) {
        val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    EarCueTheme(dark) { Screens(app, activity) }
}

@Composable
private fun Screens(app: EarCueApp, activity: ComponentActivity) {
    val settings by app.prefs.settings.collectAsState()
    val st by app.engine.state.collectAsState()
    val notice by app.notice.collectAsState()
    val pending by app.pendingImport.collectAsState()
    val imported by app.imported.collectAsState()
    val stack = remember { mutableStateListOf(Screen.HOME) }
    var cueIndex by remember { mutableIntStateOf(0) }
    var scriptId by remember { mutableLongStateOf(-1L) }
    var importTarget by remember { mutableStateOf<Long?>(null) }
    val screen = stack.last()

    fun go(s: Screen) {
        if (stack.last() != s) stack.add(s)
    }
    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
    fun home() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    BackHandler(enabled = stack.size > 1 && screen != Screen.STAGE) { back() }

    LaunchedEffect(st.blockedAt) {
        if (st.blockedAt != 0L) app.say(st.blockedReason)
    }
    LaunchedEffect(imported) {
        if (imported > 0) home()
    }

    val keepOn = settings.keepScreenOn && (screen == Screen.STAGE || st.talkStart != 0L)
    DisposableEffect(keepOn) {
        if (keepOn) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { }
    }

    Box(Modifier.fillMaxSize().background(if (screen == Screen.STAGE) Color.Black else Ink.bg)) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.985f)) togetherWith fadeOut(tween(110)) },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.HOME -> HomeScreen(app, st, settings, ::go)
                Screen.CUES -> CuesScreen(app, st, ::back) { cueIndex = it; go(Screen.CUE) }
                Screen.CUE -> CueDetailScreen(app, st, cueIndex, ::back)
                Screen.LIBRARY -> LibraryScreen(
                    app, st, ::back,
                    onPaste = { importTarget = null; go(Screen.IMPORT) },
                    onOpened = ::home,
                    onDetail = { scriptId = it; go(Screen.SCRIPT) },
                )
                Screen.SCRIPT -> ScriptDetailScreen(
                    app, st, scriptId, ::back, onUse = ::home,
                    onPasteRevision = { importTarget = scriptId; go(Screen.IMPORT) },
                )
                Screen.IMPORT -> ImportScreen(app, importTarget, ::back) { if (importTarget != null) back() else home() }
                Screen.SETTINGS -> SettingsScreen(app, st, settings, ::back, onLayout = { go(Screen.LAYOUT) }, onVoices = { go(Screen.VOICES) })
                Screen.VOICES -> VoicesScreen(app, st, settings, ::back)
                Screen.LAYOUT -> LayoutEditorScreen(app, settings, ::back)
                Screen.STAGE -> StageScreen(app, st, settings, ::back)
            }
        }
        NoticeHost(notice, Modifier.align(Alignment.TopCenter))
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = { app.pendingImport.value = null },
            containerColor = Ink.surface,
            title = { Text(stringResource(R.string.dup_title), color = Ink.text) },
            text = { Text(stringResource(R.string.dup_body, p.match.title, p.match.headRev + 1), color = Ink.dim) },
            confirmButton = {
                TextButton(onClick = { app.resolvePending(asRevision = true) }) { Text(stringResource(R.string.dup_revision), color = Ink.amber) }
            },
            dismissButton = {
                TextButton(onClick = { app.resolvePending(asRevision = false) }) { Text(stringResource(R.string.dup_new), color = Ink.text) }
            },
        )
    }
}
