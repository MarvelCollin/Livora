package com.example.livora.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

object Motion {
    const val Short = 150
    const val Medium = 280
    const val Long = 480

    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> enter(): FiniteAnimationSpec<T> = tween(Medium, easing = EmphasizedDecelerate)
    fun <T> exit(): FiniteAnimationSpec<T> = tween(Short, easing = EmphasizedAccelerate)
    fun <T> quick(): FiniteAnimationSpec<T> = tween(Short, easing = Standard)
}

@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.96f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 600f),
        label = "pressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun GrowBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHigh
) {
    val target = fraction.coerceIn(0f, 1f)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target) { progress.animateTo(target, tween(Motion.Long + 120, easing = Motion.EmphasizedDecelerate)) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(track)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = progress.value
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .background(color)
        )
    }
}

@Composable
fun SuccessCheck(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    onFinished: () -> Unit = {}
) {
    val ring = remember { Animatable(0f) }
    val check = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val pop = remember { Animatable(0.85f) }

    LaunchedEffect(Unit) {
        launch { pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 350f)) }
        ring.animateTo(1f, tween(360, easing = Motion.EmphasizedDecelerate))
        launch { burst.animateTo(1f, tween(520, easing = Motion.Standard)) }
        check.animateTo(1f, tween(260, easing = Motion.EmphasizedDecelerate))
        onFinished()
    }

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
    ) {
        val stroke = this.size.minDimension * 0.075f
        val inset = stroke * 2.2f
        val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * ring.value,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        val w = this.size.width
        val h = this.size.height
        val full = Path().apply {
            moveTo(w * 0.32f, h * 0.52f)
            lineTo(w * 0.45f, h * 0.65f)
            lineTo(w * 0.70f, h * 0.38f)
        }
        val measure = PathMeasure().apply { setPath(full, false) }
        val part = Path()
        measure.getSegment(0f, measure.length * check.value, part, true)
        drawPath(part, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (burst.value in 0.01f..0.99f) {
            drawCircle(
                color = color.copy(alpha = (1f - burst.value) * 0.35f),
                radius = (arcSize.width / 2f) * (1f + 0.45f * burst.value),
                style = Stroke(width = stroke * 0.6f)
            )
        }
    }
}
