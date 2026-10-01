package com.example.livora.ui.cleaner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.livora.data.cleaner.CleanerAccess
import com.example.livora.data.cleaner.CleanerRules
import com.example.livora.data.cleaner.CleanerSource
import com.example.livora.data.cleaner.StorageOverview
import com.example.livora.ui.components.BackButton
import com.example.livora.ui.components.ColorKey
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.Headline
import com.example.livora.ui.components.NavRow
import com.example.livora.ui.components.SectionLabel
import com.example.livora.ui.components.SkeletonBox
import com.example.livora.ui.components.TopBar
import com.example.livora.ui.components.chartColor
import com.example.livora.ui.components.chartNeutral
import com.example.livora.ui.people.EmptyBlock
import com.example.livora.ui.people.LinkButton
import com.example.livora.ui.people.PrimaryAction
import com.example.livora.ui.people.formatCount
import kotlin.math.abs
import kotlin.math.roundToInt

private fun sliceAt(storage: StorageOverview, x: Float, width: Float): Int {
    if (storage.total <= 0) return -1
    var start = 0f
    storage.slices.forEachIndexed { index, slice ->
        val end = start + width * slice.bytes / storage.total
        if (x < end) return index
        start = end
    }
    return -1
}

private fun bytes(context: Context, value: Long): String = Formatter.formatShortFileSize(context, value)

@Composable
fun CleanerScreen(
    onBack: () -> Unit,
    onOpenReview: (CleanerSource) -> Unit,
    viewModel: CleanerViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val owner = LocalLifecycleOwner.current
    var picked by rememberSaveable { mutableIntStateOf(-1) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refresh()
    }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopBar(
                title = "Storage cleaner",
                subtitle = "Swipe through photos and videos to free space",
                navigationIcon = { BackButton(onBack) }
            )
        }
    ) { innerPadding ->
        val library = state.library
        val storage = state.storage
        when {
            !state.access -> EmptyBlock(
                title = "Allow access to photos and videos",
                body = "The cleaner needs to see your photos and videos to find copies, screenshots and big files. Nothing leaves this phone and nothing is deleted until you confirm.",
                actionLabel = "Allow access",
                onAction = { permissionLauncher.launch(CleanerAccess.request()) },
                secondaryLabel = "Open app settings",
                onSecondary = { openSettings(context) },
                modifier = Modifier.padding(innerPadding)
            )

            state.loading || library == null || storage == null -> Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(Design.screenHorizontalPadding)
            ) {
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.3f).height(14.dp), shape = RoundedCornerShape(7.dp))
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.5f).height(32.dp), shape = RoundedCornerShape(8.dp))
                Spacer(modifier = Modifier.height(20.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth().height(14.dp), shape = RoundedCornerShape(4.dp))
                Spacer(modifier = Modifier.height(24.dp))
                repeat(4) {
                    SkeletonBox(modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(8.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = WindowInsets.navigationBars.asPaddingValues()
            ) {
                item(key = "headline") {
                    val media = storage.slices.filter { it.key == "photos" || it.key == "videos" }.sumOf { it.bytes }
                    Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 16.dp)) {
                        Headline(
                            label = "Free space",
                            value = bytes(context, storage.free),
                            context = "of ${bytes(context, storage.total)}. Photos and videos use ${bytes(context, media)}."
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        StorageBar(storage = storage, picked = picked, onPick = { picked = it })
                        Spacer(modifier = Modifier.height(4.dp))
                        PickedDetail(storage = storage, picked = picked)
                        Spacer(modifier = Modifier.height(8.dp))
                        Legend(storage = storage, picked = picked, onPick = { picked = if (picked == it) -1 else it })
                        if (!CleanerAccess.isFull(context)) {
                            Text(
                                text = "Showing only the photos and videos you allowed.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                            LinkButton(
                                text = "Allow all photos and videos",
                                onClick = { permissionLauncher.launch(CleanerAccess.request()) },
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                }

                item(key = "review") {
                    val count = library.candidates.size
                    val kept = library.kept.size
                    Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Swipe review",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (count == 0) {
                                "Everything has been reviewed. New photos and videos will show up here."
                            } else {
                                "${formatCount(count)} photos and videos to go through, largest first. Swipe left to keep and right to send to the trash. Nothing is deleted until you confirm."
                            },
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                        )
                        PrimaryAction(
                            text = "Start swipe review",
                            onClick = { onOpenReview(CleanerSource.All) },
                            enabled = count > 0,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (kept > 0) {
                            LinkButton(
                                text = "Show ${formatCount(kept)} kept ${if (kept == 1) "file" else "files"} again",
                                onClick = viewModel::clearKept,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                }

                item(key = "quick") {
                    val duplicates = state.duplicates
                    val shots = library.screenshots
                    val large = library.large
                    val messaging = library.messaging
                    val copies = duplicates?.copies.orEmpty().filter { it.key !in library.kept }
                    Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding)) {
                        SectionLabel(text = "Quick wins", modifier = Modifier.padding(top = 28.dp))
                        if (state.checkingDuplicates) {
                            NavRow(
                                title = "Duplicates",
                                icon = Icons.Default.ContentCopy,
                                description = "Checking for identical copies",
                                onClick = {}
                            )
                        } else if (copies.isNotEmpty()) {
                            NavRow(
                                title = "Duplicates",
                                icon = Icons.Default.ContentCopy,
                                description = "${formatCount(copies.size)} ${if (copies.size == 1) "copy" else "copies"}, ${bytes(context, copies.sumOf { it.size })}",
                                onClick = { onOpenReview(CleanerSource.Duplicates) }
                            )
                        }
                        if (shots.isNotEmpty()) {
                            NavRow(
                                title = "Screenshots",
                                icon = Icons.Default.Screenshot,
                                description = "${formatCount(shots.size)} ${if (shots.size == 1) "file" else "files"}, ${bytes(context, shots.sumOf { it.size })}",
                                onClick = { onOpenReview(CleanerSource.Screenshots) }
                            )
                        }
                        if (large.isNotEmpty()) {
                            NavRow(
                                title = "Large files",
                                icon = Icons.Default.Storage,
                                description = "${formatCount(large.size)} ${if (large.size == 1) "file" else "files"} over ${bytes(context, CleanerRules.LARGE_BYTES)}, ${bytes(context, large.sumOf { it.size })}",
                                onClick = { onOpenReview(CleanerSource.Large) }
                            )
                        }
                        if (messaging.isNotEmpty()) {
                            NavRow(
                                title = "Messaging media",
                                icon = Icons.Default.Forum,
                                description = "${formatCount(messaging.size)} photos and videos from chat apps, ${bytes(context, messaging.sumOf { it.size })}",
                                onClick = { onOpenReview(CleanerSource.Messaging) }
                            )
                        }
                        if (!state.checkingDuplicates && copies.isEmpty() && shots.isEmpty() && large.isEmpty() && messaging.isEmpty()) {
                            Text(
                                text = "Nothing obvious to clean up right now.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

private fun openSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

@Composable
private fun StorageBar(storage: StorageOverview, picked: Int, onPick: (Int) -> Unit) {
    val colors = storage.slices.map { slice -> slice.slot?.let { chartColor(it) } ?: chartNeutral() }
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(storage) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onPick(sliceAt(storage, down.position.x, size.width.toFloat()))
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            onPick(sliceAt(storage, change.position.x, size.width.toFloat()))
                            val delta = change.position - change.previousPosition
                            if (change.positionChanged() && abs(delta.x) > abs(delta.y)) change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            val gap = 2.dp.toPx()
            var x = 0f
            storage.slices.forEachIndexed { index, slice ->
                val w = size.width * slice.bytes / storage.total.coerceAtLeast(1)
                if (w > 0f) {
                    drawRect(
                        color = colors[index].copy(alpha = if (picked < 0 || picked == index) 1f else 0.3f),
                        topLeft = Offset(x, 0f),
                        size = Size((w - gap).coerceAtLeast(1f), size.height)
                    )
                }
                x += w
            }
            drawRect(color = track, topLeft = Offset(x, 0f), size = Size((size.width - x).coerceAtLeast(0f), size.height))
        }
    }
}

@Composable
private fun PickedDetail(storage: StorageOverview, picked: Int) {
    val context = LocalContext.current
    val slice = storage.slices.getOrNull(picked)
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        if (slice == null) {
            Text(
                text = "Touch the bar or a row to see how much each part takes.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            ColorKey(color = slice.slot?.let { chartColor(it) } ?: chartNeutral())
            Text(
                text = bytes(context, slice.bytes),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
            Text(
                text = "${slice.label}, ${(slice.bytes * 100f / storage.total.coerceAtLeast(1)).roundToInt()}% of your storage",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun Legend(storage: StorageOverview, picked: Int, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    Column {
        storage.slices.withIndex().chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth()) {
                pair.forEach { (index, slice) ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clickable(role = Role.Button) { onPick(index) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ColorKey(color = slice.slot?.let { chartColor(it) } ?: chartNeutral())
                        Text(
                            text = slice.label,
                            fontSize = 13.sp,
                            fontWeight = if (picked == index) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                        Text(
                            text = bytes(context, slice.bytes),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
        Text(
            text = "The bar shows used space by type, then free space in gray at the end.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
