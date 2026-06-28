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
import androidx.work.*
import java.util.concurrent.TimeUnit

class BackgroundAudioService : Service() {

    lateinit var liveSessionManager: LiveSessionManager
    lateinit var audioInputManager: AudioInputManager
    lateinit var audioTrack: AudioTrack
    lateinit var soundManager: SoundManager
    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val CHANNEL_ID = "ZoyaAssistantChannel"
    private val NOTIFICATION_ID = 1
    private val ACTION_STOP = "com.aistudio.zoya.STOP_SERVICE"
    private var isManualStop = false

    override fun onCreate() {
        super.onCreate()
        isManualStop = false
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Starting Zoya..."))

        val app = application as ZoyaApplication
        liveSessionManager = app.liveSessionManager
        
        // Acquire WakeLock to prevent the service from being killed or paused
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Zoya::AssistantWakeLock").apply {
                acquire(10 * 60 * 1000L /*10 minutes fallback*/)
            }
        } catch (e: Exception) {
            Log.e("BackgroundAudioService", "Failed to acquire wake lock: ${e.message}")
        }
        
        // Use attribution context if on Android 12+
        val attributionContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            createAttributionContext("microphone")
        } else {
            this
        }

        audioInputManager = AudioInputManager(attributionContext)
        audioTrack = app.audioTrack
        soundManager = app.soundManager
        
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
                val currentLastState = lastState
                // Only update notification if the state type changed or if it's a new error
                val stateChanged = when {
                    state::class != currentLastState::class -> true
                    state is AssistantState.Error && currentLastState is AssistantState.Error -> state.message != currentLastState.message
                    else -> false
                }

                if (stateChanged) {
                    val message = when (state) {
                        AssistantState.Idle -> "Listening for 'Zoya'..."
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
                            "Error: ${state.message}"
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
                        try {
                            if (audioTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
                                audioTrack.play()
                            }
                            
                            // Apply pitch
                            val params = audioTrack.playbackParams
                            if (params.pitch != liveSessionManager.pitch.value) {
                                params.pitch = liveSessionManager.pitch.value
                                audioTrack.playbackParams = params
                            }

                            val result = audioTrack.write(audioData, 0, audioData.size)
                            if (result < 0) {
                                Log.e("BackgroundAudioService", "Error writing audio: $result")
                            }
                        } catch (e: Exception) {
                            Log.e("BackgroundAudioService", "Audio write exception: ${e.message}")
                        }
                    }
                } else {
                    Log.d("BackgroundAudioService", "Turn complete, flushing audio track")
                    liveSessionManager.setOutputVolume(byteArrayOf())
                    try {
                        audioTrack.pause()
                        audioTrack.flush()
                    } catch (e: Exception) {
                        Log.e("BackgroundAudioService", "Error flushing audio track: ${e.message}")
                    }
                }
            }
        }

        try {
            startAudioInput()
        } catch (e: Exception) {
            Log.e("BackgroundAudioService", "Failed to start audio components: ${e.message}")
            updateNotification("Error starting microphone. Retrying...")
            serviceScope.launch {
                delay(5000)
                try {
                    startAudioInput()
                } catch (e2: Exception) {
                    updateNotification("Mic failed. Check permissions.")
                }
            }
        }
    }

    private fun startAudioInput() {
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
    }

    private var lastNotificationTime = 0L
    private val NOTIFICATION_THROTTLE_MS = 2000L

    private fun updateNotification(message: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNotificationTime < NOTIFICATION_THROTTLE_MS) return
        lastNotificationTime = currentTime
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(message))
    }

    private fun createNotification(message: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, BackgroundAudioService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Zoya Assistant")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_zoya_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(0, "Stop", stopPendingIntent)
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
        if (intent?.action == ACTION_STOP) {
            isManualStop = true
            stopSelf()
            return START_NOT_STICKY
        }
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

        if (!isManualStop) {
            Log.d("BackgroundAudioService", "Service destroyed unexpectedly, scheduling restart.")
            val workRequest = OneTimeWorkRequestBuilder<ServiceRestartWorker>()
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build()
            androidx.work.WorkManager.getInstance(this).enqueue(workRequest)
        }
    }
}
