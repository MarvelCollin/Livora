package com.example.livora.ui.docs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.ui.components.BackButton
import com.example.livora.ui.components.ChoiceOption
import com.example.livora.ui.components.ChoiceRow
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.PreviewNotice
import com.example.livora.ui.components.SkeletonBox
import com.example.livora.ui.components.TopBar
import com.example.livora.ui.components.showPreviewOnly
import com.example.livora.ui.people.EmptyBlock
import com.example.livora.ui.people.OutlineAction
import com.example.livora.ui.people.PrimaryAction
import kotlinx.coroutines.delay

class SampleDocument(val id: Int, val name: String, val pages: Int, val size: String, val date: String, val bytes: Long)

val sampleDocuments = listOf(
    SampleDocument(1, "Rent agreement", 4, "2.1 MB", "12 Sep 2026", 2_100),
    SampleDocument(2, "Campus receipt", 1, "320 KB", "3 Sep 2026", 320),
    SampleDocument(3, "Passport copy", 2, "1.4 MB", "28 Aug 2026", 1_400),
    SampleDocument(4, "Lecture notes week 5", 12, "5.8 MB", "24 Aug 2026", 5_800)
)

private enum class DocSort { Newest, Name, Size }

@Composable
fun DocumentsScreen(onBack: () -> Unit, onOpenDocument: (Int) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(DocSort.Newest) }
    var loading by remember { mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        delay(450)
        loading = false
    }

    val shown = sampleDocuments
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        .let { list ->
            when (sort) {
                DocSort.Newest -> list
                DocSort.Name -> list.sortedBy { it.name }
                DocSort.Size -> list.sortedByDescending { it.bytes }
            }
        }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopBar(
                title = "Documents",
                subtitle = "Scan pages and save them as PDF",
                navigationIcon = { BackButton(onBack) }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrimaryAction(text = "Scan document", onClick = { showPreviewOnly() }, modifier = Modifier.weight(1f))
                    OutlineAction(text = "From images", onClick = { showPreviewOnly() }, modifier = Modifier.weight(1f))
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            item(key = "notice") { PreviewNotice() }
            item(key = "controls") {
                Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 12.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search documents") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ChoiceRow(
                        options = listOf(
                            ChoiceOption(DocSort.Newest, "Newest"),
                            ChoiceOption(DocSort.Name, "Name"),
                            ChoiceOption(DocSort.Size, "Size")
                        ),
                        selected = sort,
                        enabled = true,
                        onSelect = { sort = it }
                    )
                }
            }
            if (loading) {
                items(4, key = { "sk-$it" }) { DocumentRowSkeleton() }
            } else if (shown.isEmpty()) {
                item(key = "empty") {
                    EmptyBlock(
                        title = "Nothing matches",
                        body = "Try another name, or scan a new document.",
                        actionLabel = "Clear search",
                        onAction = { query = "" }
                    )
                }
            } else {
                items(shown, key = { it.id }) { doc ->
                    Column(modifier = Modifier.animateItem()) {
                        DocumentRow(doc = doc, onClick = { onOpenDocument(doc.id) })
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 92.dp, end = Design.screenHorizontalPadding),
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp
                        )
                    }
                }
            }
            item(key = "end") { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DocumentRow(doc: SampleDocument, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = Design.screenHorizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PaperThumb(seed = doc.id, modifier = Modifier.width(52.dp).aspectRatio(0.72f))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = doc.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "${if (doc.pages == 1) "1 page" else "${doc.pages} pages"}, ${doc.size}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = doc.date,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DocumentRowSkeleton() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Design.screenHorizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkeletonBox(modifier = Modifier.width(52.dp).aspectRatio(0.72f), shape = RoundedCornerShape(4.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            SkeletonBox(modifier = Modifier.width(150.dp).height(14.dp), shape = RoundedCornerShape(7.dp))
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonBox(modifier = Modifier.width(90.dp).height(12.dp), shape = RoundedCornerShape(6.dp))
        }
    }
}

@Composable
fun PaperThumb(seed: Int, modifier: Modifier = Modifier) {
    val paper = MaterialTheme.colorScheme.surfaceContainerLowest
    val line = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val edge = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = modifier
            .background(paper, RoundedCornerShape(4.dp))
            .border(1.dp, edge, RoundedCornerShape(4.dp))
    ) {
        val pad = size.width * 0.14f
        val rows = 7
        val gap = (size.height - pad * 2) / rows
        val thick = size.height * 0.018f
        val hasBlock = seed % 2 == 1
        for (i in 0 until rows) {
            val skip = hasBlock && i in 1..2
            if (skip) continue
            val width = (size.width - pad * 2) * (if ((i + seed) % 3 == 0) 0.6f else 0.95f)
            drawRoundRect(
                color = if (i == 0) accent else line,
                topLeft = Offset(pad, pad + gap * i),
                size = Size(width, thick),
                cornerRadius = CornerRadius(thick / 2)
            )
        }
        if (hasBlock) {
            drawRoundRect(
                color = accent,
                topLeft = Offset(pad, pad + gap),
                size = Size(size.width - pad * 2, gap * 1.8f),
                cornerRadius = CornerRadius(3f)
            )
        }
    }
}

@Composable
fun DocumentDetailScreen(id: Int, onBack: () -> Unit) {
    val doc = sampleDocuments.firstOrNull { it.id == id } ?: sampleDocuments.first()
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopBar(
                title = doc.name,
                subtitle = "${if (doc.pages == 1) "1 page" else "${doc.pages} pages"}, ${doc.size}",
                navigationIcon = { BackButton(onBack) },
                actions = {
                    Column {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            listOf("Rename", "Compress", "Merge with another PDF", "Delete").forEach { label ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        menuOpen = false
                                        showPreviewOnly()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlineAction(text = "Add page", onClick = { showPreviewOnly() }, modifier = Modifier.weight(1f))
                    OutlineAction(text = "Share", onClick = { showPreviewOnly() }, modifier = Modifier.weight(1f))
                    PrimaryAction(text = "Export PDF", onClick = { showPreviewOnly() }, modifier = Modifier.weight(1f))
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            PreviewNotice()
            Text(
                text = "Press and hold a page to reorder it.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 10.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Design.screenHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items((1..doc.pages).toList(), key = { it }) { page ->
                    Column {
                        PaperThumb(
                            seed = doc.id + page,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.72f)
                                .clickable { showPreviewOnly() }
                        )
                        Text(
                            text = "Page $page",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
