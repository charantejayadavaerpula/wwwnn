package com.example.audio

import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentTrackPath = MutableStateFlow<String?>(null)
    val currentTrackPath: StateFlow<String?> = _currentTrackPath

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition

    private val _trackDuration = MutableStateFlow(0L)
    val trackDuration: StateFlow<Long> = _trackDuration

    private var progressJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    fun play(filePath: String, onCompleted: () -> Unit = {}) {
        // If it's already playing the requested track, toggle pause/resume or just ignore
        if (_currentTrackPath.value == filePath && mediaPlayer != null) {
            if (!_isPlaying.value) {
                mediaPlayer?.start()
                _isPlaying.value = true
                startProgressPolling()
            }
            return
        }

        // Otherwise, stop current playback
        stop()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()
                
                _currentTrackPath.value = filePath
                _isPlaying.value = true
                _trackDuration.value = duration.toLong()
                
                setOnCompletionListener {
                    stop()
                    onCompleted()
                }
            }
            startProgressPolling()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to play audio file", e)
            stop()
        }
    }

    fun pause() {
        if (_isPlaying.value && mediaPlayer != null) {
            mediaPlayer?.pause()
            _isPlaying.value = false
            progressJob?.cancel()
        }
    }

    fun resume() {
        if (!_isPlaying.value && mediaPlayer != null) {
            mediaPlayer?.start()
            _isPlaying.value = true
            startProgressPolling()
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error stopping player", e)
        }
        mediaPlayer = null
        _isPlaying.value = false
        _currentTrackPath.value = null
        _playbackPosition.value = 0L
        _trackDuration.value = 0L
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let {
            it.seekTo(positionMs.toInt())
            _playbackPosition.value = positionMs
        }
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = coroutineScope.launch {
            while (_isPlaying.value && mediaPlayer != null) {
                try {
                    _playbackPosition.value = (mediaPlayer?.currentPosition ?: 0).toLong()
                } catch (e: Exception) {
                    // Ignore transient exceptions from native player
                }
                delay(100)
            }
        }
    }
}
