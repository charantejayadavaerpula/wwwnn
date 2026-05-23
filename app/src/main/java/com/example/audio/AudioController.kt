package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class AudioController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // --- State Management ---
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _isLiveListening = MutableStateFlow(false)
    val isLiveListening: StateFlow<Boolean> = _isLiveListening

    private val _liveAmplitude = MutableStateFlow(0f)
    val liveAmplitude: StateFlow<Float> = _liveAmplitude

    private val _scoState = MutableStateFlow(false)
    val scoState: StateFlow<Boolean> = _scoState

    // --- Media Recorder Variables ---
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var recordingStartTime: Long = 0
    private var recorderJob: Job? = null

    // --- Live Listen Variables ---
    private var liveListenJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var gainFactor: Float = 1.0f

    // Scope for background polling/loops
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    fun setGain(gain: Float) {
        this.gainFactor = gain
    }

    // --- Feature One: Mode Recording ---
    fun startRecording(fileName: String): File? {
        if (_isRecording.value) return null
        
        try {
            val recordDir = File(context.filesDir, "recordings")
            if (!recordDir.exists()) {
                recordDir.mkdirs()
            }
            
            val sanitiseName = fileName.trim().ifEmpty { "Recording_${System.currentTimeMillis()}" }
            val file = File(recordDir, "$sanitiseName.m4a")
            currentRecordingFile = file

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recordingStartTime = System.currentTimeMillis()
            _isRecording.value = true

            // Poll Amplitude for Visualizer
            recorderJob = coroutineScope.launch {
                while (_isRecording.value) {
                    try {
                        val maxAmp = mediaRecorder?.maxAmplitude ?: 0
                        // Normalize 0 to 32767 to 0f..1f
                        val normalized = (maxAmp.toFloat() / 32767f).coerceIn(0f, 1f)
                        _liveAmplitude.value = normalized
                    } catch (e: Exception) {
                        Log.e("AudioController", "Poller error", e)
                    }
                    delay(100)
                }
            }
            return file
        } catch (e: Exception) {
            Log.e("AudioController", "Failed to start recording", e)
            _isRecording.value = false
            currentRecordingFile = null
            return null
        }
    }

    fun stopRecording(): RecordingResult? {
        if (!_isRecording.value) return null

        _isRecording.value = false
        recorderJob?.cancel()
        recorderJob = null

        val duration = System.currentTimeMillis() - recordingStartTime
        val file = currentRecordingFile

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioController", "Failed to stop mediaRecorder cleanly", e)
        }
        mediaRecorder = null
        currentRecordingFile = null
        _liveAmplitude.value = 0f

        return if (file != null && file.exists()) {
            RecordingResult(
                name = file.nameWithoutExtension,
                filePath = file.absolutePath,
                durationMs = duration,
                fileSize = file.length()
            )
        } else {
            null
        }
    }

    // --- Feature Two: Mode Live Listen / Amplify ---
    fun startLiveListening(gain: Float = 1.0f) {
        if (_isLiveListening.value) return
        this.gainFactor = gain

        _isLiveListening.value = true
        _liveAmplitude.value = 0f

        liveListenJob = coroutineScope.launch(Dispatchers.IO) {
            val sampleRate = 44100
            val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
            val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)
            // Use a small buffer to avoid latency
            val bufferSize = (minBufferSize * 2).coerceAtLeast(1024)

            try {
                // Determine audio source. If SCO is active, VOICE_COMMUNICATION holds better support for Bluetooth
                val audioSource = if (_scoState.value) {
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION
                } else {
                    MediaRecorder.AudioSource.MIC
                }

                audioRecord = AudioRecord(
                    audioSource,
                    sampleRate,
                    channelConfigIn,
                    audioFormat,
                    bufferSize
                )

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfigOut)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e("AudioController", "AudioRecord initialization failed")
                    _isLiveListening.value = false
                    return@launch
                }

                audioRecord?.startRecording()
                audioTrack?.play()

                val buffer = ShortArray(bufferSize)

                while (_isLiveListening.value) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readSize > 0) {
                        var maxVal = 0
                        // Apply Digital Gain Amplification
                        for (i in 0 until readSize) {
                            val sample = buffer[i]
                            val absSample = Math.abs(sample.toInt())
                            if (absSample > maxVal) maxVal = absSample

                            // Amplify
                            val amplified = (sample * gainFactor).toInt()
                            buffer[i] = when {
                                amplified > Short.MAX_VALUE -> Short.MAX_VALUE
                                amplified < Short.MIN_VALUE -> Short.MIN_VALUE
                                else -> amplified.toShort()
                            }
                        }

                        // Broadcast Amplitude for UI Meter
                        val normalized = (maxVal.toFloat() / 32767f).coerceIn(0f, 1f)
                        _liveAmplitude.value = normalized

                        // Stream immediately to AudioTrack
                        audioTrack?.write(buffer, 0, readSize)
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioController", "Error in Live Listening Stream Loop", e)
            } finally {
                cleanupLiveListen()
            }
        }
    }

    fun stopLiveListening() {
        _isLiveListening.value = false
        liveListenJob?.cancel()
        liveListenJob = null
        _liveAmplitude.value = 0f
    }

    private fun cleanupLiveListen() {
        try {
            audioRecord?.apply {
                if (state == AudioRecord.STATE_INITIALIZED && recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioController", "Error releasing AudioRecord", e)
        }
        audioRecord = null

        try {
            audioTrack?.apply {
                if (state == AudioTrack.STATE_INITIALIZED && playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioController", "Error releasing AudioTrack", e)
        }
        audioTrack = null
    }

    // --- Bluetooth SCO Control ---
    fun toggleBluetoothSco(enabled: Boolean) {
        try {
            if (enabled) {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                _scoState.value = true
                
                // If live listening is running, restart it to switch source
                if (_isLiveListening.value) {
                    stopLiveListening()
                    startLiveListening(gainFactor)
                }
            } else {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                _scoState.value = false

                // If live listening is running, restart it to switch source
                if (_isLiveListening.value) {
                    stopLiveListening()
                    startLiveListening(gainFactor)
                }
            }
        } catch (e: Exception) {
            Log.e("AudioController", "Error toggling Bluetooth SCO", e)
        }
    }
}

data class RecordingResult(
    val name: String,
    val filePath: String,
    val durationMs: Long,
    val fileSize: Long
)
