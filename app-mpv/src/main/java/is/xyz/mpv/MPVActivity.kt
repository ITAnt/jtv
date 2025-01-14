package `is`.xyz.mpv

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.preference.PreferenceManager.getDefaultSharedPreferences
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.blankj.utilcode.util.RegexUtils
import com.itant.jtv.mpv.ui.home.video.VideoMpvActivity
import com.itant.jtv.storage.kv.KeyValue
import com.itant.jtv.utils.ToastUtils
import com.miekir.mvvm.context.GlobalContext
import com.miekir.mvvm.core.view.base.BasicBindingFragment
import `is`.xyz.mpv.databinding.PlayerBinding
import kotlin.math.roundToInt

typealias ActivityResultCallback = (Int, Intent?) -> Unit
typealias StateRestoreCallback = () -> Unit

/**
 * [cplayer:v] Set property: android-surface-size="1920x1080" -> 1
 * --hwdec=auto
 */
class MPVActivity : BasicBindingFragment<PlayerBinding>(), MPVLib.EventObserver, TouchGesturesObserver {
    // for calls to eventUi() and eventPropertyUi()
    private val eventUiHandler = Handler(Looper.getMainLooper())
    // for use with fadeRunnable1..3
    private val fadeHandler = Handler(Looper.getMainLooper())
    // for use with stopServiceRunnable
    private val stopServiceHandler = Handler(Looper.getMainLooper())

    /**
     * DO NOT USE THIS
     */
    private var activityIsStopped = false

    private var activityIsForeground = true
    private var didResumeBackgroundPlayback = false
    private var userIsOperatingSeekbar = false

    private var toast: Toast? = null

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequestCompat? = null
    private var audioFocusRestore: () -> Unit = {}

    private val psc = Utils.PlaybackStateCache()
    private var mediaSession: MediaSessionCompat? = null

    // convenience alias
    private val player get() = binding.player

    private val seekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (!fromUser)
                return
            player.timePos = progress.toDouble() / SEEK_BAR_PRECISION
            // Note: don't call updatePlaybackPos() here either
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            userIsOperatingSeekbar = true
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            userIsOperatingSeekbar = false
            showControls() // re-trigger display timeout
        }
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                // effects are the identical
                audioFocusChangeListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
            }
        }
    }
    //private var becomingNoisyReceiverRegistered = false

    // Note that after Android 12 this is not necessarily called.
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { type ->
        Log.v(TAG, "Audio focus changed: $type")
        if (ignoreAudioFocus)
            return@OnAudioFocusChangeListener
        when (type) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // loss can occur in addition to ducking, so remember the old callback
                val oldRestore = audioFocusRestore
                val wasPlayerPaused = player.paused ?: false
                player.paused = true
                audioFocusRestore = {
                    oldRestore()
                    if (!wasPlayerPaused) player.paused = false
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                MPVLib.command(arrayOf("multiply", "volume", AUDIO_FOCUS_DUCKING.toString()))
                audioFocusRestore = {
                    val inv = 1f / AUDIO_FOCUS_DUCKING
                    MPVLib.command(arrayOf("multiply", "volume", inv.toString()))
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                audioFocusRestore()
                audioFocusRestore = {}
            }
        }
    }

    // Fade out controls
    private val fadeRunnable = object : Runnable {
        var hasStarted = false
        private val listener = object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) { hasStarted = true }

            override fun onAnimationCancel(animation: Animator) { hasStarted = false }

            override fun onAnimationEnd(animation: Animator) {
                if (hasStarted)
                    hideControls()
                hasStarted = false
            }
        }

        override fun run() {
            binding.topControls.animate().alpha(0f).setDuration(CONTROLS_FADE_DURATION)
            binding.controls.animate().alpha(0f).setDuration(CONTROLS_FADE_DURATION).setListener(listener)
        }
    }

    // Fade out unlock button
    private val fadeRunnable2 = object : Runnable {
        private val listener = object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                binding.unlockBtn.visibility = View.GONE
            }
        }

        override fun run() {
            //binding.unlockBtn.animate().alpha(0f).setDuration(CONTROLS_FADE_DURATION).setListener(listener)
        }
    }

    // Fade out gesture text
    private val fadeRunnable3 = object : Runnable {
        // okay this doesn't actually fade...
        override fun run() {
            binding.gestureTextView.visibility = View.GONE
        }
    }

    private val stopServiceRunnable = Runnable {
        val intent = Intent(requireActivity(), BackgroundPlaybackService::class.java)
        requireActivity().applicationContext.stopService(intent)
    }

    /* Settings */
    private var statsFPS = false
    private var statsLuaMode = 0 // ==0 disabled, >0 page number

    private var backgroundPlayMode = ""
    private var noUIPauseMode = ""

    private var shouldSavePosition = false

    private var autoRotationMode = ""

    private var controlsAtBottom = true
    private var showMediaTitle = false
    private var useTimeRemaining = false

    private var ignoreAudioFocus = false
    private var playlistExitWarning = true

    private var smoothSeekGesture = false

    @SuppressLint("ClickableViewAccessibility")
    private fun initListeners() {
        with (binding) {
            prevBtn.setOnClickListener { playlistPrev() }
            nextBtn.setOnClickListener { playlistNext() }
            cycleAudioBtn.setOnClickListener { cycleAudio() }
            cycleSubsBtn.setOnClickListener { cycleSub() }
            playBtn.setOnClickListener { player.cyclePause() }
            cycleDecoderBtn.setOnClickListener { player.cycleHwdec() }
            cycleSpeedBtn.setOnClickListener { cycleSpeed() }
            topLockBtn.setOnClickListener { lockUI() }
            unlockBtn.setOnClickListener { unlockUI() }
            playbackDurationTxt.setOnClickListener {
                useTimeRemaining = !useTimeRemaining
                updatePlaybackPos(psc.positionSec)
                updatePlaybackDuration(psc.durationSec)
            }

            cycleAudioBtn.setOnLongClickListener { pickAudio(); true }
            cycleSpeedBtn.setOnLongClickListener { pickSpeed(); true }
            cycleSubsBtn.setOnLongClickListener { pickSub(); true }
            cycleDecoderBtn.setOnLongClickListener { pickDecoder(); true }

            playbackSeekbar.setOnSeekBarChangeListener(seekBarChangeListener)
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.outside) { _, windowInsets ->
            // guidance: https://medium.com/androiddevelopers/gesture-navigation-handling-visual-overlaps-4aed565c134c
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val insets2 = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            binding.outside.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                // avoid system bars and cutout
                leftMargin = Math.max(insets.left, insets2.left)
                topMargin = Math.max(insets.top, insets2.top)
                bottomMargin = Math.max(insets.bottom, insets2.bottom)
                rightMargin = Math.max(insets.right, insets2.right)
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    private var playbackHasStarted = false
    private var onloadCommands = mutableListOf<Array<String>>()

    // Activity lifetime

    private var currentUrl = ""
    fun playFile(filePath: String) {
        currentUrl = filePath
        playStarted = false
        if (!RegexUtils.isURL(filePath)) {
            return
        }
        if (TextUtils.equals(KeyValue.lastFailUrl, filePath)) {
            return
        }

        /*if (mediaSession != null) {
            MPVLib.command(arrayOf("loadfile", filePath))
            player.paused = false
            //MPVLib.command(arrayOf("loadfile", filePath, "append"))
            //MPVLib.command(arrayOf("playlist-next"))
        } else {
            player.playFile(filePath)
            mediaSession = initMediaSession()
            updateMediaSession()
            BackgroundPlaybackService.mediaToken = mediaSession?.sessionToken
            requireActivity().volumeControlStream = STREAM_TYPE
        }*/

        player.playFile(filePath)
        if (mediaSession == null) {
            mediaSession = initMediaSession()
            updateMediaSession()
            BackgroundPlaybackService.mediaToken = mediaSession?.sessionToken
            requireActivity().volumeControlStream = STREAM_TYPE
        }

        MPVLib.command(arrayOf("loadfile", filePath))
        player.paused = false
    }

    override fun onDestroyView() {
        Log.v(TAG, "Exiting.")
        // Suppress any further callbacks
        activityIsForeground = false

        BackgroundPlaybackService.mediaToken = null
        mediaSession?.let {
            it.isActive = false
            it.release()
        }
        mediaSession = null

        audioFocusRequest?.let {
            AudioManagerCompat.abandonAudioFocusRequest(audioManager!!, it)
        }
        audioFocusRequest = null

        // take the background service with us
        stopServiceRunnable.run()

        player.removeObserver(this)
        player.destroy()

        super.onDestroyView()
    }

    override fun onInit() {
        // Do these here and not in MainActivity because mpv can be launched from a file browser
        Utils.copyAssets(requireActivity())
        BackgroundPlaybackService.createNotificationChannel(requireActivity())

        // Init controls to be hidden and view fullscreen
        hideControls()

        // Initialize listeners for the player view
        initListeners()

        // set up initial UI state
        readSettings()
        onConfigurationChanged(resources.configuration)
        run {
            // edge-to-edge & immersive mode
            WindowCompat.setDecorFitsSystemWindows(requireActivity().window, false)
            val insetsController = WindowCompat.getInsetsController(requireActivity().window, requireActivity().window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (!requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))
            binding.topPiPBtn.visibility = View.GONE
        if (!requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN))
            binding.topLockBtn.visibility = View.GONE

        if (showMediaTitle)
            binding.controlsTitleGroup.visibility = View.VISIBLE

        player.addObserver(this)
        player.initialize(requireActivity().filesDir.path, requireActivity().cacheDir.path)
    }

    private fun isPlayingAudioOnly(): Boolean {
        if (player.aid == -1)
            return false
        val image = MPVLib.getPropertyString("current-tracks/video/image")
        return image.isNullOrEmpty() || image == "yes"
    }

    private fun shouldBackground(): Boolean {
        if (requireActivity().isFinishing) // about to exit?
            return false
        return when (backgroundPlayMode) {
            "always" -> true
            "audio-only" -> isPlayingAudioOnly()
            else -> false // "never"
        }
    }

    private fun onPauseImpl() {
        val fmt = MPVLib.getPropertyString("video-format")
        val shouldBackground = shouldBackground()
        if (shouldBackground && !fmt.isNullOrEmpty())
            BackgroundPlaybackService.thumbnail = MPVLib.grabThumbnail(THUMB_SIZE)
        else
            BackgroundPlaybackService.thumbnail = null
        // media session uses the same thumbnail
        updateMediaSession()

        activityIsForeground = false
        eventUiHandler.removeCallbacksAndMessages(null)
        if (requireActivity().isFinishing) {
            savePosition()
            // tell mpv to shut down so that any other property changes or such are ignored,
            // preventing useless busywork
            MPVLib.command(arrayOf("stop"))
        } else if (!shouldBackground) {
            player.paused = true
        }
        writeSettings()
        super.onPause()

        didResumeBackgroundPlayback = shouldBackground
        if (shouldBackground) {
            Log.v(TAG, "Resuming playback in background")
            stopServiceHandler.removeCallbacks(stopServiceRunnable)
            val serviceIntent = Intent(requireActivity(), BackgroundPlaybackService::class.java)
            ContextCompat.startForegroundService(requireActivity(), serviceIntent)
        }
    }

    private fun readSettings() {
        // FIXME: settings should be in their own class completely
        val prefs = getDefaultSharedPreferences(requireActivity().applicationContext)
        val getString: (String, Int) -> String = { key, defaultRes ->
            prefs.getString(key, resources.getString(defaultRes))!!
        }

        val statsMode = prefs.getString("stats_mode", "") ?: ""
        this.statsFPS = statsMode == "native_fps"
        this.statsLuaMode = if (statsMode.startsWith("lua"))
            statsMode.removePrefix("lua").toInt()
        else
            0
        this.backgroundPlayMode = getString("background_play", R.string.pref_background_play_default)
        this.noUIPauseMode = getString("no_ui_pause", R.string.pref_no_ui_pause_default)
        this.shouldSavePosition = prefs.getBoolean("save_position", false)
        if (this.autoRotationMode != "manual") // don't reset
            this.autoRotationMode = getString("auto_rotation", R.string.pref_auto_rotation_default)
        this.controlsAtBottom = prefs.getBoolean("bottom_controls", true)
        this.showMediaTitle = prefs.getBoolean("display_media_title", false)
        this.useTimeRemaining = prefs.getBoolean("use_time_remaining", false)
        this.ignoreAudioFocus = prefs.getBoolean("ignore_audio_focus", false)
        this.playlistExitWarning = prefs.getBoolean("playlist_exit_warning", true)
        this.smoothSeekGesture = prefs.getBoolean("seek_gesture_smooth", false)
    }

    private fun writeSettings() {
        val prefs = getDefaultSharedPreferences(requireActivity().applicationContext)

        with (prefs.edit()) {
            putBoolean("use_time_remaining", useTimeRemaining)
            commit()
        }
    }

    override fun onStart() {
        super.onStart()
        activityIsStopped = false
    }

    override fun onStop() {
        super.onStop()
        activityIsStopped = true
    }

    private var everPaused = false
    @SuppressLint("MissingSuperCall")
    override fun onPause() {
        onPauseImpl()
        everPaused = true
    }

    override fun onResume() {
        // If we weren't actually in the background (e.g. multi window mode), don't reinitialize stuff
        if (activityIsForeground) {
            super.onResume()
            return
        }

        if (lockedUI) { // precaution
            Log.w(TAG, "resumed with locked UI, unlocking")
            unlockUI()
        }

        // Init controls to be hidden and view fullscreen
        hideControls()
        readSettings()

        activityIsForeground = true
        // stop background service with a delay
        stopServiceHandler.removeCallbacks(stopServiceRunnable)
        stopServiceHandler.postDelayed(stopServiceRunnable, 1000L)

        refreshUi()

        if (everPaused && RegexUtils.isURL(currentUrl) && !TextUtils.equals(KeyValue.lastFailUrl, currentUrl)) {
            player.paused = false
        }
        super.onResume()
    }

    private fun savePosition() {
        if (!shouldSavePosition)
            return
        if (MPVLib.getPropertyBoolean("eof-reached") ?: true) {
            Log.d(TAG, "player indicates EOF, not saving watch-later config")
            return
        }
        MPVLib.command(arrayOf("write-watch-later-config"))
    }

    // UI

    /** dpad navigation */
    private var btnSelected = -1

    private var mightWantToToggleControls = false

    private var useAudioUI = false
    private var lockedUI = true

    private fun pauseForDialog(): StateRestoreCallback {
        val useKeepOpen = when (noUIPauseMode) {
            "always" -> true
            "audio-only" -> isPlayingAudioOnly()
            else -> false // "never"
        }
        if (useKeepOpen) {
            // don't pause but set keep-open so mpv doesn't exit while the user is doing stuff
            val oldValue = MPVLib.getPropertyString("keep-open")
            MPVLib.setPropertyBoolean("keep-open", true)
            return {
                MPVLib.setPropertyString("keep-open", oldValue)
            }
        }

        // Pause playback during UI dialogs
        val wasPlayerPaused = player.paused ?: true
        player.paused = true
        return {
            if (!wasPlayerPaused)
                player.paused = false
        }
    }

    private fun updateStats() {
        if (!statsFPS)
            return
        binding.statsTextView.text = getString(R.string.ui_fps, player.estimatedVfFps)
    }

    private fun controlsShouldBeVisible(): Boolean {
        if (lockedUI)
            return false
        return useAudioUI || btnSelected != -1 || userIsOperatingSeekbar
    }

    /** Make controls visible, also controls the timeout until they fade. */
    private fun showControls() {
        if (lockedUI) {
            Log.w(TAG, "cannot show UI in locked mode")
            return
        }

        // remove all callbacks that were to be run for fading
        fadeHandler.removeCallbacks(fadeRunnable)
        binding.controls.animate().cancel()
        binding.topControls.animate().cancel()

        // reset controls alpha to be visible
        binding.controls.alpha = 1f
        binding.topControls.alpha = 1f

        if (binding.controls.visibility != View.VISIBLE) {
            binding.controls.visibility = View.VISIBLE
            binding.topControls.visibility = View.VISIBLE

            if (this.statsFPS) {
                updateStats()
                binding.statsTextView.visibility = View.VISIBLE
            }

            val insetsController = WindowCompat.getInsetsController(requireActivity().window, requireActivity().window.decorView)
            insetsController.show(WindowInsetsCompat.Type.navigationBars())
        }

        // add a new callback to hide the controls once again
        if (!controlsShouldBeVisible())
            fadeHandler.postDelayed(fadeRunnable, CONTROLS_DISPLAY_TIMEOUT)
    }

    /** Hide controls instantly */
    fun hideControls() {
        if (controlsShouldBeVisible())
            return
        // use GONE here instead of INVISIBLE (which makes more sense) because of Android bug with surface views
        // see http://stackoverflow.com/a/12655713/2606891
        binding.controls.visibility = View.GONE
        binding.topControls.visibility = View.GONE
        binding.statsTextView.visibility = View.GONE

        val insetsController = WindowCompat.getInsetsController(requireActivity().window, requireActivity().window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    /** Start fading out the controls */
    private fun hideControlsFade() {
        fadeHandler.removeCallbacks(fadeRunnable)
        fadeHandler.post(fadeRunnable)
    }

    /**
     * Returns views eligible for dpad button navigation
     */
    private fun dpadButtons(): Sequence<View> {
        val groups = arrayOf(binding.controlsButtonGroup, binding.topControls)
        return sequence {
            for (g in groups) {
                for (i in 0 until g.childCount) {
                    val view = g.getChildAt(i)
                    if (view.isEnabled && view.isVisible && view.isFocusable)
                        yield(view)
                }
            }
        }
    }

    private fun updateSelectedDpadButton() {
        val colorFocused = ContextCompat.getColor(requireActivity(), R.color.tint_btn_bg_focused)
        val colorNoFocus = ContextCompat.getColor(requireActivity(), R.color.tint_btn_bg_nofocus)

        dpadButtons().forEachIndexed { i, child ->
            if (i == btnSelected)
                child.setBackgroundColor(colorFocused)
            else
                child.setBackgroundColor(colorNoFocus)
        }
    }

    private fun playlistPrev() = MPVLib.command(arrayOf("playlist-prev"))
    private fun playlistNext() = MPVLib.command(arrayOf("playlist-next"))

    private fun showToast(msg: String, cancel: Boolean = false) {
        if (cancel)
            toast?.cancel()
        toast = Toast.makeText(requireActivity(), msg, Toast.LENGTH_SHORT).apply {
            setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 0)
            show()
        }
    }

    private fun resolveUri(data: Uri): String? {
        val filepath = when (data.scheme) {
            "file" -> data.path
            "content" -> openContentFd(data)
            // mpv supports data URIs but needs data:// to pass it through correctly
            "data" -> "data://${data.schemeSpecificPart}"
            "http", "https", "rtmp", "rtmps", "rtp", "rtsp", "mms", "mmst", "mmsh", "tcp", "udp", "lavf"
            -> data.toString()
            else -> null
        }

        if (filepath == null)
            Log.e(TAG, "unknown scheme: ${data.scheme}")
        return filepath
    }

    private fun openContentFd(uri: Uri): String? {
        val resolver = requireActivity().applicationContext.contentResolver
        Log.v(TAG, "Resolving content URI: $uri")
        val fd = try {
            val desc = resolver.openFileDescriptor(uri, "r")
            desc!!.detachFd()
        } catch(e: Exception) {
            Log.e(TAG, "Failed to open content fd: $e")
            return null
        }
        // See if we skip the indirection and read the real file directly
        val path = Utils.findRealPath(fd)
        if (path != null) {
            Log.v(TAG, "Found real file path: $path")
            ParcelFileDescriptor.adoptFd(fd).close() // we don't need that anymore
            return path
        }
        // Else, pass the fd to mpv
        return "fd://${fd}"
    }

    private fun parseIntentExtras(extras: Bundle?) {
        onloadCommands.clear()
        if (extras == null)
            return

        fun pushOption(key: String, value: String) {
            onloadCommands.add(arrayOf("set", "file-local-options/${key}", value))
        }

        // Refer to http://mpv-android.github.io/mpv-android/intent.html
        if (extras.getByte("decode_mode") == 2.toByte())
            pushOption("hwdec", "no")
        if (extras.containsKey("subs")) {
            val subList = Utils.getParcelableArray<Uri>(extras, "subs")
            val subsToEnable = Utils.getParcelableArray<Uri>(extras, "subs.enable")

            for (suburi in subList) {
                val subfile = resolveUri(suburi) ?: continue
                val flag = if (subsToEnable.any { it == suburi }) "select" else "auto"

                Log.v(TAG, "Adding subtitles from intent extras: $subfile")
                onloadCommands.add(arrayOf("sub-add", subfile, flag))
            }
        }
        extras.getInt("position", 0).let {
            if (it > 0)
                pushOption("start", "${it / 1000f}")
        }
        extras.getString("title", "").let {
            if (!it.isNullOrEmpty())
                pushOption("force-media-title", it)
        }
        // TODO: `headers` would be good, maybe `tls_verify`
    }

    // UI (Part 2)

    data class TrackData(val track_id: Int, val track_type: String)
    private fun trackSwitchNotification(f: () -> TrackData) {
        val (track_id, track_type) = f()
        val trackPrefix = when (track_type) {
            "audio" -> getString(R.string.track_audio)
            "sub"   -> getString(R.string.track_subs)
            "video" -> "Video"
            else    -> "???"
        }

        val msg = if (track_id == -1) {
            "$trackPrefix ${getString(R.string.track_off)}"
        } else {
            val trackName = player.tracks[track_type]?.firstOrNull{ it.mpvId == track_id }?.name ?: "???"
            "$trackPrefix $trackName"
        }
        showToast(msg, true)
    }

    private fun cycleAudio() = trackSwitchNotification {
        player.cycleAudio(); TrackData(player.aid, "audio")
    }
    private fun cycleSub() = trackSwitchNotification {
        player.cycleSub(); TrackData(player.sid, "sub")
    }

    private fun selectTrack(type: String, get: () -> Int, set: (Int) -> Unit) {
        val tracks = player.tracks.getValue(type)
        val selectedMpvId = get()
        val selectedIndex = tracks.indexOfFirst { it.mpvId == selectedMpvId }
        val restore = pauseForDialog()

        with (AlertDialog.Builder(requireActivity())) {
            setSingleChoiceItems(tracks.map { it.name }.toTypedArray(), selectedIndex) { dialog, item ->
                val trackId = tracks[item].mpvId

                set(trackId)
                dialog.dismiss()
                trackSwitchNotification { TrackData(trackId, type) }
            }
            setOnDismissListener { restore() }
            create().show()
        }
    }

    private fun pickAudio() = selectTrack("audio", { player.aid }, { player.aid = it })

    private fun pickSub() {
        val restore = pauseForDialog()
        val impl = SubTrackDialog(player)
        lateinit var dialog: AlertDialog
        impl.listener = { it, secondary ->
            if (secondary)
                player.secondarySid = it.mpvId
            else
                player.sid = it.mpvId
            dialog.dismiss()
            trackSwitchNotification { TrackData(it.mpvId, SubTrackDialog.TRACK_TYPE) }
        }

        dialog = with (AlertDialog.Builder(requireActivity())) {
            val inflater = LayoutInflater.from(context)
            setView(impl.buildView(inflater))
            setOnDismissListener { restore() }
            create()
        }
        dialog.show()
    }

    /**
     * 硬解
     */
    private fun pickDecoder() {
        val restore = pauseForDialog()

        val items = mutableListOf(
            Pair("HW (mediacodec-copy)", "mediacodec-copy"),
            Pair("SW", "no")
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            items.add(0, Pair("HW+ (mediacodec)", "mediacodec"))
        val hwdecActive = player.hwdecActive
        val selectedIndex = items.indexOfFirst { it.second == hwdecActive }
        with (AlertDialog.Builder(requireActivity())) {
            setSingleChoiceItems(items.map { it.first }.toTypedArray(), selectedIndex ) { dialog, idx ->
                MPVLib.setPropertyString("hwdec", items[idx].second)
                dialog.dismiss()
            }
            setOnDismissListener { restore() }
            create().show()
        }
    }

    private fun cycleSpeed() {
        player.cycleSpeed()
    }

    private fun pickSpeed() {
        // TODO: replace this with SliderPickerDialog
        val picker = SpeedPickerDialog()

        val restore = pauseForDialog()
        genericPickerDialog(picker, R.string.title_speed_dialog, "speed") {
            restore()
        }
    }

    private fun lockUI() {
        lockedUI = true
        hideControlsFade()
    }

    private fun unlockUI() {
        /*binding.unlockBtn.visibility = View.GONE
        lockedUI = false
        showControls()*/
    }

    private fun genericPickerDialog(
        picker: PickerDialog, @StringRes titleRes: Int, property: String,
        restoreState: StateRestoreCallback
    ) {
        val dialog = with(AlertDialog.Builder(requireActivity())) {
            setTitle(titleRes)
            val inflater = LayoutInflater.from(context)
            setView(picker.buildView(inflater))
            setPositiveButton(R.string.dialog_ok) { _, _ ->
                picker.number?.let {
                    if (picker.isInteger())
                        MPVLib.setPropertyInt(property, it.toInt())
                    else
                        MPVLib.setPropertyDouble(property, it)
                }
            }
            setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.cancel() }
            setOnDismissListener { restoreState() }
            create()
        }

        picker.number = MPVLib.getPropertyDouble(property)
        dialog.show()
    }


    override fun onBindingInflate() = PlayerBinding.inflate(layoutInflater)

    private fun refreshUi() {
        // forces update of entire UI, used when resuming the activity
        updatePlaybackStatus(psc.pause)
        updatePlaybackPos(psc.positionSec)
        updatePlaybackDuration(psc.durationSec)
        updateAudioUI()
        updateMetadataDisplay()
        updateDecoderButton()
        updateSpeedButton()
        updatePlaylistButtons()
        player.loadTracks()
    }

    private fun updateAudioUI() {
        val audioButtons = arrayOf(R.id.prevBtn, R.id.cycleAudioBtn, R.id.playBtn,
                R.id.cycleSpeedBtn, R.id.nextBtn)
        val videoButtons = arrayOf(R.id.cycleAudioBtn, R.id.cycleSubsBtn, R.id.playBtn,
                R.id.cycleDecoderBtn, R.id.cycleSpeedBtn)

        val shouldUseAudioUI = isPlayingAudioOnly()
        if (shouldUseAudioUI == useAudioUI)
            return
        useAudioUI = shouldUseAudioUI
        Log.v(TAG, "Audio UI: $useAudioUI")

        val seekbarGroup = binding.controlsSeekbarGroup
        val buttonGroup = binding.controlsButtonGroup

        if (useAudioUI) {
            // Move prev/next file from seekbar group to buttons group
            Utils.viewGroupMove(seekbarGroup, R.id.prevBtn, buttonGroup, 0)
            Utils.viewGroupMove(seekbarGroup, R.id.nextBtn, buttonGroup, -1)

            // Change button layout of buttons group
            Utils.viewGroupReorder(buttonGroup, audioButtons)

            // Show song title and more metadata
            binding.controlsTitleGroup.visibility = View.VISIBLE
            Utils.viewGroupReorder(binding.controlsTitleGroup, arrayOf(R.id.titleTextView, R.id.minorTitleTextView))
            updateMetadataDisplay()

            showControls()
        } else {
            Utils.viewGroupMove(buttonGroup, R.id.prevBtn, seekbarGroup, 0)
            Utils.viewGroupMove(buttonGroup, R.id.nextBtn, seekbarGroup, -1)

            Utils.viewGroupReorder(buttonGroup, videoButtons)

            // Show title only depending on settings
            if (showMediaTitle) {
                binding.controlsTitleGroup.visibility = View.VISIBLE
                Utils.viewGroupReorder(binding.controlsTitleGroup, arrayOf(R.id.fullTitleTextView))
                updateMetadataDisplay()
            } else {
                binding.controlsTitleGroup.visibility = View.GONE
            }

            hideControls() // do NOT use fade runnable
        }

        // Visibility might have changed, so update
        updatePlaylistButtons()
    }

    private fun updateMetadataDisplay() {
        if (!useAudioUI) {
            if (showMediaTitle)
                binding.fullTitleTextView.text = psc.meta.formatTitle()
        } else {
            binding.titleTextView.text = psc.meta.formatTitle()
            binding.minorTitleTextView.text = psc.meta.formatArtistAlbum()
        }
    }

    private fun updatePlaybackPos(position: Int) {
        binding.playbackPositionTxt.text = Utils.prettyTime(position)
        if (useTimeRemaining) {
            val diff = psc.durationSec - position
            binding.playbackDurationTxt.text = if (diff <= 0)
                "-00:00"
            else
                Utils.prettyTime(-diff, true)
        }
        if (!userIsOperatingSeekbar)
            binding.playbackSeekbar.progress = position * SEEK_BAR_PRECISION

        // Note: do NOT add other update functions here just because this is called every second.
        // Use property observation instead.
        updateStats()
    }

    private fun updatePlaybackDuration(duration: Int) {
        if (!useTimeRemaining)
            binding.playbackDurationTxt.text = Utils.prettyTime(duration)
        if (!userIsOperatingSeekbar)
            binding.playbackSeekbar.max = duration * SEEK_BAR_PRECISION
    }

    private fun updatePlaybackStatus(paused: Boolean) {
        val r = if (paused) R.drawable.ic_play_arrow_black_24dp else R.drawable.ic_pause_black_24dp
        binding.playBtn.setImageResource(r)

        if (paused) {
            requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//            if (becomingNoisyReceiverRegistered)
//                requireActivity().unregisterReceiver(becomingNoisyReceiver)
//            becomingNoisyReceiverRegistered = false
        } else {
            requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//            if (!becomingNoisyReceiverRegistered)
//                requireActivity().registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
//            becomingNoisyReceiverRegistered = true
        }
    }

    private fun updateDecoderButton() {
        if (!binding.cycleDecoderBtn.isVisible)
            return
        binding.cycleDecoderBtn.text = when (player.hwdecActive) {
            "mediacodec" -> "HW+"
            "no" -> "SW"
            else -> "HW"
        }
    }

    private fun updateSpeedButton() {
        binding.cycleSpeedBtn.text = getString(R.string.ui_speed, psc.speed)
    }

    private fun updatePlaylistButtons() {
        val plCount = psc.playlistCount
        val plPos = psc.playlistPos

        if (!useAudioUI && plCount == 1) {
            // use View.GONE so the buttons won't take up any space
            binding.prevBtn.visibility = View.GONE
            binding.nextBtn.visibility = View.GONE
            return
        }
        binding.prevBtn.visibility = View.VISIBLE
        binding.nextBtn.visibility = View.VISIBLE

        val g = ContextCompat.getColor(requireActivity(), R.color.tint_disabled)
        val w = ContextCompat.getColor(requireActivity(), R.color.tint_normal)
        binding.prevBtn.imageTintList = ColorStateList.valueOf(if (plPos == 0) g else w)
        binding.nextBtn.imageTintList = ColorStateList.valueOf(if (plPos == plCount-1) g else w)
    }

    // Media Session handling
    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPause() {
            player.paused = true
        }
        override fun onPlay() {
            player.paused = false
        }
        override fun onSeekTo(pos: Long) {
            player.timePos = (pos / 1000.0)
        }
        override fun onSkipToNext() = playlistNext()
        override fun onSkipToPrevious() = playlistPrev()
        override fun onSetRepeatMode(repeatMode: Int) {
            MPVLib.setPropertyString("loop-playlist",
                if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ALL) "inf" else "no")
            MPVLib.setPropertyString("loop-file",
                if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE) "inf" else "no")
        }
        override fun onSetShuffleMode(shuffleMode: Int) {
            player.changeShuffle(false, shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL)
        }
    }

    private fun initMediaSession(): MediaSessionCompat {
        /*
            https://developer.android.com/guide/topics/media-apps/working-with-a-media-session
            https://developer.android.com/guide/topics/media-apps/audio-app/mediasession-callbacks
            https://developer.android.com/reference/android/support/v4/media/session/MediaSessionCompat
         */
        val session = MediaSessionCompat(requireActivity(), TAG)
        session.setFlags(0)
        session.setCallback(mediaSessionCallback)
        return session
    }

    private fun updateMediaSession() {
        synchronized (psc) {
            mediaSession?.let { psc.write(it) }
        }
    }

    // mpv events

    private fun eventPropertyUi(property: String, dummy: Any?, metaUpdated: Boolean) {
        if (!activityIsForeground) return
        when (property) {
            "track-list" -> player.loadTracks()
            "current-tracks/video/image" -> updateAudioUI()
            "hwdec-current" -> updateDecoderButton()
        }
        if (metaUpdated)
            updateMetadataDisplay()
    }

    private fun eventPropertyUi(property: String, value: Boolean) {
        if (!activityIsForeground) return
        when (property) {
            "pause" -> updatePlaybackStatus(value)
        }
    }

    private fun eventPropertyUi(property: String, value: Long) {
        if (!activityIsForeground) return
        when (property) {
            "time-pos" -> updatePlaybackPos(psc.positionSec)
            "playlist-pos", "playlist-count" -> updatePlaylistButtons()
        }
    }

    private fun eventPropertyUi(property: String, value: Double) {
        if (!activityIsForeground) return
        when (property) {
            "duration/full" -> updatePlaybackDuration(psc.durationSec)
            "video-params/aspect", "video-params/rotate" -> {
                //updateOrientation()
                //updatePiPParams()
            }
        }
    }

    private fun eventPropertyUi(property: String, value: String, metaUpdated: Boolean) {
        if (!activityIsForeground) return
        when (property) {
            "speed" -> updateSpeedButton()
        }
        if (metaUpdated)
            updateMetadataDisplay()
    }

    private fun eventUi(eventId: Int) {
        if (!activityIsForeground) return
        // empty
    }

    override fun eventProperty(property: String) {
        val metaUpdated = psc.update(property)
        if (metaUpdated)
            updateMediaSession()
        if (property == "loop-file" || property == "loop-playlist") {
            mediaSession?.setRepeatMode(when (player.getRepeat()) {
                2 -> PlaybackStateCompat.REPEAT_MODE_ONE
                1 -> PlaybackStateCompat.REPEAT_MODE_ALL
                else -> PlaybackStateCompat.REPEAT_MODE_NONE
            })
        }

        if (!activityIsForeground) return
        eventUiHandler.post { eventPropertyUi(property, null, metaUpdated) }
    }

    override fun eventProperty(property: String, value: Boolean) {
        if (psc.update(property, value))
            updateMediaSession()
        if (property == "shuffle") {
            mediaSession?.setShuffleMode(if (value)
                PlaybackStateCompat.SHUFFLE_MODE_ALL
            else
                PlaybackStateCompat.SHUFFLE_MODE_NONE)
        }

        if (!activityIsForeground) return
        eventUiHandler.post { eventPropertyUi(property, value) }
    }

    private var playStarted = false
    override fun eventProperty(property: String, value: Long) {
        if (TextUtils.equals(property, "time-pos") && value == 1L) {
            playStarted = true
            // 硬解
            if (KeyValue.hardCodec) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // HW+
                    MPVLib.setPropertyString("hwdec", "mediacodec")
                } else {
                    MPVLib.setPropertyString("hwdec", "mediacodec-copy")
                }
            } else {
                MPVLib.setPropertyString("hwdec", "no")
            }
            showVideoInfo()
        }

        if (psc.update(property, value))
            updateMediaSession()

        if (!activityIsForeground) return
        eventUiHandler.post { eventPropertyUi(property, value) }
    }

    override fun eventProperty(property: String, value: Double) {
        if (psc.update(property, value))
            updateMediaSession()

        if (!activityIsForeground) return
        eventUiHandler.post { eventPropertyUi(property, value) }
    }

    override fun eventProperty(property: String, value: String) {
        val metaUpdated = psc.update(property, value)
        if (metaUpdated)
            updateMediaSession()

        if (!activityIsForeground) return
        eventUiHandler.post { eventPropertyUi(property, value, metaUpdated) }
    }

    override fun event(eventId: Int) {
        if (eventId == MPVLib.mpvEventId.MPV_EVENT_SHUTDOWN) {
            KeyValue.lastFailUrl = currentUrl
            GlobalContext.runOnUiThread {
                if (activity?.isFinishing == true || activity?.isDestroyed == true) {
                    return@runOnUiThread
                }
                (activity as? VideoMpvActivity)?.initVideoFragment()
                ToastUtils.showShort("播放失败")
            }
        }

        if (eventId == MPVLib.mpvEventId.MPV_EVENT_START_FILE) {
            for (c in onloadCommands)
                MPVLib.command(c)
            if (this.statsLuaMode > 0 && !playbackHasStarted) {
                MPVLib.command(arrayOf("script-binding", "stats/display-page-${this.statsLuaMode}-toggle"))
            }
            playbackHasStarted = true
        }

        if (!activityIsForeground) return
        eventUiHandler.post { eventUi(eventId) }
    }

    // Gesture handler

    private var initialSeek = 0f
    private var initialBright = 0f
    private var initialVolume = 0
    private var maxVolume = 0
    /** 0 = initial, 1 = paused, 2 = was already paused */
    private var pausedForSeek = 0

    private fun fadeGestureText() {
        fadeHandler.removeCallbacks(fadeRunnable3)
        binding.gestureTextView.visibility = View.VISIBLE

        fadeHandler.postDelayed(fadeRunnable3, 500L)
    }

    override fun onPropertyChange(p: PropertyChange, diff: Float) {
        val gestureTextView = binding.gestureTextView
        when (p) {
            /* Drag gestures */
            PropertyChange.Init -> {
                mightWantToToggleControls = false

                initialSeek = (psc.position / 1000f)
                initialBright = Utils.getScreenBrightness(requireActivity()) ?: 0.5f
                with (audioManager!!) {
                    initialVolume = getStreamVolume(STREAM_TYPE)
                    maxVolume = if (isVolumeFixed)
                        0
                    else
                        getStreamMaxVolume(STREAM_TYPE)
                }
                pausedForSeek = 0

                fadeHandler.removeCallbacks(fadeRunnable3)
                gestureTextView.visibility = View.VISIBLE
                gestureTextView.text = ""
            }
            PropertyChange.Seek -> {
                // disable seeking when duration is unknown
                val duration = (psc.duration / 1000f)
                if (duration == 0f || initialSeek < 0)
                    return
                if (smoothSeekGesture && pausedForSeek == 0) {
                    pausedForSeek = if (psc.pause) 2 else 1
                    if (pausedForSeek == 1)
                        player.paused = true
                }

                val newPosExact = (initialSeek + diff).coerceIn(0f, duration)
                val newPos = newPosExact.roundToInt()
                val newDiff = (newPosExact - initialSeek).roundToInt()
                if (smoothSeekGesture) {
                    player.timePos = newPosExact.toDouble() // (exact seek)
                } else {
                    // seek faster than assigning to timePos but less precise
                    MPVLib.command(arrayOf("seek", "$newPosExact", "absolute+keyframes"))
                }
                // Note: don't call updatePlaybackPos() here because mpv will seek a timestamp
                // actually present in the file, and not the exact one we specified.

                val posText = Utils.prettyTime(newPos)
                val diffText = Utils.prettyTime(newDiff, true)
                gestureTextView.text = getString(R.string.ui_seek_distance, posText, diffText)
            }
            PropertyChange.Volume -> {
                if (maxVolume == 0)
                    return
                val newVolume = (initialVolume + (diff * maxVolume).toInt()).coerceIn(0, maxVolume)
                val newVolumePercent = 100 * newVolume / maxVolume
                audioManager!!.setStreamVolume(STREAM_TYPE, newVolume, 0)

                gestureTextView.text = getString(R.string.ui_volume, newVolumePercent)
            }
            PropertyChange.Bright -> {
                val lp = requireActivity().window.attributes
                val newBright = (initialBright + diff).coerceIn(0f, 1f)
                lp.screenBrightness = newBright
                requireActivity().window.attributes = lp

                gestureTextView.text = getString(R.string.ui_brightness, (newBright * 100).roundToInt())
            }
            PropertyChange.Finalize -> {
                if (pausedForSeek == 1)
                    player.paused = false
                gestureTextView.visibility = View.GONE
            }

            /* Tap gestures */
            PropertyChange.SeekFixed -> {
                val seekTime = diff * 10f
                val newPos = psc.positionSec + seekTime.toInt() // only for display
                MPVLib.command(arrayOf("seek", seekTime.toString(), "relative"))

                val diffText = Utils.prettyTime(seekTime.toInt(), true)
                gestureTextView.text = getString(R.string.ui_seek_distance, Utils.prettyTime(newPos), diffText)
                fadeGestureText()
            }
            PropertyChange.PlayPause -> player.cyclePause()
            PropertyChange.Custom -> {
                val keycode = 0x10002 + diff.toInt()
                MPVLib.command(arrayOf("keypress", "0x%x".format(keycode)))
            }
        }
    }

    fun showVideoInfo(fromManually: Boolean = false) {
        // 获取视频分辨率等信息
        if (playStarted) {
            if (fromManually) {
                val index = 1 // 1, 2, 3
                MPVLib.command(arrayOf("script-binding", "stats/display-page-$index"))
            }

            when (KeyValue.videoScaleType) {
                1 -> {
                    // 16:9
                    MPVLib.setPropertyString("video-aspect-override", "16:9")
                    MPVLib.setPropertyDouble("panscan", 0.0)
                }

                2 -> {
                    // 4:3
                    MPVLib.setPropertyString("video-aspect-override", "4:3")
                    MPVLib.setPropertyDouble("panscan", 0.0)
                }

                3 -> {
                    // 填充
                    MPVLib.setPropertyString("video-aspect-override", "-1")
                    MPVLib.setPropertyDouble("panscan", 1.0)
                }
                else -> {
                    // 默认
                    MPVLib.setPropertyString("video-aspect-override", "-1")
                    MPVLib.setPropertyDouble("panscan", 0.0)
                }
            }
        }
    }

    companion object {
        private const val TAG = "mpv"
        // how long should controls be displayed on screen (ms)
        private const val CONTROLS_DISPLAY_TIMEOUT = 1500L
        // how long controls fade to disappear (ms)
        private const val CONTROLS_FADE_DURATION = 500L
        // resolution (px) of the thumbnail displayed with playback notification
        private const val THUMB_SIZE = 384
        // smallest aspect ratio that is considered non-square
        private const val ASPECT_RATIO_MIN = 1.2f // covers 5:4 and up
        // fraction to which audio volume is ducked on loss of audio focus
        private const val AUDIO_FOCUS_DUCKING = 0.5f
        // request codes for invoking other activities
        private const val RCODE_EXTERNAL_AUDIO = 1000
        private const val RCODE_EXTERNAL_SUB = 1001
        private const val RCODE_LOAD_FILE = 1002
        // action of result intent
        private const val RESULT_INTENT = "is.xyz.mpv.MPVActivity.result"
        // stream type used with AudioManager
        private const val STREAM_TYPE = AudioManager.STREAM_MUSIC
        // precision used by seekbar (1/s)
        private const val SEEK_BAR_PRECISION = 2
    }
}
