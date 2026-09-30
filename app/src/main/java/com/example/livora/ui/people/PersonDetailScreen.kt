package com.example.livora.ui.people

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.livora.data.people.FoldersState
import com.example.livora.data.people.db.LinkMode
import com.example.livora.data.people.db.PersonKind
import com.example.livora.data.people.media.MediaImages
import com.example.livora.ui.components.Toaster
import com.example.livora.ui.components.TopBar
import kotlinx.coroutines.launch

fun openPhoto(context: Context, mediaId: Long) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(MediaImages.uri(mediaId), "image/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toaster.error("No app on this phone can open photos")
    } catch (e: SecurityException) {
        Toaster.error("This photo is no longer available")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonDetailScreen(
    viewModel: PersonDetailViewModel,
    foldersViewModel: FoldersViewModel,
    onBack: () -> Unit,
    onOpenSuggestions: () -> Unit,
    onOpenRecognition: () -> Unit,
    onAddReferences: () -> Unit
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val person by viewModel.person.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val photoCount by viewModel.photoCount.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val matchCount by viewModel.matchCount.collectAsState()
    val others by viewModel.others.collectAsState()
    val left by viewModel.left.collectAsState()
    val foldersState by foldersViewModel.state.collectAsState()
    val items = viewModel.photos.collectAsLazyPagingItems()
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var mergePicker by remember { mutableStateOf(false) }
    var folderPicker by remember { mutableStateOf<FolderTarget?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()

    BackHandler(enabled = selecting) { viewModel.clearSelection() }
    LaunchedEffect(left) { if (left) onBack() }
    LaunchedEffect(Unit) { foldersViewModel.refresh() }

    val current = person
    val title = current?.name ?: "Unnamed person"

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
                            text = "Select all loaded",
                            onClick = {
                                val ids = (0 until items.itemCount).mapNotNull { items.peek(it)?.mediaId }
                                viewModel.selectAll(ids)
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                )
            } else {
                TopBar(
                    title = title,
                    subtitle = photosLabel(photoCount),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    actions = {
                        Column {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text(if (current?.name == null) "Add name" else "Rename") }, onClick = {
                                    menuOpen = false
                                    renaming = true
                                })
                                DropdownMenuItem(text = { Text("Same person as another group") }, onClick = {
                                    menuOpen = false
                                    mergePicker = true
                                })
                                DropdownMenuItem(text = { Text("Copy all photos to a folder") }, onClick = {
                                    menuOpen = false
                                    folderPicker = FolderTarget.All
                                })
                                DropdownMenuItem(text = { Text("Recognition and linked folder") }, onClick = {
                                    menuOpen = false
                                    onOpenRecognition()
                                })
                                DropdownMenuItem(text = { Text("Add reference photos") }, onClick = {
                                    menuOpen = false
                                    onAddReferences()
                                })
                                DropdownMenuItem(
                                    text = { Text(if (current?.hidden == true) "Show this person" else "Hide this person") },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.setHidden(current?.hidden != true)
                                    }
                                )
                                if (current != null && (current.kind == PersonKind.ENROLLED || current.name != null)) {
                                    DropdownMenuItem(text = { Text("Delete this person") }, onClick = {
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlineAction(text = "Not this person", onClick = { viewModel.notThisPerson() }, modifier = Modifier.weight(1f))
                        PrimaryAction(text = "Copy to folder", onClick = { folderPicker = FolderTarget.Selected }, modifier = Modifier.weight(1f))
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
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                DetailHeader(
                    name = current?.name,
                    summary = summary,
                    linkedFolder = current?.linkedFolderName,
                    linkMode = current?.linkMode ?: LinkMode.NONE,
                    matchCount = matchCount,
                    onRename = { renaming = true },
                    onOpenSuggestions = onOpenSuggestions,
                    onOpenRecognition = onOpenRecognition
                )
            }
            val refreshing = items.loadState.refresh is LoadState.Loading
            if (refreshing && items.itemCount == 0) {
                items(15, span = { GridItemSpan(1) }) {
                    com.example.livora.ui.components.SkeletonBox(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        shape = RoundedCornerShape(2.dp)
                    )
                }
            }
            if (!refreshing && items.itemCount == 0 && items.loadState.refresh !is LoadState.Error) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    EmptyBlock(
                        title = "No photos here yet",
                        body = if ((matchCount ?: 0) > 0) "Livora found possible matches. Review them to add photos to this person." else "Photos appear here as soon as they are confirmed for this person.",
                        actionLabel = if ((matchCount ?: 0) > 0) "Review matches" else null,
                        onAction = if ((matchCount ?: 0) > 0) onOpenSuggestions else null
                    )
                }
            }
            val refreshError = items.loadState.refresh as? LoadState.Error
            if (refreshError != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "error") {
                    EmptyBlock(
                        title = "Photos could not be loaded",
                        body = "Something went wrong while reading this person. Try again.",
                        actionLabel = "Try again",
                        onAction = { items.retry() }
                    )
                }
            }
            items(
                count = items.itemCount,
                key = items.itemKey { it.mediaId }
            ) { index ->
                val row = items[index]
                if (row == null) {
                    com.example.livora.ui.components.SkeletonBox(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        shape = RoundedCornerShape(2.dp)
                    )
                } else {
                    val isSelected = row.mediaId in selected
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .combinedClickable(
                                onClick = { if (selecting) viewModel.toggle(row.mediaId) else openPhoto(context, row.mediaId) },
                                onLongClick = { viewModel.toggle(row.mediaId) }
                            )
                    ) {
                        FaceTile(
                            faceId = row.faceId,
                            mediaId = row.mediaId,
                            left = row.boxLeft,
                            top = row.boxTop,
                            right = row.boxRight,
                            bottom = row.boxBottom,
                            orientation = row.orientation,
                            sizePx = 320,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(2.dp)
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(3.dp, MaterialTheme.colorScheme.primary)
                            )
                        }
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
            item(span = { GridItemSpan(maxLineSpan) }, key = "end") { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (renaming) {
        NamePersonSheet(
            initial = current?.name.orEmpty(),
            people = others.filter { it.name != null },
            onPick = {
                renaming = false
                viewModel.mergeInto(it.id, it.name)
            },
            onNew = {
                renaming = false
                viewModel.rename(it)
            },
            onDismiss = { renaming = false }
        )
    }
    if (mergePicker) {
        PersonPickerSheet(
            title = "Same person as",
            people = others,
            onPick = {
                mergePicker = false
                viewModel.mergeInto(it.id, it.name)
            },
            onDismiss = { mergePicker = false }
        )
    }
    val target = folderPicker
    if (target != null) {
        val folders = (foldersState as? FoldersState.Ready)?.folders.orEmpty()
        FolderPickerSheet(
            title = "Copy to a folder",
            folders = folders,
            onPick = { folder ->
                folderPicker = null
                if (target == FolderTarget.All) {
                    viewModel.copyAllToFolder(folder.relativePath, folder.name)
                } else {
                    viewModel.copyToFolder(selected.toList(), folder.relativePath, folder.name)
                }
            },
            onCreate = { name ->
                folderPicker = null
                scope.launch {
                    val folder = viewModel.createFolder(name)
                    if (folder == null) {
                        Toaster.error("Enter a folder name")
                    } else if (target == FolderTarget.All) {
                        viewModel.copyAllToFolder(folder.relativePath, folder.name)
                    } else {
                        viewModel.copyToFolder(selected.toList(), folder.relativePath, folder.name)
                    }
                }
            },
            onDismiss = { folderPicker = null }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${current?.name ?: "this person"}?") },
            text = {
                Text("Your photos stay on this phone. The name, reference photos and folder link of this person are removed for good.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deletePerson()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

enum class FolderTarget { All, Selected }

@Composable
private fun DetailHeader(
    name: String?,
    summary: com.example.livora.data.people.db.PersonSummary?,
    linkedFolder: String?,
    linkMode: Int,
    matchCount: Int?,
    onRename: () -> Unit,
    onOpenSuggestions: () -> Unit,
    onOpenRecognition: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FaceAvatar(
                faceId = summary?.coverFaceId,
                referenceId = summary?.coverRefId,
                size = 72.dp,
                description = name?.let { "Face of $it" } ?: "Face of an unnamed person"
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (name != null) {
                    Text(text = name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                } else {
                    LinkButton(text = "Add name", onClick = onRename, emphasis = true)
                }
                val link = when {
                    linkedFolder == null -> "Not linked to a folder"
                    linkMode == LinkMode.AUTO -> "New photos are added to $linkedFolder"
                    else -> "Matches are reviewed before they go to $linkedFolder"
                }
                Text(text = link, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinkButton(text = "Recognition and folder", onClick = onOpenRecognition)
            }
        }
        if (matchCount != null && matchCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
                    .combinedClickableNoRipple(onOpenSuggestions)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (matchCount == 1) "1 possible match to review" else "${formatCount(matchCount)} possible matches to review",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(text = "Review", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        Text(
            text = "Press and hold a photo to select. Photos open in your gallery app.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.combinedClickable(onClick = onClick))
