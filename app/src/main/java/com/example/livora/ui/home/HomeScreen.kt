package com.example.livora.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.ir.AcBrands
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.BulbScene
import com.example.livora.data.model.BulbState
import com.example.livora.ui.ac.AcViewModel
import com.example.livora.ui.bulb.BulbViewModel
import com.example.livora.ui.components.AnimatedNumber
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.StepButton
import com.example.livora.ui.components.Toaster
import com.example.livora.ui.components.TopBar
import java.util.Calendar

@Composable
fun HomeScreen(
    acViewModel: AcViewModel,
    bulbViewModel: BulbViewModel,
    onNavigateToAc: () -> Unit,
    onNavigateToBulb: () -> Unit
) {
    val acState by acViewModel.acState.collectAsState()
    val acRemote by acViewModel.remote.collectAsState()
    val acCapabilities by acViewModel.capabilities.collectAsState()
    val bulbState by bulbViewModel.bulbState.collectAsState()
    val connectedBulb by bulbViewModel.connectedBulb.collectAsState()

    val bulbIsOn = bulbState.isPoweredOn && connectedBulb != null
    val devicesOn = listOf(acState.isPoweredOn, bulbIsOn).count { it }
    val brand = AcBrands.find(acRemote.brandId)
    val model = brand.models[acRemote.modelIndex.coerceIn(0, brand.models.lastIndex)]

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopBar(
                title = greeting(),
                subtitle = when (devicesOn) {
                    0 -> "All devices are off"
                    1 -> "1 of 2 devices on"
                    else -> "Both devices are on"
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

            AcCard(
                state = acState,
                subtitle = "${brand.name} · ${model.label}",
                minTemp = acCapabilities.minTemp,
                maxTemp = acCapabilities.maxTemp,
                hasIr = acViewModel.isIrAvailable,
                onTogglePower = acViewModel::togglePower,
                onLower = acViewModel::decreaseTemperature,
                onRaise = acViewModel::increaseTemperature,
                onOpen = onNavigateToAc
            )

            Spacer(modifier = Modifier.height(Design.sectionSpacing))

            BulbCard(
                state = bulbState,
                isConnected = connectedBulb != null,
                subtitle = connectedBulb?.let { "WiZ · ${it.ip}" } ?: "WiZ smart bulb",
                onTogglePower = bulbViewModel::togglePower,
                onLower = bulbViewModel::decreaseBrightness,
                onRaise = bulbViewModel::increaseBrightness,
                onOpen = onNavigateToBulb
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Scenes",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SceneButton(
                    label = "Normal",
                    detail = "AC 20°, bulb on",
                    icon = Icons.Default.WbSunny,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        acViewModel.applyScene(20, AcMode.COOL)
                        bulbViewModel.powerOn()
                        bulbViewModel.setBrightness(100)
                        bulbViewModel.setScene(BulbScene.COOL_WHITE)
                        Toaster.success("Normal scene on")
                    }
                )
                SceneButton(
                    label = "Sleep",
                    detail = "AC 20°, bulb off",
                    icon = Icons.Default.Bedtime,
                    iconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        acViewModel.applyScene(20, AcMode.COOL)
                        bulbViewModel.powerOff()
                        Toaster.success("Sleep scene on")
                    }
                )
                SceneButton(
                    label = "Out",
                    detail = "Everything off",
                    icon = Icons.AutoMirrored.Filled.Logout,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        acViewModel.powerOff()
                        bulbViewModel.powerOff()
                        Toaster.success("Out scene on")
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AcCard(
    state: AcState,
    subtitle: String,
    minTemp: Int,
    maxTemp: Int,
    hasIr: Boolean,
    onTogglePower: () -> Unit,
    onLower: () -> Unit,
    onRaise: () -> Unit,
    onOpen: () -> Unit
) {
    val summary = when {
        !hasIr -> "No infrared blaster on this phone"
        !state.isPoweredOn -> "Off"
        else -> "${state.mode.label()} · Fan ${state.fanSpeed.name.lowercase()}"
    }
    DeviceCard(
        name = "Air conditioner",
        subtitle = subtitle,
        isOn = state.isPoweredOn,
        powerLabel = "air conditioner",
        tone = if (state.isPoweredOn) CardTone.Cool else CardTone.Neutral,
        onTogglePower = onTogglePower,
        onOpen = onOpen
    ) { colors ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AnimatedNumber(value = state.temperature) { temperature ->
                    Text(
                        text = "$temperature°",
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.content.copy(alpha = if (state.isPoweredOn) 1f else 0.4f)
                    )
                }
                Text(
                    text = summary,
                    fontSize = 13.sp,
                    color = colors.content.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StepButton(
                icon = Icons.Default.Remove,
                description = "Lower temperature",
                enabled = state.isPoweredOn && state.temperature > minTemp,
                onClick = onLower,
                contentColor = colors.content
            )
            Spacer(modifier = Modifier.width(12.dp))
            StepButton(
                icon = Icons.Default.Add,
                description = "Raise temperature",
                enabled = state.isPoweredOn && state.temperature < maxTemp,
                onClick = onRaise,
                contentColor = colors.content
            )
        }
    }
}

@Composable
private fun BulbCard(
    state: BulbState,
    isConnected: Boolean,
    subtitle: String,
    onTogglePower: () -> Unit,
    onLower: () -> Unit,
    onRaise: () -> Unit,
    onOpen: () -> Unit
) {
    val isOn = isConnected && state.isPoweredOn
    DeviceCard(
        name = "Smart bulb",
        subtitle = subtitle,
        isOn = isOn,
        powerLabel = "smart bulb",
        powerEnabled = isConnected,
        tone = if (isOn) CardTone.Warm else CardTone.Neutral,
        onTogglePower = onTogglePower,
        onOpen = onOpen
    ) { colors ->
        if (!isConnected) {
            Column {
                Text(
                    text = "Not connected",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.content.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Join the same Wi-Fi as the bulb, then scan for it.",
                    fontSize = 13.sp,
                    color = colors.content.copy(alpha = 0.75f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier.heightIn(min = 48.dp),
                    border = BorderStroke(1.dp, colors.accent),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent)
                ) {
                    Text("Find bulb")
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AnimatedNumber(value = state.brightness) { brightness ->
                        Text(
                            text = "$brightness%",
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.content.copy(alpha = if (isOn) 1f else 0.4f)
                        )
                    }
                    Text(
                        text = if (isOn) "Brightness · ${state.colorTemp}K" else "Off",
                        fontSize = 13.sp,
                        color = colors.content.copy(alpha = 0.75f)
                    )
                }
                StepButton(
                    icon = Icons.Default.Remove,
                    description = "Dimmer",
                    enabled = isOn && state.brightness > BulbState.MIN_BRIGHTNESS,
                    onClick = onLower,
                    contentColor = colors.content
                )
                Spacer(modifier = Modifier.width(12.dp))
                StepButton(
                    icon = Icons.Default.Add,
                    description = "Brighter",
                    enabled = isOn && state.brightness < BulbState.MAX_BRIGHTNESS,
                    onClick = onRaise,
                    contentColor = colors.content
                )
            }
        }
    }
}

private enum class CardTone { Neutral, Cool, Warm }

private class ToneColors(
    val container: Color,
    val content: Color,
    val accent: Color,
    val onAccent: Color
)

@Composable
private fun toneColors(tone: CardTone): ToneColors {
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        CardTone.Neutral -> ToneColors(scheme.surfaceContainerLow, scheme.onSurface, scheme.primary, scheme.onPrimary)
        CardTone.Cool -> ToneColors(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary, scheme.onPrimary)
        CardTone.Warm -> ToneColors(scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.secondary, scheme.onSecondary)
    }
}

@Composable
private fun DeviceCard(
    name: String,
    subtitle: String,
    isOn: Boolean,
    powerLabel: String,
    tone: CardTone,
    onTogglePower: () -> Unit,
    onOpen: () -> Unit,
    powerEnabled: Boolean = true,
    content: @Composable (ToneColors) -> Unit
) {
    val colors = toneColors(tone)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen),
        shape = Design.cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.container),
        elevation = CardDefaults.cardElevation(defaultElevation = Design.cardElevation)
    ) {
        Column(modifier = Modifier.padding(Design.cardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.content
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = colors.content.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = isOn,
                    enabled = powerEnabled,
                    onCheckedChange = { onTogglePower() },
                    modifier = Modifier.semantics { contentDescription = "Power $powerLabel" },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = colors.accent,
                        checkedThumbColor = colors.onAccent,
                        checkedBorderColor = colors.accent,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            content(colors)
        }
    }
}

@Composable
private fun SceneButton(
    label: String,
    detail: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 88.dp),
        shape = Design.cardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconTint
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = detail,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun AcMode.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }

private fun greeting(): String {
    return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Good night"
    }
}
