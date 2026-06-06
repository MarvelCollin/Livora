package com.example.livora.ui.dictionary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.outlined.Delete
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
import com.example.livora.data.model.DictionaryLanguage
import com.example.livora.ui.components.SkeletonBox
import com.example.livora.ui.components.SkeletonLine
import com.example.livora.ui.components.TopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    viewModel: DictionaryViewModel,
    onOpenQuiz: () -> Unit
) {
    val entries by viewModel.entries.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    var isAdding by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopBar(
                title = "Dictionary",
                subtitle = "Translate to Indonesian & quiz",
                actions = {
                    IconButton(
                        onClick = {
                            if (viewModel.canQuiz()) {
                                viewModel.startQuiz()
                                onOpenQuiz()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Quiz,
                            contentDescription = null,
                            tint = if (viewModel.canQuiz())
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    IconButton(onClick = {
                        viewModel.clearLookup()
                        isAdding = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 4.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                LanguageRow(
                    selected = selectedLanguage,
                    onSelect = { viewModel.selectLanguage(it) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (isLoading && entries.isEmpty()) {
                items(6) { DictionaryRowSkeleton() }
            }

            if (entries.isEmpty() && !isLoading) {
                item { EmptyState(onAdd = { isAdding = true }) }
            }

            itemsIndexed(entries) { index, entry ->
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

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
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
                language = selectedLanguage,
                onDone = {
                    viewModel.clearLookup()
                    isAdding = false
                }
            )
        }
    }
}

@Composable
private fun LanguageRow(
    selected: DictionaryLanguage,
    onSelect: (DictionaryLanguage) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DictionaryLanguage.entries.forEach { language ->
            val isSelected = language == selected
            Text(
                text = language.label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected)
                    MaterialTheme.colorScheme.surface
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier
                    .background(
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(50)
                    )
                    .clickable { onSelect(language) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWordForm(
    viewModel: DictionaryViewModel,
    language: DictionaryLanguage,
    onDone: () -> Unit
) {
    val lookupInProgress by viewModel.lookupInProgress.collectAsState()
    val lookupResult by viewModel.lookupResult.collectAsState()
    val suggestion by viewModel.suggestion.collectAsState()
    var word by remember { mutableStateOf("") }
    var translation by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var descriptionId by remember { mutableStateOf("") }
    var example by remember { mutableStateOf("") }
    var synonyms by remember { mutableStateOf("") }

    LaunchedEffect(lookupResult) {
        val result = lookupResult
        if (result != null) {
            if (result.translation.isNotBlank()) translation = result.translation
            if (result.description.isNotBlank()) description = result.description
            if (result.descriptionId.isNotBlank()) descriptionId = result.descriptionId
            if (result.example.isNotBlank()) example = result.example
            if (result.synonyms.isNotEmpty()) synonyms = result.synonyms.joinToString(", ")
        }
    }

    val canSave = word.isNotBlank() && (translation.isNotBlank() || description.isNotBlank() || descriptionId.isNotBlank())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 24.dp)
    ) {
        Text(
            text = "New word · ${language.label}",
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
                    placeholder = "Word in ${language.label}",
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

        if (lookupResult != null && !lookupResult!!.definitionFound) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "No dictionary entry found. Add your own description below.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        PropertyLabel(text = "Indonesian translation")
        Spacer(modifier = Modifier.height(4.dp))
        FlatTextField(
            value = translation,
            onValueChange = { translation = it },
            placeholder = "Terjemahan",
            singleLine = true
        )

        Spacer(modifier = Modifier.height(14.dp))
        PropertyLabel(text = "Description (${language.label})")
        Spacer(modifier = Modifier.height(4.dp))
        FlatTextField(
            value = description,
            onValueChange = { description = it },
            placeholder = "Meaning in ${language.label}",
            singleLine = false,
            minLines = 2
        )

        Spacer(modifier = Modifier.height(14.dp))
        PropertyLabel(text = "Description (Indonesia)")
        Spacer(modifier = Modifier.height(4.dp))
        FlatTextField(
            value = descriptionId,
            onValueChange = { descriptionId = it },
            placeholder = "Arti dalam bahasa Indonesia",
            singleLine = false,
            minLines = 2
        )

        Spacer(modifier = Modifier.height(14.dp))
        PropertyLabel(text = "Example (${language.label})")
        Spacer(modifier = Modifier.height(4.dp))
        FlatTextField(
            value = example,
            onValueChange = { example = it },
            placeholder = "Contoh kalimat dalam ${language.label}",
            singleLine = false,
            minLines = 2
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
                    if (viewModel.addEntry(word, translation, description, descriptionId, example, synonymList)) {
                        word = ""
                        translation = ""
                        description = ""
                        descriptionId = ""
                        example = ""
                        synonyms = ""
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.word,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = DictionaryLanguage.fromCode(entry.language).label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(50)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            if (entry.translation.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = entry.translation,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
            if (entry.descriptionId.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.descriptionId,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (entry.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = entry.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
            if (entry.example.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "“${entry.example}”",
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
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
                        Text(
                            text = synonym,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                    shape = RoundedCornerShape(50)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
        }
    }
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
