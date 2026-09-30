package com.example.livora.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.ui.components.TopBar

@Composable
fun MergeSuggestionsScreen(viewModel: MergeSuggestionsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopBar(
                title = "Possibly the same person",
                subtitle = "Nothing merges until you say so",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val current = state) {
                MergeListState.Loading -> items(3, key = { "sk$it" }) { MergeCardSkeleton() }
                is MergeListState.Ready -> {
                    if (current.cards.isEmpty()) {
                        item(key = "empty") {
                            EmptyBlock(
                                title = "Nothing to review",
                                body = "Livora sees no groups that look like the same person. If you spot one yourself, press and hold both on the People list and choose Same person."
                            )
                        }
                    } else {
                        item(key = "header") {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(
                                    text = if (current.cards.size == 1) "1 pair to review" else "${current.cards.size} pairs to review",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Compare the faces. Merge the pairs that are the same person. Everyone you merge keeps their name and can be undone right away.",
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlineAction(text = "Merge all ${current.cards.size} pairs", onClick = { viewModel.mergeAll() })
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        }
                        items(current.cards, key = { "${it.a.id}-${it.b.id}" }) { card ->
                            MergeCardRow(card = card, onMerge = { viewModel.merge(card) }, onApart = { viewModel.notTheSame(card) })
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        }
                    }
                }
            }
            item(key = "end") { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MergeCardRow(card: MergeCard, onMerge: () -> Unit, onApart: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PersonStrip(
                name = card.a.name,
                photos = card.a.photoCount,
                faces = card.facesA,
                modifier = Modifier.weight(1f)
            )
            PersonStrip(
                name = card.b.name,
                photos = card.b.photoCount,
                faces = card.facesB,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${(card.suggestion.score * 100).toInt().coerceIn(0, 100)}% similar",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlineAction(text = "Not the same", onClick = onApart, modifier = Modifier.weight(1f))
            PrimaryAction(text = "Merge", onClick = onMerge, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PersonStrip(name: String?, photos: Int, faces: List<Long>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            faces.take(3).forEach { id ->
                FaceAvatar(faceId = id, referenceId = null, size = 48.dp, description = "Sample face")
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name ?: "Unnamed person",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(text = photosLabel(photos), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MergeCardSkeleton() {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    repeat(3) {
                        com.example.livora.ui.components.SkeletonBox(
                            modifier = Modifier.width(48.dp).height(48.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                        )
                    }
                }
            }
        }
    }
}
