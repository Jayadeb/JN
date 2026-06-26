package com.aistudio.zoya

import android.app.Application
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.aistudio.zoya.data.audio.AudioInputManager
import com.aistudio.zoya.data.gemini.LiveSessionManager
import com.aistudio.zoya.data.tools.ToolExecutionEngine
import com.aistudio.zoya.util.SoundManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ZoyaApplication : Application() {
    lateinit var liveSessionManager: LiveSessionManager
    lateinit var audioInputManager: AudioInputManager
    lateinit var audioTrack: AudioTrack
    lateinit var soundManager: SoundManager

    override fun onCreate() {
        super.onCreate()
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
        val toolEngine = ToolExecutionEngine(this)
        liveSessionManager = LiveSessionManager(client, toolEngine)
        audioInputManager = AudioInputManager()
        soundManager = SoundManager(this)
        
        val sampleRate = 16000
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }
}
