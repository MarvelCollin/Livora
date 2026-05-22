package com.example.livora.ui.ac

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.Section
import com.example.livora.ui.components.SelectChip
import com.example.livora.ui.components.TopBar
import com.example.livora.ui.components.VoiceListeningOverlay
import com.example.livora.util.VoiceRecognitionManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed
import com.example.livora.data.model.SwingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcControllerScreen(
    viewModel: AcViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.acState.collectAsState()
    val context = LocalContext.current
    val voiceManager = remember { VoiceRecognitionManager(context) }
    var isListening by remember { mutableStateOf(false) }
    var partialText by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose { voiceManager.destroy() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceManager.startListening(
                onResult = { text -> viewModel.processVoiceCommand(text) },
                onPartialResult = { partial -> partialText = partial },
                onListeningStarted = { isListening = true },
                onListeningEnded = { isListening = false; partialText = "" }
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopBar(
                title = "Air conditioner",
                subtitle = "LG Smart Inverter",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
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
                            } else {
                                voiceManager.startListening(
                                    onResult = { text -> viewModel.processVoiceCommand(text) },
                                    onPartialResult = { partial -> partialText = partial },
                                    onListeningStarted = { isListening = true },
                                    onListeningEnded = { isListening = false; partialText = "" }
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
                .padding(horizontal = Design.screenHorizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            PowerAndTemperatureSection(
                state = state,
                onTogglePower = viewModel::togglePower,
                onIncrease = viewModel::increaseTemperature,
                onDecrease = viewModel::decreaseTemperature
            )

            Spacer(modifier = Modifier.height(Design.sectionSpacing))

            AcModeSection(
                currentMode = state.mode,
                isPoweredOn = state.isPoweredOn,
                onModeSelected = viewModel::setMode
            )

            Spacer(modifier = Modifier.height(Design.sectionSpacing))

            FanSpeedSection(
                currentSpeed = state.fanSpeed,
                isPoweredOn = state.isPoweredOn,
                onSpeedSelected = viewModel::setFanSpeed
            )

            Spacer(modifier = Modifier.height(Design.sectionSpacing))

            SwingSection(
                currentSwing = state.swingMode,
                isPoweredOn = state.isPoweredOn,
                onSwingSelected = viewModel::setSwingMode
            )

            Spacer(modifier = Modifier.height(Design.sectionSpacing))

            TimerSection(
                timerHours = state.timerHours,
                isPoweredOn = state.isPoweredOn,
                onIncrease = viewModel::increaseTimer,
                onDecrease = viewModel::decreaseTimer
            )

            Spacer(modifier = Modifier.height(Design.sectionSpacing))

            QuickTogglesSection(
                state = state,
                onToggleSleep = viewModel::toggleSleepMode,
                onToggleEnergySaving = viewModel::toggleEnergySaving,
                onToggleDisplay = viewModel::toggleDisplay
            )

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
            }
        )
    }
    }
}

@Composable
private fun PowerAndTemperatureSection(
    state: AcState,
    onTogglePower: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceContainerLow,
        label = "powerBg"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = Design.cardShape,
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = Design.cardElevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(
                        if (state.isPoweredOn)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                    .clickable(onClick = onTogglePower),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = if (state.isPoweredOn)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxSize(0.45f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (state.isPoweredOn) "ON" else "OFF",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.isPoweredOn)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.22f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(enabled = state.isPoweredOn, onClick = onDecrease),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(0.5f),
                        tint = if (state.isPoweredOn)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${state.temperature}°",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (state.isPoweredOn) 1f else 0.35f)
                    )
                    Text(
                        text = "Celsius",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (state.isPoweredOn) 0.5f else 0.3f)
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.28f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(enabled = state.isPoweredOn, onClick = onIncrease),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(0.5f),
                        tint = if (state.isPoweredOn)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "${AcState.MIN_TEMP}°C — ${AcState.MAX_TEMP}°C",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun AcModeSection(
    currentMode: AcMode,
    isPoweredOn: Boolean,
    onModeSelected: (AcMode) -> Unit
) {
    Section(title = "Mode") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SelectChip(
                label = "Cool",
                icon = Icons.Default.AcUnit,
                selected = currentMode == AcMode.COOL,
                enabled = isPoweredOn,
                onClick = { onModeSelected(AcMode.COOL) }
            )
            SelectChip(
                label = "Heat",
                icon = Icons.Default.Thermostat,
                selected = currentMode == AcMode.HEAT,
                enabled = isPoweredOn,
                onClick = { onModeSelected(AcMode.HEAT) }
            )
            SelectChip(
                label = "Dry",
                icon = Icons.Default.WaterDrop,
                selected = currentMode == AcMode.DRY,
                enabled = isPoweredOn,
                onClick = { onModeSelected(AcMode.DRY) }
            )
            SelectChip(
                label = "Fan",
                icon = Icons.Default.Air,
                selected = currentMode == AcMode.FAN,
                enabled = isPoweredOn,
                onClick = { onModeSelected(AcMode.FAN) }
            )
            SelectChip(
                label = "Auto",
                icon = Icons.Default.BrightnessHigh,
                selected = currentMode == AcMode.AUTO,
                enabled = isPoweredOn,
                onClick = { onModeSelected(AcMode.AUTO) }
            )
        }
    }
}

@Composable
private fun FanSpeedSection(
    currentSpeed: FanSpeed,
    isPoweredOn: Boolean,
    onSpeedSelected: (FanSpeed) -> Unit
) {
    Section(title = "Fan speed") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FanSpeed.entries.forEach { speed ->
                SelectChip(
                    label = speed.name.lowercase().replaceFirstChar { it.uppercase() },
                    icon = Icons.Default.Air,
                    selected = currentSpeed == speed,
                    enabled = isPoweredOn,
                    onClick = { onSpeedSelected(speed) }
                )
            }
        }
    }
}

@Composable
private fun SwingSection(
    currentSwing: SwingMode,
    isPoweredOn: Boolean,
    onSwingSelected: (SwingMode) -> Unit
) {
    Section(title = "Swing") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SwingMode.entries.forEach { swing ->
                SelectChip(
                    label = when (swing) {
                        SwingMode.OFF -> "Off"
                        SwingMode.VERTICAL -> "Vertical"
                        SwingMode.HORIZONTAL -> "Horizontal"
                        SwingMode.BOTH -> "Both"
                    },
                    icon = Icons.Default.SwapVert,
                    selected = currentSwing == swing,
                    enabled = isPoweredOn,
                    onClick = { onSwingSelected(swing) }
                )
            }
        }
    }
}

@Composable
private fun TimerSection(
    timerHours: Int,
    isPoweredOn: Boolean,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit
) {
    Section(title = "Timer") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(enabled = isPoweredOn && timerHours > 0, onClick = onDecrease),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (isPoweredOn && timerHours > 0)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (timerHours > 0) 0.85f else 0.35f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (timerHours == 0) "Off" else "${timerHours}h",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (timerHours > 0) 1f else 0.35f)
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(enabled = isPoweredOn && timerHours < 24, onClick = onIncrease),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (isPoweredOn && timerHours < 24)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun QuickTogglesSection(
    state: AcState,
    onToggleSleep: () -> Unit,
    onToggleEnergySaving: () -> Unit,
    onToggleDisplay: () -> Unit
) {
    Section(title = "Quick settings") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SelectChip(
                label = "Sleep",
                icon = Icons.Default.Bedtime,
                selected = state.isSleepMode,
                enabled = state.isPoweredOn,
                onClick = onToggleSleep
            )
            SelectChip(
                label = "Eco",
                icon = Icons.Default.EnergySavingsLeaf,
                selected = state.isEnergySaving,
                enabled = state.isPoweredOn,
                onClick = onToggleEnergySaving
            )
            SelectChip(
                label = "Display",
                icon = Icons.Default.Brightness6,
                selected = state.isDisplayOn,
                enabled = state.isPoweredOn,
                onClick = onToggleDisplay
            )
        }
    }
}
