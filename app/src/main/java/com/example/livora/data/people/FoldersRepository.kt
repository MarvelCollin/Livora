package com.example.livora.data.people

import android.content.Context
import com.example.livora.data.people.db.PeopleDatabase
import com.example.livora.data.people.db.VirtualFolderEntity
import com.example.livora.data.people.media.FolderInfo
import com.example.livora.data.people.media.MediaFolders
import com.example.livora.data.people.media.MediaImage
import com.example.livora.data.people.media.MediaImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed interface FoldersState {
    data object Loading : FoldersState
    class Ready(val folders: List<FolderInfo>, val totalPhotos: Int) : FoldersState
}

class FoldersRepository(private val context: Context, private val database: PeopleDatabase) {

    private val stateFlow = MutableStateFlow<FoldersState>(FoldersState.Loading)
    val state: StateFlow<FoldersState> = stateFlow.asStateFlow()

    private val imagesFlow = MutableStateFlow<List<MediaImage>>(emptyList())

    val images: StateFlow<List<MediaImage>> = imagesFlow.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val images = MediaImages.queryAll(context)
        val real = MediaFolders.aggregate(images)
        val realPaths = real.map { it.relativePath.lowercase() }.toSet()
        val virtual = database.virtualFolders().all()
        val pending = ArrayList<FolderInfo>()
        for (folder in virtual) {
            if (folder.relativePath.lowercase() in realPaths) {
                database.virtualFolders().delete(folder.id)
            } else {
                pending.add(
                    FolderInfo(
                        bucketId = -folder.id,
                        name = folder.name,
                        relativePath = folder.relativePath,
                        count = 0,
                        coverId = null,
                        newest = folder.createdAt,
                        virtual = true
                    )
                )
            }
        }
        imagesFlow.value = images.sortedByDescending { it.sortDate }
        stateFlow.value = FoldersState.Ready(real + pending, images.size)
    }

    fun folderByKey(key: String): FolderInfo? =
        (stateFlow.value as? FoldersState.Ready)?.folders?.firstOrNull { it.key == key }

    suspend fun createFolder(rawName: String): FolderInfo? = withContext(Dispatchers.IO) {
        val name = MediaFolders.sanitizeName(rawName)
        if (name.isBlank()) return@withContext null
        val path = MediaFolders.pathForName(name)
        val current = (stateFlow.value as? FoldersState.Ready)?.folders.orEmpty()
        current.firstOrNull { it.relativePath.equals(path, ignoreCase = true) }?.let { return@withContext it }
        val id = database.virtualFolders().insert(VirtualFolderEntity(name = name, relativePath = path, createdAt = System.currentTimeMillis()))
        refresh()
        current.firstOrNull { it.relativePath.equals(path, ignoreCase = true) }
            ?: (stateFlow.value as? FoldersState.Ready)?.folders?.firstOrNull { it.virtual && it.bucketId == -id }
    }

    suspend fun removeVirtual(relativePath: String) = withContext(Dispatchers.IO) {
        database.virtualFolders().deleteByPath(relativePath)
        refresh()
    }

    suspend fun restoreVirtual(name: String, relativePath: String) = withContext(Dispatchers.IO) {
        database.virtualFolders().insert(VirtualFolderEntity(name = name, relativePath = relativePath, createdAt = System.currentTimeMillis()))
        refresh()
    }

    suspend fun idsInFolder(bucketId: Long): List<Long> = withContext(Dispatchers.IO) {
        MediaImages.queryAll(context, bucketId).map { it.id }
    }

}
