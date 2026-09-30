package com.example.livora.ui.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livora.data.people.FoldersState
import com.example.livora.data.people.media.AccessLevel
import com.example.livora.data.people.media.FolderInfo
import com.example.livora.ui.components.SkeletonBox

const val ALL_PHOTOS_KEY = "all"

@Composable
fun FoldersContent(
    viewModel: FoldersViewModel,
    access: AccessLevel,
    onOpenFolder: (String) -> Unit,
    onAllowAll: () -> Unit,
    creating: Boolean,
    onCreatingChange: (Boolean) -> Unit
) {
    val state by viewModel.state.collectAsState()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (access == AccessLevel.Partial) {
            item(key = "partial") {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = "Showing only the photos you picked",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Allow all photos in settings to see every folder.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinkButton(text = "Open settings", onClick = onAllowAll)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
        }
        when (val current = state) {
            FoldersState.Loading -> items(6, key = { "sk$it" }) { FolderRowSkeleton() }
            is FoldersState.Ready -> {
                if (current.folders.isEmpty()) {
                    item(key = "empty") {
                        EmptyBlock(
                            title = "No photos on this phone yet",
                            body = "Folders appear here as soon as there are photos. You can also create an empty folder and add photos to it later.",
                            actionLabel = "New folder",
                            onAction = { onCreatingChange(true) }
                        )
                    }
                } else {
                    item(key = ALL_PHOTOS_KEY) {
                        val cover = current.folders.maxByOrNull { it.newest }?.coverId
                        FolderRow(
                            name = "All photos",
                            detail = photosLabel(current.totalPhotos),
                            path = null,
                            coverId = cover,
                            onClick = { onOpenFolder(ALL_PHOTOS_KEY) }
                        )
                        FolderDivider()
                    }
                    items(current.folders, key = { it.key }) { folder ->
                        FolderRow(
                            name = folder.name,
                            detail = if (folder.virtual) "Empty, add photos to create it" else photosLabel(folder.count),
                            path = folder.relativePath.trimEnd('/'),
                            coverId = folder.coverId,
                            onClick = { onOpenFolder(folder.key) }
                        )
                        FolderDivider()
                    }
                }
            }
        }
        item(key = "end") { Spacer(modifier = Modifier.height(24.dp)) }
    }
    if (creating) {
        RenameDialog(
            initial = "",
            title = "New folder",
            label = "Folder name",
            onDismiss = { onCreatingChange(false) },
            onSave = {
                viewModel.createFolder(it)
                onCreatingChange(false)
            }
        )
    }
}

@Composable
private fun FolderDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 94.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp
    )
}

@Composable
fun FolderRow(
    name: String,
    detail: String,
    path: String?,
    coverId: Long?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 80.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (coverId != null) {
            PhotoThumb(
                mediaId = coverId,
                sizePx = 192,
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(12.dp),
                description = "Cover photo of $name"
            )
        } else {
            SkeletonBox(modifier = Modifier.size(64.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = detail,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (path != null) {
                Text(
                    text = path,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FolderRowSkeleton() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkeletonBox(modifier = Modifier.size(64.dp), shape = RoundedCornerShape(12.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            SkeletonBox(modifier = Modifier.width(120.dp).height(14.dp), shape = RoundedCornerShape(7.dp))
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonBox(modifier = Modifier.width(70.dp).height(12.dp), shape = RoundedCornerShape(6.dp))
        }
    }
}

fun FolderInfo.displayPath(): String = relativePath.trimEnd('/')
