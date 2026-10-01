package com.example.livora.ui.vault

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.vault.PasswordStrength
import com.example.livora.data.vault.Strength
import com.example.livora.data.vault.Totp
import com.example.livora.data.vault.VaultClipboard
import com.example.livora.data.vault.VaultEntry
import com.example.livora.ui.components.AddAction
import com.example.livora.ui.components.ChoiceOption
import com.example.livora.ui.components.ChoiceRow
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.Motion
import com.example.livora.ui.components.Toaster
import com.example.livora.ui.components.TopBar
import kotlinx.coroutines.delay

private enum class VaultFilter { ALL, FAVORITES, WEAK, REUSED }

@Composable
fun VaultContent(
    entries: List<VaultEntry>,
    error: String?,
    onBack: () -> Unit,
    onLock: () -> Unit,
    onSave: (VaultEntry) -> Unit,
    onDelete: (String) -> Unit,
    newId: () -> String
) {
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }

    val detail = entries.firstOrNull { it.id == detailId }
    val editing = entries.firstOrNull { it.id == editingId }

    val pane = when {
        creating -> VaultPane.Editor(null)
        editing != null -> VaultPane.Editor(editing.id)
        detail != null -> VaultPane.Detail(detail.id)
        else -> VaultPane.List
    }

    AnimatedContent(
        targetState = pane,
        transitionSpec = {
            val deeper = targetState.depth > initialState.depth
            val enter = if (deeper) {
                slideInVertically(Motion.enter()) { it / 8 } + fadeIn(Motion.enter())
            } else {
                fadeIn(Motion.enter())
            }
            val exit = if (deeper) {
                fadeOut(Motion.exit())
            } else {
                slideOutVertically(Motion.exit()) { it / 8 } + fadeOut(Motion.exit())
            }
            (enter togetherWith exit).apply {
                targetContentZIndex = if (deeper) 1f else 0f
            }
        },
        label = "vaultPane"
    ) { target ->
        when (target) {
            is VaultPane.Editor -> {
                val existing = entries.firstOrNull { it.id == target.id }
                if (target.id == null) {
                    EntryEditor(
                        initial = null,
                        onCancel = { creating = false },
                        onSave = { draft ->
                            val now = System.currentTimeMillis()
                            onSave(draft.copy(id = newId(), createdAt = now, updatedAt = now))
                            creating = false
                        }
                    )
                } else if (existing != null) {
                    EntryEditor(
                        initial = existing,
                        onCancel = { editingId = null },
                        onSave = { draft ->
                            onSave(draft.copy(id = existing.id, createdAt = existing.createdAt, updatedAt = System.currentTimeMillis()))
                            editingId = null
                        }
                    )
                }
            }
            is VaultPane.Detail -> {
                val shown = entries.firstOrNull { it.id == target.id }
                if (shown != null) {
                    EntryDetail(
                        entry = shown,
                        onBack = { detailId = null },
                        onEdit = { editingId = shown.id },
                        onDelete = {
                            onDelete(shown.id)
                            detailId = null
                            Toaster.success("Deleted ${shown.title}", "Undo") { onSave(shown) }
                        }
                    )
                }
            }
            VaultPane.List -> EntryList(
                entries = entries,
                error = error,
                onBack = onBack,
                onLock = onLock,
                onOpen = { detailId = it.id },
                onAdd = { creating = true }
            )
        }
    }
}

private sealed interface VaultPane {
    val depth: Int

    data object List : VaultPane {
        override val depth = 0
    }

    data class Detail(val id: String) : VaultPane {
        override val depth = 1
    }

    data class Editor(val id: String?) : VaultPane {
        override val depth = 2
    }
}

@Composable
private fun EntryList(
    entries: List<VaultEntry>,
    error: String?,
    onBack: () -> Unit,
    onLock: () -> Unit,
    onOpen: (VaultEntry) -> Unit,
    onAdd: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(VaultFilter.ALL) }

    val reused = remember(entries) {
        entries.map { it.password }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
    }
    val weakIds = remember(entries) {
        entries.filter { it.password.isNotEmpty() && PasswordStrength.rate(it.password).level <= Strength.WEAK.level }
            .map { it.id }
            .toSet()
    }
    val visible = remember(entries, query, filter, reused, weakIds) {
        entries
            .filter { entry ->
                when (filter) {
                    VaultFilter.ALL -> true
                    VaultFilter.FAVORITES -> entry.favorite
                    VaultFilter.WEAK -> entry.id in weakIds
                    VaultFilter.REUSED -> entry.password in reused
                }
            }
            .filter { entry ->
                val needle = query.trim()
                needle.isEmpty() ||
                    entry.title.contains(needle, ignoreCase = true) ||
                    entry.username.contains(needle, ignoreCase = true) ||
                    entry.url.contains(needle, ignoreCase = true)
            }
            .sortedWith(compareByDescending<VaultEntry> { it.favorite }.thenBy { it.title.lowercase() })
    }
    val reusedCount = entries.count { it.password in reused }

    Scaffold(
        topBar = {
            TopBar(
                title = "Password vault",
                subtitle = if (entries.size == 1) "1 login" else "${entries.size} logins",
                navigationIcon = { BackAction(onBack) },
                actions = {
                    IconButton(onClick = onLock) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock vault", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    AddAction(description = "Add login", onClick = onAdd)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Design.screenHorizontalPadding)
        ) {
            if (error != null) {
                Text(
                    text = error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (entries.isEmpty()) {
                EmptyVault(onAdd)
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search logins") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                ChoiceRow(
                    options = listOf(
                        ChoiceOption(VaultFilter.ALL, "All"),
                        ChoiceOption(VaultFilter.FAVORITES, "Favorites"),
                        ChoiceOption(VaultFilter.WEAK, "Weak ${weakIds.size}"),
                        ChoiceOption(VaultFilter.REUSED, "Reused $reusedCount")
                    ),
                    selected = filter,
                    enabled = true,
                    onSelect = { filter = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (visible.isEmpty()) {
                    NothingMatches {
                        query = ""
                        filter = VaultFilter.ALL
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(visible, key = { it.id }) { entry ->
                            EntryRow(
                                entry = entry,
                                weak = entry.id in weakIds,
                                reused = entry.password in reused,
                                onClick = { onOpen(entry) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: VaultEntry, weak: Boolean, reused: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.username.isNotEmpty()) {
                Text(
                    text = entry.username,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (weak || reused) {
                Text(
                    text = listOfNotNull(if (weak) "Weak password" else null, if (reused) "Reused password" else null)
                        .joinToString(", "),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (entry.favorite) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Favorite",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EmptyVault(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No logins yet",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add a website or app login. It is encrypted before it is saved.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Add login")
        }
    }
}

@Composable
private fun NothingMatches(onClear: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Nothing matches",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onClear, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Clear search and filters")
        }
    }
}

@Composable
private fun EntryDetail(entry: VaultEntry, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var revealed by remember { mutableStateOf(false) }
    val totp = remember(entry.totpSecret) { Totp.parse(entry.totpSecret) }
    val now by produceState(System.currentTimeMillis(), totp) {
        while (totp != null) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopBar(
                title = entry.title,
                navigationIcon = { BackAction(onBack) },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit login", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete login", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Design.screenHorizontalPadding)
        ) {
            if (entry.username.isNotEmpty()) {
                FieldRow(label = "Username", value = entry.username) {
                    CopyAction("username") {
                        VaultClipboard.copySecret(context, "Livora username", entry.username)
                        Toaster.info("Username copied. It clears in 30 seconds")
                    }
                }
            }
            if (entry.password.isNotEmpty()) {
                FieldRow(
                    label = "Password",
                    value = if (revealed) entry.password else "••••••••••",
                    mono = revealed
                ) {
                    IconButton(onClick = { revealed = !revealed }) {
                        Icon(
                            imageVector = if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (revealed) "Hide password" else "Show password",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    CopyAction("password") {
                        VaultClipboard.copySecret(context, "Livora password", entry.password)
                        Toaster.info("Password copied. It clears in 30 seconds")
                    }
                }
                StrengthMeter(entry.password, modifier = Modifier.padding(bottom = 8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (totp != null) {
                val code = Totp.generate(totp, now)
                val remaining = Totp.secondsRemaining(totp, now)
                FieldRow(
                    label = "One-time code",
                    value = code.chunked((code.length + 1) / 2).joinToString(" "),
                    mono = true
                ) {
                    CopyAction("one-time code") {
                        VaultClipboard.copySecret(context, "Livora code", code)
                        Toaster.info("Code copied. It clears in 30 seconds")
                    }
                }
                LinearProgressIndicator(
                    progress = { remaining.toFloat() / totp.periodSeconds },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    text = "New code in $remaining s",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (entry.url.isNotEmpty()) {
                FieldRow(label = "Website", value = entry.url) {
                    IconButton(onClick = {
                        val target = if (entry.url.contains("://")) entry.url else "https://${entry.url}"
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open website",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    CopyAction("website") {
                        VaultClipboard.copySecret(context, "Livora website", entry.url)
                        Toaster.info("Website copied")
                    }
                }
            }
            if (entry.notes.isNotEmpty()) {
                FieldRow(label = "Notes", value = entry.notes) {}
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CopyAction(what: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copy $what",
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    mono: Boolean = false,
    actions: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    fontSize = if (mono) 20.sp else 16.sp,
                    fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            actions()
        }
    }
}

@Composable
internal fun StrengthMeter(password: String, modifier: Modifier = Modifier) {
    if (password.isEmpty()) return
    val strength = PasswordStrength.rate(password)
    val active = when {
        strength.level <= 1 -> MaterialTheme.colorScheme.error
        strength.level == 2 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(5) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(
                            color = if (index <= strength.level) active else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(3.dp)
                        )
                )
            }
        }
        Text(
            text = "Strength: ${strength.label}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun EntryEditor(initial: VaultEntry?, onCancel: () -> Unit, onSave: (VaultEntry) -> Unit) {
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(initial?.username.orEmpty()) }
    var password by rememberSaveable { mutableStateOf(initial?.password.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(initial?.url.orEmpty()) }
    var totpSecret by rememberSaveable { mutableStateOf(initial?.totpSecret.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(initial?.notes.orEmpty()) }
    var favorite by rememberSaveable { mutableStateOf(initial?.favorite ?: false) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showGenerator by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var triedSave by remember { mutableStateOf(false) }

    val dirty = title != initial?.title.orEmpty() ||
        username != initial?.username.orEmpty() ||
        password != initial?.password.orEmpty() ||
        url != initial?.url.orEmpty() ||
        totpSecret != initial?.totpSecret.orEmpty() ||
        notes != initial?.notes.orEmpty() ||
        favorite != (initial?.favorite ?: false)
    val titleMissing = title.isBlank()
    val totpInvalid = totpSecret.isNotBlank() && Totp.parse(totpSecret) == null

    fun requestClose() {
        if (dirty) confirmDiscard = true else onCancel()
    }
    BackHandler(onBack = ::requestClose)

    Scaffold(
        topBar = {
            TopBar(
                title = if (initial == null) "New login" else "Edit login",
                navigationIcon = { BackAction(::requestClose) },
                actions = {
                    TextButton(
                        onClick = {
                            triedSave = true
                            if (!titleMissing && !totpInvalid) {
                                onSave(
                                    VaultEntry(
                                        id = initial?.id.orEmpty(),
                                        title = title.trim(),
                                        username = username.trim(),
                                        password = password,
                                        url = url.trim(),
                                        notes = notes,
                                        totpSecret = totpSecret.trim(),
                                        favorite = favorite,
                                        createdAt = 0L,
                                        updatedAt = 0L
                                    )
                                )
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Save", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Design.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Name") },
                singleLine = true,
                isError = triedSave && titleMissing,
                supportingText = { if (triedSave && titleMissing) Text("Enter a name for this login") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username or email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            StrengthMeter(password)
            OutlinedButton(
                onClick = { showGenerator = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Generate a strong password")
            }
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Website") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = totpSecret,
                onValueChange = { totpSecret = it },
                label = { Text("One-time code key (optional)") },
                supportingText = {
                    Text(
                        if (totpInvalid) "That is not a valid setup key or otpauth link"
                        else "Paste the setup key or the otpauth link from the website"
                    )
                },
                isError = totpInvalid,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Favorite",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = favorite, onCheckedChange = { favorite = it })
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showGenerator) {
        GeneratorSheet(
            onDismiss = { showGenerator = false },
            onUse = {
                password = it
                showPassword = true
                showGenerator = false
            }
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to this login have not been saved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onCancel()
                }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") }
            }
        )
    }
}
