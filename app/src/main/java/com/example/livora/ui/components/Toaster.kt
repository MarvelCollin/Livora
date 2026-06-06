package com.example.livora.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class ToastType { Success, Error, Info, Loading }

class ToastItem(
    val id: Long,
    val message: String,
    val type: ToastType,
    val durationMs: Long,
    val actionLabel: String?,
    val onAction: (() -> Unit)?
) {
    val transitionState = MutableTransitionState(false).apply { targetState = true }
}

object Toaster {

    private const val MAX_VISIBLE = 3
    private var counter = 0L
    val items = mutableStateListOf<ToastItem>()

    fun show(
        message: String,
        type: ToastType = ToastType.Info,
        durationMs: Long = defaultDuration(type),
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ): Long {
        val id = ++counter
        items.add(ToastItem(id, message, type, durationMs, actionLabel, onAction))
        while (items.size > MAX_VISIBLE) {
            items.removeAt(0)
        }
        return id
    }

    fun success(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) =
        show(message, ToastType.Success, actionLabel = actionLabel, onAction = onAction)

    fun error(message: String) = show(message, ToastType.Error)

    fun info(message: String) = show(message, ToastType.Info)

    fun dismiss(id: Long) {
        items.firstOrNull { it.id == id }?.transitionState?.targetState = false
    }

    fun remove(item: ToastItem) {
        items.remove(item)
    }

    private fun defaultDuration(type: ToastType): Long = when (type) {
        ToastType.Error -> 5000
        ToastType.Loading -> 60000
        else -> 3000
    }
}

@Composable
fun ToasterHost(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Toaster.items.forEach { item ->
            LaunchedEffect(item.id) {
                delay(item.durationMs)
                item.transitionState.targetState = false
            }
            LaunchedEffect(item.transitionState.currentState, item.transitionState.targetState) {
                if (!item.transitionState.currentState && !item.transitionState.targetState) {
                    Toaster.remove(item)
                }
            }
            AnimatedVisibility(
                visibleState = item.transitionState,
                enter = slideInVertically(animationSpec = tween(220)) { -it } + fadeIn(tween(220)),
                exit = slideOutVertically(animationSpec = tween(180)) { -it } + fadeOut(tween(180))
            ) {
                ToastCard(item)
            }
        }
    }
}

@Composable
private fun ToastCard(item: ToastItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(14.dp))
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (item.type) {
            ToastType.Success -> Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color(0xFF22C55E)
            )
            ToastType.Error -> Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error
            )
            ToastType.Info -> Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            ToastType.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = item.message,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        if (item.actionLabel != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.actionLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clickable {
                        item.onAction?.invoke()
                        Toaster.dismiss(item.id)
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
