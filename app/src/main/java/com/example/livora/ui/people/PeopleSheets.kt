package com.example.livora.ui.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.people.db.PersonSummary
import com.example.livora.data.people.media.FolderInfo
import com.example.livora.data.people.media.MediaFolders

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    title: String,
    folders: List<FolderInfo>,
    onPick: (FolderInfo) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    excludeKey: String? = null,
    pickOnly: Boolean = false
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var creating by remember { mutableStateOf(false) }
    val targets = folders.filter {
        it.key != excludeKey && if (pickOnly) !it.virtual else MediaFolders.isWritableTarget(it.relativePath)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.navigationBarsPadding().heightIn(max = 560.dp)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            if (!pickOnly) {
                Text(
                    text = "Only folders inside Pictures or DCIM can receive photos.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .clickable { creating = true }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "New folder",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(targets, key = { it.key }) { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 64.dp)
                            .clickable { onPick(folder) }
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (folder.coverId != null) {
                            PhotoThumb(
                                mediaId = folder.coverId,
                                sizePx = 128,
                                modifier = Modifier.size(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                description = "Cover photo of ${folder.name}"
                            )
                        } else {
                            Spacer(modifier = Modifier.size(48.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = folder.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (folder.virtual) "Empty" else photosLabel(folder.count),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 82.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
    if (creating) {
        RenameDialog(
            initial = "",
            title = "New folder",
            label = "Folder name",
            onDismiss = { creating = false },
            onSave = {
                creating = false
                onCreate(it)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonPickerSheet(
    title: String,
    people: List<PersonSummary>,
    onPick: (PersonSummary) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.navigationBarsPadding().heightIn(max = 560.dp)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            if (people.isEmpty()) {
                Text(
                    text = "There is nobody else to choose from yet.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
            LazyColumn {
                items(people, key = { it.id }) { person ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 64.dp)
                            .clickable { onPick(person) }
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FaceAvatar(
                            faceId = person.coverFaceId,
                            referenceId = person.coverRefId,
                            size = 48.dp,
                            description = person.name?.let { "Face of $it" } ?: "Face of an unnamed person"
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = person.name ?: "Unnamed person",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = photosLabel(person.photoCount),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 82.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NamePersonSheet(
    initial: String,
    people: List<PersonSummary>,
    onPick: (PersonSummary) -> Unit,
    onNew: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf(initial) }
    val typed = text.trim()
    val exact = people.firstOrNull { it.name.equals(typed, ignoreCase = true) }
    val matches = if (typed.isEmpty()) people else people.filter { it.name?.contains(typed, ignoreCase = true) == true }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.navigationBarsPadding().imePadding().heightIn(max = 560.dp)) {
            Text(
                text = "Who is this?",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(60) },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryAction(
                text = if (exact != null) "Add to ${exact.name}" else "Save name",
                onClick = { if (exact != null) onPick(exact) else onNew(typed) },
                enabled = typed.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
            if (matches.isNotEmpty()) {
                Text(
                    text = "Or choose someone you already named",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
                )
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(matches, key = { it.id }) { person ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 64.dp)
                                .clickable { onPick(person) }
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FaceAvatar(
                                faceId = person.coverFaceId,
                                referenceId = person.coverRefId,
                                size = 48.dp,
                                description = person.name?.let { "Face of $it" }
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = person.name.orEmpty(),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = photosLabel(person.photoCount),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(start = 82.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}
