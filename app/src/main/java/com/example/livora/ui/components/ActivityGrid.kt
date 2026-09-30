package com.example.livora.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class GridDay(
    val count: Int,
    val detail: String,
    val future: Boolean,
    val today: Boolean
)

@Composable
fun ActivityGrid(
    days: List<GridDay>,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
    weekdayLabels: List<String> = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
) {
    if (days.isEmpty()) return
    val weeks = (days.size + 6) / 7
    val startSelected = days.indexOfLast { it.today }.takeIf { it >= 0 } ?: days.indexOfLast { !it.future }.coerceAtLeast(0)
    var selected by remember(days) { mutableIntStateOf(startSelected) }
    val top = days.maxOf { it.count }.coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val ring = MaterialTheme.colorScheme.onSurface
    val gap = 4.dp

    Column(modifier = modifier.fillMaxWidth().semantics { contentDescription = description }) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cell = ((maxWidth - gap * 6) / 7).coerceAtMost(56.dp)
            val gridWidth = cell * 7 + gap * 6
            val gridHeight = cell * weeks + gap * (weeks - 1)
            val density = LocalDensity.current
            Column(modifier = Modifier.width(gridWidth)) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    weekdayLabels.forEach { label ->
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(cell)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Canvas(
                    modifier = Modifier
                        .width(gridWidth)
                        .height(gridHeight)
                        .pointerInput(days) {
                            val cellPx = with(density) { cell.toPx() }
                            val gapPx = with(density) { gap.toPx() }
                            fun indexAt(offset: Offset): Int? {
                                val col = (offset.x / (cellPx + gapPx)).toInt()
                                val row = (offset.y / (cellPx + gapPx)).toInt()
                                if (col !in 0 until 7 || row !in 0 until weeks) return null
                                val index = row * 7 + col
                                return index.takeIf { it < days.size && !days[it].future }
                            }
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                indexAt(down.position)?.let { selected = it }
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (change.pressed) indexAt(change.position)?.let { selected = it }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                ) {
                    val cellPx = cell.toPx()
                    val gapPx = gap.toPx()
                    val radius = CornerRadius(5.dp.toPx())
                    days.forEachIndexed { index, day ->
                        val x = (index % 7) * (cellPx + gapPx)
                        val y = (index / 7) * (cellPx + gapPx)
                        if (day.future) {
                            drawRoundRect(
                                color = track.copy(alpha = 0.6f),
                                topLeft = Offset(x + 0.5.dp.toPx(), y + 0.5.dp.toPx()),
                                size = Size(cellPx - 1.dp.toPx(), cellPx - 1.dp.toPx()),
                                cornerRadius = radius,
                                style = Stroke(1.dp.toPx())
                            )
                        } else {
                            val fill = if (day.count == 0) track else color.copy(alpha = 0.3f + 0.7f * day.count / top)
                            drawRoundRect(color = fill, topLeft = Offset(x, y), size = Size(cellPx, cellPx), cornerRadius = radius)
                        }
                        if (index == selected) {
                            drawRoundRect(
                                color = ring,
                                topLeft = Offset(x + 1.dp.toPx(), y + 1.dp.toPx()),
                                size = Size(cellPx - 2.dp.toPx(), cellPx - 2.dp.toPx()),
                                cornerRadius = radius,
                                style = Stroke(2.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = days.getOrNull(selected)?.detail.orEmpty(),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(text = "Less", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(0f, 0.34f, 0.67f, 1f).forEach { step ->
                Box(
                    modifier = Modifier
                        .padding(start = 3.dp)
                        .size(12.dp)
                        .background(
                            if (step == 0f) track else color.copy(alpha = 0.3f + 0.7f * step),
                            RoundedCornerShape(3.dp)
                        )
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "More", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
