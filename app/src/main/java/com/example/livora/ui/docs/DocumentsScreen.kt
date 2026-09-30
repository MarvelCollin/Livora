package com.example.livora.ui.docs

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.text.format.Formatter
import android.util.LruCache
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.livora.data.docs.DocumentNames
import com.example.livora.ui.components.BackButton
import com.example.livora.ui.components.ChoiceOption
import com.example.livora.ui.components.ChoiceRow
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.SkeletonBox
import com.example.livora.ui.components.Toaster
import com.example.livora.ui.components.TopBar
import com.example.livora.ui.people.EmptyBlock
import com.example.livora.ui.people.LinkButton
import com.example.livora.ui.people.OutlineAction
import com.example.livora.ui.people.PrimaryAction
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class DocSort { Newest, Name, Size }

private object PageBitmaps {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun peek(file: File, side: Int): Bitmap? = cache.get(key(file, side))

    fun load(file: File, side: Int): Bitmap? {
        cache.get(key(file, side))?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) sample *= 2
        val bitmap = try {
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: OutOfMemoryError) {
            null
        } ?: return null
        cache.put(key(file, side), bitmap)
        return bitmap
    }

    private fun key(file: File, side: Int) = "${file.absolutePath}:${file.lastModified()}:$side"
}

@Composable
private fun PageThumb(
    file: File?,
    side: Int,
    modifier: Modifier = Modifier,
    scale: ContentScale = ContentScale.Fit,
    paper: Color = MaterialTheme.colorScheme.surfaceContainerLowest
) {
    var bitmap by remember(file?.absolutePath, side) { mutableStateOf(file?.let { PageBitmaps.peek(it, side) }) }
    LaunchedEffect(file?.absolutePath, side) {
        if (file != null && bitmap == null) bitmap = withContext(Dispatchers.IO) { PageBitmaps.load(file, side) }
    }
    val loaded = bitmap
    Box(modifier = modifier.background(paper, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
        if (loaded != null) {
            Image(bitmap = loaded.asImageBitmap(), contentDescription = null, contentScale = scale, modifier = Modifier.fillMaxSize())
        } else if (file != null) {
            SkeletonBox(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(4.dp))
        }
    }
}

@Composable
private fun rememberDocumentScanner(onPages: (List<android.net.Uri>) -> Unit): () -> Unit {
    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val uris = scan?.pages?.map { it.imageUri }.orEmpty()
            if (uris.isNotEmpty()) onPages(uris)
        }
    }
    return {
        if (activity == null) {
            Toaster.error("Scanning is not available here")
        } else {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(20)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                .addOnSuccessListener { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }
                .addOnFailureListener { Toaster.error("Scanning needs Google Play services. You can still add pages from images.") }
        }
    }
}

@Composable
private fun rememberImagePicker(onPicked: (List<android.net.Uri>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

private fun pagesText(count: Int) = if (count == 1) "1 page" else "$count pages"

@Composable
fun DocumentsScreen(onBack: () -> Unit, onOpenDocument: (Long) -> Unit, viewModel: DocumentsViewModel = viewModel()) {
    val context = LocalContext.current
    val documents by viewModel.documents.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(DocSort.Newest) }

    val scan = rememberDocumentScanner { viewModel.create(it, "Scan", onOpenDocument) }
    val fromImages = rememberImagePicker { viewModel.create(it, "Images", onOpenDocument) }

    val all = documents
    val shown = all.orEmpty()
        .filter { query.isBlank() || it.document.name.contains(query, ignoreCase = true) }
        .let { list ->
            when (sort) {
                DocSort.Newest -> list
                DocSort.Name -> list.sortedBy { it.document.name.lowercase() }
                DocSort.Size -> list.sortedByDescending { it.document.sizeBytes }
            }
        }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopBar(
                    title = "Documents",
                    subtitle = "Scan pages and save them as PDF",
                    navigationIcon = { BackButton(onBack) }
                )
                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrimaryAction(text = "Scan document", onClick = scan, enabled = !busy, modifier = Modifier.weight(1f))
                    OutlineAction(text = "From images", onClick = fromImages, enabled = !busy, modifier = Modifier.weight(1f))
                }
            }
        }
    ) { innerPadding ->
        when {
            all == null -> Column(modifier = Modifier.padding(innerPadding)) { repeat(4) { DocumentRowSkeleton() } }

            all.isEmpty() -> EmptyBlock(
                title = "No documents yet",
                body = "Scan a paper document, a receipt or an ID card. Pages are cleaned up, saved on this phone and turned into a PDF whenever you share or save it.",
                modifier = Modifier.padding(innerPadding)
            )

            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                item(key = "controls") {
                    Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 12.dp)) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search documents") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ChoiceRow(
                            options = listOf(
                                ChoiceOption(DocSort.Newest, "Newest"),
                                ChoiceOption(DocSort.Name, "Name"),
                                ChoiceOption(DocSort.Size, "Size")
                            ),
                            selected = sort,
                            enabled = true,
                            onSelect = { sort = it }
                        )
                    }
                }
                if (shown.isEmpty()) {
                    item(key = "empty") {
                        EmptyBlock(
                            title = "Nothing matches",
                            body = "Try another name, or scan a new document.",
                            actionLabel = "Clear search",
                            onAction = { query = "" }
                        )
                    }
                } else {
                    items(shown, key = { it.document.id }) { item ->
                        Column(modifier = Modifier.animateItem()) {
                            DocumentRow(item = item, context = context, onClick = { onOpenDocument(item.document.id) })
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 92.dp, end = Design.screenHorizontalPadding),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
                item(key = "end") { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun DocumentRow(item: DocumentItem, context: Context, onClick: () -> Unit) {
    val doc = item.document
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Design.screenHorizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PageThumb(
            file = item.cover,
            side = 240,
            modifier = Modifier
                .width(52.dp)
                .aspectRatio(0.72f)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = doc.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "${pagesText(doc.pageCount)}, ${Formatter.formatShortFileSize(context, doc.sizeBytes)}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = DateUtils.formatDateTime(context, doc.updatedAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DocumentRowSkeleton() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Design.screenHorizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkeletonBox(modifier = Modifier.width(52.dp).aspectRatio(0.72f), shape = RoundedCornerShape(4.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            SkeletonBox(modifier = Modifier.width(150.dp).height(14.dp), shape = RoundedCornerShape(7.dp))
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonBox(modifier = Modifier.width(90.dp).height(12.dp), shape = RoundedCornerShape(6.dp))
        }
    }
}

@Composable
fun DocumentDetailScreen(onBack: () -> Unit, viewModel: DocumentDetailViewModel = viewModel()) {
    val context = LocalContext.current
    val document by viewModel.document.collectAsState()
    val pages by viewModel.pages.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var viewing by rememberSaveable { mutableStateOf<Int?>(null) }

    val scan = rememberDocumentScanner { viewModel.addPages(it) }
    val fromImages = rememberImagePicker { viewModel.addPages(it) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) viewModel.savePdf(uri)
    }

    val doc = document
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopBar(
                    title = doc?.name ?: "Document",
                    subtitle = doc?.let { "${pagesText(it.pageCount)}, ${Formatter.formatShortFileSize(context, it.sizeBytes)}" },
                    navigationIcon = { BackButton(onBack) },
                    actions = {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = {
                                    menuOpen = false
                                    renaming = true
                                })
                                DropdownMenuItem(text = { Text("Delete document") }, onClick = {
                                    menuOpen = false
                                    deleting = true
                                })
                            }
                        }
                    }
                )
                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlineAction(text = "Add pages", onClick = { addOpen = true }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                        DropdownMenu(expanded = addOpen, onDismissRequest = { addOpen = false }) {
                            DropdownMenuItem(text = { Text("Scan pages") }, onClick = {
                                addOpen = false
                                scan()
                            })
                            DropdownMenuItem(text = { Text("From images") }, onClick = {
                                addOpen = false
                                fromImages()
                            })
                        }
                    }
                    OutlineAction(
                        text = "Share",
                        onClick = {
                            viewModel.sharePdf { file ->
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                                val send = Intent(Intent.ACTION_SEND)
                                    .setType("application/pdf")
                                    .putExtra(Intent.EXTRA_STREAM, uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                context.startActivity(Intent.createChooser(send, null))
                            }
                        },
                        enabled = !busy && !pages.isNullOrEmpty(),
                        modifier = Modifier.weight(1f)
                    )
                    PrimaryAction(
                        text = "Save PDF",
                        onClick = { saver.launch(DocumentNames.fileName(doc?.name ?: "document") + ".pdf") },
                        enabled = !busy && !pages.isNullOrEmpty(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { innerPadding ->
        val list = pages
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                list == null -> Column(modifier = Modifier.padding(16.dp)) {
                    SkeletonBox(modifier = Modifier.fillMaxWidth().aspectRatio(0.72f), shape = RoundedCornerShape(4.dp))
                }

                list.isEmpty() -> EmptyBlock(
                    title = "No pages left",
                    body = "This document has no pages. Add some, or delete it.",
                    actionLabel = "Scan pages",
                    onAction = scan
                )

                else -> {
                    Text(
                        text = "Tap a page to view it, rotate it, move it or delete it.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 10.dp)
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Design.screenHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(list, key = { _, item -> item.page.id }) { index, item ->
                            Column(modifier = Modifier.animateItem()) {
                                PageThumb(
                                    file = item.file,
                                    side = 600,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.72f)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                                        .clickable(role = Role.Button, onClickLabel = "View page ${index + 1}") { viewing = index }
                                )
                                Text(
                                    text = "Page ${index + 1}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val list = pages
    val open = viewing
    if (open != null && !list.isNullOrEmpty()) {
        PageViewer(
            pages = list,
            start = open.coerceIn(0, list.lastIndex),
            onRotate = viewModel::rotatePage,
            onMove = viewModel::movePage,
            onDelete = viewModel::deletePage,
            onClose = { viewing = null }
        )
    } else if (open != null) {
        viewing = null
    }

    if (renaming && doc != null) {
        var name by remember { mutableStateOf(doc.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename document") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(name)
                    renaming = false
                }, enabled = name.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } }
        )
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete this document") },
            text = { Text("The pages are removed from this phone. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    viewModel.delete(onBack)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PageViewer(
    pages: List<PageItem>,
    start: Int,
    onRotate: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
    onDelete: (Long) -> Unit,
    onClose: () -> Unit
) {
    val pager = rememberPagerState(initialPage = start) { pages.size }
    val current = pages.getOrNull(pager.currentPage)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
                Text(
                    text = "Page ${pager.currentPage + 1} of ${pages.size}",
                    fontSize = 15.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                key = { pages[it].page.id }
            ) { index ->
                ZoomablePage(file = pages[index].file, modifier = Modifier.fillMaxSize())
            }
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ViewerAction("Rotate", true) { current?.let { onRotate(it.page.id) } }
                ViewerAction("Earlier", pager.currentPage > 0) { current?.let { onMove(it.page.id, -1) } }
                ViewerAction("Later", pager.currentPage < pages.lastIndex) { current?.let { onMove(it.page.id, 1) } }
                ViewerAction("Delete", true) { current?.let { onDelete(it.page.id) } }
            }
        }
    }
}

@Composable
private fun ViewerAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomablePage(file: File, modifier: Modifier = Modifier) {
    var scale by remember(file) { mutableFloatStateOf(1f) }
    var offset by remember(file) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + pan else androidx.compose.ui.geometry.Offset.Zero
    }
    Box(
        modifier = modifier
            .transformable(state = state, canPan = { scale > 1f })
            .pointerInput(file) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = androidx.compose.ui.geometry.Offset.Zero
                    } else {
                        scale = 2.5f
                    }
                })
            }
    ) {
        PageThumb(
            file = file,
            side = 1800,
            paper = Color.Transparent,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
        )
    }
}
