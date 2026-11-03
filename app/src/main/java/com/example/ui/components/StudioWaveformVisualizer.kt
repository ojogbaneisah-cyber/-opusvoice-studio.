package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.WaveformInactive
import kotlin.math.max

@Composable
fun StudioWaveformVisualizer(
    amplitudes: List<Float>,
    progress: Float,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    activeColor: Color = StudioCyan,
    inactiveColor: Color = WaveformInactive
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_pulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .testTag("waveform_canvas")
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(newProgress)
                }
            }
    ) {
        val barCount = if (amplitudes.isNotEmpty()) amplitudes.size else 48
        val effectiveAmplitudes = if (amplitudes.isNotEmpty()) amplitudes else List(barCount) { 0.25f }

        val canvasWidth = size.width
        val canvasHeight = size.height
        val totalSpacingRatio = 0.35f
        val totalBarWidth = canvasWidth / barCount
        val barWidth = max(2f, totalBarWidth * (1f - totalSpacingRatio))
        val barSpacing = totalBarWidth * totalSpacingRatio

        val currentProgressX = progress * canvasWidth

        val activeBrush = Brush.verticalGradient(
            colors = listOf(StudioCyan, StudioViolet)
        )

        for (i in 0 until barCount) {
            val amp = effectiveAmplitudes[i].coerceIn(0.12f, 1.0f)
            val animatedAmp = if (isPlaying && i.toFloat() / barCount <= progress) {
                (amp * pulseGlow).coerceIn(0.12f, 1.0f)
            } else {
                amp
            }

            val barHeight = canvasHeight * animatedAmp
            val x = i * totalBarWidth + barSpacing / 2
            val y = (canvasHeight - barHeight) / 2

            val isPlayed = (x + barWidth / 2) <= currentProgressX

            if (isPlayed) {
                drawRoundRect(
                    brush = activeBrush,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                )
            } else {
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                )
            }
        }

        // Draw cursor line at current playback position
        if (progress in 0.001f..0.999f) {
            drawLine(
                color = Color.White,
                start = Offset(currentProgressX, 4f),
                end = Offset(currentProgressX, canvasHeight - 4f),
                strokeWidth = 3f
            )
            drawCircle(
                color = StudioCyan,
                radius = 5f,
                center = Offset(currentProgressX, canvasHeight / 2)
            )
        }
    }
}
