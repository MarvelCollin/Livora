package com.example.livora.ui.quicksettings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.BulbScene
import com.example.livora.ui.ac.AcViewModel
import com.example.livora.ui.bulb.BulbViewModel
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.TopBar
import com.example.livora.ui.components.VoiceListeningOverlay
import com.example.livora.util.VoiceRecognitionManager
import com.example.livora.util.WakeWordListener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsScreen(
    acViewModel: AcViewModel,
    bulbViewModel: BulbViewModel,
    onNavigateToAdvanced: () -> Unit
) {
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

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopBar(
                title = "Livora",
                subtitle = "Smart home controller",
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
                .padding(horizontal = Design.screenHorizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SectionLabel(
                text = "Quick modes",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.weight(1f))

            AdvancedControlsRow(onClick = onNavigateToAdvanced)

            Spacer(modifier = Modifier.height(10.dp))

            if (isWakeWordActive) {
                Text(
                    text = "Say \"Jarvis\" to activate voice control",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
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

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun AdvancedControlsRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Advanced controls",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Manage devices individually",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = modifier
    )
}
