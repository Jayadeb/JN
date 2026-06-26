package com.aistudio.zoya.presentation.ui.main

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.aistudio.zoya.domain.model.AssistantState
import com.aistudio.zoya.domain.model.Sentiment
import com.aistudio.zoya.presentation.ui.components.ZoyaOrb
import com.aistudio.zoya.presentation.viewmodel.AssistantViewModel
import com.aistudio.zoya.ui.theme.DarkBackground
import com.aistudio.zoya.ui.theme.NeonBlue
import com.aistudio.zoya.ui.theme.NeonPink
import com.aistudio.zoya.ui.theme.NeonPurple

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: AssistantViewModel) {
    val realState by viewModel.state.collectAsState()
    val realVolume by viewModel.volume.collectAsState()
    val testingMode by viewModel.testingMode.collectAsState()
    val testState by viewModel.testState.collectAsState()
    val testVolume by viewModel.testVolume.collectAsState()

    val state = if (testingMode) testState else realState
    val volume = if (testingMode) testVolume else realVolume
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Immersive background pulse
    val bgPulse by animateFloatAsState(
        targetValue = if (state != AssistantState.Idle) 0.15f + (volume * 0.2f) else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
        label = "bgPulse"
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DarkBackground,
                drawerContentColor = Color.White
            ) {
                Spacer(Modifier.height(48.dp))
                Text(
                    "ZOYA SETTINGS",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = NeonBlue
                )
                NavigationDrawerItem(
                    label = { Text("Voice History") },
                    selected = false,
                    onClick = { /* TODO */ },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = Color.White
                    )
                )
                NavigationDrawerItem(
                    label = { Text("AI Personality") },
                    selected = false,
                    onClick = { /* TODO */ },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = Color.White
                    )
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "v1.2.0-Alpha",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            DarkBackground.copy(alpha = 1f),
                            Color(0xFF000000)
                        )
                    )
                )
        ) {
            // Pulse Overlay
            if (bgPulse > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(NeonPurple.copy(alpha = bgPulse), Color.Transparent),
                                center = Offset.Unspecified
                            )
                        )
                )
            }

            // Menu Toggle
            IconButton(
                onClick = { scope.launch { drawerState.open() } },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 48.dp, start = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = Color.White
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "ZOYA",
                    style = MaterialTheme.typography.displayLarge.copy(
                        color = NeonBlue,
                        letterSpacing = 8.sp,
                        fontWeight = FontWeight.Light
                    ),
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = { viewModel.toggleTestingMode() }
                    )
                )
                
                Spacer(modifier = Modifier.height(64.dp))

                ZoyaOrb(
                    state = state,
                    volume = volume
                )

                Spacer(modifier = Modifier.height(64.dp))

                Text(
                    text = when (state) {
                        AssistantState.Idle -> "I'm here for you."
                        AssistantState.Listening -> "Listening..."
                        is AssistantState.Thinking -> "Thinking..."
                        is AssistantState.Speaking -> "Zoya is speaking"
                        AssistantState.Sleeping -> "Resting..."
                        AssistantState.Offline -> "Offline"
                        is AssistantState.Error -> (state as AssistantState.Error).message
                    },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.ExtraLight
                    )
                )
            }

            // Action Button
            val haptic = LocalHapticFeedback.current
            
            if (!testingMode) {
                FloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (state == AssistantState.Idle) viewModel.startListening()
                        else viewModel.stopListening()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 64.dp),
                    containerColor = NeonPurple,
                    contentColor = Color.White
                ) {
                    Text(if (state == AssistantState.Idle) "ACTIVATE" else "STOP")
                }
            } else {
                // Testing Controls
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = Color.Black.copy(alpha = 0.8f),
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("TESTING MODE", color = NeonPink, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TestingButton("IDLE") { viewModel.setTestState(AssistantState.Idle) }
                            TestingButton("LISTEN") { viewModel.setTestState(AssistantState.Listening) }
                            TestingButton("THINK") { viewModel.setTestState(AssistantState.Thinking()) }
                            TestingButton("SPEAK") { viewModel.setTestState(AssistantState.Speaking()) }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TestingButton("HAPPY") { viewModel.setTestState(AssistantState.Speaking(Sentiment.Happy)) }
                            TestingButton("SAD") { viewModel.setTestState(AssistantState.Speaking(Sentiment.Sad)) }
                            TestingButton("ANGRY") { viewModel.setTestState(AssistantState.Speaking(Sentiment.Angry)) }
                            TestingButton("EXCITED") { viewModel.setTestState(AssistantState.Speaking(Sentiment.Excited)) }
                            TestingButton("TEST SOUND") { viewModel.playTestSound() }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Volume", color = Color.White, modifier = Modifier.width(80.dp))
                            Slider(
                                value = testVolume,
                                onValueChange = { viewModel.setTestVolume(it) },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.toggleTestingMode() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Test", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TestingButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.5f))
    ) {
        Text(text, fontSize = 10.sp)
    }
}
