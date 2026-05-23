package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioController
import com.example.audio.AudioPlayer
import com.example.data.AppDatabase
import com.example.data.RecordingEntity
import com.example.data.RecordingRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = RecordingRepository(db.recordingDao())

    private val audioController = AudioController(application)
    private val audioPlayer = AudioPlayer()

    // --- Active UI Mode ---
    // 0 = Record Audio (Mode One)
    // 1 = Live Listen / Amplify (Mode Two)
    private val _currentMode = MutableStateFlow(0)
    val currentMode: StateFlow<Int> = _currentMode

    // --- State Observables from Controller ---
    val isRecording = audioController.isRecording
    val isLiveListening = audioController.isLiveListening
    val liveAmplitude = audioController.liveAmplitude
    val scoState = audioController.scoState

    // --- State Observables from Player ---
    val isPlaying = audioPlayer.isPlaying
    val currentTrackPath = audioPlayer.currentTrackPath
    val playbackPosition = audioPlayer.playbackPosition
    val trackDuration = audioPlayer.trackDuration

    // --- Recordings List ---
    val recordings: StateFlow<List<RecordingEntity>> = repository.allRecordings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // --- Settings / Live Params ---
    private val _liveGain = MutableStateFlow(1.5f) // Default 1.5x gain
    val liveGain: StateFlow<Float> = _liveGain

    fun setMode(mode: Int) {
        if (_isNewRecordingDialogShown.value) return
        _currentMode.value = mode
        // Stop components when shifting modes to avoid microphone clashes
        if (mode == 0) {
            audioController.stopLiveListening()
        } else {
            audioController.stopRecording()
            audioPlayer.stop()
        }
    }

    // --- Dialogue / Temp state ---
    private val _tempRecordingResult = MutableStateFlow<com.example.audio.RecordingResult?>(null)
    private val _isNewRecordingDialogShown = MutableStateFlow(false)
    val isNewRecordingDialogShown: StateFlow<Boolean> = _isNewRecordingDialogShown

    // --- Feature One ACTIONS (Recorder) ---
    fun startRecording(customName: String) {
        // Stop playback when recording starts
        audioPlayer.stop()
        audioController.startRecording(customName)
    }

    fun stopRecording() {
        val result = audioController.stopRecording()
        if (result != null) {
            // Instantly save to DB
            viewModelScope.launch {
                val entity = RecordingEntity(
                    name = result.name,
                    filePath = result.filePath,
                    durationMs = result.durationMs,
                    fileSize = result.fileSize
                )
                repository.insert(entity)
            }
        }
    }

    fun deleteRecording(recording: RecordingEntity) {
        viewModelScope.launch {
            if (audioPlayer.currentTrackPath.value == recording.filePath) {
                audioPlayer.stop()
            }
            repository.delete(recording)
        }
    }

    fun renameRecording(recording: RecordingEntity, newName: String) {
        viewModelScope.launch {
            val updated = recording.copy(name = newName.trim().ifEmpty { recording.name })
            repository.update(updated)
        }
    }

    // --- Playback Actions ---
    fun playRecording(recording: RecordingEntity) {
        // Ensure live listen is disabled before starting playback
        audioController.stopLiveListening()
        audioPlayer.play(recording.filePath)
    }

    fun pausePlayback() {
        audioPlayer.pause()
    }

    fun resumePlayback() {
        audioPlayer.resume()
    }

    fun seekPlayback(positionMs: Long) {
        audioPlayer.seekTo(positionMs)
    }

    fun stopPlayback() {
        audioPlayer.stop()
    }

    // --- Feature Two ACTIONS (Live Listen / Amplification) ---
    fun toggleLiveListening() {
        if (isLiveListening.value) {
            audioController.stopLiveListening()
        } else {
            // Ensure player/recorder is stopped
            audioPlayer.stop()
            audioController.stopRecording()
            audioController.startLiveListening(_liveGain.value)
        }
    }

    fun setGainFactor(gain: Float) {
        _liveGain.value = gain
        audioController.setGain(gain)
    }

    fun toggleBluetoothSco(enabled: Boolean) {
        audioController.toggleBluetoothSco(enabled)
    }

    override fun onCleared() {
        super.onCleared()
        audioController.stopRecording()
        audioController.stopLiveListening()
        audioPlayer.stop()
    }
}
