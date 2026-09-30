package com.example.livora.ui.people

import com.example.livora.ui.components.pressScale
import com.example.livora.ui.components.Motion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.people.PeopleServices
import com.example.livora.data.people.media.FaceImages
import com.example.livora.data.people.media.ThumbnailLoader
import com.example.livora.data.people.scan.ScanPhase
import com.example.livora.data.people.scan.ScanProgress
import com.example.livora.ui.components.SkeletonBox
import java.text.NumberFormat

fun formatCount(value: Int): String = NumberFormat.getIntegerInstance().format(value)

fun photosLabel(count: Int): String = if (count == 1) "1 photo" else "${formatCount(count)} photos"

sealed interface ImageLoad {
    data object Loading : ImageLoad
    data object Failed : ImageLoad
    class Loaded(val bitmap: Bitmap) : ImageLoad
}

private fun Bitmap?.toLoad(): ImageLoad = if (this == null) ImageLoad.Failed else ImageLoad.Loaded(this)

@Composable
private fun LoadedImage(load: ImageLoad, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape) {
    when (load) {
        ImageLoad.Loading -> SkeletonBox(modifier = modifier, shape = shape)
        ImageLoad.Failed -> Box(modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
        is ImageLoad.Loaded -> Image(
            bitmap = load.bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape)
        )
    }
}

@Composable
fun FaceAvatar(
    faceId: Long?,
    referenceId: Long?,
    size: Dp,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    val context = LocalContext.current
    val services = remember { PeopleServices.get(context) }
    val shape = RoundedCornerShape(size / 4)
    val load by produceState<ImageLoad>(initialValue = ImageLoad.Loading, faceId, referenceId) {
        value = when {
            faceId != null -> FaceImages.avatar(context, services.database, faceId).toLoad()
            referenceId != null -> FaceImages.referenceAvatar(context, referenceId).toLoad()
            else -> ImageLoad.Failed
        }
    }
    val base = modifier
        .size(size)
        .let { if (description != null) it.semantics { contentDescription = description } else it }
    LoadedImage(load, base, shape)
}

@Composable
fun PhotoThumb(
    mediaId: Long,
    sizePx: Int,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp),
    description: String? = null
) {
    val context = LocalContext.current
    val cached = ThumbnailLoader.peek(mediaId, sizePx)
    val load by produceState<ImageLoad>(
        initialValue = if (cached != null) ImageLoad.Loaded(cached) else ImageLoad.Loading,
        mediaId,
        sizePx
    ) {
        if (value !is ImageLoad.Loaded) value = ThumbnailLoader.load(context, mediaId, sizePx).toLoad()
    }
    val base = modifier.let { if (description != null) it.semantics { contentDescription = description } else it }
    LoadedImage(load, base, shape)
}

@Composable
fun FaceTile(
    faceId: Long,
    mediaId: Long,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    orientation: Int,
    sizePx: Int,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)
) {
    val context = LocalContext.current
    val load by produceState<ImageLoad>(initialValue = ImageLoad.Loading, faceId, sizePx) {
        value = FaceImages.tile(context, faceId, mediaId, left, top, right, bottom, orientation, sizePx).toLoad()
    }
    LoadedImage(load, modifier, shape)
}

@Composable
fun SegmentTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            val underline by animateFloatAsState(
                targetValue = if (active) 1f else 0f,
                animationSpec = tween(Motion.Medium, easing = Motion.EmphasizedDecelerate),
                label = "tabUnderline"
            )
            val tint by animateColorAsState(
                targetValue = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = Motion.quick(),
                label = "tabTint"
            )
            Column(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable(role = Role.Tab) { onSelect(index) },
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    fontSize = 15.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = tint
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(28.dp)
                        .graphicsLayer { scaleX = underline }
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
fun LinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasis: Boolean = false
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            textDecoration = TextDecoration.Underline
        )
    }
}

@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(12.dp)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(interaction)
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

@Composable
fun OutlineAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(12.dp)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(interaction)
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.7f else 0.25f), shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        )
    }
}

@Composable
fun EmptyBlock(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = body,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(20.dp))
            PrimaryAction(text = actionLabel, onClick = onAction)
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(modifier = Modifier.height(4.dp))
            LinkButton(text = secondaryLabel, onClick = onSecondary)
        }
    }
}

@Composable
fun ScanBlock(
    progress: ScanProgress,
    faceCount: Int,
    modifier: Modifier = Modifier
) {
    val title = when (progress.phase) {
        ScanPhase.Preparing -> "Getting ready"
        ScanPhase.Grouping -> "Grouping faces into people"
        else -> "Finding people in your photos"
    }
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (progress.total > 0) {
            Text(
                text = "Scanned ${formatCount(progress.scanned)} of ${formatCount(progress.total)} photos",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                drawStopIndicator = {}
            )
            Spacer(modifier = Modifier.height(8.dp))
            val rate = if (progress.photosPerSecond > 0.5f) "${progress.photosPerSecond.toInt()} photos per second, " else ""
            Text(
                text = "$rate${formatCount(faceCount)} faces found. You can leave this screen, the scan continues in the background.",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            SkeletonBox(modifier = Modifier.fillMaxWidth().height(14.dp), shape = RoundedCornerShape(7.dp))
        }
    }
}

@Composable
fun PeopleRowSkeleton() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkeletonBox(modifier = Modifier.size(56.dp), shape = RoundedCornerShape(14.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            SkeletonBox(modifier = Modifier.width(140.dp).height(14.dp), shape = RoundedCornerShape(7.dp))
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonBox(modifier = Modifier.width(80.dp).height(12.dp), shape = RoundedCornerShape(6.dp))
        }
    }
}

@Composable
fun GridSkeleton(columns: Int, rows: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(columns) {
                    SkeletonBox(
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        shape = RoundedCornerShape(2.dp)
                    )
                }
            }
        }
    }
}
