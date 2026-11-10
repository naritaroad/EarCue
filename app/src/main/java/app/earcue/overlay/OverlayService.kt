package app.earcue.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.os.LocaleList
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.earcue.EarCueApp
import app.earcue.MainActivity
import app.earcue.R
import app.earcue.ui.EarCueTheme
import app.earcue.ui.OverlayHost
import app.earcue.ui.OverlayPanel
import app.earcue.ui.ScriptPane
import app.earcue.ui.ScriptGrip
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import app.earcue.data.OverlaySize
import app.earcue.data.dockScript
import androidx.compose.foundation.layout.fillMaxWidth
import app.earcue.ui.isDarkTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Keeps EarCue usable from other apps: a foreground service (media playback, since it speaks into the
 * earphones) that owns a floating window drawn over other apps. The window shows while EarCue itself is
 * in the background and hides when the user comes back, where the full deck is on screen anyway.
 */
class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    private val app get() = application as EarCueApp
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    private val scope = MainScope()
    private lateinit var wm: WindowManager
    private var view: ComposeView? = null
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Never takes focus or keys from the app underneath; touches outside the controls pass through.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "EarCue floating controls"
    }

    /** The script text display: its own window that never takes a touch (FLAG_NOT_TOUCHABLE). */
    private var pane: ComposeView? = null
    private val paneParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "EarCue script display"
    }

    /** The script box's handle: a small touchable window of its own, at the box's top-left corner. */
    private var scriptGrip: ComposeView? = null
    private val gripParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "EarCue script handle"
    }
    private val paneFolded = mutableStateOf(false)
    private var paneDragging = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
        wm = getSystemService(WindowManager::class.java)
        startInForeground()
        _running.value = true
        app.engine.setSessionActive(true)
        scope.launch {
            combine(app.inForeground, app.prefs.settings) { fg, s -> !fg && s.overlayOn }
                .distinctUntilChanged()
                .collect { show -> if (show && Settings.canDrawOverlays(this@OverlayService)) attach() else detach() }
        }
        scope.launch {
            app.prefs.settings.collect { s ->
                if (!s.overlayOn) stopSelf()
                if (view != null) applyPosition()
                syncPane()
            }
        }
        // Keep the screen on during a talk, as the app does, while the controls float over other apps.
        scope.launch {
            combine(app.engine.state, app.prefs.settings) { st, s -> s.keepScreenOn && st.talkStart != 0L }
                .distinctUntilChanged()
                .collect { on ->
                    val flags = if (on) params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    else params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
                    if (flags != params.flags) {
                        params.flags = flags
                        view?.let { wm.updateViewLayout(it, params) }
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CLOSE) turnOff()
        // Not sticky: after the process dies the controls come back the next time EarCue is opened.
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotating into a landscape slide app must not leave the controls off screen.
        view?.post {
            applyPosition()
            syncPane()
        }
    }

    override fun onDestroy() {
        detach()
        scope.cancel()
        registry.currentState = Lifecycle.State.DESTROYED
        _running.value = false
        if (!app.inForeground.value) app.engine.setSessionActive(false)
        super.onDestroy()
    }

    /** Before Android 13 there is no per-app language API, so the chosen locale is applied here too. */
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

    // ---- window ----

    private fun attach() {
        if (view != null) return
        val v = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { FloatingContent() }
            // Folding, a new style or a new size changes the window size: keep it on screen and at its edge.
            addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
                if (r - l != or - ol || b - t != ob - ot) post {
                    applyPosition()
                    syncPane()
                }
            }
        }
        registry.currentState = Lifecycle.State.RESUMED
        applyPosition(measure = false)
        wm.addView(v, params)
        view = v
        syncPane()
        // The first time, place the controls at the right edge, a little below the middle.
        v.post { applyPosition() }
    }

    private fun detach() {
        removePane()
        removeScriptGrip()
        val v = view ?: return
        view = null
        runCatching { wm.removeView(v) }
        if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) registry.currentState = Lifecycle.State.CREATED
    }

    // Not named Content(): inside ComposeView.apply {} that would call ComposeView.Content() and recurse forever.
    @androidx.compose.runtime.Composable
    private fun FloatingContent() {
        val settings by app.prefs.settings.collectAsState()
        val st by app.engine.state.collectAsState()
        val collapsed by folded
        val join by joinSide
        val host = remember {
            OverlayHost(
                onDrag = { d -> moveBy(d.x, d.y) },
                onDragEnd = { savePosition() },
                onOpen = { openApp() },
                onClose = { turnOff() },
                onScript = { app.prefs.update { it.copy(overlay = it.overlay.copy(scriptOn = !it.overlay.scriptOn)) } },
            )
        }
        EarCueTheme(isDarkTheme(settings.theme)) {
            OverlayPanel(app, st, settings, host, collapsed, join) { fold(it) }
        }
    }

    /** Folded into the small tab, which tucks against the nearer screen edge. */
    private val folded = mutableStateOf(false)

    /** A joined script box: 0 = not joined, 1 = it sits above the controls, 2 = below. Squares the touching corners. */
    private val joinSide = mutableStateOf(0)
    private var panelWidth = 0

    private fun fold(on: Boolean) {
        if (on == folded.value) return
        if (on) panelWidth = view?.width ?: 0
        folded.value = on
        // The layout listener places the window once the new size is known.
    }

    /** Shows, updates or removes the script display and its handle to match the settings; only while the controls are up. */
    private fun syncPane() {
        val o = app.prefs.value.overlay
        if (view == null || !o.scriptOn) {
            removePane()
            removeScriptGrip()
            paneFolded.value = false
            return
        }
        if (o.scriptAttach) {
            syncAttachedPane()
            return
        }
        joinSide.value = 0
        if (!paneDragging) {
            val b = screen()
            paneParams.width = (b.width() * o.scriptWidth / 100f).roundToInt()
            paneParams.height = if (o.scriptHeight > 0) (b.height() * o.scriptHeight / 100f).roundToInt()
            else WindowManager.LayoutParams.WRAP_CONTENT
            paneParams.x = (b.width() * o.scriptLeft / 100f).roundToInt()
            paneParams.y = (b.height() * o.scriptY / 100f).roundToInt()
        }
        // Above 80% opacity Android would block touches meant for the app underneath.
        paneParams.alpha = o.scriptWindowAlpha
        placeScriptGrip()
        if (paneFolded.value) {
            removePane()
        } else if (pane == null) {
            val v = composeWindow { PaneContent() }.apply {
                // Keep a tall pane (big text, several cues) from running off the bottom of the screen.
                addOnLayoutChangeListener { _, _, t, _, bottom, _, ot, _, ob ->
                    if (bottom - t != ob - ot) post { if (app.prefs.value.overlay.scriptAttach) syncPane() else clampPane() }
                }
            }
            // Added before the handle (re-added below), so the handle stays on top where they overlap.
            removeScriptGrip()
            wm.addView(v, paneParams)
            pane = v
        } else {
            runCatching { wm.updateViewLayout(pane, paneParams) }
        }
        val g = scriptGrip
        if (g == null) {
            val v = composeWindow { ScriptGripContent() }
            wm.addView(v, gripParams)
            scriptGrip = v
        } else {
            runCatching { wm.updateViewLayout(g, gripParams) }
        }
    }

    /**
     * Joined mode: the script box sits right against the controls, as wide as they are, and has no
     * handle of its own; the controls' grip moves both. Folding the controls folds it too.
     */
    private fun syncAttachedPane() {
        val o = app.prefs.value.overlay
        val panel = view ?: return
        removeScriptGrip()
        paneFolded.value = false
        if (folded.value) {
            removePane()
            joinSide.value = 0
            return
        }
        val b = screen()
        paneParams.height = if (o.scriptHeight > 0) (b.height() * o.scriptHeight / 100f).roundToInt()
        else WindowManager.LayoutParams.WRAP_CONTENT
        val paneH = if (o.scriptHeight > 0) paneParams.height else pane?.height ?: 0
        val minW = (OverlaySize.ATTACHED_MIN_WIDTH * resources.displayMetrics.density).roundToInt()
        val spot = dockScript(params.x, params.y, panel.width, panel.height, paneH, minW, b.width(), b.height(), o.scriptBelow)
        paneParams.x = spot.x
        paneParams.y = spot.y
        paneParams.width = spot.width
        paneParams.alpha = o.scriptWindowAlpha
        joinSide.value = if (!spot.joined) 0 else if (spot.below) 2 else 1
        val p = pane
        if (p == null) {
            val v = composeWindow { PaneContent() }.apply {
                addOnLayoutChangeListener { _, _, t, _, bottom, _, ot, _, ob ->
                    if (bottom - t != ob - ot) post { syncPane() }
                }
            }
            wm.addView(v, paneParams)
            pane = v
        } else {
            runCatching { wm.updateViewLayout(p, paneParams) }
        }
    }

    private fun composeWindow(content: @androidx.compose.runtime.Composable () -> Unit) = ComposeView(this).apply {
        setViewTreeLifecycleOwner(this@OverlayService)
        setViewTreeSavedStateRegistryOwner(this@OverlayService)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent(content)
    }

    private fun placeScriptGrip() {
        val d = resources.displayMetrics.density
        gripParams.x = paneParams.x + (4 * d).roundToInt()
        gripParams.y = paneParams.y + (8 * d).roundToInt()
    }

    private fun movePane(dx: Float, dy: Float) {
        paneDragging = true
        val b = screen()
        val h = pane?.height ?: scriptGrip?.height ?: 0
        paneParams.x = (paneParams.x + dx.roundToInt()).coerceIn(0, (b.width() - paneParams.width).coerceAtLeast(0))
        paneParams.y = (paneParams.y + dy.roundToInt()).coerceIn(0, (b.height() - h).coerceAtLeast(0))
        placeScriptGrip()
        pane?.let { runCatching { wm.updateViewLayout(it, paneParams) } }
        scriptGrip?.let { runCatching { wm.updateViewLayout(it, gripParams) } }
    }

    private fun savePane() {
        paneDragging = false
        val b = screen()
        val x = (paneParams.x * 100f / b.width()).roundToInt()
        val y = (paneParams.y * 100f / b.height()).roundToInt()
        app.prefs.update { it.copy(overlay = it.overlay.copy(scriptX = x, scriptY = y)) }
    }

    private fun clampPane() {
        val p = pane ?: return
        val max = (screen().height() - p.height).coerceAtLeast(0)
        if (paneParams.y > max) {
            paneParams.y = max
            placeScriptGrip()
            runCatching { wm.updateViewLayout(p, paneParams) }
            scriptGrip?.let { runCatching { wm.updateViewLayout(it, gripParams) } }
        }
    }

    private fun removePane() {
        val p = pane ?: return
        pane = null
        runCatching { wm.removeView(p) }
    }

    private fun removeScriptGrip() {
        val g = scriptGrip ?: return
        scriptGrip = null
        runCatching { wm.removeView(g) }
    }

    @androidx.compose.runtime.Composable
    private fun PaneContent() {
        val settings by app.prefs.settings.collectAsState()
        val st by app.engine.state.collectAsState()
        EarCueTheme(isDarkTheme(settings.theme)) {
            val m = androidx.compose.ui.Modifier.fillMaxWidth()
            val join by joinSide
            ScriptPane(st, settings, if (settings.overlay.scriptHeight > 0) m.fillMaxHeight() else m, join)
        }
    }

    @androidx.compose.runtime.Composable
    private fun ScriptGripContent() {
        val settings by app.prefs.settings.collectAsState()
        val folded by paneFolded
        EarCueTheme(isDarkTheme(settings.theme)) {
            ScriptGrip(
                folded,
                androidx.compose.ui.Modifier
                    .pointerInput(Unit) {
                        detectTapGestures {
                            paneFolded.value = !paneFolded.value
                            syncPane()
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(onDragEnd = { savePane() }, onDragCancel = { savePane() }) { change, amount ->
                            change.consume()
                            movePane(amount.x, amount.y)
                        }
                    },
            )
        }
    }

    private fun screen(): Rect = if (Build.VERSION.SDK_INT >= 30) {
        wm.currentWindowMetrics.bounds
    } else {
        val m = resources.displayMetrics
        Rect(0, 0, m.widthPixels, m.heightPixels)
    }

    /** Places the window at the saved position, or the default one, kept fully on screen. */
    private fun applyPosition(measure: Boolean = true) {
        val o = app.prefs.value.overlay
        val b = screen()
        val w = if (measure) view?.width ?: 0 else 0
        val h = if (measure) view?.height ?: 0 else 0
        val margin = (16 * resources.displayMetrics.density).roundToInt()
        val y = if (o.y < 0) (b.height() * 0.55f).roundToInt() else o.y
        val x = when {
            folded.value -> if (o.x >= 0 && o.x + panelWidth / 2 < b.width() / 2) 0 else b.width() - w
            o.x < 0 -> b.width() - w - margin
            else -> o.x
        }
        val nx = x.coerceIn(0, (b.width() - w).coerceAtLeast(0))
        val ny = y.coerceIn(0, (b.height() - h).coerceAtLeast(0))
        if (nx == params.x && ny == params.y) return
        params.x = nx
        params.y = ny
        view?.let { runCatching { wm.updateViewLayout(it, params) } }
    }

    private fun moveBy(dx: Float, dy: Float) {
        val v = view ?: return
        val b = screen()
        params.x = (params.x + dx.roundToInt()).coerceIn(0, (b.width() - v.width).coerceAtLeast(0))
        params.y = (params.y + dy.roundToInt()).coerceIn(0, (b.height() - v.height).coerceAtLeast(0))
        wm.updateViewLayout(v, params)
        if (app.prefs.value.overlay.scriptAttach) syncPane()
    }

    private fun savePosition() {
        val y = params.y
        // A dragged tab only moves the panel up or down; it unfolds where the panel was, at the new height.
        val x = if (folded.value) app.prefs.value.overlay.x else params.x
        app.prefs.update { it.copy(overlay = it.overlay.copy(x = x, y = y)) }
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        )
    }

    private fun turnOff() {
        app.prefs.update { it.copy(overlayOn = false) }
        stopSelf()
    }

    // ---- notification ----

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.ov_notif_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val close = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_CLOSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_earcue)
            .setContentTitle(getString(R.string.ov_notif_title))
            .setContentText(getString(R.string.ov_notif_text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.ov_notif_close), close)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, n,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
    }

    companion object {
        private const val CHANNEL = "overlay"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_CLOSE = "app.earcue.overlay.CLOSE"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        /** Starts the service if the user turned the controls on and the permission is still granted. Call from the foreground. */
        fun sync(context: Context) {
            val app = context.applicationContext as EarCueApp
            if (app.prefs.value.overlayOn && Settings.canDrawOverlays(context) && !running.value) {
                ContextCompat.startForegroundService(context, Intent(context, OverlayService::class.java))
            }
        }
    }
}
