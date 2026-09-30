package com.example.livora.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.people.FoldersState
import com.example.livora.data.people.cluster.PersonMatcher
import com.example.livora.data.people.db.LinkMode
import com.example.livora.ui.components.ChoiceOption
import com.example.livora.ui.components.ChoiceRow
import com.example.livora.ui.components.TopBar

@Composable
fun RecognitionScreen(
    viewModel: RecognitionViewModel,
    onBack: () -> Unit,
    onAddReferences: () -> Unit,
    onOpenSuggestions: () -> Unit
) {
    val person by viewModel.person.collectAsState()
    val references by viewModel.references.collectAsState()
    val set by viewModel.suggestionSet.collectAsState()
    val threshold by viewModel.threshold.collectAsState()
    val foldersState by viewModel.folders.collectAsState()
    var picking by remember { mutableStateOf(false) }

    val current = person
    val matches = set?.atThreshold(threshold)?.size
    val linked = current?.linkedFolderPath != null

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopBar(
                title = "Recognition",
                subtitle = current?.name ?: "Unnamed person",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Title("Reference photos")
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Livora compares every face with these examples and lists the ones that look similar. It does not retrain a neural network, so a few clear photos from different angles work best.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (references.isEmpty()) {
                    Text(
                        text = "No explicit reference photos yet. The faces already in this group are used instead.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = "${references.size} reference ${if (references.size == 1) "photo" else "photos"}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
                        items(references, key = { it.id }) { ref ->
                            FaceAvatar(faceId = ref.faceId, referenceId = if (ref.faceId == null) ref.id else null, size = 56.dp, description = "Reference face")
                        }
                    }
                }
                LinkButton(text = "Add reference photos", onClick = onAddReferences)
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

            Title("Linked folder")
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = if (linked) current?.linkedFolderName.orEmpty() else "No folder, this person is only a virtual album in the app",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (linked) {
                    Text(
                        text = current?.linkedFolderPath?.trimEnd('/').orEmpty(),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LinkButton(text = if (linked) "Choose another folder" else "Choose or create a folder", onClick = { picking = true })
                    if (linked) LinkButton(text = "Unlink", onClick = { viewModel.unlink() })
                }
                if (linked) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ChoiceRow(
                        options = listOf(ChoiceOption(LinkMode.REVIEW, "Review first"), ChoiceOption(LinkMode.AUTO, "Add automatically")),
                        selected = if (current?.linkMode == LinkMode.AUTO) LinkMode.AUTO else LinkMode.REVIEW,
                        enabled = true,
                        onSelect = { viewModel.setMode(it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (current?.linkMode == LinkMode.AUTO)
                            "New photos found by the background scan are copied into the folder without asking. Older matches still wait for your review. Copies use extra storage."
                        else
                            "Matches wait in a review list. You approve them in a batch and only then are they copied into the folder.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

            Title("How close a match must be")
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Higher means fewer but surer matches. The default is careful on purpose.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = threshold,
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
                Text(
                    text = when {
                        set == null -> "Counting matches"
                        matches == 0 -> "No photos match at ${(threshold * 100).toInt()}%"
                        else -> "${formatCount(matches ?: 0)} ${if (matches == 1) "photo matches" else "photos match"} at ${(threshold * 100).toInt()}%"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryAction(
                    text = if ((matches ?: 0) > 0) "Review ${formatCount(matches ?: 0)} matches" else "Review matches",
                    onClick = onOpenSuggestions,
                    enabled = (matches ?: 0) > 0,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (picking) {
        val list = (foldersState as? FoldersState.Ready)?.folders.orEmpty()
        FolderPickerSheet(
            title = "Link a folder",
            folders = list,
            onPick = {
                picking = false
                viewModel.link(it)
            },
            onCreate = {
                picking = false
                viewModel.createAndLink(it)
            },
            onDismiss = { picking = false }
        )
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}
