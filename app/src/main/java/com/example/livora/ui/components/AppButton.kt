package com.example.livora.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ButtonKind { Primary, Tonal, Danger }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Tonal,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val container = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.08f)
        kind == ButtonKind.Primary -> scheme.primary
        kind == ButtonKind.Danger -> scheme.errorContainer
        else -> scheme.surfaceContainerHigh
    }
    val content = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.38f)
        kind == ButtonKind.Primary -> scheme.onPrimary
        kind == ButtonKind.Danger -> scheme.onErrorContainer
        else -> scheme.onSurface
    }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .pressScale(interactionSource, pressedScale = 0.97f)
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(container)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = content,
            textAlign = TextAlign.Center
        )
    }
}
