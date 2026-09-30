package com.example.livora.ui.people

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.people.EnrollFace
import com.example.livora.data.people.ml.FaceIssue
import com.example.livora.ui.components.SkeletonBox
import com.example.livora.ui.components.TopBar

@Composable
fun EnrollScreen(
    viewModel: EnrollViewModel,
    onBack: () -> Unit,
    onDone: (Long) -> Unit
) {
    val items by viewModel.items.collectAsState()
    val name by viewModel.name.collectAsState()
    val personName by viewModel.personName.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val done by viewModel.done.collectAsState()
    val people by viewModel.people.collectAsState()
    val chosen by viewModel.chosen.collectAsState()
    val selectedCount = items.count { it.selectedFace != null }
    val existing = viewModel.personId != null

    LaunchedEffect(done) { done?.let { onDone(it) } }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(EnrollViewModel.MAX_PHOTOS)) { uris ->
        viewModel.addUris(uris)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopBar(
                title = if (existing) "Add reference photos" else "Add a person",
                subtitle = if (existing) personName else "From photos you choose",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding().imePadding()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    PrimaryAction(
                        text = when {
                            selectedCount == 0 -> "Pick a face first"
                            existing -> "Add $selectedCount reference ${if (selectedCount == 1) "photo" else "photos"}"
                            chosen != null -> "Add $selectedCount ${if (selectedCount == 1) "photo" else "photos"} to ${chosen?.name.orEmpty()}"
                            else -> "Save person with $selectedCount ${if (selectedCount == 1) "photo" else "photos"}"
                        },
                        onClick = { viewModel.save() },
                        enabled = selectedCount > 0 && !saving && (existing || chosen != null || name.isNotBlank()),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            item(key = "intro") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = "Choose clear photos of one person. Livora turns each face into a set of numbers and looks for similar faces in your gallery.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "This does not retrain a neural network. It compares faces, so more photos from different angles, light and years give better matches. You can also open a folder, select photos and choose Use as reference photos.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlineAction(
                        text = if (items.isEmpty()) "Choose photos" else "Choose more photos",
                        onClick = { pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
            if (!existing) {
                item(key = "name") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { viewModel.setName(it) },
                            label = { Text("Name") },
                            enabled = chosen == null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                }
                if (people.isNotEmpty()) {
                    item(key = "existing") {
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Text(
                                text = "Or add these photos to someone you already named",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(people, key = { it.id }) { person ->
                                    val picked = chosen?.id == person.id
                                    Column(
                                        modifier = Modifier
                                            .width(72.dp)
                                            .clickable(role = Role.RadioButton) { viewModel.choose(person) },
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        FaceAvatar(
                                            faceId = person.coverFaceId,
                                            referenceId = person.coverRefId,
                                            size = 56.dp,
                                            modifier = Modifier.border(
                                                if (picked) 3.dp else 0.dp,
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(14.dp)
                                            ),
                                            description = "Face of ${person.name.orEmpty()}. ${if (picked) "Selected" else "Not selected"}"
                                        )
                                        Text(
                                            text = person.name.orEmpty(),
                                            fontSize = 12.sp,
                                            color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    }
                }
            }
            if (items.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "No photos chosen yet. Pick a few and tap the right face in each one.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            items(items, key = { it.uri.toString() }) { item ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    val analyzed = item.analyzed
                    when {
                        analyzed == null -> {
                            Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                repeat(3) { SkeletonBox(modifier = Modifier.size(96.dp), shape = RoundedCornerShape(14.dp)) }
                            }
                            Text(
                                text = "Looking for faces",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        analyzed.failed -> Message("This photo could not be opened.", onRemove = { viewModel.remove(item.uri) })
                        analyzed.faces.isEmpty() -> Message(
                            "No face found. Try a photo where the face is bigger and turned to the camera.",
                            onRemove = { viewModel.remove(item.uri) }
                        )
                        else -> {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(
                                    text = if (analyzed.faces.size > 1) "Several faces, tap the right one" else "One face found, tap to use it",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                LinkButton(text = "Remove", onClick = { viewModel.remove(item.uri) })
                            }
                            LazyRow(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(analyzed.faces) { index, face ->
                                    FaceChoice(
                                        face = face,
                                        selected = item.selectedFace == index,
                                        position = index + 1,
                                        total = analyzed.faces.size,
                                        onClick = { viewModel.select(item.uri, index) }
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
            item(key = "end") { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Message(text: String, onRemove: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(text = text, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinkButton(text = "Remove", onClick = onRemove)
    }
}

@Composable
private fun FaceChoice(face: EnrollFace, selected: Boolean, position: Int, total: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val issues = describe(face)
    Column(modifier = Modifier.width(96.dp)) {
        Image(
            bitmap = face.crop.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(96.dp)
                .clip(shape)
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
                .semantics { contentDescription = "Face $position of $total. ${if (issues.isEmpty()) "Good quality" else issues}. ${if (selected) "Selected" else "Not selected"}" }
                .clickable(role = Role.Checkbox, onClick = onClick)
        )
        Text(
            text = if (issues.isEmpty()) "Good" else issues,
            fontSize = 12.sp,
            color = if (issues.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun describe(face: EnrollFace): String {
    val parts = ArrayList<String>()
    if (FaceIssue.TooSmall in face.issues) parts.add("Too small")
    if (FaceIssue.Blurry in face.issues) parts.add("Blurry")
    if (FaceIssue.SideView in face.issues) parts.add("Side view")
    if (parts.isEmpty() && !face.usable) parts.add("Low quality")
    return parts.joinToString(", ")
}
