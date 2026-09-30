package com.example.livora.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

class DonutSlice(
    val id: Long,
    val label: String,
    val value: Float,
    val valueText: String,
    val shareText: String,
    val color: Color
)

@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    centerLabel: String,
    centerValue: String,
    description: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 208.dp
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    val grow = remember(slices) { Animatable(0f) }
    LaunchedEffect(slices) { grow.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    val dim by animateFloatAsState(
        targetValue = if (selectedId != null) 1f else 0f,
        animationSpec = tween(Motion.Short),
        label = "donutDim"
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val stroke = 26.dp

    Box(
        modifier = modifier
            .size(diameter)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(diameter)
                .pointerInput(slices) {
                    detectTapGestures { tap ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = tap.x - center.x
                        val dy = tap.y - center.y
                        val distance = hypot(dx, dy)
                        val outer = size.width / 2f
                        val inner = outer - stroke.toPx() - 8.dp.toPx()
                        if (distance > outer || distance < inner || total <= 0f) {
                            onSelect(null)
                        } else {
                            var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 90f
                            if (angle < 0f) angle += 360f
                            var start = 0f
                            var hit: DonutSlice? = null
                            for (slice in slices) {
                                val sweep = slice.value / total * 360f
                                if (angle >= start && angle < start + sweep) {
                                    hit = slice
                                    break
                                }
                                start += sweep
                            }
                            onSelect(if (hit == null || hit.id == selectedId) null else hit.id)
                        }
                    }
                }
        ) {
            val strokePx = stroke.toPx()
            val extra = 4.dp.toPx()
            val radius = (size.minDimension - strokePx - extra * 2) / 2f
            val topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius)
            val arc = Size(radius * 2f, radius * 2f)
            drawCircle(color = track, radius = radius, style = Stroke(strokePx))
            if (total > 0f) {
                val gap = if (slices.size > 1) (2.dp.toPx() / radius) * (180f / PI.toFloat()) else 0f
                var start = -90f
                slices.forEach { slice ->
                    val sweep = slice.value / total * 360f * grow.value
                    val drawn = (sweep - gap).coerceAtLeast(0.5f)
                    val chosen = slice.id == selectedId
                    val alpha = if (selectedId == null || chosen) 1f else 1f - 0.62f * dim
                    drawArc(
                        color = slice.color.copy(alpha = alpha),
                        startAngle = start + gap / 2f,
                        sweepAngle = drawn,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arc,
                        style = Stroke(width = if (chosen) strokePx + extra else strokePx)
                    )
                    start += sweep
                }
            }
        }
        Crossfade(
            targetState = selectedId,
            animationSpec = tween(Motion.Short),
            label = "donutCenter"
        ) { id ->
            val slice = slices.firstOrNull { it.id == id }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.size(diameter - stroke * 2 - 12.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text(
                    text = slice?.label ?: centerLabel,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = slice?.valueText ?: centerValue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                if (slice != null) {
                    Text(
                        text = slice.shareText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
