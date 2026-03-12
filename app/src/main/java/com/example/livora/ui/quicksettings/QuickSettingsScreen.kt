package com.example.livora.ui.quicksettings

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.runtime.DisposableEffect
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
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.WizScene
import com.example.livora.ui.ac.AcViewModel
import com.example.livora.ui.bulb.BulbViewModel
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
        bulbViewModel.setScene(WizScene.COOL_WHITE)
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
            lower.contains("normal") && lower.contains("mode") -> activateNormalMode()
            lower.contains("sleep") && lower.contains("mode") -> activateSleepMode()
            lower.contains("out") && lower.contains("mode") -> activateOutMode()
            lower.contains("leave") || lower.contains("going out") -> activateOutMode()
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

    DisposableEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            isWakeWordActive = true
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
                        if (isWakeWordActive) {
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
                                    onListeningEnded = { isListening = false; partialText = "" }
                                )
                            }
                        }
                    }
                )
            }
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
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Livora",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Smart Home Controller",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
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
                                        if (isWakeWordActive) {
                                            wakeWordListener.start {
                                                wakeWordListener.stop()
                                                voiceManager.startListening(
                                                    onResult = { text2 ->
                                                        processQuickVoiceCommand(text2)
                                                        acViewModel.processVoiceCommand(text2)
                                                        bulbViewModel.processVoiceCommand(text2)
                                                    },
                                                    onPartialResult = { partial -> partialText = partial },
                                                    onListeningStarted = { isListening = true },
                                                    onListeningEnded = { isListening = false; partialText = "" }
                                                )
                                            }
                                        }
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
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Quick Modes",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Select a mode to control all devices at once",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            QuickModeCard(
                title = "Normal Mode",
                description = "AC 20°C Cool · Bulb 100% Cool White",
                icon = Icons.Default.WbSunny,
                iconColor = Color(0xFF4CAF50),
                cardColor = Color(0xFF1B5E20).copy(alpha = 0.12f),
                onClick = { activateNormalMode() }
            )

            Spacer(modifier = Modifier.height(14.dp))

            QuickModeCard(
                title = "Sleep Mode",
                description = "AC 20°C Cool · Bulb Off",
                icon = Icons.Default.Bedtime,
                iconColor = Color(0xFF5C6BC0),
                cardColor = Color(0xFF283593).copy(alpha = 0.12f),
                onClick = { activateSleepMode() }
            )

            Spacer(modifier = Modifier.height(14.dp))

            QuickModeCard(
                title = "Out Mode",
                description = "AC Off · Bulb Off",
                icon = Icons.Default.ExitToApp,
                iconColor = Color(0xFFEF5350),
                cardColor = Color(0xFFC62828).copy(alpha = 0.12f),
                onClick = { activateOutMode() }
            )

            Spacer(modifier = Modifier.weight(1f))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToAdvanced),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Advanced Controls",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Manage individual devices",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isWakeWordActive) {
                Text(
                    text = "Say \"Jarvis\" to activate voice control",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
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
                if (isWakeWordActive) {
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
                            onListeningEnded = { isListening = false; partialText = "" }
                        )
                    }
                }
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
    iconColor: Color,
    cardColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = iconColor
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
