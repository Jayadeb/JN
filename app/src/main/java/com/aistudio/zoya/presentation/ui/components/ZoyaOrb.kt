package com.aistudio.zoya.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aistudio.zoya.domain.model.AssistantState
import com.aistudio.zoya.ui.theme.NeonBlue
import com.aistudio.zoya.ui.theme.NeonPink
import com.aistudio.zoya.ui.theme.NeonPurple
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.PI
import kotlin.math.sin

import com.aistudio.zoya.domain.model.Sentiment

@Composable
fun ZoyaOrb(
    state: AssistantState,
    volume: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbTransition")
    
    // Sentiment based colors
    val sentiment = when (state) {
        is AssistantState.Speaking -> state.sentiment
        is AssistantState.Thinking -> state.sentiment
        else -> Sentiment.Neutral
    }

    val primaryColorState = animateColorAsState(
        targetValue = when (sentiment) {
            Sentiment.Happy -> Color(0xFF00FFCC) // Cyan
            Sentiment.Sad -> Color(0xFF4A90E2) // Deep Blue
            Sentiment.Angry -> Color(0xFFFF4B2B) // Red
            Sentiment.Excited -> Color(0xFFFF00CC) // Magenta
            Sentiment.Thinking -> NeonPurple
            else -> NeonBlue
        },
        animationSpec = tween(1000),
        label = "primaryColor"
    )
    val primaryColor = primaryColorState.value

    val secondaryColorState = animateColorAsState(
        targetValue = when (sentiment) {
            Sentiment.Happy -> Color(0xFFF7FF00) // Yellow
            Sentiment.Sad -> Color(0xFFB0BEC5) // Gray
            Sentiment.Angry -> Color(0xFFFF8C00) // Orange
            Sentiment.Excited -> Color(0xFFFFD700) // Gold
            Sentiment.Thinking -> NeonPink
            else -> NeonPurple
        },
        animationSpec = tween(1000),
        label = "secondaryColor"
    )
    val secondaryColor = secondaryColorState.value

    // Pulse intensity based on sentiment
    val pulseDuration = when (sentiment) {
        Sentiment.Excited -> 1000
        Sentiment.Angry -> 1500
        Sentiment.Sad -> 5000
        else -> 3000
    }

    // Floating motion
    val floatingOffset by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floating"
    )

    // Wave phase animation
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // Smooth volume response
    val animatedVolume by animateFloatAsState(
        targetValue = volume,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "animatedVolume"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Base scale modified by volume
    val dynamicScale = pulse + (animatedVolume * 0.4f)
    
    Canvas(
        modifier = modifier
            .size(300.dp)
            .offset(y = floatingOffset.dp)
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val baseRadius = size.width * 0.3f
        
        val colors: List<Color> = when (state) {
            is AssistantState.Idle -> listOf(primaryColor.copy(alpha = 0.3f), Color.Transparent)
            is AssistantState.Listening -> listOf(primaryColor, secondaryColor)
            is AssistantState.Thinking -> listOf(primaryColor, secondaryColor)
            is AssistantState.Speaking -> listOf(primaryColor, secondaryColor, primaryColor.copy(alpha = 0.8f))
            else -> listOf(Color.Gray, Color.DarkGray)
        }

        // Outer Glow (expands with volume)
        drawCircle(
            brush = Brush.radialGradient(
                colors = colors.map { it.copy(alpha = it.alpha * 0.4f) },
                center = center,
                radius = baseRadius * dynamicScale * 2.5f
            ),
            radius = baseRadius * dynamicScale * 2.5f,
            center = center
        )

        // Wavefront Visualization
        if (state != AssistantState.Idle) {
            val wavePath = Path()
            val waveCount = 3
            val waveHeight = baseRadius * 0.4f * (0.5f + animatedVolume)
            
            for (i in 0 until waveCount) {
                val alpha = 0.3f / (i + 1)
                val phaseOffset = i * (PI.toFloat() / 2f)
                
                wavePath.reset()
                for (x in 0..size.width.toInt() step 5) {
                    val normalizedX = x / size.width
                    val y = center.y + sin(normalizedX * 2f * PI.toFloat() + wavePhase + phaseOffset) * waveHeight
                    if (x == 0) wavePath.moveTo(x.toFloat(), y.toFloat())
                    else wavePath.lineTo(x.toFloat(), y.toFloat())
                }
                
                drawPath(
                    path = wavePath,
                    color = colors.first().copy(alpha = alpha),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Main Body
        drawCircle(
            brush = Brush.radialGradient(
                colors = colors,
                center = center,
                radius = baseRadius * dynamicScale
            ),
            radius = baseRadius * dynamicScale,
            center = center
        )

        // Core (shines brighter with volume)
        drawCircle(
            color = Color.White.copy(alpha = 0.8f + (animatedVolume * 0.2f)),
            radius = baseRadius * 0.4f * (1f + animatedVolume * 0.2f),
            center = center
        )

        // Orbital rings for Listening/Speaking
        if (state is AssistantState.Listening || state is AssistantState.Speaking) {
            drawCircle(
                brush = Brush.sweepGradient(colors, center),
                radius = baseRadius * (1.2f + animatedVolume * 0.5f),
                center = center,
                style = Stroke(width = (2.dp + (animatedVolume * 4).dp).toPx())
            )
        }
    }
}
