package com.example.livora.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.model.Todo
import com.example.livora.data.model.TodoDurationUnit
import com.example.livora.data.model.TodoIntervalUnit
import com.example.livora.data.model.TodoStats
import com.example.livora.ui.components.SkeletonBox
import com.example.livora.ui.components.SkeletonLine
import com.example.livora.ui.components.TaskTimerChip
import com.example.livora.ui.components.TopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    viewModel: TodoViewModel,
    onOpenDetail: (String) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val stats by viewModel.stats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val runningTimers by viewModel.runningTimers.collectAsState()
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingTodo = stats.firstOrNull { it.todo.id == editingId }?.todo

    Scaffold(
        topBar = {
            TopBar(
                title = "Tasks",
                subtitle = "Routines and streaks",
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editingId = null
                        isEditing = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 4.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            if (isEditing) {
                item {
                    TodoForm(
                        todo = editingTodo,
                        onSave = { title, notes, intervalValue, intervalUnit, timeOfDay, durationValue, durationUnit, hasTimer ->
                            if (viewModel.upsertTodo(
                                    editingTodo,
                                    title,
                                    notes,
                                    intervalValue,
                                    intervalUnit,
                                    timeOfDay,
                                    durationValue,
                                    durationUnit,
                                    hasTimer
                                )
                            ) {
                                isEditing = false
                                editingId = null
                            }
                        },
                        onCancel = {
                            isEditing = false
                            editingId = null
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        thickness = 0.5.dp
                    )
                }
            }

            if (stats.isEmpty() && !isEditing && isLoading) {
                items(5) { TodoRowSkeleton() }
            }

            if (stats.isEmpty() && !isEditing && !isLoading) {
                item {
                    EmptyState(
                        onAdd = {
                            editingId = null
                            isEditing = true
                        }
                    )
                }
            }

            itemsIndexed(stats) { index, item ->
                TodoRow(
                    stats = item,
                    remainingMs = runningTimers[item.todo.id],
                    onToggle = { viewModel.toggleCurrentInterval(item.todo.id) },
                    onStartTimer = { viewModel.startTimer(item.todo.id) },
                    onCancelTimer = { viewModel.cancelTimer(item.todo.id) },
                    onEdit = {
                        editingId = item.todo.id
                        isEditing = true
                    },
                    onDelete = { viewModel.deleteTodo(item.todo) },
                    onOpen = { onOpenDetail(item.todo.id) }
                )
                if (index < stats.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                        thickness = 0.5.dp
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun TodoRow(
    stats: TodoStats,
    remainingMs: Long?,
    onToggle: () -> Unit,
    onStartTimer: () -> Unit,
    onCancelTimer: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = if (stats.isDoneCurrentInterval) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (stats.isDoneCurrentInterval)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stats.todo.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textDecoration = if (stats.isDoneCurrentInterval) TextDecoration.LineThrough else TextDecoration.None
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = scheduleSummary(stats.todo),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StreakDots(stats = stats)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = streakSummary(stats),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
            if (stats.todo.hasTimer && !stats.isDoneCurrentInterval) {
                Spacer(modifier = Modifier.height(8.dp))
                TaskTimerChip(
                    remainingMs = remainingMs,
                    onStart = onStartTimer,
                    onCancel = onCancelTimer
                )
            }
        }

        IconButton(
            onClick = onEdit,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
private fun StreakDots(stats: TodoStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        val visible = stats.history.take(7).reversed()
        if (visible.isEmpty()) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(50)
                    )
            )
        } else {
            visible.forEach { interval ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            color = if (interval.isDone)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(50)
                        )
                )
            }
        }
    }
}

private fun streakSummary(stats: TodoStats): String {
    val streak = stats.currentStreak
    return when {
        streak <= 0 -> "No streak yet"
        streak == 1 -> "1 in a row"
        else -> "$streak in a row"
    }
}

internal fun scheduleSummary(todo: Todo): String {
    val intervalLabel = if (todo.intervalValue == 1) {
        "Every ${todo.intervalUnit.label.lowercase()}"
    } else {
        "Every ${todo.intervalValue} ${todo.intervalUnit.label.lowercase()}s"
    }
    val timePart = todo.timeOfDay?.takeIf { it.isNotBlank() }?.let { " at $it" } ?: ""
    if (!todo.hasTimer) {
        return "$intervalLabel$timePart"
    }
    val durationLabel = "${todo.durationValue} ${todo.durationUnit.label.lowercase()}"
    return "$intervalLabel$timePart  ·  $durationLabel timer"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoForm(
    todo: Todo?,
    onSave: (String, String, Int, TodoIntervalUnit, String?, Int, TodoDurationUnit, Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember(todo?.id) { mutableStateOf(todo?.title ?: "") }
    var notes by remember(todo?.id) { mutableStateOf(todo?.notes ?: "") }
    var intervalValue by remember(todo?.id) { mutableStateOf((todo?.intervalValue ?: 1).toString()) }
    var intervalUnit by remember(todo?.id) { mutableStateOf(todo?.intervalUnit ?: TodoIntervalUnit.Day) }
    var timeOfDay by remember(todo?.id) { mutableStateOf(todo?.timeOfDay ?: "") }
    var durationValue by remember(todo?.id) { mutableStateOf((todo?.durationValue ?: 30).toString()) }
    var durationUnit by remember(todo?.id) { mutableStateOf(todo?.durationUnit ?: TodoDurationUnit.Minute) }
    var hasTimer by remember(todo?.id) { mutableStateOf(todo?.hasTimer ?: false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val canSave = title.isNotBlank() &&
        (intervalValue.toIntOrNull() ?: 0) > 0 &&
        (!hasTimer || (durationValue.toIntOrNull() ?: 0) > 0)
    val supportsTime = intervalUnit == TodoIntervalUnit.Day || intervalUnit == TodoIntervalUnit.Week

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text(
            text = if (todo == null) "New task" else "Edit task",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(14.dp))

        FlatTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = "Task title",
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        FlatTextField(
            value = notes,
            onValueChange = { notes = it },
            placeholder = "Notes",
            singleLine = false,
            minLines = 2
        )

        Spacer(modifier = Modifier.height(18.dp))

        PropertyLabel(text = "Repeat")
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Every",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            CompactNumberField(
                value = intervalValue,
                onValueChange = { intervalValue = it.filter(Char::isDigit) }
            )
            Spacer(modifier = Modifier.width(10.dp))
            UnitSelector(
                options = TodoIntervalUnit.entries.toList(),
                selected = intervalUnit,
                label = { it.label },
                onSelect = { intervalUnit = it }
            )
        }

        if (supportsTime) {
            Spacer(modifier = Modifier.height(16.dp))
            PropertyLabel(text = "Time of day")
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (timeOfDay.isBlank()) "Not set" else timeOfDay,
                    fontSize = 14.sp,
                    fontWeight = if (timeOfDay.isBlank()) FontWeight.Normal else FontWeight.Medium,
                    color = if (timeOfDay.isBlank())
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(12.dp))
                LinkText(
                    text = if (timeOfDay.isBlank()) "Set time" else "Change",
                    onClick = { showTimePicker = true }
                )
                if (timeOfDay.isNotBlank()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    LinkText(
                        text = "Clear",
                        onClick = { timeOfDay = "" }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Timer,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Timer",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Count down while you do this task",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Switch(
                checked = hasTimer,
                onCheckedChange = { hasTimer = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                    checkedTrackColor = MaterialTheme.colorScheme.onSurface,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    uncheckedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
            )
        }

        if (hasTimer) {
            Spacer(modifier = Modifier.height(14.dp))
            PropertyLabel(text = "Timer length")
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompactNumberField(
                    value = durationValue,
                    onValueChange = { durationValue = it.filter(Char::isDigit) }
                )
                Spacer(modifier = Modifier.width(10.dp))
                UnitSelector(
                    options = TodoDurationUnit.entries.toList(),
                    selected = durationUnit,
                    label = { it.label },
                    onSelect = { durationUnit = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            }
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(
                onClick = {
                    onSave(
                        title,
                        notes,
                        intervalValue.toIntOrNull() ?: 0,
                        intervalUnit,
                        timeOfDay.takeIf { it.isNotBlank() },
                        durationValue.toIntOrNull() ?: 0,
                        durationUnit,
                        hasTimer
                    )
                },
                enabled = canSave
            ) { 
                Text(
                    text = "Save",
                    fontWeight = FontWeight.SemiBold,
                    color = if (canSave) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }

    if (showTimePicker) {
        val initialHour = timeOfDay.substringBefore(':', "9").toIntOrNull() ?: 9
        val initialMinute = timeOfDay.substringAfter(':', "0").toIntOrNull() ?: 0
        val pickerState = rememberTimePickerState(
            initialHour = initialHour.coerceIn(0, 23),
            initialMinute = initialMinute.coerceIn(0, 59),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val hh = pickerState.hour.toString().padStart(2, '0')
                    val mm = pickerState.minute.toString().padStart(2, '0')
                    timeOfDay = "$hh:$mm"
                    showTimePicker = false
                }) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = pickerState) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlatTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                fontSize = 15.sp
            )
        },
        singleLine = singleLine,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactNumberField(
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(72.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
        )
    )
}

@Composable
private fun <T> UnitSelector(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Text(
                text = label(option),
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textDecoration = if (isSelected) TextDecoration.Underline else TextDecoration.None,
                modifier = Modifier
                    .clickable { onSelect(option) }
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun PropertyLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    )
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun TodoRowSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        SkeletonBox(
            modifier = Modifier.size(26.dp),
            shape = RoundedCornerShape(50)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            SkeletonLine(width = 150.dp, height = 15.dp)
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonLine(width = 110.dp, height = 12.dp)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(7) {
                    SkeletonBox(
                        modifier = Modifier.size(6.dp),
                        shape = RoundedCornerShape(50)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Nothing here yet",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Add a routine to start tracking your streak.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        )
        Spacer(modifier = Modifier.height(14.dp))
        LinkText(text = "New task", onClick = onAdd)
    }
}
