package com.example.livora.ui.people

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.people.media.MediaImage
import kotlinx.coroutines.flow.StateFlow

enum class PickerMode { Copy, Move }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryGrid(
    rows: List<GalleryRow>?,
    selected: Set<Long>,
    onOpen: (Long) -> Unit,
    onToggle: (Long) -> Unit,
    onToggleGroup: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
    aiLabels: Map<Long, String> = emptyMap(),
    topContent: @Composable () -> Unit = {},
    emptyContent: @Composable () -> Unit = {}
) {
    if (rows == null) {
        Column(modifier = modifier.fillMaxSize()) {
            topContent()
            GridSkeleton(columns = 3, rows = 8)
        }
        return
    }
    val selecting = selected.isNotEmpty()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item(key = "top", span = { GridItemSpan(maxLineSpan) }) { topContent() }
        if (rows.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { emptyContent() }
        }
        items(
            items = rows,
            key = { it.key },
            span = { row -> if (row is GalleryRow.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) }
        ) { row ->
            when (row) {
                is GalleryRow.Header -> DayHeader(
                    header = row,
                    selecting = selecting,
                    allSelected = selecting && selected.containsAll(row.ids),
                    onToggle = { onToggleGroup(row.ids) }
                )
                is GalleryRow.Photo -> PhotoCell(
                    image = row.image,
                    aiName = aiLabels[row.image.id],
                    isSelected = row.image.id in selected,
                    selecting = selecting,
                    onOpen = { onOpen(row.image.id) },
                    onToggle = { onToggle(row.image.id) }
                )
            }
        }
        item(key = "end", span = { GridItemSpan(maxLineSpan) }) { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayHeader(header: GalleryRow.Header, selecting: Boolean, allSelected: Boolean, onToggle: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .combinedClickable(
                onClick = { if (selecting) onToggle() },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                }
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = header.label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (selecting) {
            SelectMark(
                selected = allSelected,
                description = if (allSelected) "All photos of ${header.label} selected" else "Select all photos of ${header.label}"
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoCell(
    image: MediaImage,
    aiName: String?,
    isSelected: Boolean,
    selecting: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val inset by animateDpAsState(targetValue = if (isSelected) 10.dp else 0.dp, label = "photoInset")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) else Modifier)
            .combinedClickable(
                onClick = { if (selecting) onToggle() else onOpen() },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                }
            )
    ) {
        PhotoThumb(
            mediaId = image.id,
            sizePx = 320,
            modifier = Modifier.fillMaxSize().padding(inset),
            shape = RoundedCornerShape(if (isSelected) 6.dp else 2.dp),
            description = "Photo"
        )
        if (aiName != null) {
            Text(
                text = if (aiName.isEmpty()) "AI" else "AI $aiName",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
        if (selecting) {
            SelectMark(
                selected = isSelected,
                description = if (isSelected) "Selected photo" else "Photo not selected",
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
            )
        }
    }
}

@Composable
fun ReviewBanner(text: String, action: String, onAction: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            LinkButton(text = action, onClick = onAction, emphasis = true)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
    }
}

@Composable
fun ReviewBar(count: Int, onWrong: () -> Unit, onRight: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlineAction(text = "Looks right", onClick = onRight, enabled = count > 0, modifier = Modifier.weight(1f))
            PrimaryAction(
                text = if (count == 0) "Tap the wrong ones" else "This is wrong ($count)",
                onClick = onWrong,
                enabled = count > 0,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun PickPersonBar(count: Int, onUse: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            PrimaryAction(
                text = when (count) {
                    0 -> "Tap the photos of the person"
                    1 -> "Use 1 photo of the person"
                    else -> "Use $count photos of the person"
                },
                onClick = onUse,
                enabled = count > 0,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun PhotoSelectionBar(
    canModify: Boolean,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrimaryAction(text = "Copy to", onClick = onCopy, modifier = Modifier.weight(1f))
            OutlineAction(text = "Move to", onClick = onMove, enabled = canModify, modifier = Modifier.weight(1f))
            OutlineAction(text = "Delete", onClick = onDelete, enabled = canModify, modifier = Modifier.weight(1f))
        }
        if (!canModify) {
            Text(
                text = "Moving and deleting photos needs Android 11 or newer.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            )
        }
    }
}

@Composable
fun ConsentEffect(consent: StateFlow<ConsentRequest?>) {
    val request by consent.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        consent.value?.onResult(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(request) {
        val current = request
        if (current != null) launcher.launch(IntentSenderRequest.Builder(current.sender).build())
    }
}

@Composable
fun PartialAccessNotice(onOpenSettings: () -> Unit) {
    Column {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Showing only the photos you picked",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Allow all photos in settings to see your whole gallery.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinkButton(text = "Open settings", onClick = onOpenSettings)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
    }
}
