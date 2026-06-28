package com.aistudio.zoya.data.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log

class AudioInputManager(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var isRunning = false
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private var onAudioDataListener: ((ByteArray) -> Unit)? = null
    private var onWakeWordDetectedListener: (() -> Unit)? = null

    // VAD and Wake detection parameters
    private val energyThreshold = 500.0 // Adjusted for 16-bit PCM RMS
    private val voiceHangoverMs = 1500L
    private var lastVoiceTime = 0L
    private var isVoiceDetected = false

    // Wake word detection (simple energy + count pattern for 'Hey Zoya')
    private var wakeTriggerCount = 0
    private val wakeTriggerRequired = 3
    private val wakeEnergyThreshold = 1500.0

    fun start(onAudioData: (ByteArray) -> Unit, onWakeWordDetected: () -> Unit) {
        if (isRunning) return
        isRunning = true
        onAudioDataListener = onAudioData
        onWakeWordDetectedListener = onWakeWordDetected

        try {
            audioRecord = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setContext(context)
                    .build()
            } else {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
            }
        } catch (e: Exception) {
            Log.e("AudioInputManager", "Failed to create AudioRecord: ${e.message}")
            isRunning = false
            return
        }

        Thread {
            try {
                audioRecord?.startRecording()
                val shortBuffer = ShortArray(bufferSize / 2)
                
                while (isRunning) {
                    val read = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                    if (read > 0) {
                        val currentTime = System.currentTimeMillis()
                        
                        // Calculate RMS energy for VAD
                        var sum = 0.0
                        for (i in 0 until read) {
                            sum += shortBuffer[i].toInt() * shortBuffer[i].toInt()
                        }
                        val rms = Math.sqrt(sum / read)

                        // 1. Wake word detection logic (only if not active)
                        if (rms > wakeEnergyThreshold) {
                            wakeTriggerCount++
                            if (wakeTriggerCount >= wakeTriggerRequired) {
                                Log.d("AudioInputManager", "Wake trigger detected (RMS: $rms)")
                                onWakeWordDetectedListener?.invoke()
                                wakeTriggerCount = 0
                            }
                        } else {
                            if (wakeTriggerCount > 0) wakeTriggerCount--
                        }

                        // 2. VAD Logic
                        if (rms > energyThreshold) {
                            lastVoiceTime = currentTime
                            isVoiceDetected = true
                        } else {
                            if (currentTime - lastVoiceTime > voiceHangoverMs) {
                                isVoiceDetected = false
                            }
                        }

                        // 3. Streaming Logic (only if voice is detected or hangover active)
                        if (isVoiceDetected) {
                            val byteArray = ByteArray(read * 2)
                            for (i in 0 until read) {
                                val s = shortBuffer[i].toInt()
                                byteArray[i * 2] = (s and 0x00FF).toByte()
                                byteArray[i * 2 + 1] = (s shr 8).toByte()
                            }
                            onAudioDataListener?.invoke(byteArray)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioInputManager", "Error in audio loop: ${e.message}")
            } finally {
                try {
                    audioRecord?.stop()
                } catch (e: Exception) {
                    Log.e("AudioInputManager", "Error stopping AudioRecord: ${e.message}")
                }
                audioRecord?.release()
                audioRecord = null
            }
        }.apply {
            name = "ZoyaAudioInputThread"
            start()
        }
    }

    fun stop() {
        isRunning = false
    }
}
