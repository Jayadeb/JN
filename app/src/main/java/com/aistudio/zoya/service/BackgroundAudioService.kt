package com.aistudio.zoya.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aistudio.zoya.MainActivity
import com.aistudio.zoya.R
import com.aistudio.zoya.ZoyaApplication
import com.aistudio.zoya.data.audio.AudioInputManager
import com.aistudio.zoya.data.gemini.LiveSessionManager
import com.aistudio.zoya.domain.model.AssistantState
import com.aistudio.zoya.util.SoundManager
import kotlinx.coroutines.*

class BackgroundAudioService : Service() {

    lateinit var liveSessionManager: LiveSessionManager
    lateinit var audioInputManager: AudioInputManager
    lateinit var audioTrack: AudioTrack
    lateinit var soundManager: SoundManager
    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val CHANNEL_ID = "ZoyaAssistantChannel"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Starting Zoya..."))

        val app = application as ZoyaApplication
        liveSessionManager = app.liveSessionManager
        audioInputManager = app.audioInputManager
        audioTrack = app.audioTrack
        soundManager = app.soundManager
        
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Zoya::WakeWordLock").apply {
                acquire()
            }
        } catch (e: Exception) {
            Log.e("BackgroundAudioService", "Failed to acquire wake lock: ${e.message}")
        }

        val hasRecordPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasRecordPermission) {
            updateNotification("Microphone permission required.")
            return
        }
        
        var lastState: AssistantState = AssistantState.Idle
        serviceScope.launch {
            liveSessionManager.state.collect { state ->
                if (state != lastState) {
                    val message = when (state) {
                        AssistantState.Idle -> {
                            if (lastState != AssistantState.Idle && lastState != AssistantState.Sleeping) {
                                soundManager.playDeactivationSound()
                            }
                            "Listening for 'Zoya'..."
                        }
                        AssistantState.Listening -> {
                            soundManager.playActivationSound()
                            "Zoya is listening..."
                        }
                        is AssistantState.Thinking -> "Zoya is thinking..."
                        is AssistantState.Speaking -> "Zoya is speaking..."
                        AssistantState.Sleeping -> "Zoya is resting."
                        AssistantState.Offline -> "Zoya is offline."
                        is AssistantState.Error -> {
                            soundManager.playErrorSound()
                            "Zoya encountered an error."
                        }
                    }
                    updateNotification(message)
                    lastState = state
                }
            }
        }
        
        serviceScope.launch {
            liveSessionManager.audioOutput.collect { audioData ->
                if (audioData != null) {
                    liveSessionManager.setOutputVolume(audioData)
                    if (audioTrack.state == AudioTrack.STATE_INITIALIZED) {
                        if (audioTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
                            audioTrack.play()
                        }
                        audioTrack.write(audioData, 0, audioData.size)
                    }
                } else {
                    if (audioTrack.state == AudioTrack.STATE_INITIALIZED && 
                        audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        audioTrack.pause()
                        audioTrack.flush()
                    }
                    liveSessionManager.setOutputVolume(byteArrayOf())
                }
            }
        }

        try {
            audioInputManager.start(
                onAudioData = { audioData ->
                    if (liveSessionManager.state.value is AssistantState.Listening || 
                        liveSessionManager.state.value is AssistantState.Speaking) {
                        liveSessionManager.sendAudio(audioData)
                    }
                },
                onWakeWordDetected = {
                    if (liveSessionManager.state.value == AssistantState.Idle) {
                        liveSessionManager.startSession()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("BackgroundAudioService", "Failed to start audio components: ${e.message}")
            updateNotification("Error starting microphone.")
        }
    }

    private fun updateNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(message))
    }

    private fun createNotification(message: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Zoya Assistant")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_zoya_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Zoya Assistant Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        serviceScope.cancel()
        audioInputManager.stop()
        liveSessionManager.stopSession()
        audioTrack.stop()
        audioTrack.release()
    }
}
