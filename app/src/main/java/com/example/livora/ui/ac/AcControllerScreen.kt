package com.example.livora.ui.ac

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SettingsRemote
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.ir.AcBrands
import com.example.livora.data.ir.AcCapabilities
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed
import com.example.livora.data.model.SwingMode
import com.example.livora.ui.components.AnimatedNumber
import com.example.livora.ui.components.ChoiceOption
import com.example.livora.ui.components.ChoiceRow
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.Section
import com.example.livora.ui.components.SelectChip
import com.example.livora.ui.components.StepButton
import com.example.livora.ui.components.TopBar
import kotlinx.coroutines.delay
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcControllerScreen(
    viewModel: AcViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.acState.collectAsState()
    val capabilities by viewModel.capabilities.collectAsState()
    val remote by viewModel.remote.collectAsState()
    val transmitFailed by viewModel.transmitFailed.collectAsState()
    var showRemoteSheet by remember { mutableStateOf(false) }

    val brand = AcBrands.find(remote.brandId)
    val model = brand.models[remote.modelIndex.coerceIn(0, brand.models.lastIndex)]

    Scaffold(
        topBar = {
            TopBar(
                title = "Air conditioner",
                subtitle = "${brand.name} · ${model.label}",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showRemoteSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.SettingsRemote,
                            contentDescription = "Change remote brand",
                            tint = MaterialTheme.colorScheme.onSurface
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

            if (!viewModel.isIrAvailable) {
                Notice(text = "This phone has no infrared blaster, so the AC cannot be controlled.")
                Spacer(modifier = Modifier.height(Design.sectionSpacing))
            } else if (transmitFailed) {
                Notice(text = "The last signal could not be sent. Try again.")
                Spacer(modifier = Modifier.height(Design.sectionSpacing))
            }

            PowerAndTemperatureSection(
                state = state,
                capabilities = capabilities,
                onTogglePower = viewModel::togglePower,
                onIncrease = viewModel::increaseTemperature,
                onDecrease = viewModel::decreaseTemperature
            )

            Spacer(modifier = Modifier.height(Design.sectionSpacing))

            AcModeSection(
                capabilities = capabilities,
                currentMode = state.mode,
                isPoweredOn = state.isPoweredOn,
                onModeSelected = viewModel::setMode
            )

            Spacer(modifier = Modifier.height(Design.sectionSpacing))

            FanSpeedSection(
                capabilities = capabilities,
                currentSpeed = state.fanSpeed,
                isPoweredOn = state.isPoweredOn,
                onSpeedSelected = viewModel::setFanSpeed
            )

            if (capabilities.swingModes.size > 1) {
                Spacer(modifier = Modifier.height(Design.sectionSpacing))

                SwingSection(
                    capabilities = capabilities,
                    currentSwing = state.swingMode,
                    isPoweredOn = state.isPoweredOn,
                    onSwingSelected = viewModel::setSwingMode
                )
            }

            Spacer(modifier = Modifier.height(Design.sectionSpacing))

            TimerSection(
                timerEndsAtMillis = state.timerEndsAtMillis,
                isPoweredOn = state.isPoweredOn,
                onHoursChange = viewModel::setTimerHours
            )

            if (capabilities.hasSleep || capabilities.hasEco || capabilities.hasDisplay) {
                Spacer(modifier = Modifier.height(Design.sectionSpacing))

                QuickTogglesSection(
                    state = state,
                    capabilities = capabilities,
                    onToggleSleep = viewModel::toggleSleepMode,
                    onToggleEnergySaving = viewModel::toggleEnergySaving,
                    onToggleDisplay = viewModel::toggleDisplay
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showRemoteSheet) {
        AcBrandSheet(
            currentBrandId = remote.brandId,
            currentModelIndex = remote.modelIndex,
            onDismiss = { showRemoteSheet = false },
            onSendTest = viewModel::sendTestSignal,
            onConfirm = viewModel::selectRemote
        )
    }
}

@Composable
private fun Notice(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = Design.cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = Design.cardElevation)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(Design.cardPadding)
        )
    }
}

@Composable
private fun PowerAndTemperatureSection(
    state: AcState,
    capabilities: AcCapabilities,
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
                    .clickable(role = Role.Switch, onClick = onTogglePower),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = if (state.isPoweredOn) "Turn off" else "Turn on",
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
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StepButton(
                    icon = Icons.Default.Remove,
                    description = "Lower temperature",
                    enabled = state.isPoweredOn && state.temperature > capabilities.minTemp,
                    onClick = onDecrease
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedNumber(value = state.temperature) { temperature ->
                        Text(
                            text = "$temperature°",
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (state.isPoweredOn) 1f else 0.35f)
                        )
                    }
                    Text(
                        text = "Celsius",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (state.isPoweredOn) 0.5f else 0.3f)
                    )
                }

                StepButton(
                    icon = Icons.Default.Add,
                    description = "Raise temperature",
                    enabled = state.isPoweredOn && state.temperature < capabilities.maxTemp,
                    onClick = onIncrease
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "${capabilities.minTemp} to ${capabilities.maxTemp}°C",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun AcModeSection(
    capabilities: AcCapabilities,
    currentMode: AcMode,
    isPoweredOn: Boolean,
    onModeSelected: (AcMode) -> Unit
) {
    Section(title = "Mode") {
        ChoiceRow(
            options = capabilities.modes.map { mode ->
                when (mode) {
                    AcMode.COOL -> ChoiceOption(mode, "Cool", Icons.Default.AcUnit)
                    AcMode.HEAT -> ChoiceOption(mode, "Heat", Icons.Default.Thermostat)
                    AcMode.DRY -> ChoiceOption(mode, "Dry", Icons.Default.WaterDrop)
                    AcMode.FAN -> ChoiceOption(mode, "Fan", Icons.Default.Air)
                    AcMode.AUTO -> ChoiceOption(mode, "Auto", Icons.Default.AutoMode)
                }
            },
            selected = currentMode,
            enabled = isPoweredOn,
            onSelect = onModeSelected,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FanSpeedSection(
    capabilities: AcCapabilities,
    currentSpeed: FanSpeed,
    isPoweredOn: Boolean,
    onSpeedSelected: (FanSpeed) -> Unit
) {
    Section(title = "Fan speed") {
        ChoiceRow(
            options = capabilities.fanSpeeds.map { speed ->
                ChoiceOption(speed, speed.name.lowercase().replaceFirstChar { it.uppercase() })
            },
            selected = currentSpeed,
            enabled = isPoweredOn,
            onSelect = onSpeedSelected,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SwingSection(
    capabilities: AcCapabilities,
    currentSwing: SwingMode,
    isPoweredOn: Boolean,
    onSwingSelected: (SwingMode) -> Unit
) {
    Section(title = "Swing") {
        ChoiceRow(
            options = capabilities.swingModes.map { swing ->
                ChoiceOption(
                    swing,
                    when (swing) {
                        SwingMode.OFF -> "Off"
                        SwingMode.VERTICAL -> if (capabilities.swingModes.size > 2) "Vertical" else "On"
                        SwingMode.HORIZONTAL -> "Horizontal"
                        SwingMode.BOTH -> "Both"
                    }
                )
            },
            selected = currentSwing,
            enabled = isPoweredOn,
            onSelect = onSwingSelected,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TimerSection(
    timerEndsAtMillis: Long,
    isPoweredOn: Boolean,
    onHoursChange: (Int) -> Unit
) {
    val hasTimer = timerEndsAtMillis > 0L
    val now by produceState(initialValue = System.currentTimeMillis(), key1 = hasTimer) {
        while (hasTimer) {
            value = System.currentTimeMillis()
            delay(TIMER_TICK_MILLIS)
        }
    }
    val remainingMillis = (timerEndsAtMillis - now).coerceAtLeast(0L)
    val remainingMinutes = ceil(remainingMillis / 60_000.0).toInt()
    val currentHours = ceil(remainingMillis / 3_600_000.0).toInt()

    Section(title = "Sleep timer") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepButton(
                icon = Icons.Default.Remove,
                description = "Shorter timer",
                enabled = isPoweredOn && hasTimer,
                onClick = { onHoursChange(currentHours - 1) }
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (hasTimer) 0.85f else 0.35f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasTimer) formatRemaining(remainingMinutes) else "Off",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (hasTimer) 1f else 0.35f)
                    )
                }
                Text(
                    text = if (hasTimer) "until the AC turns off" else "Tap plus to add hours",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            StepButton(
                icon = Icons.Default.Add,
                description = "Longer timer",
                enabled = isPoweredOn && currentHours < MAX_TIMER_HOURS,
                onClick = { onHoursChange(currentHours + 1) }
            )
        }
    }
}

private fun formatRemaining(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> "${minutes}m"
        minutes == 0 -> "${hours}h"
        else -> "${hours}h ${minutes}m"
    }
}

@Composable
private fun QuickTogglesSection(
    state: AcState,
    capabilities: AcCapabilities,
    onToggleSleep: () -> Unit,
    onToggleEnergySaving: () -> Unit,
    onToggleDisplay: () -> Unit
) {
    Section(title = "Quick settings") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (capabilities.hasSleep) {
                SelectChip(
                    label = "Sleep",
                    icon = Icons.Default.Bedtime,
                    selected = state.isSleepMode,
                    enabled = state.isPoweredOn,
                    onClick = onToggleSleep,
                    modifier = Modifier.weight(1f)
                )
            }
            if (capabilities.hasEco) {
                SelectChip(
                    label = "Eco",
                    icon = Icons.Default.EnergySavingsLeaf,
                    selected = state.isEnergySaving,
                    enabled = state.isPoweredOn,
                    onClick = onToggleEnergySaving,
                    modifier = Modifier.weight(1f)
                )
            }
            if (capabilities.hasDisplay) {
                SelectChip(
                    label = "Display",
                    icon = Icons.Default.Brightness6,
                    selected = state.isDisplayOn,
                    enabled = state.isPoweredOn,
                    onClick = onToggleDisplay,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private const val TIMER_TICK_MILLIS = 20_000L
private const val MAX_TIMER_HOURS = 12
