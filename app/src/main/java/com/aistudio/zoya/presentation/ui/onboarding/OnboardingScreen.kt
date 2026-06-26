package com.aistudio.zoya.presentation.ui.onboarding

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.zoya.ui.theme.DarkBackground
import com.aistudio.zoya.ui.theme.NeonBlue
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.POST_NOTIFICATIONS
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "Welcome to Zoya",
            style = MaterialTheme.typography.displayLarge,
            color = NeonBlue
        )
        Text(
            text = "Your AI companion requires a few permissions to work her magic.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        PermissionItem(
            icon = Icons.Default.Mic,
            title = "Microphone",
            description = "To hear your voice and talk back in real-time."
        )
        PermissionItem(
            icon = Icons.Default.Phone,
            title = "Phone & Contacts",
            description = "To call your friends and manage your reach."
        )
        PermissionItem(
            icon = Icons.Default.Notifications,
            title = "Notifications",
            description = "To keep the connection alive in the background."
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (permissionsState.allPermissionsGranted) {
                    onComplete()
                } else {
                    permissionsState.launchMultiplePermissionRequest()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
        ) {
            Text(
                text = if (permissionsState.allPermissionsGranted) "GET STARTED" else "GRANT PERMISSIONS",
                color = DarkBackground,
                fontWeight = FontWeight.Bold
            )
        }
        
        if (permissionsState.allPermissionsGranted) {
            LaunchedEffect(Unit) {
                onComplete()
            }
        }
    }
}

@Composable
fun PermissionItem(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NeonBlue,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(text = description, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }
}
