package com.gmusic.app.ui.player.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun WavySlider(
    value: Float, // 0.0f .. 1.0f
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    isPlaying: Boolean = true,
    enabled: Boolean = true,
    waveAmplitude: Dp = 4.dp,
    waveLength: Dp = 26.dp,
    trackHeight: Dp = 4.dp,
    thumbRadius: Dp = 6.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(value) }

    val currentFraction = if (isDragging) dragFraction else value.coerceIn(0f, 1f)

    // Animazione dell'ampiezza dell'onda: quando in pausa, l'onda si distende (A -> 0)
    val animatedAmplitude by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "wavyAmplitudeAnim"
    )

    // Scorrimento continuo di fase dell'onda quando in riproduzione
    val infiniteTransition = rememberInfiniteTransition(label = "wavePhaseTransition")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying) (2f * PI.toFloat()) else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing)
        ),
        label = "wavePhase"
    )

    // Riutilizzo del Path per evitare continue allocazioni a 60/120 FPS nello scope di disegno (Zero GC churn)
    val wavePath = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width
                    if (width > 0) {
                        isDragging = true
                        dragFraction = (down.position.x / width).coerceIn(0f, 1f)
                        onValueChange(dragFraction)

                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (change.pressed) {
                                change.consume()
                                dragFraction = (change.position.x / width).coerceIn(0f, 1f)
                                onValueChange(dragFraction)
                            } else {
                                break
                            }
                        }
                        isDragging = false
                        onValueChangeFinished?.invoke()
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val centerY = size.height / 2f
            val thumbX = width * currentFraction

            val trackStrokePx = trackHeight.toPx()
            val ampPx = waveAmplitude.toPx() * animatedAmplitude
            val waveLengthPx = waveLength.toPx()
            val thumbRadiusPx = thumbRadius.toPx()

            // 1. Traccia Inattiva (Rimanente dal cursore alla fine): linea retta morbida
            if (thumbX < width) {
                drawLine(
                    color = inactiveColor,
                    start = Offset(thumbX, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = trackStrokePx,
                    cap = StrokeCap.Round
                )
            }

            // 2. Traccia Attiva (Da 0 fino al cursore): Sinusoide fluida se c'è ampiezza, altrimenti retta
            if (thumbX > 0f) {
                if (ampPx > 0.05f) {
                    wavePath.rewind()
                    wavePath.moveTo(0f, centerY)

                    // Campionamento ottimizzato proporzionale alla lunghezza d'onda
                    val stepPx = (waveLengthPx / 16f).coerceIn(2f, 6f)
                    var x = 0f
                    while (x <= thumbX) {
                        val angle = (2f * PI.toFloat() * (x / waveLengthPx)) - wavePhase
                        val y = centerY + (ampPx * sin(angle))
                        wavePath.lineTo(x, y)
                        x += stepPx
                    }
                    wavePath.lineTo(thumbX, centerY)

                    drawPath(
                        path = wavePath,
                        color = activeColor,
                        style = Stroke(
                            width = trackStrokePx,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                } else {
                    drawLine(
                        color = activeColor,
                        start = Offset(0f, centerY),
                        end = Offset(thumbX, centerY),
                        strokeWidth = trackStrokePx,
                        cap = StrokeCap.Round
                    )
                }
            }

            // 3. Thumb / Scrubber (Pillola/Cerchio M3)
            drawCircle(
                color = activeColor,
                radius = if (isDragging) thumbRadiusPx * 1.35f else thumbRadiusPx,
                center = Offset(thumbX, centerY)
            )
        }
    }
}
