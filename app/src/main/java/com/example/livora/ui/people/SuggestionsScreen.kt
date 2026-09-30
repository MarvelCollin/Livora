package com.example.livora.ui.people

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.people.cluster.PersonMatcher
import com.example.livora.data.people.db.LinkMode
import com.example.livora.ui.components.ChoiceOption
import com.example.livora.ui.components.ChoiceRow
import com.example.livora.ui.components.SkeletonBox
import com.example.livora.ui.components.TopBar

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SuggestionsScreen(viewModel: SuggestionsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val selecting = selected.isNotEmpty()
    val progress by viewModel.progress.collectAsState()
    val faces by viewModel.indexedFaces.collectAsState()
    val moveMatches by viewModel.moveMatches.collectAsState()
    var picking by remember { mutableStateOf(false) }
    var approveAfter by remember { mutableStateOf(false) }

    BackHandler(enabled = selecting) { viewModel.clearSelection() }
    ConsentEffect(viewModel.consent)

    val ready = state as? SuggestionsState.Ready
    val name = ready?.person?.name ?: "this person"
    val folder = ready?.person?.linkedFolderName
    val linked = ready != null && ready.person.linkedFolderPath != null && ready.person.linkMode != LinkMode.NONE
    val moving = moveMatches && viewModel.supportsMove

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopBar(
                title = "Possible matches",
                subtitle = if (ready != null) "For $name" else null,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    if (ready != null && ready.visible.isNotEmpty()) {
                        LinkButton(
                            text = if (selecting) "Clear" else "Select all",
                            onClick = { if (selecting) viewModel.clearSelection() else viewModel.selectAll() },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (selecting) {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlineAction(text = "Not this person", onClick = { viewModel.reject() }, modifier = Modifier.weight(1f))
                        PrimaryAction(
                            text = when {
                                !linked -> "Choose a folder"
                                moving -> "Move ${selected.size}"
                                else -> "Copy ${selected.size}"
                            },
                            onClick = {
                                if (linked) {
                                    viewModel.approve()
                                } else {
                                    approveAfter = true
                                    picking = true
                                }
                            },
                            modifier = Modifier.weight(1f)
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
            if (progress.active) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "scan") {
                    Column {
                        ScanBlock(progress = progress, faceCount = faces)
                        LinkButton(
                            text = "Show what is found so far",
                            onClick = { viewModel.load() },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    }
                }
            }
            when (val current = state) {
                SuggestionsState.Loading -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SkeletonBox(modifier = Modifier.fillMaxWidth().height(14.dp), shape = RoundedCornerShape(7.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            SkeletonBox(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp), shape = RoundedCornerShape(7.dp))
                        }
                    }
                    items(15) {
                        SkeletonBox(modifier = Modifier.fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(2.dp))
                    }
                }
                SuggestionsState.Missing -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyBlock(title = "This person is gone", body = "They were deleted or merged into someone else.", actionLabel = "Back", onAction = onBack)
                }
                is SuggestionsState.Ready -> {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (current.visible.isEmpty()) "No matches at this strictness" else "${formatCount(current.visible.size)} possible photos",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Livora compares faces with the reference photos and lists similar ones. These are guesses, not certainties. Tap to select, press and hold to open a photo.",
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Approved photos go to",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (linked) folder.orEmpty() else "No folder chosen yet",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                LinkButton(
                                    text = if (linked) "Change" else "Choose a folder",
                                    onClick = {
                                        approveAfter = false
                                        picking = true
                                    }
                                )
                            }
                            ChoiceRow(
                                options = listOf(ChoiceOption(true, "Move"), ChoiceOption(false, "Copy")),
                                selected = moving,
                                enabled = viewModel.supportsMove,
                                onSelect = { viewModel.setMoveMatches(it) }
                            )
                            Text(
                                text = when {
                                    !viewModel.supportsMove -> "Moving needs Android 11 or newer, so photos are copied."
                                    moving -> "Moving takes a photo out of the folder it is in now. Undo it right after, or mark it as wrong later when you open the folder."
                                    else -> "Copies use extra storage."
                                },
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            if (!linked) {
                                LinkButton(
                                    text = "Add without a folder",
                                    onClick = { viewModel.approve() },
                                    enabled = selecting
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Match strictness ${(current.threshold * 100).toInt()}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Slider(
                                value = current.threshold,
                                onValueChange = { viewModel.setThreshold(it) },
                                onValueChangeFinished = { viewModel.saveThreshold() },
                                valueRange = PersonMatcher.MIN_THRESHOLD..PersonMatcher.MAX_THRESHOLD,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                modifier = Modifier.semantics { contentDescription = "Match strictness" }
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    }
                    if (current.visible.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                            EmptyBlock(
                                title = "Nothing to review",
                                body = if (current.all.prototypes == 0) "This person has no reference photos yet. Add a few good photos of them to start matching."
                                else "Lower the strictness to see less certain matches, or add more reference photos."
                            )
                        }
                    }
                    items(current.visible, key = { it.faceId }) { suggestion ->
                        val isSelected = suggestion.faceId in selected
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .combinedClickable(
                                    onClick = { viewModel.toggle(suggestion.faceId) },
                                    onLongClick = { openPhoto(context, suggestion.mediaId) }
                                )
                        ) {
                            FaceTile(
                                faceId = suggestion.faceId,
                                mediaId = suggestion.mediaId,
                                left = suggestion.left,
                                top = suggestion.top,
                                right = suggestion.right,
                                bottom = suggestion.bottom,
                                orientation = suggestion.orientation,
                                sizePx = 320,
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(2.dp)
                            )
                            if (isSelected) {
                                Box(modifier = Modifier.fillMaxSize().border(3.dp, MaterialTheme.colorScheme.primary))
                            }
                            Text(
                                text = "${(suggestion.score * 100).toInt().coerceIn(0, 99)}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(2.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
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

    if (picking) {
        FolderPickerSheet(
            title = "Choose the folder for matches",
            folders = viewModel.folderList(),
            onPick = { picked ->
                picking = false
                viewModel.chooseFolder(picked, approveAfter)
            },
            onCreate = { newName ->
                picking = false
                viewModel.createFolderAnd(newName, approveAfter)
            },
            onDismiss = { picking = false }
        )
    }
}
