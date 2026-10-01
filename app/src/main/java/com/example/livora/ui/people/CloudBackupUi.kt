package com.example.livora.ui.people

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.people.cloud.CloudState
import com.example.livora.data.people.cloud.RemoteBackup
import com.example.livora.ui.components.AppButton
import com.example.livora.ui.components.ButtonKind
import com.example.livora.ui.components.Motion

private fun ago(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
        .toString().lowercase()

private fun counts(remote: RemoteBackup): String =
    "${plural(remote.persons, "person", "people")}, ${plural(remote.photos, "photo", "photos")}, ${plural(remote.faces, "face", "faces")}"

private fun plural(value: Int, one: String, many: String): String = "${formatCount(value)} ${if (value == 1) one else many}"

@Composable
fun CloudRestoreBanner(
    offer: RemoteBackup?,
    state: CloudState,
    onRestore: () -> Unit,
    onStartFresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmFresh by remember { mutableStateOf(false) }
    val shown = remember { mutableStateOf<RemoteBackup?>(null) }
    if (offer != null) shown.value = offer
    val restoring = state == CloudState.Restoring

    AnimatedVisibility(
        visible = offer != null,
        enter = expandVertically(Motion.enter()) + fadeIn(Motion.enter()),
        exit = shrinkVertically(Motion.exit()) + fadeOut(Motion.exit()),
        modifier = modifier
    ) {
        val backup = offer ?: shown.value
        Column {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    text = "Restore your people from the cloud",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (backup != null) {
                    Text(
                        text = "Saved ${ago(backup.generation)}: ${counts(backup)}. Photos that did not change are not scanned again.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (restoring) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Restoring, please wait",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(text = "Restore people", onClick = onRestore, kind = ButtonKind.Primary)
                        AppButton(text = "Start fresh", onClick = { confirmFresh = true })
                    }
                }
                if (state is CloudState.Failed) {
                    Text(
                        text = state.message,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        }
    }

    if (confirmFresh) {
        ConfirmDialog(
            title = "Start fresh?",
            body = "This phone starts with no people. The cloud backup is replaced as soon as your new scan is saved.",
            confirm = "Start fresh",
            destructive = true,
            onConfirm = {
                confirmFresh = false
                onStartFresh()
            },
            onDismiss = { confirmFresh = false }
        )
    }
}

@Composable
fun CloudBackupSection(
    state: CloudState,
    remote: RemoteBackup?,
    savedAt: Long,
    linked: Boolean,
    busy: Boolean,
    onSave: (Boolean) -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmReplace by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    val working = state == CloudState.Saving || state == CloudState.Restoring
    val usable = state != CloudState.NotConfigured && state != CloudState.NotReady

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = "Your people are saved to your cloud database so they survive an uninstall. Only face data and scan results are saved, encrypted on this phone first. Photos are never uploaded.",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = statusLine(state, remote, savedAt, linked),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (state is CloudState.Failed || state == CloudState.NotReady) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        if (remote != null && usable) {
            Text(
                text = counts(remote),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppButton(
                text = if (state == CloudState.Saving) "Saving" else "Save now",
                onClick = { if (!linked && remote != null) confirmReplace = true else onSave(false) },
                kind = ButtonKind.Primary,
                enabled = usable && !working && !busy,
                modifier = Modifier.weight(1f)
            )
            AppButton(
                text = if (state == CloudState.Restoring) "Restoring" else "Restore",
                onClick = { confirmRestore = true },
                enabled = usable && !working && !busy && remote != null,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (confirmReplace) {
        ConfirmDialog(
            title = "Replace the cloud backup?",
            body = "A backup from ${remote?.let { ago(it.generation) } ?: "earlier"} is already in the cloud. Saving now replaces it with what is on this phone.",
            confirm = "Replace",
            destructive = true,
            onConfirm = {
                confirmReplace = false
                onSave(true)
            },
            onDismiss = { confirmReplace = false }
        )
    }
    if (confirmRestore) {
        ConfirmDialog(
            title = "Restore from the cloud?",
            body = "This replaces the people data on this phone with the cloud backup. Photos that changed since then are checked again automatically.",
            confirm = "Restore",
            onConfirm = {
                confirmRestore = false
                onRestore()
            },
            onDismiss = { confirmRestore = false }
        )
    }
}

private fun statusLine(state: CloudState, remote: RemoteBackup?, savedAt: Long, linked: Boolean): String = when (state) {
    CloudState.NotConfigured -> "Not set up. Add PEOPLE_BACKUP_KEY to secrets.properties and rebuild the app."
    CloudState.NotReady -> "The people_backup table is missing in Supabase. Run supabase/migrations/20261001120000_people_backup.sql in the SQL editor, then open this screen again."
    CloudState.Saving -> "Saving to the cloud"
    CloudState.Restoring -> "Restoring from the cloud"
    is CloudState.Failed -> state.message
    CloudState.Idle -> when {
        savedAt > 0L -> "Saved to the cloud ${ago(savedAt)}"
        remote != null && !linked -> "A cloud backup from ${ago(remote.generation)} is waiting"
        else -> "Not saved yet. It saves by itself a moment after each scan or change."
    }
}
