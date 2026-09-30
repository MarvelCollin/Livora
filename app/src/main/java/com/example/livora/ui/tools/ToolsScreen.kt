package com.example.livora.ui.tools

import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.vault.VaultRepository
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.NavRow
import com.example.livora.ui.components.SectionLabel
import com.example.livora.ui.components.Tag
import com.example.livora.ui.components.TopBar
import com.example.livora.ui.navigation.ToolRoutes
import com.example.livora.ui.vault.VaultViewModel
import java.io.File

@Composable
fun ToolsScreen(
    onOpenVault: () -> Unit,
    onOpenRoute: (String) -> Unit
) {
    val context = LocalContext.current
    val vaultReady = remember {
        File(File(context.filesDir, VaultViewModel.DIRECTORY), VaultRepository.VAULT_FILE).exists()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopBar(title = "Tools", subtitle = "Small helpers that stay on your phone") }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Design.screenHorizontalPadding)
        ) {
            SectionLabel(text = "Scan and create", modifier = Modifier.padding(top = 8.dp))
            NavRow(
                title = "QR codes",
                icon = Icons.Default.QrCode2,
                description = "Scan a code or make your own",
                onClick = { onOpenRoute(ToolRoutes.QR) }
            )
            NavRow(
                title = "Documents",
                icon = Icons.Default.DocumentScanner,
                description = "Scan pages and save them as PDF",
                onClick = { onOpenRoute(ToolRoutes.DOCUMENTS) },
                value = { Tag("Preview") }
            )

            SectionLabel(text = "Private")
            NavRow(
                title = "Password vault",
                icon = Icons.Default.Lock,
                description = "Passwords protected by your fingerprint or phone lock",
                onClick = onOpenVault,
                value = {
                    Text(
                        text = if (vaultReady) "Locked" else "Not set up",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            )

            SectionLabel(text = "Phone care")
            NavRow(
                title = "Storage cleaner",
                icon = Icons.Default.CleaningServices,
                description = "Swipe through photos and videos to free space",
                onClick = { onOpenRoute(ToolRoutes.CLEANER) }
            )
            NavRow(
                title = "App usage",
                icon = Icons.Default.BarChart,
                description = "Screen time and apps you never open",
                onClick = { onOpenRoute(ToolRoutes.USAGE) }
            )
        }
    }
}
