package com.example.livora.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.model.TodoIntervalStatus
import com.example.livora.data.model.TodoStats
import com.example.livora.ui.components.TaskTimerChip
import com.example.livora.ui.components.TopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoDetailScreen(
    viewModel: TodoViewModel,
    todoId: String,
    onBack: () -> Unit
) {
    val statsList by viewModel.stats.collectAsState()
    val runningTimers by viewModel.runningTimers.collectAsState()
    val stats = statsList.firstOrNull { it.todo.id == todoId }

    Scaffold(
        topBar = {
            TopBar(
                title = "Task",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (stats == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Task not found",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stats.todo.title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = scheduleSummary(stats.todo),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
            if (stats.todo.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stats.todo.notes,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }

            if (stats.todo.hasTimer && !stats.isDoneCurrentInterval) {
                Spacer(modifier = Modifier.height(20.dp))
                TaskTimerChip(
                    remainingMs = runningTimers[stats.todo.id],
                    onStart = { viewModel.startTimer(stats.todo.id) },
                    onCancel = { viewModel.cancelTimer(stats.todo.id) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            ThinDivider()
            Spacer(modifier = Modifier.height(8.dp))

            PropertyRow(label = "Streak", value = streakValue(stats.currentStreak))
            PropertyRow(label = "Best streak", value = streakValue(stats.bestStreak))
            PropertyRow(label = "Completion rate", value = "${(stats.completionRate * 100).roundToInt()}%")
            PropertyRow(label = "This interval", value = if (stats.isDoneCurrentInterval) "Done" else "Pending")
            if (stats.todo.hasTimer) {
                PropertyRow(
                    label = "Timer",
                    value = "${stats.todo.durationValue} ${stats.todo.durationUnit.label.lowercase()}"
                )
            }
            PropertyRow(label = "Last done", value = lastDoneValue(stats.lastCompletedAt))
            PropertyRow(label = "Total check-ins", value = stats.completions.size.toString())

            Spacer(modifier = Modifier.height(8.dp))
            ThinDivider()
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "History",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (stats.history.isEmpty()) {
                Text(
                    text = "No intervals yet",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                stats.history.forEachIndexed { index, item ->
                    HistoryRow(item = item)
                    if (index < stats.history.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun HistoryRow(item: TodoIntervalStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    color = if (item.isDone)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(50)
                )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = intervalLabel(item),
                fontSize = 14.sp,
                fontWeight = if (item.isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                textDecoration = if (!item.isDone && !item.isCurrent) TextDecoration.None else TextDecoration.None
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = intervalRange(item),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
        Text(
            text = when {
                item.isDone && item.completedAt != null -> "Done · " + formatTime(item.completedAt)
                item.isCurrent -> "Pending"
                else -> "Missed"
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = when {
                item.isDone -> MaterialTheme.colorScheme.onSurface
                item.isCurrent -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
            }
        )
    }
}

@Composable
private fun ThinDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        thickness = 0.5.dp
    )
}

private fun streakValue(value: Int): String = when {
    value <= 0 -> "0"
    value == 1 -> "1 interval"
    else -> "$value intervals"
}

private fun lastDoneValue(timestamp: Long?): String {
    if (timestamp == null) return "Never"
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minute = 60_000L
    val hour = 3_600_000L
    val day = 86_400_000L
    return when {
        diff < minute -> "Just now"
        diff < hour -> "${diff / minute} min ago"
        diff < day -> "${diff / hour} h ago"
        diff < 7 * day -> "${diff / day} d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun intervalLabel(item: TodoIntervalStatus): String {
    if (item.isCurrent) return "Now"
    val now = System.currentTimeMillis()
    val day = 86_400_000L
    val diff = now - item.end
    return when {
        diff < day -> "Previous interval"
        diff < 2 * day -> "Yesterday"
        diff < 7 * day -> "${diff / day} days ago"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(item.end))
    }
}

private fun intervalRange(item: TodoIntervalStatus): String {
    val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return formatter.format(Date(item.start)) + "  ‒  " + formatter.format(Date(item.end))
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(timestamp))
}
