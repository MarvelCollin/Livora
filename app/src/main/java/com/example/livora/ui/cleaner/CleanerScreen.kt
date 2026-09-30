package com.example.livora.ui.cleaner

import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.example.livora.ui.components.ChartSlot
import com.example.livora.ui.components.ColorKey
import com.example.livora.ui.components.chartColor
import com.example.livora.ui.components.chartNeutral
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.ui.components.BackButton
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.Headline
import com.example.livora.ui.components.NavRow
import com.example.livora.ui.components.PreviewNotice
import com.example.livora.ui.components.SectionLabel
import com.example.livora.ui.components.TopBar
import com.example.livora.ui.components.showPreviewOnly
import com.example.livora.ui.people.LinkButton
import com.example.livora.ui.people.PrimaryAction

private class Slice(val label: String, val gb: Float, val slot: Int?)

private const val TOTAL_GB = 256f

private val slices = listOf(
    Slice("Apps", 71.3f, ChartSlot.Blue),
    Slice("Images", 58.4f, ChartSlot.Orange),
    Slice("Videos", 36.9f, ChartSlot.Aqua),
    Slice("Other", 28.4f, null),
    Slice("Audio", 7.2f, ChartSlot.Yellow),
    Slice("Documents", 5.6f, ChartSlot.Magenta)
)

private fun sliceAt(x: Float, width: Float): Int {
    var start = 0f
    slices.forEachIndexed { index, slice ->
        val end = start + width * slice.gb / TOTAL_GB
        if (x < end) return index
        start = end
    }
    return -1
}

@Composable
fun CleanerScreen(onBack: () -> Unit, onOpenReview: () -> Unit) {
    var picked by rememberSaveable { mutableIntStateOf(-1) }
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = WindowInsets.navigationBars.asPaddingValues()
        ) {
            item(key = "notice") { PreviewNotice() }
            item(key = "headline") {
                Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 16.dp)) {
                    Headline(
                        label = "Free space",
                        value = "48.2 GB",
                        context = "of 256 GB. Photos and videos use 95.3 GB."
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    StorageBar(picked = picked, onPick = { picked = it })
                    Spacer(modifier = Modifier.height(4.dp))
                    PickedDetail(picked = picked)
                    Spacer(modifier = Modifier.height(8.dp))
                    Legend(picked = picked, onPick = { picked = if (picked == it) -1 else it })
                }
            }
            item(key = "review") {
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
                        text = "1,204 photos and videos to go through. Swipe left to keep and right to send to the trash. Nothing is deleted until you confirm.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                    )
                    PrimaryAction(
                        text = "Start swipe review",
                        onClick = onOpenReview,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item(key = "quick") {
                Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding)) {
                    SectionLabel(text = "Quick wins", modifier = Modifier.padding(top = 28.dp))
                    NavRow(
                        title = "Duplicates",
                        icon = Icons.Default.ContentCopy,
                        description = "86 exact copies, 412 MB",
                        onClick = { showPreviewOnly() }
                    )
                    NavRow(
                        title = "Screenshots",
                        icon = Icons.Default.Screenshot,
                        description = "214 files, 1.1 GB",
                        onClick = { showPreviewOnly() }
                    )
                    NavRow(
                        title = "Blurry photos",
                        icon = Icons.Default.BlurOn,
                        description = "58 photos, 190 MB",
                        onClick = { showPreviewOnly() }
                    )
                    NavRow(
                        title = "Large files",
                        icon = Icons.Default.Storage,
                        description = "12 files over 100 MB, 3.4 GB",
                        onClick = { showPreviewOnly() }
                    )
                    NavRow(
                        title = "Messaging media",
                        icon = Icons.Default.Forum,
                        description = "WhatsApp photos and videos, 6.2 GB",
                        onClick = { showPreviewOnly() }
                    )
                }
            }
            item(key = "full") {
                Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding)) {
                    SectionLabel(text = "Full cleaner mode")
                    Text(
                        text = "Old installers, large downloads and empty folders need access to all files. Without it the cleaner still works on photos and videos.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinkButton(text = "Allow access to all files", onClick = { showPreviewOnly() })
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun StorageBar(picked: Int, onPick: (Int) -> Unit) {
    val colors = slices.map { slice -> slice.slot?.let { chartColor(it) } ?: chartNeutral() }
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onPick(sliceAt(down.position.x, size.width.toFloat()))
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            onPick(sliceAt(change.position.x, size.width.toFloat()))
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
            slices.forEachIndexed { index, slice ->
                val w = size.width * slice.gb / TOTAL_GB
                drawRect(
                    color = colors[index].copy(alpha = if (picked < 0 || picked == index) 1f else 0.3f),
                    topLeft = Offset(x, 0f),
                    size = Size((w - gap).coerceAtLeast(1f), size.height)
                )
                x += w
            }
            drawRect(color = track, topLeft = Offset(x, 0f), size = Size((size.width - x).coerceAtLeast(0f), size.height))
        }
    }
}

@Composable
private fun PickedDetail(picked: Int) {
    val slice = slices.getOrNull(picked)
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
                text = "${slice.gb} GB",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
            Text(
                text = "${slice.label}, ${(slice.gb / TOTAL_GB * 100).roundToInt()}% of your storage",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun Legend(picked: Int, onPick: (Int) -> Unit) {
    Column {
        slices.withIndex().chunked(2).forEach { pair ->
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
                            text = "${slice.gb} GB",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
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
