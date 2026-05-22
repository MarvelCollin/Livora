package com.example.livora.ui.bulb

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
import com.example.livora.data.model.Bulb
import com.example.livora.data.model.BulbState
import com.example.livora.data.model.BulbScene
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.Section
import com.example.livora.ui.components.SelectChip
import com.example.livora.ui.components.TopBar
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
    val isAddingBulb by viewModel.isAddingBulb.collectAsState()
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
                    title = "Smart bulb",
                    subtitle = if (connectedBulb != null) "WiZ · ${connectedBulb!!.ip}" else "WiZ Downlight",
                    navigationIcon = {
                        IconButton(onClick = {
                            viewModel.cancelBulbSetup()
                            onBack()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    },
                    actions = {
                        if (connectedBulb != null && !isAddingBulb) {
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
                    }
                )
            }
        ) { innerPadding ->
            if (connectedBulb == null || isAddingBulb) {
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
    discoveredBulbs: List<Bulb>,
    isScanning: Boolean,
    onScan: () -> Unit,
    onSelectBulb: (Bulb) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Design.screenHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Find WiZ bulbs",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Connect your bulb to the same Wi-Fi as your phone, then scan.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isScanning, onClick = onScan),
            shape = Design.cardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = Design.cardElevation)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Scanning",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.surface
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Scan for bulbs",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.surface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        if (discoveredBulbs.isNotEmpty()) {
            Text(
                text = "Found devices",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            discoveredBulbs.forEach { bulb ->
                BulbDiscoveryCard(
                    bulb = bulb,
                    onClick = { onSelectBulb(bulb) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (!isScanning && discoveredBulbs.isEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No bulbs found yet. Tap Scan to search your network.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun BulbDiscoveryCard(
    bulb: Bulb,
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
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (bulb.moduleName.isNotEmpty()) bulb.moduleName else "WiZ bulb",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${bulb.ip} · ${bulb.mac}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }

            Text(
                text = "Connect",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun BulbControlContent(
    state: BulbState,
    onTogglePower: () -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onColorTempChange: (Int) -> Unit,
    onSetRgb: (Int, Int, Int) -> Unit,
    onSetScene: (BulbScene) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Design.screenHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        PowerSection(
            isPoweredOn = state.isPoweredOn,
            brightness = state.brightness,
            onTogglePower = onTogglePower
        )

        Spacer(modifier = Modifier.height(Design.sectionSpacing))

        BrightnessSection(
            brightness = state.brightness,
            isPoweredOn = state.isPoweredOn,
            onBrightnessChange = onBrightnessChange
        )

        Spacer(modifier = Modifier.height(Design.sectionSpacing))

        ColorTemperatureSection(
            colorTemp = state.colorTemp,
            isPoweredOn = state.isPoweredOn,
            isActive = !state.useRgb && state.sceneId == 0,
            onColorTempChange = onColorTempChange
        )

        Spacer(modifier = Modifier.height(Design.sectionSpacing))

        ColorPresetsSection(
            isPoweredOn = state.isPoweredOn,
            onSetRgb = onSetRgb
        )

        Spacer(modifier = Modifier.height(Design.sectionSpacing))

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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = Design.cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPoweredOn)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    .clickable(onClick = onTogglePower),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = if (isPoweredOn)
                        MaterialTheme.colorScheme.surface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isPoweredOn) "On" else "Off",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isPoweredOn) 1f else 0.45f)
            )

            if (isPoweredOn) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Brightness ${brightness}%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
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
    Section(title = "Brightness") {
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
                valueRange = BulbState.MIN_BRIGHTNESS.toFloat()..BulbState.MAX_BRIGHTNESS.toFloat(),
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
    Section(title = "Color temperature") {
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
                valueRange = BulbState.MIN_COLOR_TEMP.toFloat()..BulbState.MAX_COLOR_TEMP.toFloat(),
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

    Section(title = "Colors") {
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
    onSetScene: (BulbScene) -> Unit
) {
    val popularScenes = listOf(
        BulbScene.WARM_WHITE,
        BulbScene.DAYLIGHT,
        BulbScene.COOL_WHITE,
        BulbScene.NIGHT_LIGHT,
        BulbScene.COZY,
        BulbScene.FOCUS,
        BulbScene.RELAX,
        BulbScene.TV_TIME,
        BulbScene.ROMANCE,
        BulbScene.SUNSET,
        BulbScene.PARTY,
        BulbScene.FIREPLACE,
        BulbScene.OCEAN,
        BulbScene.FOREST,
        BulbScene.CANDLELIGHT,
        BulbScene.BEDTIME
    )

    Section(title = "Scenes") {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            popularScenes.forEach { scene ->
                SelectChip(
                    label = scene.label,
                    selected = currentSceneId == scene.id,
                    enabled = isPoweredOn,
                    onClick = { onSetScene(scene) }
                )
            }
        }
    }
}

