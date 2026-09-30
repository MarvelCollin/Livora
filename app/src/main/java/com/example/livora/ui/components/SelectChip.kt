package com.example.livora.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class ChoiceOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null
)

@Composable
fun <T> ChoiceRow(
    options: List<ChoiceOption<T>>,
    selected: T,
    enabled: Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            SelectChip(
                label = option.label,
                icon = option.icon,
                selected = option.value == selected,
                enabled = enabled,
                onClick = { onSelect(option.value) },
                modifier = Modifier.weight(1f),
                horizontalPadding = 4.dp
            )
        }
    }
}

@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = Design.chipHorizontalPadding
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            selected && enabled -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "chipBg"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            selected && enabled -> MaterialTheme.colorScheme.surface
            enabled -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        },
        label = "chipContent"
    )

    Column(
        modifier = modifier
            .defaultMinSize(minHeight = if (icon != null) 56.dp else 48.dp)
            .clip(Design.chipShape)
            .background(bgColor)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = horizontalPadding, vertical = Design.chipVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
