package com.example.livora.ui.vault

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.livora.data.vault.DeviceAuthStatus
import com.example.livora.data.vault.VaultClipboard
import com.example.livora.ui.components.Toaster
import com.example.livora.ui.components.TopBar
import androidx.compose.foundation.selection.toggleable

private fun Context.findActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun VaultScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = viewModel()
) {
    val context = LocalContext.current
    val stage by viewModel.stage.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.lock()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            viewModel.lock()
        }
    }

    when (val current = stage) {
        is VaultStage.Unavailable -> UnavailableScreen(current.status, onBack, viewModel::recheckDevice)
        VaultStage.NeedsSetup -> SetupScreen(busy, error, onBack) { viewModel.createVault(context) }
        is VaultStage.ShowRecovery -> RecoveryScreen(current.code, onBack, viewModel::finishSetup)
        VaultStage.Locked -> LockedScreen(busy, error, onBack) { viewModel.unlock(context) }
        VaultStage.KeyLost -> RestoreScreen(
            damaged = false,
            busy = busy,
            error = error,
            onBack = onBack,
            onRestore = { code -> viewModel.restore(context, code) },
            onErase = { viewModel.erase(context) }
        )
        VaultStage.Damaged -> RestoreScreen(
            damaged = true,
            busy = busy,
            error = error,
            onBack = onBack,
            onRestore = { },
            onErase = { viewModel.erase(context) }
        )
        VaultStage.Unlocked -> VaultContent(
            entries = entries,
            error = error,
            onBack = onBack,
            onLock = viewModel::lock,
            onSave = viewModel::upsert,
            onDelete = viewModel::delete,
            newId = viewModel::newEntryId
        )
    }
}

@Composable
internal fun BackAction(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun GateScreen(
    onBack: () -> Unit,
    headline: String,
    body: String,
    error: String?,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopBar(title = "Password vault", navigationIcon = { BackAction(onBack) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = headline,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = body,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (error != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = error,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            content()
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PrimaryAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    ) {
        Text(text)
    }
}

@Composable
private fun UnavailableScreen(status: DeviceAuthStatus, onBack: () -> Unit, onRecheck: () -> Unit) {
    val context = LocalContext.current
    val body = when (status) {
        DeviceAuthStatus.UNSUPPORTED_OS -> "The vault needs Android 11 or newer to protect your passwords with your fingerprint or phone lock."
        DeviceAuthStatus.NO_SCREEN_LOCK -> "Set a fingerprint or a screen lock in your phone settings first. The vault uses it to protect your passwords."
        DeviceAuthStatus.NO_HARDWARE -> "This phone cannot check a fingerprint or screen lock, so the vault cannot protect your passwords here."
        else -> "Your phone cannot check your fingerprint or screen lock right now. Try again in a moment."
    }
    GateScreen(onBack, "Vault is not available yet", body, null) {
        if (status == DeviceAuthStatus.NO_SCREEN_LOCK) {
            PrimaryAction("Open security settings") {
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (status != DeviceAuthStatus.UNSUPPORTED_OS && status != DeviceAuthStatus.NO_HARDWARE) {
            OutlinedButton(
                onClick = onRecheck,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                Text("Check again")
            }
        }
    }
}

@Composable
private fun SetupScreen(busy: Boolean, error: String?, onBack: () -> Unit, onCreate: () -> Unit) {
    GateScreen(
        onBack = onBack,
        headline = "Keep passwords safe on this phone",
        body = "Your passwords are encrypted and stay on this phone. Only your fingerprint or phone lock can open them. Nothing is ever sent anywhere.",
        error = error
    ) {
        PrimaryAction(if (busy) "Waiting for you" else "Create vault", enabled = !busy, onClick = onCreate)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "You will get a recovery code next. Keep it somewhere safe, it is the only way back in if the phone key is lost.",
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LockedScreen(busy: Boolean, error: String?, onBack: () -> Unit, onUnlock: () -> Unit) {
    var asked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!asked) {
            asked = true
            onUnlock()
        }
    }
    GateScreen(
        onBack = onBack,
        headline = "Vault is locked",
        body = "Use your fingerprint or phone lock to open your passwords.",
        error = error
    ) {
        PrimaryAction(if (busy) "Waiting for you" else "Unlock", enabled = !busy, onClick = onUnlock)
    }
}

@Composable
private fun RecoveryScreen(code: String, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var saved by remember { mutableStateOf(false) }
    val groups = code.split("-")
    GateScreen(
        onBack = onBack,
        headline = "Save your recovery code",
        body = "If the phone key for this vault is ever lost, this code is the only way to get your passwords back. Write it down and keep it away from this phone. It is shown only once.",
        error = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            groups.chunked(4).forEach { row ->
                Text(
                    text = row.joinToString("  "),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                VaultClipboard.copySecret(context, "Livora recovery code", code)
                Toaster.info("Recovery code copied. It clears in 30 seconds")
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
        ) {
            Text("Copy code")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = saved, role = Role.Checkbox, onValueChange = { saved = it }),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = saved, onCheckedChange = null)
            Text(
                text = "I saved this code somewhere safe",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        PrimaryAction("Continue", enabled = saved, onClick = onDone)
    }
}

@Composable
private fun RestoreScreen(
    damaged: Boolean,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRestore: (String) -> Unit,
    onErase: () -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }
    var confirmErase by remember { mutableStateOf(false) }
    GateScreen(
        onBack = onBack,
        headline = if (damaged) "The vault file is damaged" else "The phone key is gone",
        body = if (damaged) {
            "The saved vault cannot be opened, so it cannot be repaired here. You can erase it and start a new vault."
        } else {
            "This happens when the screen lock is removed or the phone is reset. Enter your recovery code to get your passwords back."
        },
        error = error
    ) {
        if (!damaged) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Recovery code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(14.dp))
            PrimaryAction(
                text = if (busy) "Waiting for you" else "Restore vault",
                enabled = !busy && code.isNotBlank(),
                onClick = { onRestore(code) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        TextButton(
            onClick = { confirmErase = true },
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("Erase vault", color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Erase the vault?") },
            text = { Text("This deletes every saved password on this phone. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmErase = false
                    onErase()
                }) {
                    Text("Erase", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmErase = false }) { Text("Keep") }
            }
        )
    }
}
