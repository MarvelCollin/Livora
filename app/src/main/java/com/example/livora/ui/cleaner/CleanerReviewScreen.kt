package com.example.livora.ui.cleaner

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.livora.data.cleaner.CleanerFile
import com.example.livora.ui.components.BackButton
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.SkeletonBox
import com.example.livora.ui.components.SuccessCheck
import com.example.livora.ui.components.TopBar
import com.example.livora.ui.components.statusGood
import com.example.livora.ui.people.ConsentEffect
import com.example.livora.ui.people.EmptyBlock
import com.example.livora.ui.people.LinkButton
import com.example.livora.ui.people.OutlineAction
import com.example.livora.ui.people.PrimaryAction
import com.example.livora.ui.people.formatCount
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun CleanerReviewScreen(onBack: () -> Unit, viewModel: CleanerReviewViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    ConsentEffect(viewModel.consent)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopBar(
                title = viewModel.source.title,
                subtitle = when {
                    state.loading -> "Getting your files"
                    state.finished -> "Done"
                    state.queue.isEmpty() -> "Nothing to review"
                    state.done -> "All reviewed"
                    else -> "${state.index + 1} of ${formatCount(state.queue.size)}"
                },
                navigationIcon = { BackButton(onBack) }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.loading -> SkeletonBox(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
                )

                state.queue.isEmpty() -> EmptyBlock(
                    title = "Nothing to review here",
                    body = "There are no files left in this group. Files you kept are hidden until you show them again from the cleaner.",
                    actionLabel = "Back to the cleaner",
                    onAction = onBack
                )

                state.finished -> Finished(
                    count = state.trashed,
                    bytes = state.trashedBytes,
                    onBack = onBack
                )

                state.done -> Summary(
                    kept = state.keptCount,
                    files = state.toTrash,
                    total = state.queue.size,
                    onTrash = viewModel::trash,
                    onUndo = viewModel::undo,
                    onRestart = viewModel::restart,
                    onBack = onBack
                )

                else -> {
                    Text(
                        text = "Largest first. Nothing is deleted until you confirm at the end.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 8.dp)
                    )
                    ReviewStack(
                        items = state.queue,
                        index = state.index,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onDecision = viewModel::decide,
                        canUndo = state.decisions.isNotEmpty(),
                        onUndo = viewModel::undo
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewStack(
    items: List<CleanerFile>,
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
                if (index + 1 < items.size) {
                    ReviewCard(
                        item = items[index + 1],
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = 0.93f
                            translationY = 30f
                        }
                    )
                }
                ReviewCard(
                    item = items[index],
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
private fun ReviewCard(item: CleanerFile, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        FileThumb(
            file = item,
            sizePx = 1080,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RectangleShape
        )
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = item.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = DateUtils.formatDateTime(context, item.dateMs, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR) +
                    ", " + Formatter.formatShortFileSize(context, item.size),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = (if (item.width > 0) "${item.width} x ${item.height}, " else "") + item.folder.ifBlank { "Storage" },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Summary(
    kept: Int,
    files: List<CleanerFile>,
    total: Int,
    onTrash: () -> Unit,
    onUndo: () -> Unit,
    onRestart: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val trashed = files.size
    val freed = files.sumOf { it.size }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 32.dp)
    ) {
        SuccessCheck(color = statusGood(), size = 72.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "All done for now",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "You reviewed ${formatCount(total)} files. You kept ${formatCount(kept)} and marked ${formatCount(trashed)} for the trash.",
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(16.dp))
        PrimaryAction(
            text = "Trash ${formatCount(trashed)} ${if (trashed == 1) "item" else "items"} and free ${Formatter.formatShortFileSize(context, freed)}",
            onClick = onTrash,
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

@Composable
private fun Finished(count: Int, bytes: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp)
    ) {
        SuccessCheck(color = statusGood(), size = 88.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "${Formatter.formatShortFileSize(context, bytes)} freed",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "${formatCount(count)} ${if (count == 1) "file is" else "files are"} in the system trash. Open your gallery or Files app to restore them within about 30 days.",
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryAction(text = "Back to the cleaner", onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}
