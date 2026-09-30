package com.example.livora.ui.people

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.livora.ui.components.TopBar

@Composable
fun FolderDetailScreen(
    viewModel: FolderDetailViewModel,
    onBack: () -> Unit,
    onOpenPhoto: (Long) -> Unit,
    onUseAsReferences: () -> Unit
) {
    val folder by viewModel.folder.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val left by viewModel.left.collectAsState()
    val images by viewModel.images.collectAsState()
    val rows by viewModel.rows.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<PickerMode?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    val pick = viewModel.pickMode
    val all = viewModel.isAll
    val title = if (all) "All photos" else folder?.name ?: "Folder"
    val count = if (all) images.size else folder?.count ?: images.size
    val canAdd = !all && folder != null && com.example.livora.data.people.media.MediaFolders.isWritableTarget(folder?.relativePath.orEmpty())

    BackHandler(enabled = selecting) { viewModel.clearSelection() }
    LaunchedEffect(left) { if (left) onBack() }

    ConsentEffect(viewModel.consent)
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
                        if (!pick) {
                            LinkButton(text = "All", onClick = { viewModel.selectAll() })
                            LinkButton(
                                text = "Use as reference photos",
                                onClick = { if (viewModel.useAsReferences()) onUseAsReferences() },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                )
            } else {
                TopBar(
                    title = title,
                    subtitle = when {
                        pick -> "Tap the photos of the person"
                        folder?.virtual == true -> "Empty folder"
                        else -> photosLabel(count)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    actions = {
                        if (canAdd && !pick) {
                            IconButton(onClick = { pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                Icon(Icons.Default.Add, contentDescription = "Add photos to this folder", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        if (!all && !pick) {
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
            if (pick) {
                PickPersonBar(
                    count = selected.size,
                    onUse = { if (viewModel.useAsReferences()) onUseAsReferences() },
                    modifier = Modifier.navigationBarsPadding()
                )
            } else if (selecting) {
                PhotoSelectionBar(
                    canModify = viewModel.supportsConsent,
                    onCopy = { picker = PickerMode.Copy },
                    onMove = { picker = PickerMode.Move },
                    onDelete = { viewModel.trashSelected() },
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }
    ) { innerPadding ->
        GalleryGrid(
            rows = rows,
            selected = selected,
            onOpen = { if (pick) viewModel.toggle(it) else onOpenPhoto(it) },
            onToggle = { viewModel.toggle(it) },
            onToggleGroup = { viewModel.toggleGroup(it) },
            modifier = Modifier.padding(innerPadding),
            emptyContent = {
                EmptyBlock(
                    title = if (folder?.virtual == true) "This folder is empty" else "No photos here",
                    body = if (folder?.virtual == true) "It shows up in your gallery as soon as the first photo is added." else "Photos you add or take will appear here.",
                    actionLabel = if (canAdd) "Add photos" else null,
                    onAction = if (canAdd) ({ pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) else null
                )
            }
        )
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
