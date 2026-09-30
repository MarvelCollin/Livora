package com.example.livora.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

class BarPoint(val value: Float, val title: String, val valueText: String)

@Composable
fun BarChart(
    points: List<BarPoint>,
    axisLabels: List<String?>,
    color: Color,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 120.dp,
    description: String,
    initialSelected: Int? = null
) {
    if (points.isEmpty()) return
    val peak = points.indices.maxByOrNull { points[it].value } ?: 0
    var selected by remember(points) { mutableStateOf((initialSelected ?: peak).coerceIn(0, points.lastIndex)) }
    val grow = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { grow.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }

    val top = points.maxOf { it.value }.coerceAtLeast(1f)
    val band = MaterialTheme.colorScheme.surfaceContainerHigh
    val baseline = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Box(modifier = Modifier.fillMaxWidth().height(chartHeight + 64.dp)) {
            val tip = points[selected]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        val slot = constraints.maxWidth.toFloat() / points.size
                        val center = slot * (selected + 0.5f)
                        val x = (center - placeable.width / 2f).roundToInt().coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
                        layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(x, 0) }
                    }
            ) {
                Tooltip(title = tip.title, value = tip.valueText, color = color)
            }
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(chartHeight)
                    .pointerInput(points) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val slot = size.width.toFloat() / points.size
                            selected = (down.position.x / slot).toInt().coerceIn(0, points.lastIndex)
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.pressed) {
                                    selected = (change.position.x / slot).toInt().coerceIn(0, points.lastIndex)
                                    val delta = change.position - change.previousPosition
                                    if (change.positionChanged() && abs(delta.x) > abs(delta.y)) change.consume()
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            ) {
                val slot = size.width / points.size
                val barWidth = min(24.dp.toPx(), slot * 0.72f)
                val radius = 4.dp.toPx()
                drawRect(
                    color = band.copy(alpha = 0.55f),
                    topLeft = Offset(slot * selected, 0f),
                    size = Size(slot, size.height)
                )
                points.forEachIndexed { index, point ->
                    val h = (size.height * point.value / top * grow.value).coerceAtLeast(if (point.value > 0f) 3.dp.toPx() * grow.value else 0f)
                    val left = slot * index + (slot - barWidth) / 2f
                    val rect = Rect(left, size.height - h, left + barWidth, size.height)
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect,
                                topLeft = CornerRadius(radius),
                                topRight = CornerRadius(radius),
                                bottomLeft = CornerRadius.Zero,
                                bottomRight = CornerRadius.Zero
                            )
                        )
                    }
                    drawPath(path, if (index == selected) color else color.copy(alpha = 0.5f))
                }
                drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            axisLabels.forEach { label ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (label != null) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.wrapContentWidth(unbounded = true)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Tooltip(title: String, value: String, color: Color) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(12.dp).height(3.dp).background(color, RoundedCornerShape(2.dp)))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Text(
            text = title,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun ColorKey(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
}
