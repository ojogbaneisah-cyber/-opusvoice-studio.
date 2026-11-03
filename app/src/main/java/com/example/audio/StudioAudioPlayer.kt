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

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0,
    val currentFilePath: String? = null,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f
) {
    val progress: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

class StudioAudioPlayer(private val context: Context) {
    private val TAG = "StudioAudioPlayer"
    private var mediaPlayer: MediaPlayer? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    fun loadAndPlay(filePath: String, autoPlay: Boolean = true) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "Audio file does not exist: $filePath")
            return
        }

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            val player = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
            }

            mediaPlayer = player
            val duration = player.duration

            _playerState.value = _playerState.value.copy(
                currentFilePath = filePath,
                currentPositionMs = 0,
                durationMs = duration,
                isPlaying = autoPlay
            )

            applyPlaybackParams(_playerState.value.speed, _playerState.value.pitch)

            player.setOnCompletionListener {
                _playerState.value = _playerState.value.copy(
                    isPlaying = false,
                    currentPositionMs = duration
                )
                stopProgressPolling()
            }

            if (autoPlay) {
                player.start()
                startProgressPolling()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio file", e)
        }
    }

    fun play() {
        mediaPlayer?.let { player ->
            if (!player.isPlaying) {
                if (player.currentPosition >= player.duration) {
                    player.seekTo(0)
                }
                player.start()
                _playerState.value = _playerState.value.copy(isPlaying = true)
                startProgressPolling()
            }
        }
    }

    fun pause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _playerState.value = _playerState.value.copy(
                    isPlaying = false,
                    currentPositionMs = player.currentPosition
                )
                stopProgressPolling()
            }
        }
    }

    fun togglePlayPause() {
        if (_playerState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun replay() {
        seekTo(0f)
        play()
    }

    fun seekTo(progress: Float) {
        mediaPlayer?.let { player ->
            val targetMs = (progress * player.duration).toInt().coerceIn(0, player.duration)
            player.seekTo(targetMs)
            _playerState.value = _playerState.value.copy(currentPositionMs = targetMs)
        }
    }

    fun forward5Seconds() {
        mediaPlayer?.let { player ->
            val targetMs = (player.currentPosition + 5000).coerceAtMost(player.duration)
            player.seekTo(targetMs)
            _playerState.value = _playerState.value.copy(currentPositionMs = targetMs)
        }
    }

    fun rewind5Seconds() {
        mediaPlayer?.let { player ->
            val targetMs = (player.currentPosition - 5000).coerceAtLeast(0)
            player.seekTo(targetMs)
            _playerState.value = _playerState.value.copy(currentPositionMs = targetMs)
        }
    }

    fun setSpeed(speed: Float) {
        _playerState.value = _playerState.value.copy(speed = speed)
        applyPlaybackParams(speed, _playerState.value.pitch)
    }

    fun setPitch(pitch: Float) {
        _playerState.value = _playerState.value.copy(pitch = pitch)
        applyPlaybackParams(_playerState.value.speed, pitch)
    }

    private fun applyPlaybackParams(speed: Float, pitch: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.let { player ->
                    val wasPlaying = player.isPlaying
                    val params = PlaybackParams().apply {
                        this.speed = speed.coerceIn(0.5f, 2.5f)
                        this.pitch = pitch.coerceIn(0.5f, 2.0f)
                    }
                    player.playbackParams = params
                    if (!wasPlaying) {
                        player.pause()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to apply PlaybackParams", e)
            }
        }
    }

    private fun startProgressPolling() {
        stopProgressPolling()
        progressJob = coroutineScope.launch {
            while (isActive) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        _playerState.value = _playerState.value.copy(
                            currentPositionMs = player.currentPosition
                        )
                    }
                }
                delay(50) // Smooth 20fps progress tracking
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopProgressPolling()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MediaPlayer", e)
        }
        mediaPlayer = null
    }
}
