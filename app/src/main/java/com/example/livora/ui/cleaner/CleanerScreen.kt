package com.example.livora.ui.cleaner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.ui.components.BackButton
import com.example.livora.ui.components.Design
import com.example.livora.ui.components.Headline
import com.example.livora.ui.components.NavRow
import com.example.livora.ui.components.PreviewNotice
import com.example.livora.ui.components.SectionLabel
import com.example.livora.ui.components.TopBar
import com.example.livora.ui.components.showPreviewOnly
import com.example.livora.ui.people.LinkButton
import com.example.livora.ui.people.PrimaryAction

private class Slice(val label: String, val gb: Float)

private val slices = listOf(
    Slice("Apps", 71.3f),
    Slice("Images", 58.4f),
    Slice("Videos", 36.9f),
    Slice("Other", 28.4f),
    Slice("Audio", 7.2f),
    Slice("Documents", 5.6f)
)

@Composable
fun CleanerScreen(onBack: () -> Unit, onOpenReview: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopBar(
                title = "Storage cleaner",
                subtitle = "Swipe through photos and videos to free space",
                navigationIcon = { BackButton(onBack) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = WindowInsets.navigationBars.asPaddingValues()
        ) {
            item(key = "notice") { PreviewNotice() }
            item(key = "headline") {
                Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding, vertical = 16.dp)) {
                    Headline(
                        label = "Free space",
                        value = "48.2 GB",
                        context = "of 256 GB. Photos and videos use 95.3 GB."
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    StorageBar()
                    Spacer(modifier = Modifier.height(12.dp))
                    Legend()
                }
            }
            item(key = "review") {
                Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Swipe review",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "1,204 photos and videos to go through. Swipe left to keep and right to send to the trash. Nothing is deleted until you confirm.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                    )
                    PrimaryAction(
                        text = "Start swipe review",
                        onClick = onOpenReview,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item(key = "quick") {
                Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding)) {
                    SectionLabel(text = "Quick wins", modifier = Modifier.padding(top = 28.dp))
                    NavRow(
                        title = "Duplicates",
                        description = "86 exact copies, 412 MB",
                        onClick = { showPreviewOnly() }
                    )
                    NavRow(
                        title = "Screenshots",
                        description = "214 files, 1.1 GB",
                        onClick = { showPreviewOnly() }
                    )
                    NavRow(
                        title = "Blurry photos",
                        description = "58 photos, 190 MB",
                        onClick = { showPreviewOnly() }
                    )
                    NavRow(
                        title = "Large files",
                        description = "12 files over 100 MB, 3.4 GB",
                        onClick = { showPreviewOnly() }
                    )
                    NavRow(
                        title = "Messaging media",
                        description = "WhatsApp photos and videos, 6.2 GB",
                        onClick = { showPreviewOnly() }
                    )
                }
            }
            item(key = "full") {
                Column(modifier = Modifier.padding(horizontal = Design.screenHorizontalPadding)) {
                    SectionLabel(text = "Full cleaner mode")
                    Text(
                        text = "Old installers, large downloads and empty folders need access to all files. Without it the cleaner still works on photos and videos.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinkButton(text = "Allow access to all files", onClick = { showPreviewOnly() })
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun StorageBar() {
    val total = slices.sumOf { it.gb.toDouble() }.toFloat() + 48.2f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(4.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        slices.forEachIndexed { index, slice ->
            Box(
                modifier = Modifier
                    .weight(slice.gb / total)
                    .height(14.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 1f - index * 0.14f))
            )
        }
        Box(
            modifier = Modifier
                .weight(48.2f / total)
                .height(14.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
    }
}

@Composable
private fun Legend() {
    Column {
        slices.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth()) {
                pair.forEach { slice ->
                    Row(modifier = Modifier.weight(1f).heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = slice.label,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${slice.gb} GB",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
            }
        }
        Text(
            text = "The bar shows used space from darkest to lightest, then free space at the end.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
