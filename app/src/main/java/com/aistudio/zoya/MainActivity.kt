package com.aistudio.zoya

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import android.content.Intent
import android.os.Build
import com.aistudio.zoya.service.BackgroundAudioService
import com.aistudio.zoya.presentation.ui.main.MainScreen
import com.aistudio.zoya.presentation.ui.onboarding.OnboardingScreen
import com.aistudio.zoya.presentation.viewmodel.AssistantViewModel
import com.aistudio.zoya.presentation.viewmodel.PermissionsViewModel
import com.aistudio.zoya.ui.theme.ZoyaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        val app = application as ZoyaApplication
        val assistantViewModel = AssistantViewModel(app.liveSessionManager, app.soundManager)
        val permissionsViewModel = PermissionsViewModel()

        setContent {
            ZoyaTheme {
                val onboardingCompleted by permissionsViewModel.onboardingCompleted.collectAsState()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (onboardingCompleted) {
                        MainScreen(assistantViewModel)
                    } else {
                        OnboardingScreen {
                            permissionsViewModel.completeOnboarding()
                            val serviceIntent = Intent(this, BackgroundAudioService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(serviceIntent)
                            } else {
                                startService(serviceIntent)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getStringExtra("action") == "listen") {
            // Intent handling logic
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
}
