package com.example.livora.ui.dictionary

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
import com.example.livora.data.model.DictionaryEntry
import com.example.livora.data.model.QuizMode
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.SkeletonBox
import com.example.livora.ui.components.SkeletonLine
import com.example.livora.ui.components.Tag
import com.example.livora.ui.components.TopBar

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

        if (isLoading && entries.isEmpty()) {
            items(6) { DictionaryRowSkeleton() }
        }

        if (entries.isEmpty() && !isLoading) {
            item { EmptyState(onAdd = { isAdding = true }) }
        }

        itemsIndexed(entries, key = { _, it -> it.id }) { index, entry ->
            Column(modifier = Modifier.animateItem()) {
            EntryRow(
                entry = entry,
                onDelete = { viewModel.deleteEntry(entry) }
            )
            if (index < entries.lastIndex) {
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
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                viewModel.clearLookup()
                isAdding = false
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AddWordForm(
                viewModel = viewModel,
                onDone = {
                    viewModel.clearLookup()
                    isAdding = false
                }
            )
        }
    }

    if (showQuizChooser) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showQuizChooser = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            QuizChooser(
                totalWords = entries.count { it.translation.isNotBlank() },
                hardestWords = viewModel.hardestCount(),
                onPick = { mode ->
                    viewModel.startQuiz(mode)
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
    onPick: (QuizMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 28.dp)
    ) {
        Text(
            text = "Start a quiz",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(14.dp))
        QuizModeCard(
            title = "All words",
            description = "$totalWords words · random order",
            enabled = totalWords >= 2,
            onClick = { onPick(QuizMode.All) }
        )
        Spacer(modifier = Modifier.height(10.dp))
        QuizModeCard(
            title = "Hardest first",
            description = if (hardestWords > 0)
                "$hardestWords words you miss most"
            else
                "No mistakes yet · uses all words",
            enabled = totalWords >= 2,
            onClick = { onPick(QuizMode.Hardest) }
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWordForm(
    viewModel: DictionaryViewModel,
    onDone: () -> Unit
) {
    val lookupInProgress by viewModel.lookupInProgress.collectAsState()
    val lookupResult by viewModel.lookupResult.collectAsState()
    val suggestion by viewModel.suggestion.collectAsState()
    var word by remember { mutableStateOf("") }
    var translation by remember { mutableStateOf("") }
    var synonyms by remember { mutableStateOf("") }
    var example by remember { mutableStateOf("") }

    LaunchedEffect(lookupResult) {
        val result = lookupResult
        if (result != null) {
            if (result.translation.isNotBlank()) translation = result.translation
            if (result.synonyms.isNotEmpty()) synonyms = result.synonyms.joinToString(", ")
            if (result.example.isNotBlank()) example = result.example
        }
    }

    val canSave = word.isNotBlank() && translation.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 24.dp)
    ) {
        Text(
            text = "New word",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                FlatTextField(
                    value = word,
                    onValueChange = {
                        word = it
                        viewModel.dismissSuggestion()
                    },
                    placeholder = "English word",
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = { viewModel.lookup(word) },
                enabled = word.isNotBlank() && !lookupInProgress
            ) {
                if (lookupInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = "Look up",
                        fontWeight = FontWeight.SemiBold,
                        color = if (word.isNotBlank())
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
        }

        val pendingSuggestion = suggestion
        if (pendingSuggestion != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Did you mean",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = pendingSuggestion,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(onClick = {
                    word = pendingSuggestion
                    viewModel.performLookup(pendingSuggestion)
                }) {
                    Text(
                        text = "Fix & look up",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(onClick = {
                    viewModel.dismissSuggestion()
                    viewModel.performLookup(word)
                }) {
                    Text(
                        text = "Keep",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        if (lookupResult?.isEmpty == true) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Nothing found. Fill in the fields yourself.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        PropertyLabel(text = "Translation")
        Spacer(modifier = Modifier.height(4.dp))
        FlatTextField(
            value = translation,
            onValueChange = { translation = it },
            placeholder = "Terjemahan bahasa Indonesia",
            singleLine = true
        )

        Spacer(modifier = Modifier.height(14.dp))
        PropertyLabel(text = "Synonyms")
        Spacer(modifier = Modifier.height(4.dp))
        FlatTextField(
            value = synonyms,
            onValueChange = { synonyms = it },
            placeholder = "Comma separated, e.g. big, large, huge",
            singleLine = false
        )

        Spacer(modifier = Modifier.height(14.dp))
        PropertyLabel(text = "Example")
        Spacer(modifier = Modifier.height(4.dp))
        FlatTextField(
            value = example,
            onValueChange = { example = it },
            placeholder = "One short sentence",
            singleLine = false
        )

        Spacer(modifier = Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = {
                viewModel.clearLookup()
                onDone()
            }) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            }
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(
                onClick = {
                    val synonymList = synonyms.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    if (viewModel.addEntry(word, translation, synonymList, example)) {
                        word = ""
                        translation = ""
                        synonyms = ""
                        example = ""
                        onDone()
                    }
                },
                enabled = canSave
            ) {
                Text(
                    text = "Save",
                    fontWeight = FontWeight.SemiBold,
                    color = if (canSave) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
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
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                ) {
                    entry.synonyms.forEach { synonym ->
                        Tag(text = synonym)
                    }
                }
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
                contentDescription = "Delete word",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
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
        Text(
            text = "Add word",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.clickable(onClick = onAdd)
        )
    }
}

@Composable
private fun PropertyLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlatTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                fontSize = 15.sp
            )
        },
        singleLine = singleLine,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
    )
}
