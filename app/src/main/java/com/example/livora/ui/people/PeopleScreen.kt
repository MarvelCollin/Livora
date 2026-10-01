package com.example.livora.ui.people

import com.example.livora.ui.components.Motion
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.livora.data.people.IndexStatus
import com.example.livora.data.people.db.LinkMode
import com.example.livora.data.people.db.PersonKind
import com.example.livora.data.people.db.PersonSummary
import com.example.livora.data.people.media.AccessLevel
import com.example.livora.data.people.media.MediaAccess
import com.example.livora.data.people.scan.ScanPhase
import com.example.livora.data.people.scan.ScanProgress
import com.example.livora.ui.components.LocalSwipeLock
import com.example.livora.ui.components.TopBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PeopleScreen(
    viewModel: PeopleViewModel,
    foldersViewModel: FoldersViewModel,
    galleryViewModel: FolderDetailViewModel,
    onOpenPhoto: (Long) -> Unit,
    onOpenPerson: (Long) -> Unit,
    onOpenFolder: (String) -> Unit,
    onPickFolder: (String) -> Unit,
    onOpenMerge: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val access by viewModel.access.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val people by viewModel.people.collectAsState()
    val indexedFaces by viewModel.indexedFaces.collectAsState()
    val indexedPhotos by viewModel.indexedPhotos.collectAsState()
    val initialDone by viewModel.initialScanDone.collectAsState()
    val workState by viewModel.workState.collectAsState()
    val showHidden by viewModel.showHidden.collectAsState()
    val showSmall by viewModel.showSmall.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val status by viewModel.status.collectAsState()
    val mergeCount by viewModel.mergeSuggestionCount.collectAsState()
    val cloudOffer by viewModel.cloudOffer.collectAsState()
    val cloudState by viewModel.cloudState.collectAsState()
    val minPhotos by viewModel.minPhotos.collectAsState()
    val photoSelected by galleryViewModel.selected.collectAsState()
    val photoRows by galleryViewModel.rows.collectAsState()
    val photos by galleryViewModel.images.collectAsState()
    val aiLabels by galleryViewModel.aiLabels.collectAsState()
    val segmentPager = rememberPagerState { 3 }
    val segment = segmentPager.currentPage
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<PersonSummary?>(null) }
    var creatingFolder by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<PickerMode?>(null) }
    var choosingFolder by remember { mutableStateOf(false) }
    var movingPerson by remember { mutableStateOf<PersonSummary?>(null) }
    var changingPerson by remember { mutableStateOf<PersonSummary?>(null) }
    val selecting = selected.isNotEmpty()
    val selectingPhotos = photoSelected.isNotEmpty() && segment == 0

    BackHandler(enabled = selecting) { viewModel.clearSelection() }
    BackHandler(enabled = selectingPhotos) { galleryViewModel.clearSelection() }
    ConsentEffect(galleryViewModel.consent)

    val swipeLock = LocalSwipeLock.current
    val locked = selecting || selectingPhotos
    LaunchedEffect(locked) { swipeLock?.value = locked }
    DisposableEffect(Unit) { onDispose { swipeLock?.value = false } }
    LaunchedEffect(segment) {
        viewModel.clearSelection()
        galleryViewModel.clearSelection()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAccess()
                foldersViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.markNotificationAsked()
        viewModel.startScan()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refreshAccess()
        foldersViewModel.refresh()
    }

    fun requestAccess() {
        permissionLauncher.launch(MediaAccess.requestPermissions())
    }

    fun openSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun startScanWithPrompt() {
        if (Build.VERSION.SDK_INT >= 33 && !viewModel.notificationAsked) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.startScan()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (selectingPhotos) {
                TopBar(
                    title = "${photoSelected.size} selected",
                    navigationIcon = {
                        IconButton(onClick = { galleryViewModel.clearSelection() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear selection",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        LinkButton(
                            text = "All",
                            onClick = { galleryViewModel.selectAll() },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                )
            } else if (selecting) {
                TopBar(
                    title = "${selected.size} selected",
                    subtitle = if (selected.size < 2) "Pick at least two to merge" else null,
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear selection",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        LinkButton(text = "Hide", onClick = { viewModel.hideSelected() })
                        LinkButton(
                            text = "Same person",
                            onClick = { viewModel.sameSelected() },
                            enabled = selected.size >= 2,
                            emphasis = true,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                )
            } else {
                TopBar(
                    title = "Gallery",
                    subtitle = when (segment) {
                        0 -> if (photoRows == null) null else photosLabel(photos.size)
                        1 -> "Folders on this phone"
                        else -> "Photos stay on this phone and are never uploaded"
                    },
                    actions = {
                        if (access != AccessLevel.None && segment != 0) {
                            IconButton(onClick = { if (segment == 2) choosingFolder = true else creatingFolder = true }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = if (segment == 2) "Check for a person in a folder" else "New folder",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (access != AccessLevel.None && segment == 2) {
                            Column {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More options",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    val ready = people as? PeopleListState.Ready
                                    DropdownMenuItem(
                                        text = { Text(if (showSmall) "Hide small groups" else "Show small groups (${ready?.smallCount ?: 0})") },
                                        onClick = {
                                            menuOpen = false
                                            viewModel.toggleSmall()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (showHidden) "Hide hidden people" else "Show hidden people (${ready?.hiddenCount ?: 0})") },
                                        onClick = {
                                            menuOpen = false
                                            viewModel.toggleHidden()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Grouping and data") },
                                        onClick = {
                                            menuOpen = false
                                            onOpenSettings()
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (selectingPhotos) {
                PhotoSelectionBar(
                    canModify = galleryViewModel.supportsConsent,
                    onCopy = { picker = PickerMode.Copy },
                    onMove = { picker = PickerMode.Move },
                    onDelete = { galleryViewModel.trashSelected() }
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (access == AccessLevel.None) {
                EmptyBlock(
                    title = "Allow access to your photos",
                    body = "Livora shows your photos so you can move or delete them, and finds the people in them. It reads photos on this phone only and never uploads them. The face data is saved encrypted to your cloud backup so it survives a reinstall.",
                    actionLabel = "Allow photo access",
                    onAction = { requestAccess() }
                )
            } else {
                SegmentTabs(
                    labels = listOf("Photos", "Albums", "People"),
                    selected = segment,
                    onSelect = { scope.launch { segmentPager.animateScrollToPage(it) } }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                HorizontalPager(
                    state = segmentPager,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    userScrollEnabled = !locked,
                    key = { it }
                ) { page ->
                if (page == 0) {
                    GalleryGrid(
                        rows = photoRows,
                        selected = photoSelected,
                        onOpen = onOpenPhoto,
                        onToggle = { galleryViewModel.toggle(it) },
                        onToggleGroup = { galleryViewModel.toggleGroup(it) },
                        aiLabels = aiLabels,
                        topContent = {
                            if (access == AccessLevel.Partial) PartialAccessNotice(onOpenSettings = { openSettings() })
                        },
                        emptyContent = {
                            EmptyBlock(
                                title = "No photos on this phone yet",
                                body = "Photos you take or save will show up here."
                            )
                        }
                    )
                } else if (page == 2) {
                    Column(modifier = Modifier.fillMaxSize()) {
                    CloudRestoreBanner(
                        offer = cloudOffer,
                        state = cloudState,
                        onRestore = { viewModel.restoreFromCloud() },
                        onStartFresh = { viewModel.startFresh() }
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    PeopleList(
                        access = access,
                        people = people,
                        progress = progress,
                        status = status,
                        indexedFaces = indexedFaces,
                        indexedPhotos = indexedPhotos,
                        initialDone = initialDone,
                        workState = workState,
                        showSmall = showSmall,
                        minPhotos = minPhotos,
                        selected = selected,
                        mergeCount = mergeCount,
                        onOpenPerson = onOpenPerson,
                        onToggle = { viewModel.toggleSelect(it) },
                        onRename = { renaming = it },
                        onMoveAll = { person ->
                            if (person.linkedFolderName != null) viewModel.moveToLinked(person) else movingPerson = person
                        },
                        onChangeFolder = { changingPerson = it },
                        onStart = { startScanWithPrompt() },
                        onAllowAll = { openSettings() },
                        onAddPerson = { choosingFolder = true },
                        onToggleSmall = { viewModel.toggleSmall() },
                        onOpenMerge = onOpenMerge
                    )
                    }
                    }
                } else {
                    FoldersContent(
                        viewModel = foldersViewModel,
                        access = access,
                        onOpenFolder = onOpenFolder,
                        onAllowAll = { openSettings() },
                        creating = creatingFolder,
                        onCreatingChange = { creatingFolder = it }
                    )
                }
                }
            }
        }
    }

    val mover = movingPerson
    if (mover != null) {
        FolderPickerSheet(
            title = "Move photos of ${mover.name.orEmpty()} to",
            folders = galleryViewModel.folderList(),
            onPick = { folder ->
                movingPerson = null
                viewModel.moveTo(mover, folder)
            },
            onCreate = { folderName ->
                movingPerson = null
                galleryViewModel.createFolderAnd(folderName) { folder -> viewModel.moveTo(mover, folder) }
            },
            onDismiss = { movingPerson = null }
        )
    }
    val changer = changingPerson
    if (changer != null) {
        FolderPickerSheet(
            title = "Change the folder for ${changer.name.orEmpty()}",
            folders = galleryViewModel.folderList(),
            onPick = { folder ->
                changingPerson = null
                viewModel.changeFolder(changer, folder)
            },
            onCreate = { folderName ->
                changingPerson = null
                galleryViewModel.createFolderAnd(folderName) { folder -> viewModel.changeFolder(changer, folder) }
            },
            onDismiss = { changingPerson = null }
        )
    }

    if (choosingFolder) {
        FolderPickerSheet(
            title = "Choose the folder to look in",
            folders = galleryViewModel.folderList(),
            pickOnly = true,
            onPick = { folder ->
                choosingFolder = false
                onPickFolder(folder.key)
            },
            onCreate = {},
            onDismiss = { choosingFolder = false }
        )
    }

    val mode = picker
    if (mode != null) {
        FolderPickerSheet(
            title = if (mode == PickerMode.Copy) "Copy to a folder" else "Move to a folder",
            folders = galleryViewModel.folderList(),
            onPick = { folder ->
                picker = null
                if (mode == PickerMode.Copy) galleryViewModel.copySelected(folder) else galleryViewModel.moveSelected(folder)
            },
            onCreate = { name ->
                picker = null
                galleryViewModel.createFolderAnd(name) { folder ->
                    if (mode == PickerMode.Copy) galleryViewModel.copySelected(folder) else galleryViewModel.moveSelected(folder)
                }
            },
            onDismiss = { picker = null }
        )
    }

    val target = renaming
    if (target != null) {
        val named = (people as? PeopleListState.Ready)?.people.orEmpty()
            .filter { it.name != null && it.id != target.id }
        NamePersonSheet(
            initial = target.name.orEmpty(),
            people = named,
            onPick = { existing ->
                renaming = null
                viewModel.mergeInto(existing, target)
            },
            onNew = { name ->
                renaming = null
                viewModel.rename(target.id, name)
            },
            onDismiss = { renaming = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PeopleList(
    access: AccessLevel,
    people: PeopleListState,
    progress: ScanProgress,
    status: IndexStatus?,
    indexedFaces: Int,
    indexedPhotos: Int,
    initialDone: Boolean,
    workState: WorkState,
    showSmall: Boolean,
    minPhotos: Int,
    selected: Set<Long>,
    mergeCount: Int,
    onOpenPerson: (Long) -> Unit,
    onToggle: (Long) -> Unit,
    onRename: (PersonSummary) -> Unit,
    onMoveAll: (PersonSummary) -> Unit,
    onChangeFolder: (PersonSummary) -> Unit,
    onStart: () -> Unit,
    onAllowAll: () -> Unit,
    onAddPerson: () -> Unit,
    onToggleSmall: () -> Unit,
    onOpenMerge: () -> Unit
) {
    val scanning = progress.active || workState == WorkState.Running
    val ready = people as? PeopleListState.Ready
    val selecting = selected.isNotEmpty()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (access == AccessLevel.Partial) {
            item(key = "partial") {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = "Livora can only see the photos you picked",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "People outside those photos will be missed. Allow all photos in settings to find everyone.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinkButton(text = "Open settings", onClick = onAllowAll)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
        }
        if (scanning) {
            item(key = "scan") {
                ScanBlock(progress = progress, faceCount = indexedFaces)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
        } else if (workState == WorkState.Queued && !initialDone) {
            item(key = "queued") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = "Waiting to start",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "The scan starts as soon as the phone allows it.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
        } else if (progress.phase == ScanPhase.Failed) {
            item(key = "failed") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = "The scan stopped",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Some photos could not be read. Your results so far are kept.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinkButton(text = "Try again", onClick = onStart)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
        } else if (status != null && status.indexedPhotos > 0) {
            item(key = "status") {
                IndexStatusBlock(status = status, onScanNew = onStart)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
        }
        if (!scanning && mergeCount > 0 && !selecting) {
            item(key = "merge") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .combinedClickable(onClick = onOpenMerge)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (mergeCount == 1) "1 pair might be the same person" else "$mergeCount pairs might be the same person",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Review them side by side and merge",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Review",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
        }

        when {
            ready == null -> items(6, key = { "skeleton$it" }) { PeopleRowSkeleton() }
            ready.people.isEmpty() -> item(key = "empty") {
                PeopleEmptyState(
                    scanning = scanning,
                    indexedPhotos = indexedPhotos,
                    indexedFaces = indexedFaces,
                    smallCount = ready.smallCount,
                    minPhotos = minPhotos,
                    onStart = onStart,
                    onAddPerson = onAddPerson,
                    onShowSmall = onToggleSmall
                )
            }
            else -> {
                item(key = "count") {
                    Text(
                        text = "${ready.people.size} ${if (ready.people.size == 1) "person" else "people"}, most photos first. Press and hold to select.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                    )
                }
                items(ready.people, key = { it.id }) { person ->
                    Column(modifier = Modifier.animateItem()) {
                    PersonRow(
                        person = person,
                        selecting = selecting,
                        isSelected = person.id in selected,
                        onOpen = { if (selecting) onToggle(person.id) else onOpenPerson(person.id) },
                        onSelect = { onToggle(person.id) },
                        onRename = { onRename(person) },
                        onMoveAll = { onMoveAll(person) },
                        onChangeFolder = { onChangeFolder(person) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 86.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp
                    )
                    }
                }
                if (ready.smallCount > 0 && !showSmall) {
                    item(key = "small") {
                        LinkButton(
                            text = "Show ${ready.smallCount} smaller groups with fewer than $minPhotos photos",
                            onClick = onToggleSmall,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
                item(key = "end") { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun IndexStatusBlock(status: IndexStatus, onScanNew: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${formatCount(status.indexedPhotos)} photos indexed, saved on this phone",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (status.lastScanAt > 0) {
                val ago = DateUtils.getRelativeTimeSpanString(
                    status.lastScanAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                ).toString().lowercase()
                val news = if (status.lastNewPhotos == 1) "1 new photo" else "${formatCount(status.lastNewPhotos)} new photos"
                Text(
                    text = "Last scan $ago, $news",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LinkButton(text = "Scan all photos", onClick = onScanNew)
    }
}

@Composable
private fun PeopleEmptyState(
    scanning: Boolean,
    indexedPhotos: Int,
    indexedFaces: Int,
    smallCount: Int,
    minPhotos: Int,
    onStart: () -> Unit,
    onAddPerson: () -> Unit,
    onShowSmall: () -> Unit
) {
    when {
        scanning -> {
            repeat(4) { PeopleRowSkeleton() }
        }
        indexedPhotos == 0 -> EmptyBlock(
            title = "Find someone in your photos",
            body = "Choose a folder, tap a few photos of the person, and Livora looks only inside that folder. Nothing is scanned until you ask.",
            actionLabel = "Choose a folder",
            onAction = onAddPerson,
            secondaryLabel = "Scan every photo instead",
            onSecondary = onStart
        )
        indexedFaces == 0 -> EmptyBlock(
            title = "No faces found",
            body = "Livora checked ${formatCount(indexedPhotos)} photos and found no faces that are large and clear enough. Screenshots are skipped.",
            actionLabel = "Choose another folder",
            onAction = onAddPerson
        )
        smallCount > 0 -> EmptyBlock(
            title = "No one appears in $minPhotos photos yet",
            body = "Livora found $smallCount smaller groups. You can also choose a folder and pick a person's photos yourself.",
            actionLabel = "Show smaller groups",
            onAction = onShowSmall,
            secondaryLabel = "Choose a folder",
            onSecondary = onAddPerson
        )
        else -> EmptyBlock(
            title = "No people yet",
            body = "Choose a folder and pick a few photos of one person. Livora will look for them inside that folder.",
            actionLabel = "Choose a folder",
            onAction = onAddPerson
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonRow(
    person: PersonSummary,
    selecting: Boolean,
    isSelected: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onMoveAll: () -> Unit,
    onChangeFolder: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onOpen, onLongClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FaceAvatar(
            faceId = person.coverFaceId,
            referenceId = person.coverRefId,
            size = 56.dp,
            description = person.name?.let { "Face of $it" } ?: "Face of an unnamed person"
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (person.name != null) {
                Text(
                    text = person.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (!selecting) {
                LinkButton(text = "Add name", onClick = onRename)
            } else {
                Text(
                    text = "Unnamed person",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = buildSubtitle(person),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selecting) {
            SelectMark(selected = isSelected, description = if (isSelected) "Selected" else "Not selected")
        } else if (person.name != null) {
            val target = person.linkedFolderName
            LinkButton(
                text = if (target != null) "Move to $target" else "Move photos",
                onClick = onMoveAll,
                modifier = Modifier.widthIn(max = 150.dp)
            )
            if (target != null) {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Change the folder for ${person.name}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Change folder") },
                            onClick = {
                                menuOpen = false
                                onChangeFolder()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SelectMark(selected: Boolean, description: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    val fill by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        animationSpec = Motion.quick(),
        label = "markFill"
    )
    val line by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = Motion.quick(),
        label = "markLine"
    )
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(shape)
            .background(fill)
            .border(1.5.dp, line, shape),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = 500f)) + fadeIn(Motion.quick()),
            exit = fadeOut(Motion.quick())
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = description,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

private fun buildSubtitle(person: PersonSummary): String {
    val base = if (person.photoCount == 0 && person.kind == PersonKind.ENROLLED) "No photos confirmed yet" else photosLabel(person.photoCount)
    val hidden = if (person.hidden) ", hidden" else ""
    val link = when {
        person.linkedFolderName == null -> ""
        person.linkMode == LinkMode.AUTO -> ", adds to ${person.linkedFolderName}"
        else -> ", linked to ${person.linkedFolderName}"
    }
    return base + hidden + link
}

@Composable
fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    title: String = "Name this person",
    label: String = "Name"
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(60) },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
