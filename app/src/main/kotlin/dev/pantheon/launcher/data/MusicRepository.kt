package dev.pantheon.launcher.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import dev.pantheon.launcher.notifications.PantheonNotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MusicTrack(
    val title: String,
    val artist: String,
    val album: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val albumArt: Bitmap? = null,
    val packageName: String? = null,
    val onPlayPause: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onSeek: (Long) -> Unit = {},
)

/**
 * Tracks currently playing audio via [MediaSessionManager] and active media controllers.
 * Also provides a built-in demo track mode for testing on emulators and offline devices.
 */
class MusicRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _currentTrack = MutableStateFlow<MusicTrack?>(null)
    val currentTrack: StateFlow<MusicTrack?> = _currentTrack.asStateFlow()

    private var activeController: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private var tickerJob: Job? = null

    // Built-in demo track state
    private var demoPlaying: Boolean = false
    private var demoPos: Long = 42_000L
    private val demoDuration: Long = 214_000L
    private var demoTrackIndex: Int = 0

    private val demoPlaylist = listOf(
        Triple("Never Fade Away", "Refused // SAMURAI", "Cyberpunk 2077"),
        Triple("Resist and Disorder", "Rezodrone", "Cyberpunk 2077"),
        Triple("Chippin' In", "Kerry Eurodyne & SAMURAI", "Cyberpunk 2077"),
        Triple("Master Boot Record", "C:\\> CHKDSK /F", "Floppy Disk Overdrive"),
    )

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateFromController(activeController)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateFromController(activeController)
        }

        override fun onSessionDestroyed() {
            activeController = null
            if (!demoPlaying) {
                _currentTrack.value = null
                stopTicker()
            }
            bindSessions()
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        onSessionsChanged(controllers)
    }

    fun bindSessions() {
        val msm = context.getSystemService(MediaSessionManager::class.java) ?: return
        val comp = ComponentName(context, PantheonNotificationListener::class.java)
        runCatching {
            msm.removeOnActiveSessionsChangedListener(sessionsListener)
            msm.addOnActiveSessionsChangedListener(sessionsListener, comp, handler)
            onSessionsChanged(msm.getActiveSessions(comp))
        }
    }

    private fun onSessionsChanged(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) {
            if (!demoPlaying) {
                activeController?.unregisterCallback(mediaCallback)
                activeController = null
                _currentTrack.value = null
                stopTicker()
            }
            return
        }

        // Prefer currently playing session, or fallback to the most recent one
        val candidate = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()

        if (candidate != activeController) {
            activeController?.unregisterCallback(mediaCallback)
            activeController = candidate
            candidate?.registerCallback(mediaCallback, handler)
        }
        updateFromController(candidate)
    }

    private fun updateFromController(controller: MediaController?) {
        if (controller == null) {
            if (!demoPlaying) {
                _currentTrack.value = null
                stopTicker()
            }
            return
        }

        val metadata = controller.metadata
        val state = controller.playbackState
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: "audio stream"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: controller.packageName.substringAfterLast('.')
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val pos = state?.position ?: 0L
        val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        _currentTrack.value = MusicTrack(
            title = title,
            artist = artist,
            album = album,
            isPlaying = isPlaying,
            positionMs = pos,
            durationMs = duration,
            albumArt = art,
            packageName = controller.packageName,
            onPlayPause = {
                if (isPlaying) controller.transportControls.pause() else controller.transportControls.play()
            },
            onNext = { controller.transportControls.skipToNext() },
            onPrevious = { controller.transportControls.skipToPrevious() },
            onSeek = { ms -> controller.transportControls.seekTo(ms) },
        )

        if (isPlaying) startTicker() else stopTicker()
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                if (demoPlaying) {
                    demoPos = (demoPos + 1000).coerceAtMost(demoDuration)
                    if (demoPos >= demoDuration) demoPos = 0L
                    updateDemoTrack()
                } else {
                    _currentTrack.value?.let { current ->
                        if (current.isPlaying && current.durationMs > 0) {
                            val nextPos = (current.positionMs + 1000).coerceAtMost(current.durationMs)
                            _currentTrack.value = current.copy(positionMs = nextPos)
                        }
                    }
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    fun toggleDemo(): Boolean {
        if (demoPlaying || _currentTrack.value?.packageName == "dev.pantheon.demo") {
            stopDemo()
            return false
        } else {
            startDemo()
            return true
        }
    }

    fun startDemo() {
        demoPlaying = true
        updateDemoTrack()
        startTicker()
    }

    fun stopDemo() {
        demoPlaying = false
        stopTicker()
        if (activeController != null) {
            updateFromController(activeController)
        } else {
            _currentTrack.value = null
        }
    }

    private fun updateDemoTrack() {
        val (title, artist, album) = demoPlaylist[demoTrackIndex % demoPlaylist.size]
        _currentTrack.value = MusicTrack(
            title = title,
            artist = artist,
            album = album,
            isPlaying = demoPlaying,
            positionMs = demoPos,
            durationMs = demoDuration,
            packageName = "dev.pantheon.demo",
            onPlayPause = {
                demoPlaying = !demoPlaying
                updateDemoTrack()
                if (demoPlaying) startTicker() else stopTicker()
            },
            onNext = {
                demoTrackIndex = (demoTrackIndex + 1) % demoPlaylist.size
                demoPos = 0L
                updateDemoTrack()
            },
            onPrevious = {
                demoTrackIndex = (demoTrackIndex - 1 + demoPlaylist.size) % demoPlaylist.size
                demoPos = 0L
                updateDemoTrack()
            },
            onSeek = { ms ->
                demoPos = ms.coerceIn(0L, demoDuration)
                updateDemoTrack()
            },
        )
    }
}
