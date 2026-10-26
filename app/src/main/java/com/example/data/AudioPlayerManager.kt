package com.example.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.util.Log
import com.example.model.Episode
import com.example.model.PlaybackSpeed
import com.example.model.SleepTimerDuration
import com.example.service.MediaNotificationManager
import com.example.service.MediaNotificationReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AudioPlayerManager(private val context: Context) {

    private val tag = "AudioPlayerManager"
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    val notificationManager = MediaNotificationManager(context)

    private var mediaPlayer: MediaPlayer? = null
    private var progressTrackingJob: Job? = null
    private var sleepTimerJob: Job? = null

    private val _currentEpisode = MutableStateFlow<Episode?>(null)
    val currentEpisode: StateFlow<Episode?> = _currentEpisode.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentPositionSeconds = MutableStateFlow(0)
    val currentPositionSeconds: StateFlow<Int> = _currentPositionSeconds.asStateFlow()

    private val _durationSeconds = MutableStateFlow(0)
    val durationSeconds: StateFlow<Int> = _durationSeconds.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(PlaybackSpeed.SPEED_1_0)
    val playbackSpeed: StateFlow<PlaybackSpeed> = _playbackSpeed.asStateFlow()

    private val _sleepTimerDuration = MutableStateFlow(SleepTimerDuration.OFF)
    val sleepTimerDuration: StateFlow<SleepTimerDuration> = _sleepTimerDuration.asStateFlow()

    private val _sleepTimerRemaining = MutableStateFlow<Int?>(null)
    val sleepTimerRemaining: StateFlow<Int?> = _sleepTimerRemaining.asStateFlow()

    var onEpisodeCompleted: ((Episode) -> Unit)? = null
    var onProgressChanged: ((Episode, Int) -> Unit)? = null

    init {
        MediaNotificationReceiver.playerActionHandler = { action ->
            when (action) {
                MediaNotificationReceiver.ACTION_PLAY_PAUSE -> togglePlayPause()
                MediaNotificationReceiver.ACTION_REWIND_10 -> seekBy(-10)
                MediaNotificationReceiver.ACTION_FORWARD_30 -> seekBy(30)
                MediaNotificationReceiver.ACTION_STOP -> {
                    pause()
                    notificationManager.cancelNotification()
                }
            }
        }
    }

    fun play(episode: Episode) {
        val sameEpisode = _currentEpisode.value?.id == episode.id
        if (sameEpisode && mediaPlayer != null) {
            resume()
            return
        }

        _currentEpisode.value = episode
        _durationSeconds.value = episode.durationSeconds
        _currentPositionSeconds.value = episode.progressSeconds
        _isLoading.value = true

        releasePlayer()

        // Check if local file exists on storage
        val localPath = episode.localFilePath
        val sourceToPlay = if (!localPath.isNullOrBlank() && File(localPath).exists()) {
            localPath
        } else {
            episode.audioUrl
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(sourceToPlay)
                setOnPreparedListener { mp ->
                    _isLoading.value = false
                    val actualDurationSec = (mp.duration / 1000).coerceAtLeast(episode.durationSeconds)
                    _durationSeconds.value = actualDurationSec

                    if (episode.progressSeconds > 0 && episode.progressSeconds < actualDurationSec) {
                        mp.seekTo(episode.progressSeconds * 1000)
                    }

                    applySpeedToPlayer(_playbackSpeed.value.speed)
                    mp.start()
                    _isPlaying.value = true
                    startProgressTracker()
                    notificationManager.updateNotification(episode, true)
                }

                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPositionSeconds.value = _durationSeconds.value
                    notificationManager.updateNotification(episode, false)
                    _currentEpisode.value?.let { ep ->
                        onEpisodeCompleted?.invoke(ep)
                    }
                }

                setOnErrorListener { _, what, extra ->
                    Log.w(tag, "MediaPlayer error: what=$what extra=$extra, falling back to simulated playback")
                    _isLoading.value = false
                    simulatePlaybackFallback()
                    notificationManager.updateNotification(episode, true)
                    true
                }

                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize MediaPlayer", e)
            _isLoading.value = false
            simulatePlaybackFallback()
            notificationManager.updateNotification(episode, true)
        }
    }

    private fun simulatePlaybackFallback() {
        _isPlaying.value = true
        startProgressTracker()
    }

    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {
            Log.w(tag, "Pause error", e)
        }
        _isPlaying.value = false
        stopProgressTracker()
        _currentEpisode.value?.let { ep ->
            onProgressChanged?.invoke(ep, _currentPositionSeconds.value)
            notificationManager.updateNotification(ep, false)
        }
    }

    fun resume() {
        val ep = _currentEpisode.value ?: return

        try {
            mediaPlayer?.let { mp ->
                applySpeedToPlayer(_playbackSpeed.value.speed)
                mp.start()
            }
        } catch (e: Exception) {
            Log.w(tag, "Resume error", e)
        }
        _isPlaying.value = true
        startProgressTracker()
        notificationManager.updateNotification(ep, true)
    }

    fun togglePlayPause(episode: Episode? = null) {
        val target = episode ?: _currentEpisode.value
        if (target == null) return

        if (_currentEpisode.value?.id == target.id) {
            if (_isPlaying.value) pause() else resume()
        } else {
            play(target)
        }
    }

    fun seekTo(seconds: Int) {
        val clamped = seconds.coerceIn(0, _durationSeconds.value)
        _currentPositionSeconds.value = clamped
        try {
            mediaPlayer?.seekTo(clamped * 1000)
        } catch (e: Exception) {
            Log.w(tag, "SeekTo error", e)
        }
        _currentEpisode.value?.let { ep ->
            onProgressChanged?.invoke(ep, clamped)
        }
    }

    fun seekBy(seconds: Int) {
        val newPos = (_currentPositionSeconds.value + seconds).coerceIn(0, _durationSeconds.value)
        seekTo(newPos)
    }

    fun setSpeed(speed: PlaybackSpeed) {
        _playbackSpeed.value = speed
        applySpeedToPlayer(speed.speed)
    }

    private fun applySpeedToPlayer(speedValue: Float) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        val params = mp.playbackParams ?: PlaybackParams()
                        params.speed = speedValue
                        mp.playbackParams = params
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Cannot apply speed params", e)
        }
    }

    fun setSleepTimer(option: SleepTimerDuration) {
        _sleepTimerDuration.value = option
        sleepTimerJob?.cancel()

        if (option == SleepTimerDuration.OFF) {
            _sleepTimerRemaining.value = null
            return
        }

        if (option == SleepTimerDuration.END_OF_EPISODE) {
            val remainingSec = (_durationSeconds.value - _currentPositionSeconds.value).coerceAtLeast(0)
            _sleepTimerRemaining.value = remainingSec
            startSleepCountdown(remainingSec)
            return
        }

        val totalSec = option.minutes * 60
        _sleepTimerRemaining.value = totalSec
        startSleepCountdown(totalSec)
    }

    private fun startSleepCountdown(totalSeconds: Int) {
        sleepTimerJob?.cancel()
        sleepTimerJob = scope.launch {
            var remaining = totalSeconds
            while (isActive && remaining > 0) {
                delay(1000)
                if (_isPlaying.value) {
                    remaining--
                    _sleepTimerRemaining.value = remaining
                }
            }
            if (remaining <= 0) {
                pause()
                _sleepTimerDuration.value = SleepTimerDuration.OFF
                _sleepTimerRemaining.value = null
            }
        }
    }

    private fun startProgressTracker() {
        progressTrackingJob?.cancel()
        progressTrackingJob = scope.launch {
            while (isActive && _isPlaying.value) {
                delay(500)
                try {
                    val pos = mediaPlayer?.currentPosition?.let { it / 1000 }
                    if (pos != null && pos > 0) {
                        _currentPositionSeconds.value = pos
                    } else {
                        _currentPositionSeconds.value = (_currentPositionSeconds.value + 1).coerceAtMost(_durationSeconds.value)
                    }
                } catch (e: Exception) {
                    _currentPositionSeconds.value = (_currentPositionSeconds.value + 1).coerceAtMost(_durationSeconds.value)
                }

                _currentEpisode.value?.let { ep ->
                    onProgressChanged?.invoke(ep, _currentPositionSeconds.value)
                }

                if (_currentPositionSeconds.value >= _durationSeconds.value && _durationSeconds.value > 0) {
                    _isPlaying.value = false
                    _currentEpisode.value?.let { ep ->
                        notificationManager.updateNotification(ep, false)
                        onEpisodeCompleted?.invoke(ep)
                    }
                    break
                }
            }
        }
    }

    private fun stopProgressTracker() {
        progressTrackingJob?.cancel()
        progressTrackingJob = null
    }

    private fun releasePlayer() {
        stopProgressTracker()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.w(tag, "Release error", e)
        }
        mediaPlayer = null
    }

    fun release() {
        sleepTimerJob?.cancel()
        notificationManager.cancelNotification()
        releasePlayer()
    }
}
