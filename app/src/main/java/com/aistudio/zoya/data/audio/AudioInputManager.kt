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

    // For wake word detection (simple energy threshold)
    private val energyThreshold = 10000000.0
    private val peakThreshold = 22000

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
                val byteBuffer = ByteArray(bufferSize)
                val shortBuffer = ShortArray(bufferSize / 2)
                
                while (isRunning) {
                    val read = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                    if (read > 0) {
                        // Process for wake word
                        val energy = shortBuffer.take(read).map { it.toInt() * it.toInt() }.average()
                        val peak = shortBuffer.take(read).maxOrNull() ?: 0
                        
                        if (peak > peakThreshold && energy > energyThreshold) {
                            Log.d("AudioInputManager", "Wake word potential: Peak=$peak, Energy=$energy")
                            onWakeWordDetectedListener?.invoke()
                        }

                        // Convert to ByteArray for streaming
                        val byteArray = ByteArray(read * 2)
                        for (i in 0 until read) {
                            val s = shortBuffer[i].toInt()
                            byteArray[i * 2] = (s and 0x00FF).toByte()
                            byteArray[i * 2 + 1] = (s shr 8).toByte()
                        }
                        onAudioDataListener?.invoke(byteArray)
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioInputManager", "Error in audio loop: ${e.message}")
            } finally {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
            }
        }.start()
    }

    fun stop() {
        isRunning = false
    }
}
