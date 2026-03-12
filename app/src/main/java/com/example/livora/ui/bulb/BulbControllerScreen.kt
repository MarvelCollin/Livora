package com.example.livora.ui.bulb

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.livora.data.model.WizBulb
import com.example.livora.data.model.WizBulbState
import com.example.livora.data.model.WizScene
import com.example.livora.ui.components.VoiceListeningOverlay
import com.example.livora.util.VoiceRecognitionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulbControllerScreen(
    viewModel: BulbViewModel,
    onBack: () -> Unit
) {
    val bulbState by viewModel.bulbState.collectAsState()
    val connectedBulb by viewModel.connectedBulb.collectAsState()
    val discoveredBulbs by viewModel.discoveredBulbs.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
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
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Smart Bulb",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (connectedBulb != null) "WiZ · ${connectedBulb!!.ip}" else "WiZ Downlight",
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
                    actions = {
                        if (connectedBulb != null) {
                            IconButton(onClick = { viewModel.refreshBulbState() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null
                                )
                            }
                        }
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { innerPadding ->
            if (connectedBulb == null) {
                DiscoveryContent(
                    discoveredBulbs = discoveredBulbs,
                    isScanning = isScanning,
                    onScan = { viewModel.scanForBulbs() },
                    onSelectBulb = { viewModel.connectToBulb(it) },
                    modifier = Modifier.padding(innerPadding)
                )
            } else {
                BulbControlContent(
                    state = bulbState,
                    onTogglePower = viewModel::togglePower,
                    onBrightnessChange = viewModel::setBrightness,
                    onColorTempChange = viewModel::setColorTemperature,
                    onSetRgb = viewModel::setRgbColor,
                    onSetScene = viewModel::setScene,
                    modifier = Modifier.padding(innerPadding)
                )
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
private fun DiscoveryContent(
    discoveredBulbs: List<WizBulb>,
    isScanning: Boolean,
    onScan: () -> Unit,
    onSelectBulb: (WizBulb) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Find WiZ Bulbs",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Make sure your WiZ bulb is connected to the same WiFi network as your phone",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isScanning, onClick = onScan),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Scanning...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Scan for Bulbs",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (discoveredBulbs.isNotEmpty()) {
            Text(
                text = "Found Devices",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            discoveredBulbs.forEach { bulb ->
                BulbDiscoveryCard(
                    bulb = bulb,
                    onClick = { onSelectBulb(bulb) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (!isScanning && discoveredBulbs.isEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "No bulbs found yet.\nTap Scan to search your network.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun BulbDiscoveryCard(
    bulb: WizBulb,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (bulb.moduleName.isNotEmpty()) bulb.moduleName else "WiZ Bulb",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${bulb.ip} · ${bulb.mac}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Text(
                text = "Connect",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun BulbControlContent(
    state: WizBulbState,
    onTogglePower: () -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onColorTempChange: (Int) -> Unit,
    onSetRgb: (Int, Int, Int) -> Unit,
    onSetScene: (WizScene) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        PowerSection(
            isPoweredOn = state.isPoweredOn,
            brightness = state.brightness,
            onTogglePower = onTogglePower
        )

        Spacer(modifier = Modifier.height(16.dp))

        BrightnessSection(
            brightness = state.brightness,
            isPoweredOn = state.isPoweredOn,
            onBrightnessChange = onBrightnessChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        ColorTemperatureSection(
            colorTemp = state.colorTemp,
            isPoweredOn = state.isPoweredOn,
            isActive = !state.useRgb && state.sceneId == 0,
            onColorTempChange = onColorTempChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        ColorPresetsSection(
            isPoweredOn = state.isPoweredOn,
            onSetRgb = onSetRgb
        )

        Spacer(modifier = Modifier.height(16.dp))

        ScenesSection(
            currentSceneId = state.sceneId,
            isPoweredOn = state.isPoweredOn,
            onSetScene = onSetScene
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PowerSection(
    isPoweredOn: Boolean,
    brightness: Int,
    onTogglePower: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isPoweredOn)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant,
        label = "bulbPowerBg"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPoweredOn)
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
                    tint = if (isPoweredOn)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isPoweredOn) "ON" else "OFF",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPoweredOn)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            if (isPoweredOn) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Brightness: ${brightness}%",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun BrightnessSection(
    brightness: Int,
    isPoweredOn: Boolean,
    onBrightnessChange: (Int) -> Unit
) {
    BulbSectionCard(title = "Brightness") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Brightness5,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isPoweredOn)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )

            Slider(
                value = brightness.toFloat(),
                onValueChange = { onBrightnessChange(it.toInt()) },
                valueRange = WizBulbState.MIN_BRIGHTNESS.toFloat()..WizBulbState.MAX_BRIGHTNESS.toFloat(),
                enabled = isPoweredOn,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            Icon(
                imageVector = Icons.Default.Brightness7,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isPoweredOn)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "${brightness}%",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isPoweredOn)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ColorTemperatureSection(
    colorTemp: Int,
    isPoweredOn: Boolean,
    isActive: Boolean,
    onColorTempChange: (Int) -> Unit
) {
    BulbSectionCard(title = "Color Temperature") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Warm",
                fontSize = 11.sp,
                color = if (isPoweredOn)
                    Color(0xFFFF9800)
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )

            Slider(
                value = colorTemp.toFloat(),
                onValueChange = { onColorTempChange(it.toInt()) },
                valueRange = WizBulbState.MIN_COLOR_TEMP.toFloat()..WizBulbState.MAX_COLOR_TEMP.toFloat(),
                enabled = isPoweredOn,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    activeTrackColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            )

            Text(
                text = "Cool",
                fontSize = 11.sp,
                color = if (isPoweredOn)
                    Color(0xFF42A5F5)
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "${colorTemp}K",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isPoweredOn && isActive)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPresetsSection(
    isPoweredOn: Boolean,
    onSetRgb: (Int, Int, Int) -> Unit
) {
    val presets = listOf(
        Triple(255, 0, 0) to "Red",
        Triple(255, 100, 0) to "Orange",
        Triple(255, 255, 0) to "Yellow",
        Triple(0, 255, 0) to "Green",
        Triple(0, 200, 255) to "Cyan",
        Triple(0, 0, 255) to "Blue",
        Triple(128, 0, 255) to "Purple",
        Triple(255, 0, 128) to "Pink",
        Triple(255, 255, 255) to "White"
    )

    BulbSectionCard(title = "Colors") {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            presets.forEach { (rgb, name) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = isPoweredOn) {
                            onSetRgb(rgb.first, rgb.second, rgb.third)
                        }
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPoweredOn)
                                    Color(rgb.first, rgb.second, rgb.third)
                                else
                                    Color(rgb.first, rgb.second, rgb.third).copy(alpha = 0.3f)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = name,
                        fontSize = 10.sp,
                        color = if (isPoweredOn)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScenesSection(
    currentSceneId: Int,
    isPoweredOn: Boolean,
    onSetScene: (WizScene) -> Unit
) {
    val popularScenes = listOf(
        WizScene.WARM_WHITE,
        WizScene.DAYLIGHT,
        WizScene.COOL_WHITE,
        WizScene.NIGHT_LIGHT,
        WizScene.COZY,
        WizScene.FOCUS,
        WizScene.RELAX,
        WizScene.TV_TIME,
        WizScene.ROMANCE,
        WizScene.SUNSET,
        WizScene.PARTY,
        WizScene.FIREPLACE,
        WizScene.OCEAN,
        WizScene.FOREST,
        WizScene.CANDLELIGHT,
        WizScene.BEDTIME
    )

    BulbSectionCard(title = "Scenes") {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            popularScenes.forEach { scene ->
                SceneChip(
                    label = scene.label,
                    isSelected = currentSceneId == scene.id,
                    isEnabled = isPoweredOn,
                    onClick = { onSetScene(scene) }
                )
            }
        }
    }
}

@Composable
private fun SceneChip(
    label: String,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected && isEnabled -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "sceneBg"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected && isEnabled -> MaterialTheme.colorScheme.onPrimary
            isEnabled -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        },
        label = "sceneContent"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = isEnabled, onClick = onClick)
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}

@Composable
private fun BulbSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}
