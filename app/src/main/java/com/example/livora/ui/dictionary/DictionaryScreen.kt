package com.example.livora.ui.dictionary

import androidx.compose.foundation.border
import com.example.livora.ui.components.AppButton
import com.example.livora.ui.components.ButtonKind
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import com.example.livora.ui.components.FormSection
import com.example.livora.ui.components.FormSheet
import com.example.livora.ui.components.FormTextField
import com.example.livora.ui.components.Motion
import com.example.livora.ui.components.SegmentedControl
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.dictionary.ClozeBuilder
import com.example.livora.data.model.ClozeLevel
import com.example.livora.data.model.DictionaryEntry
import com.example.livora.data.model.EntryCategory
import com.example.livora.data.model.QuizMode
import com.example.livora.data.model.SynonymInput
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.SkeletonBox
import com.example.livora.ui.components.SkeletonLine
import com.example.livora.ui.components.Tag
import com.example.livora.ui.components.TopBar

private enum class ListFilter(val label: String) {
    All("All"),
    Vocabulary("Vocabulary"),
    Writing("IELTS writing")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    viewModel: DictionaryViewModel,
    onOpenQuiz: () -> Unit,
    addRequests: Flow<Unit>,
    quizRequests: Flow<Unit>
) {
    val entries by viewModel.entries.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var isAdding by rememberSaveable { mutableStateOf(false) }
    var showQuizChooser by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(ListFilter.All) }
    val hasWriting = entries.any { it.category == EntryCategory.Writing }
    val activeFilter = if (hasWriting) filter else ListFilter.All
    val visible = when (activeFilter) {
        ListFilter.All -> entries
        ListFilter.Vocabulary -> entries.filter { it.category == EntryCategory.Vocabulary }
        ListFilter.Writing -> entries.filter { it.category == EntryCategory.Writing }
    }

    LaunchedEffect(addRequests) {
        addRequests.collect {
            viewModel.clearLookup()
            isAdding = true
        }
    }
    LaunchedEffect(quizRequests) {
        quizRequests.collect { if (viewModel.canQuiz()) showQuizChooser = true }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        if (hasWriting) {
            item {
                SegmentedControl(
                    options = ListFilter.entries.toList(),
                    selected = activeFilter,
                    label = { it.label },
                    onSelect = { filter = it },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        if (isLoading && entries.isEmpty()) {
            items(6) { DictionaryRowSkeleton() }
        }

        if (entries.isEmpty() && !isLoading) {
            item { EmptyState(onAdd = { isAdding = true }) }
        }

        if (entries.isNotEmpty() && visible.isEmpty()) {
            item {
                Text(
                    text = "Nothing in this section yet.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 24.dp)
                )
            }
        }

        itemsIndexed(visible, key = { _, it -> it.id }) { index, entry ->
            Column(modifier = Modifier.animateItem()) {
            EntryRow(
                entry = entry,
                onDelete = { viewModel.deleteEntry(entry) }
            )
            if (index < visible.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                    thickness = 0.5.dp
                )
            }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    if (isAdding) {
        AddWordSheet(
            viewModel = viewModel,
            onDismiss = { isAdding = false }
        )
    }

    if (showQuizChooser) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showQuizChooser = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            QuizChooser(
                totalWords = viewModel.vocabularyCount(),
                hardestWords = viewModel.hardestCount(),
                writingWords = viewModel.writingCount(),
                onPick = { mode, input ->
                    viewModel.startQuiz(mode, input)
                    showQuizChooser = false
                    onOpenQuiz()
                }
            )
        }
    }
}

@Composable
private fun QuizChooser(
    totalWords: Int,
    hardestWords: Int,
    writingWords: Int,
    onPick: (QuizMode, SynonymInput) -> Unit
) {
    var writingOpen by rememberSaveable { mutableStateOf(false) }
    val sentenceCount = ClozeBuilder.count(ClozeLevel.Sentence)
    val paragraphCount = ClozeBuilder.count(ClozeLevel.Paragraph)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 28.dp)
    ) {
        Text(
            text = "Start a quiz",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        FormSection(label = "Vocabulary", topGap = 14.dp) {
            QuizModeCard(
                title = "All words",
                description = "$totalWords words · random order",
                enabled = totalWords >= 2,
                onClick = { onPick(QuizMode.All, SynonymInput.Click) }
            )
            Spacer(modifier = Modifier.height(4.dp))
            QuizModeCard(
                title = "Hardest first",
                description = if (hardestWords > 0)
                    "$hardestWords words you miss most"
                else
                    "No mistakes yet · uses all words",
                enabled = totalWords >= 2,
                onClick = { onPick(QuizMode.Hardest, SynonymInput.Click) }
            )
        }
        FormSection(label = "IELTS writing", topGap = 20.dp) {
            QuizModeCard(
                title = "Writing upgrades",
                description = if (writingWords > 0)
                    "$writingWords words · tap to choose click or write"
                else
                    "No writing words yet",
                enabled = writingWords >= 1,
                onClick = { writingOpen = !writingOpen }
            )
            AnimatedVisibility(
                visible = writingOpen && writingWords >= 1,
                enter = expandVertically(Motion.enter()) + fadeIn(Motion.enter()),
                exit = shrinkVertically(Motion.exit()) + fadeOut(Motion.exit())
            ) {
                Column(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                    Text(
                        text = "How do you want to answer the synonyms?",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(
                            text = "Click",
                            onClick = { onPick(QuizMode.Writing, SynonymInput.Click) },
                            kind = ButtonKind.Primary,
                            modifier = Modifier.weight(1f)
                        )
                        AppButton(
                            text = "Write",
                            onClick = { onPick(QuizMode.Writing, SynonymInput.Write) },
                            kind = ButtonKind.Primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Click picks the synonyms from many options. Write types them one by one and tells you how many to find.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            QuizModeCard(
                title = "Sentence drag",
                description = "$sentenceCount sentences · drag the missing word into the blank",
                enabled = sentenceCount >= 1,
                onClick = { onPick(QuizMode.Sentence, SynonymInput.Click) }
            )
            QuizModeCard(
                title = "Paragraph drag",
                description = "$paragraphCount paragraphs · drag many words so the paragraph is complete",
                enabled = paragraphCount >= 1,
                onClick = { onPick(QuizMode.Paragraph, SynonymInput.Click) }
            )
        }
    }
}

@Composable
private fun QuizModeCard(
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = Design.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Design.cardElevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Design.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun AddWordSheet(
    viewModel: DictionaryViewModel,
    onDismiss: () -> Unit
) {
    val lookupInProgress by viewModel.lookupInProgress.collectAsState()
    val lookupResult by viewModel.lookupResult.collectAsState()
    val suggestion by viewModel.suggestion.collectAsState()
    var word by remember { mutableStateOf("") }
    var translation by remember { mutableStateOf("") }
    var synonyms by remember { mutableStateOf("") }
    var example by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(EntryCategory.Vocabulary) }
    var shownSuggestion by remember { mutableStateOf("") }
    val wordFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(Motion.Medium.toLong())
        wordFocus.requestFocus()
    }

    LaunchedEffect(suggestion) {
        suggestion?.let { shownSuggestion = it }
    }

    LaunchedEffect(lookupResult) {
        val result = lookupResult
        if (result != null) {
            if (result.translation.isNotBlank()) translation = result.translation
            if (result.synonyms.isNotEmpty()) synonyms = result.synonyms.joinToString(", ")
            if (result.example.isNotBlank()) example = result.example
        }
    }

    FormSheet(
        title = "New word",
        confirmLabel = "Add word",
        confirmEnabled = word.isNotBlank() && translation.isNotBlank(),
        onDismiss = {
            viewModel.clearLookup()
            onDismiss()
        },
        onConfirm = {
            val synonymList = synonyms.split(",").map { it.trim() }.filter { it.isNotBlank() }
            viewModel.saveEntry(word, translation, synonymList, example, category)
        }
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            FormTextField(
                value = word,
                onValueChange = {
                    word = it
                    viewModel.dismissSuggestion()
                },
                label = "Word",
                placeholder = "English word",
                modifier = Modifier.weight(1f),
                focusRequester = wordFocus,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { if (word.isNotBlank() && !lookupInProgress) viewModel.lookup(word) }
                )
            )
            Box(
                modifier = Modifier.defaultMinSize(minWidth = 88.dp, minHeight = 52.dp),
                contentAlignment = Alignment.Center
            ) {
                if (lookupInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    AppButton(
                        text = "Look up",
                        onClick = { viewModel.lookup(word) },
                        enabled = word.isNotBlank(),
                        modifier = Modifier.defaultMinSize(minHeight = 52.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = suggestion != null,
            enter = expandVertically(Motion.enter()) + fadeIn(Motion.enter()),
            exit = shrinkVertically(Motion.exit()) + fadeOut(Motion.exit())
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "Did you mean",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = suggestion ?: shownSuggestion,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppButton(
                        text = "Fix and look up",
                        onClick = {
                            val fixed = suggestion ?: shownSuggestion
                            word = fixed
                            viewModel.performLookup(fixed)
                        },
                        kind = ButtonKind.Primary,
                        modifier = Modifier.weight(1f)
                    )
                    AppButton(
                        text = "Keep mine",
                        onClick = {
                            viewModel.dismissSuggestion()
                            viewModel.performLookup(word)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = lookupResult?.isEmpty == true,
            enter = expandVertically(Motion.enter()) + fadeIn(Motion.enter()),
            exit = shrinkVertically(Motion.exit()) + fadeOut(Motion.exit())
        ) {
            Text(
                text = "Nothing found. Fill in the fields yourself.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        FormTextField(
            value = translation,
            onValueChange = { translation = it },
            label = "Translation",
            placeholder = "Terjemahan bahasa Indonesia",
            modifier = Modifier.padding(top = 18.dp)
        )

        FormTextField(
            value = synonyms,
            onValueChange = { synonyms = it },
            label = "Synonyms",
            placeholder = "Comma separated, e.g. big, large, huge",
            singleLine = false,
            modifier = Modifier.padding(top = 18.dp)
        )

        FormTextField(
            value = example,
            onValueChange = { example = it },
            label = "Example",
            placeholder = "One short sentence",
            singleLine = false,
            modifier = Modifier.padding(top = 18.dp)
        )

        FormSection(label = "Type") {
            SegmentedControl(
                options = EntryCategory.entries.toList(),
                selected = category,
                label = { if (it == EntryCategory.Writing) "IELTS writing" else "Vocabulary" },
                onSelect = { category = it }
            )
        }
    }
}

@Composable
private fun EntryRow(
    entry: DictionaryEntry,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.word,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (entry.translation.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = entry.translation,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
            if (entry.synonyms.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Similar: " + entry.synonyms.joinToString(", ") { synonym ->
                        entry.translationOf(synonym)?.let { "$synonym ($it)" } ?: synonym
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (entry.example.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "“${entry.example}”",
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            if (entry.attempts > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DifficultyTag(entry = entry)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "✓ ${entry.correctCount}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "✗ ${entry.wrongCount}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Delete ${entry.word}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DifficultyTag(entry: DictionaryEntry) {
    val label = when {
        entry.attempts < 2 -> "New"
        entry.accuracy >= 0.8f -> "Easy"
        entry.accuracy >= 0.5f -> "Medium"
        else -> "Hard"
    }
    Text(
        text = label,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun DictionaryRowSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        SkeletonLine(width = 120.dp, height = 16.dp)
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonLine(width = 170.dp, height = 13.dp)
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) {
                SkeletonBox(
                    modifier = Modifier
                        .width(46.dp)
                        .height(18.dp),
                    shape = RoundedCornerShape(50)
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No words yet",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Add a word, translate it to Indonesian, then quiz yourself.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        )
        Spacer(modifier = Modifier.height(14.dp))
        AppButton(text = "Add word", onClick = onAdd, kind = ButtonKind.Primary)
    }
}
