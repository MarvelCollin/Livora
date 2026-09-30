package com.example.livora.ui.people

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.livora.data.people.media.MediaImage
import com.example.livora.data.people.media.MediaImages
import com.example.livora.data.people.media.PhotoDecoder
import com.example.livora.data.people.media.ThumbnailLoader
import com.example.livora.ui.components.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val VIEWER_LONG_SIDE = 2048
private const val PREVIEW_SIZE = 320
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoViewerScreen(viewModel: PhotoViewerViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val images by viewModel.images.collectAsState()
    var chromeVisible by remember { mutableStateOf(true) }
    var moving by remember { mutableStateOf(false) }
    var seen by remember { mutableStateOf(false) }
    var positioned by remember { mutableStateOf(images.any { it.id == viewModel.startId }) }
    val pagerState = rememberPagerState(
        initialPage = images.indexOfFirst { it.id == viewModel.startId }.coerceAtLeast(0)
    ) { images.size }

    ConsentEffect(viewModel.consent)
    LightSystemBarIcons()

    LaunchedEffect(images) {
        if (images.isNotEmpty()) {
            seen = true
            if (!positioned) {
                val index = images.indexOfFirst { it.id == viewModel.startId }
                if (index >= 0) {
                    pagerState.scrollToPage(index)
                    positioned = true
                }
            }
        } else if (seen) {
            onBack()
        }
    }

    val current = images.getOrNull(pagerState.currentPage)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 12.dp,
            key = { index -> images.getOrNull(index)?.id ?: index }
        ) { page ->
            val image = images.getOrNull(page)
            if (image != null) {
                ViewerPage(image = image, onTap = { chromeVisible = !chromeVisible })
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                if (current != null) {
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(
                            text = DateUtils.formatDateTime(
                                context,
                                current.sortDate,
                                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME
                            ),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "${pagerState.currentPage + 1} of ${images.size}",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = chromeVisible && current != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ViewerAction(
                    icon = Icons.Default.Share,
                    label = "Share",
                    onClick = { if (current != null) share(context, current) }
                )
                ViewerAction(
                    icon = Icons.Default.Folder,
                    label = "Move",
                    enabled = viewModel.supportsConsent,
                    onClick = { moving = true }
                )
                ViewerAction(
                    icon = Icons.Default.Delete,
                    label = "Delete",
                    enabled = viewModel.supportsConsent,
                    onClick = { if (current != null) viewModel.delete(current) }
                )
            }
        }
    }

    if (moving && current != null) {
        FolderPickerSheet(
            title = "Move to a folder",
            folders = viewModel.folderList(),
            excludeKey = "b:${current.bucketId}",
            onPick = { target ->
                moving = false
                viewModel.move(current, target)
            },
            onCreate = { name ->
                moving = false
                viewModel.createFolderAnd(name) { target -> viewModel.move(current, target) }
            },
            onDismiss = { moving = false }
        )
    }
}

@Composable
private fun ViewerPage(image: MediaImage, onTap: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(
        initialValue = ThumbnailLoader.peek(image.id, PREVIEW_SIZE),
        image.id
    ) {
        if (value == null) value = ThumbnailLoader.load(context, image.id, PREVIEW_SIZE)
        val full = withContext(Dispatchers.IO) {
            PhotoDecoder.decode(context, MediaImages.uri(image.id), image.orientation, image.width, image.height, VIEWER_LONG_SIDE)
        }
        if (full != null) value = full
    }
    val shown = bitmap
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (shown == null) {
            CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f))
        } else {
            ZoomableImage(bitmap = shown, onTap = onTap)
        }
    }
}

@Composable
private fun ZoomableImage(bitmap: Bitmap, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }

    fun clamp(value: Offset, zoom: Float): Offset {
        val maxX = box.width * (zoom - 1f) / 2f
        val maxY = box.height * (zoom - 1f) / 2f
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { box = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val fromCenter = tap - Offset(box.width / 2f, box.height / 2f)
                            scale = DOUBLE_TAP_ZOOM
                            offset = clamp(fromCenter - fromCenter * DOUBLE_TAP_ZOOM, DOUBLE_TAP_ZOOM)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        if (zoomChange != 1f || (scale > 1f && panChange != Offset.Zero)) {
                            val next = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val fromCenter = if (centroid.isSpecified) {
                                centroid - Offset(box.width / 2f, box.height / 2f)
                            } else {
                                Offset.Zero
                            }
                            val moved = fromCenter + panChange - (fromCenter - offset) * (next / scale)
                            offset = clamp(moved, next)
                            scale = next
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        Image(
            bitmap = image,
            contentDescription = "Photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

@Composable
private fun ViewerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
    Column(
        modifier = Modifier
            .defaultMinSize(minWidth = 72.dp, minHeight = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = tint)
    }
}

@Composable
private fun LightSystemBarIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = if (window != null) WindowCompat.getInsetsController(window, view) else null
        val statusWasLight = controller?.isAppearanceLightStatusBars
        val navigationWasLight = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (controller != null && statusWasLight != null && navigationWasLight != null) {
                controller.isAppearanceLightStatusBars = statusWasLight
                controller.isAppearanceLightNavigationBars = navigationWasLight
            }
        }
    }
}

private fun share(context: Context, image: MediaImage) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(image.mime ?: "image/*")
        .putExtra(Intent.EXTRA_STREAM, MediaImages.uri(image.id))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(send, "Share photo"))
    } catch (e: ActivityNotFoundException) {
        Toaster.error("No app on this phone can share photos")
    }
}
