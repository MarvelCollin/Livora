package com.example.livora.ui.cleaner

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.ui.components.BackButton
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.Tag
import com.example.livora.ui.components.TopBar
import com.example.livora.ui.components.showPreviewOnly
import com.example.livora.ui.people.LinkButton
import com.example.livora.ui.people.OutlineAction
import com.example.livora.ui.people.PrimaryAction
import kotlinx.coroutines.launch
import kotlin.math.abs

private class ReviewItem(
    val name: String,
    val date: String,
    val size: String,
    val mb: Float,
    val detail: String,
    val video: Boolean = false
)

private val reviewItems = listOf(
    ReviewItem("IMG_4821.jpg", "12 Mar 2026", "3.4 MB", 3.4f, "4032 x 3024, Camera"),
    ReviewItem("Screenshot_2026-09-28.png", "28 Sep 2026", "1.1 MB", 1.1f, "1220 x 2712, Screenshots"),
    ReviewItem("VID_0093.mp4", "2 Sep 2026", "48.6 MB", 48.6f, "1080p, 0:42, Camera", video = true),
    ReviewItem("IMG_4790.jpg", "9 Mar 2026", "2.8 MB", 2.8f, "4032 x 3024, Camera"),
    ReviewItem("IMG-WA0043.jpg", "30 Sep 2026", "0.2 MB", 0.2f, "1280 x 960, WhatsApp Images"),
    ReviewItem("IMG_4755.jpg", "1 Mar 2026", "4.1 MB", 4.1f, "4032 x 3024, Camera"),
    ReviewItem("VID_0071.mp4", "14 Aug 2026", "22.3 MB", 22.3f, "720p, 0:18, Camera", video = true),
    ReviewItem("IMG_4712.jpg", "20 Feb 2026", "3.0 MB", 3.0f, "4032 x 3024, Camera")
)

@Composable
fun CleanerReviewScreen(onBack: () -> Unit) {
    var log by rememberSaveable { mutableStateOf("") }
    val index = log.length
    val done = index >= reviewItems.size

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopBar(
                title = "Swipe review",
                subtitle = if (done) "All reviewed" else "${index + 1} of ${reviewItems.size}",
                navigationIcon = { BackButton(onBack) }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (done) {
                Summary(
                    log = log,
                    onUndo = { log = log.dropLast(1) },
                    onRestart = { log = "" },
                    onBack = onBack
                )
            } else {
                Text(
                    text = "Preview with sample photos. Nothing is deleted.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 8.dp)
                )
                ReviewStack(
                    index = index,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onDecision = { keep -> log += if (keep) "K" else "T" },
                    canUndo = log.isNotEmpty(),
                    onUndo = { log = log.dropLast(1) }
                )
            }
        }
    }
}

@Composable
private fun ReviewStack(
    index: Int,
    canUndo: Boolean,
    onDecision: (Boolean) -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val offset = remember(index) { Animatable(0f) }

    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val threshold = widthPx * 0.28f

        suspend fun commit(keep: Boolean) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            offset.animateTo(if (keep) -widthPx * 1.3f else widthPx * 1.3f, tween(180))
            onDecision(keep)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (index + 1 < reviewItems.size) {
                    ReviewCard(
                        item = reviewItems[index + 1],
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = 0.93f
                            translationY = 30f
                        }
                    )
                }
                ReviewCard(
                    item = reviewItems[index],
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = offset.value
                            rotationZ = offset.value / widthPx * 10f
                        }
                        .pointerInput(index) {
                            detectDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        when {
                                            offset.value < -threshold -> commit(true)
                                            offset.value > threshold -> commit(false)
                                            else -> offset.animateTo(0f, tween(160))
                                        }
                                    }
                                },
                                onDragCancel = { scope.launch { offset.animateTo(0f, tween(160)) } }
                            ) { change, drag ->
                                change.consume()
                                scope.launch { offset.snapTo(offset.value + drag.x) }
                            }
                        }
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction("Keep this file") {
                                    scope.launch { commit(true) }
                                    true
                                },
                                CustomAccessibilityAction("Send this file to the trash") {
                                    scope.launch { commit(false) }
                                    true
                                }
                            )
                        }
                )
                val progress = (abs(offset.value) / threshold).coerceIn(0f, 1f)
                if (offset.value < 0f) {
                    EdgeLabel(text = "Keep", keep = true, alpha = progress, modifier = Modifier.align(Alignment.CenterStart))
                }
                if (offset.value > 0f) {
                    EdgeLabel(text = "Trash", keep = false, alpha = progress, modifier = Modifier.align(Alignment.CenterEnd))
                }
            }
            ReviewActions(
                canUndo = canUndo,
                onKeep = { scope.launch { commit(true) } },
                onTrash = { scope.launch { commit(false) } },
                onUndo = onUndo,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun ReviewActions(
    canUndo: Boolean,
    onKeep: () -> Unit,
    onTrash: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
        Text(
            text = "Swipe left to keep, right to trash",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlineAction(text = "Keep", onClick = onKeep, modifier = Modifier.weight(1f))
            LinkButton(text = "Undo", onClick = onUndo, enabled = canUndo)
            OutlineAction(text = "Trash", onClick = onTrash, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun EdgeLabel(text: String, keep: Boolean, alpha: Float, modifier: Modifier = Modifier) {
    val fill = if (keep) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val ink = if (keep) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = ink,
        modifier = modifier
            .padding(20.dp)
            .alpha(alpha)
            .background(fill, RoundedCornerShape(4.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun ReviewCard(item: ReviewItem, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            PhotoArt(seed = item.name.length, modifier = Modifier.fillMaxSize())
            if (item.video) {
                Tag(text = "Video", modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
            }
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = item.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "${item.date}, ${item.size}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = item.detail,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PhotoArt(seed: Int, modifier: Modifier = Modifier) {
    val sky = MaterialTheme.colorScheme.surfaceContainerHigh
    val sun = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
    val far = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val near = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    Canvas(modifier = modifier.background(sky)) {
        val w = size.width
        val h = size.height
        val shift = (seed % 5) * 0.06f
        drawCircle(color = sun, radius = w * 0.09f, center = Offset(w * (0.25f + shift), h * 0.24f))
        val back = Path().apply {
            moveTo(0f, h)
            lineTo(w * (0.3f + shift), h * 0.5f)
            lineTo(w * (0.62f + shift), h)
            close()
        }
        drawPath(back, far)
        val front = Path().apply {
            moveTo(w * 0.35f, h)
            lineTo(w * (0.68f - shift), h * 0.42f)
            lineTo(w, h)
            close()
        }
        drawPath(front, near)
    }
}

@Composable
private fun Summary(log: String, onUndo: () -> Unit, onRestart: () -> Unit, onBack: () -> Unit) {
    val kept = log.count { it == 'K' }
    val trashed = log.count { it == 'T' }
    val freed = reviewItems.filterIndexed { i, _ -> log.getOrNull(i) == 'T' }.sumOf { it.mb.toDouble() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 32.dp)
    ) {
        Text(
            text = "All done for now",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "You reviewed ${reviewItems.size} files. You kept $kept and marked $trashed for the trash.",
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(16.dp))
        PrimaryAction(
            text = "Trash $trashed ${if (trashed == 1) "item" else "items"} and free ${"%.1f".format(freed)} MB",
            onClick = { showPreviewOnly() },
            enabled = trashed > 0,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Your phone asks once, then moves them to the system trash. You can restore them for about 30 days.",
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinkButton(text = "Undo last swipe", onClick = onUndo)
        LinkButton(text = "Start over", onClick = onRestart)
        LinkButton(text = "Back to the cleaner", onClick = onBack)
    }
}
