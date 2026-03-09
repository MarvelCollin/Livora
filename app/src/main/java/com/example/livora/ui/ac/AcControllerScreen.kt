package com.example.livora.ui.ac

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Air Conditioner",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "LG Smart Inverter",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            PowerAndTemperatureSection(
                state = state,
                onTogglePower = viewModel::togglePower,
                onIncrease = viewModel::increaseTemperature,
                onDecrease = viewModel::decreaseTemperature
            )

            Spacer(modifier = Modifier.height(20.dp))

            AcModeSection(
                currentMode = state.mode,
                isPoweredOn = state.isPoweredOn,
                onModeSelected = viewModel::setMode
            )

            Spacer(modifier = Modifier.height(16.dp))

            FanSpeedSection(
                currentSpeed = state.fanSpeed,
                isPoweredOn = state.isPoweredOn,
                onSpeedSelected = viewModel::setFanSpeed
            )

            Spacer(modifier = Modifier.height(16.dp))

            SwingSection(
                currentSwing = state.swingMode,
                isPoweredOn = state.isPoweredOn,
                onSwingSelected = viewModel::setSwingMode
            )

            Spacer(modifier = Modifier.height(16.dp))

            TimerSection(
                timerHours = state.timerHours,
                isPoweredOn = state.isPoweredOn,
                onIncrease = viewModel::increaseTimer,
                onDecrease = viewModel::decreaseTimer
            )

            Spacer(modifier = Modifier.height(16.dp))

            QuickTogglesSection(
                state = state,
                onToggleSleep = viewModel::toggleSleepMode,
                onToggleEnergySaving = viewModel::toggleEnergySaving,
                onToggleDisplay = viewModel::toggleDisplay
            )

            Spacer(modifier = Modifier.height(24.dp))
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
        targetValue = if (state.isPoweredOn)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant,
        label = "powerBg"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = onTogglePower,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (state.isPoweredOn)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = if (state.isPoweredOn)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (state.isPoweredOn) "ON" else "OFF",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (state.isPoweredOn)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = onDecrease,
                    enabled = state.isPoweredOn,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${state.temperature}°",
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.isPoweredOn)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "Celsius",
                        fontSize = 13.sp,
                        color = if (state.isPoweredOn)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

                IconButton(
                    onClick = onIncrease,
                    enabled = state.isPoweredOn,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
    SectionCard(title = "Mode") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ModeChip(
                label = "Cool",
                icon = Icons.Default.AcUnit,
                isSelected = currentMode == AcMode.COOL,
                isEnabled = isPoweredOn,
                onClick = { onModeSelected(AcMode.COOL) }
            )
            ModeChip(
                label = "Heat",
                icon = Icons.Default.Thermostat,
                isSelected = currentMode == AcMode.HEAT,
                isEnabled = isPoweredOn,
                onClick = { onModeSelected(AcMode.HEAT) }
            )
            ModeChip(
                label = "Dry",
                icon = Icons.Default.WaterDrop,
                isSelected = currentMode == AcMode.DRY,
                isEnabled = isPoweredOn,
                onClick = { onModeSelected(AcMode.DRY) }
            )
            ModeChip(
                label = "Fan",
                icon = Icons.Default.Air,
                isSelected = currentMode == AcMode.FAN,
                isEnabled = isPoweredOn,
                onClick = { onModeSelected(AcMode.FAN) }
            )
            ModeChip(
                label = "Auto",
                icon = Icons.Default.BrightnessHigh,
                isSelected = currentMode == AcMode.AUTO,
                isEnabled = isPoweredOn,
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
    SectionCard(title = "Fan Speed") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FanSpeed.entries.forEach { speed ->
                ModeChip(
                    label = speed.name.lowercase().replaceFirstChar { it.uppercase() },
                    icon = Icons.Default.Air,
                    isSelected = currentSpeed == speed,
                    isEnabled = isPoweredOn,
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
    SectionCard(title = "Swing") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SwingMode.entries.forEach { swing ->
                ModeChip(
                    label = when (swing) {
                        SwingMode.OFF -> "Off"
                        SwingMode.VERTICAL -> "Vertical"
                        SwingMode.HORIZONTAL -> "Horizontal"
                        SwingMode.BOTH -> "Both"
                    },
                    icon = Icons.Default.SwapVert,
                    isSelected = currentSwing == swing,
                    isEnabled = isPoweredOn,
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
    SectionCard(title = "Timer") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDecrease,
                enabled = isPoweredOn && timerHours > 0,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (timerHours == 0) "Off" else "${timerHours}h",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (timerHours > 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            IconButton(
                onClick = onIncrease,
                enabled = isPoweredOn && timerHours < 24,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
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
    SectionCard(title = "Quick Settings") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ToggleChip(
                label = "Sleep",
                icon = Icons.Default.Bedtime,
                isActive = state.isSleepMode,
                isEnabled = state.isPoweredOn,
                onClick = onToggleSleep
            )
            ToggleChip(
                label = "Eco",
                icon = Icons.Default.EnergySavingsLeaf,
                isActive = state.isEnergySaving,
                isEnabled = state.isPoweredOn,
                onClick = onToggleEnergySaving
            )
            ToggleChip(
                label = "Display",
                icon = Icons.Default.Brightness6,
                isActive = state.isDisplayOn,
                isEnabled = state.isPoweredOn,
                onClick = onToggleDisplay
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected && isEnabled -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "chipBg"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected && isEnabled -> MaterialTheme.colorScheme.onPrimary
            isEnabled -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        },
        label = "chipContent"
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = isEnabled, onClick = onClick)
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = contentColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}

@Composable
private fun ToggleChip(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isActive && isEnabled -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "toggleBg"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isActive && isEnabled -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        },
        label = "toggleBorder"
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = isEnabled, onClick = onClick)
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (isActive && isEnabled)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isActive && isEnabled)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}
