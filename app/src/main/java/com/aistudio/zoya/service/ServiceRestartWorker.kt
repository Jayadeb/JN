package com.aistudio.zoya.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.core.content.ContextCompat

class ServiceRestartWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("ServiceRestartWorker", "Checking if BackgroundAudioService is running...")
        
        // Start the service. If it's already running, onStartCommand will be called.
        // If it's not running, it will be created.
        val intent = Intent(context, BackgroundAudioService::class.java)
        try {
            ContextCompat.startForegroundService(context, intent)
            Log.d("ServiceRestartWorker", "Service restart intent sent.")
        } catch (e: Exception) {
            Log.e("ServiceRestartWorker", "Failed to start service: ${e.message}")
            return Result.retry()
        }

        return Result.success()
    }
}
