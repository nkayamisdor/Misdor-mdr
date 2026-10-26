package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.util.Log
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

data class AudioPlayerState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val progress: Float = 0f,
    val playbackSpeed: Float = 1.0f,
    val currentFilePath: String? = null,
    val waveformHeights: List<Float> = List(32) { 0.2f }
)

class AudioPlayerManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressTrackerJob: Job? = null

    private val _playerState = MutableStateFlow(AudioPlayerState())
    val playerState: StateFlow<AudioPlayerState> = _playerState.asStateFlow()

    fun loadAudio(filePath: String, autoPlay: Boolean = false) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.e("AudioPlayerManager", "Audio file not found: $filePath")
            return
        }

        stop()
        mediaPlayer?.release()
        mediaPlayer = null

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                setOnPreparedListener { mp ->
                    val duration = mp.duration.toLong().coerceAtLeast(1000L)
                    _playerState.value = _playerState.value.copy(
                        currentPositionMs = 0L,
                        durationMs = duration,
                        progress = 0f,
                        currentFilePath = filePath,
                        waveformHeights = generateWaveformData(duration)
                    )
                    applyPlaybackSpeed(_playerState.value.playbackSpeed)
                    if (autoPlay) {
                        play()
                    }
                }
                setOnCompletionListener {
                    _playerState.value = _playerState.value.copy(
                        isPlaying = false,
                        currentPositionMs = 0L,
                        progress = 0f
                    )
                    progressTrackerJob?.cancel()
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Error preparing media player: ${e.message}")
        }
    }

    fun play() {
        val player = mediaPlayer ?: return
        try {
            player.start()
            _playerState.value = _playerState.value.copy(isPlaying = true)
            startProgressTracker()
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Error starting playback: ${e.message}")
        }
    }

    fun pause() {
        val player = mediaPlayer ?: return
        try {
            if (player.isPlaying) {
                player.pause()
            }
            _playerState.value = _playerState.value.copy(isPlaying = false)
            progressTrackerJob?.cancel()
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Error pausing playback: ${e.message}")
        }
    }

    fun togglePlayPause() {
        if (_playerState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        try {
            player.seekTo(positionMs.toInt())
            val duration = _playerState.value.durationMs.coerceAtLeast(1L)
            _playerState.value = _playerState.value.copy(
                currentPositionMs = positionMs,
                progress = (positionMs.toFloat() / duration).coerceIn(0f, 1f)
            )
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Error seeking: ${e.message}")
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playerState.value = _playerState.value.copy(playbackSpeed = speed)
        applyPlaybackSpeed(speed)
    }

    private fun applyPlaybackSpeed(speed: Float) {
        val player = mediaPlayer ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val params = player.playbackParams ?: PlaybackParams()
                params.speed = speed
                player.playbackParams = params
            }
        } catch (e: Exception) {
            Log.w("AudioPlayerManager", "Could not set playback speed: ${e.message}")
        }
    }

    fun stop() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
        } catch (_: Exception) {}
        _playerState.value = _playerState.value.copy(
            isPlaying = false,
            currentPositionMs = 0L,
            progress = 0f
        )
        progressTrackerJob?.cancel()
    }

    fun release() {
        stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun startProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = scope.launch {
            while (isActive && _playerState.value.isPlaying) {
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    val pos = player.currentPosition.toLong()
                    val dur = _playerState.value.durationMs.coerceAtLeast(1L)
                    val prog = (pos.toFloat() / dur).coerceIn(0f, 1f)
                    _playerState.value = _playerState.value.copy(
                        currentPositionMs = pos,
                        progress = prog
                    )
                }
                delay(100L)
            }
        }
    }

    private fun generateWaveformData(durationMs: Long): List<Float> {
        val count = 32
        val randomSeed = (durationMs % 1000).toInt()
        return List(count) { index ->
            val factor = kotlin.math.sin((index + randomSeed) * 0.45).toFloat()
            0.2f + (0.75f * kotlin.math.abs(factor))
        }
    }

    companion object {
        fun formatTime(millis: Long): String {
            val totalSeconds = (millis / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
    }
}
