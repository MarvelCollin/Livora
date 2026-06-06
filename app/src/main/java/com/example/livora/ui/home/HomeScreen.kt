package com.example.livora.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.BulbScene
import com.example.livora.data.model.TodoStats
import com.example.livora.ui.ac.AcViewModel
import com.example.livora.ui.bulb.BulbViewModel
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.TopBar
import com.example.livora.ui.components.DeviceCard
import com.example.livora.ui.components.SkeletonBox
import com.example.livora.ui.components.SkeletonLine
import com.example.livora.ui.components.TaskTimerChip
import com.example.livora.ui.components.VoiceListeningOverlay
import com.example.livora.ui.todo.TodoViewModel
import com.example.livora.ui.todo.scheduleSummary
import com.example.livora.util.VoiceRecognitionManager
import com.example.livora.util.WakeWordListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    acViewModel: AcViewModel,
    bulbViewModel: BulbViewModel,
    todoViewModel: TodoViewModel,
    onNavigateToAc: () -> Unit,
    onNavigateToBulb: () -> Unit,
    onOpenTodoDetail: (String) -> Unit
) {
    val acState by acViewModel.acState.collectAsState()
    val bulbState by bulbViewModel.bulbState.collectAsState()
    val connectedBulb by bulbViewModel.connectedBulb.collectAsState()
    val stats by todoViewModel.stats.collectAsState()
    val runningTimers by todoViewModel.runningTimers.collectAsState()
    val todoLoading by todoViewModel.isLoading.collectAsState()

    val context = LocalContext.current
    val voiceManager = remember { VoiceRecognitionManager(context) }
    val wakeWordListener = remember { WakeWordListener(context) }
    var isListening by remember { mutableStateOf(false) }
    var partialText by remember { mutableStateOf("") }
    var isWakeWordActive by remember { mutableStateOf(false) }

    val activateNormalMode = {
        acViewModel.setTemperature(20)
        acViewModel.setMode(AcMode.COOL)
        acViewModel.powerOn()
        bulbViewModel.powerOn()
        bulbViewModel.setBrightness(100)
        bulbViewModel.setScene(BulbScene.COOL_WHITE)
    }

    val activateSleepMode = {
        acViewModel.setTemperature(20)
        acViewModel.setMode(AcMode.COOL)
        acViewModel.powerOn()
        bulbViewModel.powerOff()
    }

    val activateOutMode = {
        acViewModel.powerOff()
        bulbViewModel.powerOff()
    }

    val processQuickVoiceCommand: (String) -> Unit = { text ->
        val lower = text.lowercase()
        when {
            lower.contains("normal") -> activateNormalMode()
            lower.contains("sleep") -> activateSleepMode()
            lower.contains("out") || lower.contains("leave") -> activateOutMode()
        }
    }

    val startVoiceListening = {
        voiceManager.startListening(
            onResult = { text ->
                processQuickVoiceCommand(text)
                acViewModel.processVoiceCommand(text)
                bulbViewModel.processVoiceCommand(text)
            },
            onPartialResult = { partial -> partialText = partial },
            onListeningStarted = { isListening = true },
            onListeningEnded = {
                isListening = false
                partialText = ""
            }
        )
    }

    fun restartWakeWordLoop() {
        if (!isWakeWordActive) return
        wakeWordListener.start {
            wakeWordListener.stop()
            voiceManager.startListening(
                onResult = { text ->
                    processQuickVoiceCommand(text)
                    acViewModel.processVoiceCommand(text)
                    bulbViewModel.processVoiceCommand(text)
                },
                onPartialResult = { partial -> partialText = partial },
                onListeningStarted = { isListening = true },
                onListeningEnded = {
                    isListening = false
                    partialText = ""
                    restartWakeWordLoop()
                }
            )
        }
    }

    DisposableEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            isWakeWordActive = true
            restartWakeWordLoop()
        }
        onDispose {
            wakeWordListener.destroy()
            voiceManager.destroy()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceListening()
            isWakeWordActive = true
        }
    }

    val doneCount = stats.count { it.isDoneCurrentInterval }
    val totalCount = stats.size
    val pending = stats.filterNot { it.isDoneCurrentInterval }
    val activeStreaks = stats.count { it.currentStreak > 0 }
    val bestStreak = stats.maxOfOrNull { it.currentStreak } ?: 0

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopBar(
                    title = greeting(),
                    subtitle = today(),
                    actions = {
                        IconButton(onClick = {
                            if (ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                if (isListening) {
                                    voiceManager.stopListening()
                                    isListening = false
                                    partialText = ""
                                    restartWakeWordLoop()
                                } else {
                                    wakeWordListener.stop()
                                    voiceManager.startListening(
                                        onResult = { text ->
                                            processQuickVoiceCommand(text)
                                            acViewModel.processVoiceCommand(text)
                                            bulbViewModel.processVoiceCommand(text)
                                        },
                                        onPartialResult = { partial -> partialText = partial },
                                        onListeningStarted = { isListening = true },
                                        onListeningEnded = {
                                            isListening = false
                                            partialText = ""
                                            restartWakeWordLoop()
                                        }
                                    )
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Design.screenHorizontalPadding)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                if (todoLoading && stats.isEmpty()) {
                    TodayProgressSkeleton()
                    Spacer(modifier = Modifier.height(20.dp))
                    SectionLabel(text = "Up next")
                    Spacer(modifier = Modifier.height(10.dp))
                    repeat(3) {
                        UpNextSkeleton()
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else {
                    TodayProgressCard(
                        doneCount = doneCount,
                        totalCount = totalCount,
                        activeStreaks = activeStreaks,
                        bestStreak = bestStreak
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    SectionLabel(text = "Up next")
                    Spacer(modifier = Modifier.height(10.dp))
                    if (pending.isEmpty()) {
                        AllClearCard(hasTasks = totalCount > 0)
                    } else {
                        pending.take(4).forEachIndexed { index, item ->
                            UpNextRow(
                                stats = item,
                                remainingMs = runningTimers[item.todo.id],
                                onToggle = { todoViewModel.toggleCurrentInterval(item.todo.id) },
                                onStartTimer = { todoViewModel.startTimer(item.todo.id) },
                                onCancelTimer = { todoViewModel.cancelTimer(item.todo.id) },
                                onOpen = { onOpenTodoDetail(item.todo.id) }
                            )
                            if (index < pending.take(4).lastIndex) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                SectionLabel(text = "Quick modes")
                Spacer(modifier = Modifier.height(10.dp))
                QuickModeCard(
                    title = "Normal",
                    description = "AC 20°C cool · Bulb 100% cool white",
                    icon = Icons.Default.WbSunny,
                    onClick = { activateNormalMode() }
                )
                Spacer(modifier = Modifier.height(Design.sectionSpacing))
                QuickModeCard(
                    title = "Sleep",
                    description = "AC 20°C cool · Bulb off",
                    icon = Icons.Default.Bedtime,
                    onClick = { activateSleepMode() }
                )
                Spacer(modifier = Modifier.height(Design.sectionSpacing))
                QuickModeCard(
                    title = "Out",
                    description = "AC off · Bulb off",
                    icon = Icons.AutoMirrored.Filled.Logout,
                    onClick = { activateOutMode() }
                )

                Spacer(modifier = Modifier.height(24.dp))

                SectionLabel(text = "Devices")
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        DeviceCard(
                            name = "Air conditioner",
                            brand = "LG",
                            isOn = acState.isPoweredOn,
                            statusText = if (acState.isPoweredOn) "${acState.temperature}°C · ${acState.mode.name.lowercase().replaceFirstChar { it.uppercase() }}" else "Off",
                            icon = { modifier ->
                                Icon(
                                    imageVector = Icons.Default.AcUnit,
                                    contentDescription = null,
                                    modifier = modifier,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (acState.isPoweredOn) 0.85f else 0.35f)
                                )
                            },
                            onTogglePower = { acViewModel.togglePower() },
                            onClick = onNavigateToAc
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        val bulbIsOn = bulbState.isPoweredOn && connectedBulb != null
                        DeviceCard(
                            name = "Smart bulb",
                            brand = "WiZ",
                            isOn = bulbIsOn,
                            statusText = when {
                                bulbIsOn -> "${bulbState.brightness}% · ${bulbState.colorTemp}K"
                                connectedBulb != null -> "Off"
                                else -> "Not connected"
                            },
                            icon = { modifier ->
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    modifier = modifier,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (bulbIsOn) 0.85f else 0.35f)
                                )
                            },
                            onTogglePower = {
                                if (connectedBulb != null) {
                                    bulbViewModel.togglePower()
                                }
                            },
                            onClick = onNavigateToBulb
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        AnimatedVisibility(
            visible = isListening,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            VoiceListeningOverlay(
                partialText = partialText,
                onDismiss = {
                    voiceManager.stopListening()
                    isListening = false
                    partialText = ""
                    restartWakeWordLoop()
                }
            )
        }
    }
}

@Composable
private fun TodayProgressCard(
    doneCount: Int,
    totalCount: Int,
    activeStreaks: Int,
    bestStreak: Int
) {
    val ratio = if (totalCount > 0) doneCount.toFloat() / totalCount.toFloat() else 0f
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = Design.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Design.cardElevation)
    ) {
        Column(modifier = Modifier.padding(Design.cardPadding)) {
            Text(
                text = "Today's progress",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$doneCount",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = " / $totalCount",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (totalCount == 0) "No routines yet" else "${(ratio * 100).toInt()}% done",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            ) {
                if (ratio > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.onSurface)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                StatItem(value = "$activeStreaks", label = "Active streaks")
                Spacer(modifier = Modifier.width(28.dp))
                StatItem(value = "$bestStreak", label = "Longest streak")
                Spacer(modifier = Modifier.width(28.dp))
                StatItem(value = "${totalCount - doneCount}", label = "Remaining")
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        )
    }
}

@Composable
private fun UpNextRow(
    stats: TodoStats,
    remainingMs: Long?,
    onToggle: () -> Unit,
    onStartTimer: () -> Unit,
    onCancelTimer: () -> Unit,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = Design.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Design.cardElevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
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
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stats.todo.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = scheduleSummary(stats.todo),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
            if (stats.todo.hasTimer) {
                Spacer(modifier = Modifier.width(8.dp))
                TaskTimerChip(
                    remainingMs = remainingMs,
                    onStart = onStartTimer,
                    onCancel = onCancelTimer
                )
            }
        }
    }
}

@Composable
private fun AllClearCard(hasTasks: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = Design.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Design.cardElevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (hasTasks) "All caught up for now" else "Add a routine in Tasks to get started",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun QuickModeCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = Design.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Design.cardElevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Design.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun TodayProgressSkeleton() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = Design.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Design.cardElevation)
    ) {
        Column(modifier = Modifier.padding(Design.cardPadding)) {
            SkeletonLine(width = 110.dp, height = 12.dp)
            Spacer(modifier = Modifier.height(14.dp))
            SkeletonLine(width = 90.dp, height = 32.dp)
            Spacer(modifier = Modifier.height(14.dp))
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                shape = RoundedCornerShape(50)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                repeat(3) {
                    Column {
                        SkeletonLine(width = 28.dp, height = 18.dp)
                        Spacer(modifier = Modifier.height(4.dp))
                        SkeletonLine(width = 60.dp, height = 11.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun UpNextSkeleton() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = Design.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Design.cardElevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkeletonBox(
                modifier = Modifier.size(28.dp),
                shape = RoundedCornerShape(50)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                SkeletonLine(width = 140.dp, height = 15.dp)
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonLine(width = 180.dp, height = 12.dp)
            }
        }
    }
}

private fun greeting(): String {
    return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Good night"
    }
}

private fun today(): String {
    return SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())
}
