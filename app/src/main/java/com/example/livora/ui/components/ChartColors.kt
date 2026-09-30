package com.example.livora.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val lightSlots = listOf(
    Color(0xFF2A78D6),
    Color(0xFFEB6834),
    Color(0xFF1BAF7A),
    Color(0xFFEDA100),
    Color(0xFFE87BA4),
    Color(0xFF008300),
    Color(0xFF4A3AA7),
    Color(0xFFE34948)
)

private val darkSlots = listOf(
    Color(0xFF3987E5),
    Color(0xFFD95926),
    Color(0xFF199E70),
    Color(0xFFC98500),
    Color(0xFFD55181),
    Color(0xFF008300),
    Color(0xFF9085E9),
    Color(0xFFE66767)
)

object ChartSlot {
    const val Blue = 0
    const val Orange = 1
    const val Aqua = 2
    const val Yellow = 3
    const val Magenta = 4
    const val Green = 5
    const val Violet = 6
    const val Red = 7
}

@Composable
fun chartColor(slot: Int): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val palette = if (dark) darkSlots else lightSlots
    return palette[slot.coerceIn(0, palette.lastIndex)]
}

fun statusGood(): Color = Color(0xFF0CA30C)

@Composable
fun chartNeutral(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
