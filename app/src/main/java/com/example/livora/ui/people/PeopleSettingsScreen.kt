package com.example.livora.ui.people

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.ui.components.ChoiceOption
import com.example.livora.ui.components.ChoiceRow
import com.example.livora.ui.components.TopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Pending { None, GroupAgain, Export, Import, Rescan }

@Composable
fun PeopleSettingsScreen(viewModel: PeopleSettingsViewModel, onBack: () -> Unit) {
    val strictness by viewModel.strictness.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val current by viewModel.currentPeople.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val status by viewModel.status.collectAsState()
    val skip by viewModel.skipScreenshots.collectAsState()
    val minPhotos by viewModel.minPhotos.collectAsState()
    var pending by remember { mutableStateOf(Pending.None) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFrom(uri)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopBar(
                title = "Grouping and data",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
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
                .padding(bottom = 32.dp)
        ) {
            SectionTitle("How strictly faces are grouped")
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Looser settings put more photos of one person together but can mix up people who look alike. Stricter settings keep people apart but can split one person into several groups.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = strictness,
                    onValueChange = { viewModel.setStrictness(it) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = "Grouping strictness"
                        stateDescription = strictnessWord(strictness)
                    }
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Loose", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(strictnessWord(strictness), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Strict", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = when {
                        preview != null -> "This would give $preview people. Now there are $current."
                        else -> "Now there are $current people. Move the slider to preview a change."
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryAction(text = "Group again", onClick = { pending = Pending.GroupAgain }, enabled = busy == null)
                Text(
                    text = "Group again rebuilds the unnamed groups. Every name, merge and correction you made is kept.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

            SectionTitle("Which groups are listed")
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Show unnamed people who appear in at least this many photos",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                ChoiceRow(
                    options = listOf(ChoiceOption(2, "2 photos"), ChoiceOption(3, "3 photos"), ChoiceOption(5, "5 photos")),
                    selected = minPhotos,
                    enabled = true,
                    onSelect = { viewModel.setMinPhotos(it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Skip screenshots", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Screenshots rarely hold people and slow the scan down.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = skip, onCheckedChange = { viewModel.setSkipScreenshots(it) })
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

            SectionTitle("Saved on this phone")
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                val s = status
                if (s == null) {
                    com.example.livora.ui.components.SkeletonBox(
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    )
                } else {
                    Text(
                        text = "${formatCount(s.indexedPhotos)} photos indexed with ${formatCount(s.faces)} faces",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (s.lastScanAt > 0) {
                        val ago = DateUtils.getRelativeTimeSpanString(s.lastScanAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
                            .toString().lowercase()
                        Text(
                            text = "Last scan $ago, ${s.lastNewPhotos} new photos",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (s.failedPhotos > 0) {
                        Text(
                            text = "${formatCount(s.failedPhotos)} photos could not be read and are skipped until they change",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "Finished photos are never scanned again. Only new or changed photos are read.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinkButton(text = "Scan new photos", onClick = { viewModel.scanNew() })
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

            SectionTitle("Keep your work safe")
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Uninstalling the app deletes the people data. Save it to a file first and restore it later without scanning again. The file holds face data, so keep it only somewhere you trust.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlineAction(text = "Export people data", onClick = { pending = Pending.Export }, enabled = busy == null, modifier = Modifier.weight(1f))
                    OutlineAction(text = "Import people data", onClick = { pending = Pending.Import }, enabled = busy == null, modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

            SectionTitle("Start over")
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Rescan everything forgets which photos were read and looks at every photo again.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinkButton(text = "Rescan everything", onClick = { pending = Pending.Rescan }, enabled = busy == null)
            }
            val message = busy
            if (message != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "$message, please wait", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }

    when (pending) {
        Pending.GroupAgain -> ConfirmDialog(
            title = "Group again?",
            body = "Unnamed groups are rebuilt with the new setting. Names, merges, hidden people and your corrections stay as they are.",
            confirm = "Group again",
            onConfirm = {
                pending = Pending.None
                viewModel.groupAgain()
            },
            onDismiss = { pending = Pending.None }
        )
        Pending.Export -> ConfirmDialog(
            title = "Save people data?",
            body = "The file contains face data of the people in your photos. It stays only where you save it. Livora does not upload it anywhere.",
            confirm = "Choose where to save",
            onConfirm = {
                pending = Pending.None
                val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                exportLauncher.launch("livora-people-$stamp.lvr")
            },
            onDismiss = { pending = Pending.None }
        )
        Pending.Import -> ConfirmDialog(
            title = "Restore people data?",
            body = "This replaces the people data on this phone with the file. Photos that changed since the file was saved are checked again automatically.",
            confirm = "Choose a file",
            onConfirm = {
                pending = Pending.None
                importLauncher.launch(arrayOf("*/*"))
            },
            onDismiss = { pending = Pending.None }
        )
        Pending.Rescan -> ConfirmDialog(
            title = "Rescan every photo?",
            body = "This takes a while and uses battery. Unnamed groups and their corrections are removed. Named people keep their reference photos. Photos on your phone are not touched.",
            confirm = "Rescan everything",
            destructive = true,
            onConfirm = {
                pending = Pending.None
                viewModel.rescanEverything()
            },
            onDismiss = { pending = Pending.None }
        )
        Pending.None -> Unit
    }
}

private fun strictnessWord(value: Float): String = when {
    value < 0.2f -> "Very loose"
    value < 0.4f -> "Loose"
    value < 0.6f -> "Balanced"
    value < 0.8f -> "Strict"
    else -> "Very strict"
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
