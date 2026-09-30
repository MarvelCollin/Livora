package com.example.livora.ui.people

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.livora.ui.components.SkeletonBox
import com.example.livora.ui.components.TopBar

private enum class PickerMode { Copy, Move }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderDetailScreen(
    viewModel: FolderDetailViewModel,
    onBack: () -> Unit,
    onUseAsReferences: () -> Unit
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val folder by viewModel.folder.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val consent by viewModel.consent.collectAsState()
    val left by viewModel.left.collectAsState()
    val items = viewModel.photos.collectAsLazyPagingItems()
    var menuOpen by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<PickerMode?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    val all = viewModel.isAll
    val title = if (all) "All photos" else folder?.name ?: "Folder"
    val count = if (all) items.itemCount else folder?.count ?: 0
    val canAdd = !all && folder != null && com.example.livora.data.people.media.MediaFolders.isWritableTarget(folder?.relativePath.orEmpty())

    BackHandler(enabled = selecting) { viewModel.clearSelection() }
    LaunchedEffect(left) { if (left) onBack() }

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.consent.value?.onResult(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(consent) {
        val request = consent
        if (request != null) consentLauncher.launch(IntentSenderRequest.Builder(request.sender).build())
    }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        viewModel.addPicked(uris)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (selecting) {
                TopBar(
                    title = "${selected.size} selected",
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    actions = {
                        LinkButton(
                            text = "Use as reference photos",
                            onClick = { if (viewModel.useAsReferences()) onUseAsReferences() },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                )
            } else {
                TopBar(
                    title = title,
                    subtitle = if (folder?.virtual == true) "Empty folder" else photosLabel(count),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    actions = {
                        if (canAdd) {
                            IconButton(onClick = { pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                Icon(Icons.Default.Add, contentDescription = "Add photos to this folder", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        if (!all) {
                            Column {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurface)
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(text = { Text("Rename folder") }, onClick = {
                                        menuOpen = false
                                        renaming = true
                                    })
                                    DropdownMenuItem(text = { Text("Delete folder") }, onClick = {
                                        menuOpen = false
                                        confirmDelete = true
                                    })
                                }
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (selecting) {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PrimaryAction(text = "Copy to", onClick = { picker = PickerMode.Copy }, modifier = Modifier.weight(1f))
                        OutlineAction(
                            text = "Move to",
                            onClick = { picker = PickerMode.Move },
                            enabled = viewModel.supportsConsent,
                            modifier = Modifier.weight(1f)
                        )
                        OutlineAction(
                            text = "Remove",
                            onClick = { viewModel.trashSelected() },
                            enabled = viewModel.supportsConsent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (!viewModel.supportsConsent) {
                        Text(
                            text = "Moving and removing photos needs Android 11 or newer.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val refreshing = items.loadState.refresh is LoadState.Loading
            if (refreshing && items.itemCount == 0) {
                items(15) {
                    SkeletonBox(modifier = Modifier.fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(2.dp))
                }
            }
            val error = items.loadState.refresh as? LoadState.Error
            if (error != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyBlock(
                        title = "Photos could not be loaded",
                        body = "Check that Livora can still see your photos, then try again.",
                        actionLabel = "Try again",
                        onAction = { items.retry() }
                    )
                }
            }
            if (!refreshing && error == null && items.itemCount == 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyBlock(
                        title = if (folder?.virtual == true) "This folder is empty" else "No photos here",
                        body = if (folder?.virtual == true) "It shows up in your gallery as soon as the first photo is added." else "Photos you add or take will appear here.",
                        actionLabel = if (canAdd) "Add photos" else null,
                        onAction = if (canAdd) ({ pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) else null
                    )
                }
            }
            items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                val image = items[index]
                if (image == null) {
                    SkeletonBox(modifier = Modifier.fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(2.dp))
                } else {
                    val isSelected = image.id in selected
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .combinedClickable(
                                onClick = { if (selecting) viewModel.toggle(image.id) else openPhoto(context, image.id) },
                                onLongClick = { viewModel.toggle(image.id) }
                            )
                    ) {
                        PhotoThumb(
                            mediaId = image.id,
                            sizePx = 320,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(2.dp),
                            description = "Photo"
                        )
                        if (isSelected) Box(modifier = Modifier.fillMaxSize().border(3.dp, MaterialTheme.colorScheme.primary))
                        if (selecting) {
                            SelectMark(
                                selected = isSelected,
                                description = if (isSelected) "Selected photo" else "Photo not selected",
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                            )
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    val mode = picker
    if (mode != null) {
        FolderPickerSheet(
            title = if (mode == PickerMode.Copy) "Copy to a folder" else "Move to a folder",
            folders = viewModel.folderList(),
            excludeKey = viewModel.key,
            onPick = { target ->
                picker = null
                if (mode == PickerMode.Copy) viewModel.copySelected(target) else viewModel.moveSelected(target)
            },
            onCreate = { name ->
                picker = null
                viewModel.createFolderAnd(name) { target ->
                    if (mode == PickerMode.Copy) viewModel.copySelected(target) else viewModel.moveSelected(target)
                }
            },
            onDismiss = { picker = null }
        )
    }
    if (renaming) {
        RenameDialog(
            initial = folder?.name.orEmpty(),
            title = "Rename folder",
            label = "Folder name",
            onDismiss = { renaming = false },
            onSave = {
                renaming = false
                viewModel.renameFolder(it)
            }
        )
    }
    if (confirmDelete) {
        val current = folder
        ConfirmDialog(
            title = "Delete ${current?.name ?: "this folder"}?",
            body = if (current?.virtual == true) "The empty folder is removed."
            else "${photosLabel(current?.count ?: 0)} move to the trash. You can bring them back from the trash for 30 days, or press Undo right after.",
            confirm = "Delete folder",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.deleteFolder()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}
