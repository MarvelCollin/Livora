package com.example.livora.data.people.media

class FolderInfo(
    val bucketId: Long,
    val name: String,
    val relativePath: String,
    val count: Int,
    val coverId: Long?,
    val newest: Long,
    val virtual: Boolean = false
) {
    val key: String get() = if (virtual) "v:$relativePath" else "b:$bucketId"
}

object MediaFolders {

    fun aggregate(images: List<MediaImage>): List<FolderInfo> {
        val groups = LinkedHashMap<Long, MutableList<MediaImage>>()
        for (image in images) groups.getOrPut(image.bucketId) { ArrayList() }.add(image)
        val out = ArrayList<FolderInfo>(groups.size)
        for ((bucketId, list) in groups) {
            val newest = list.maxByOrNull { it.sortDate } ?: continue
            val name = newest.bucketName.ifBlank { folderNameOf(newest.relativePath) }
            out.add(
                FolderInfo(
                    bucketId = bucketId,
                    name = name,
                    relativePath = newest.relativePath,
                    count = list.size,
                    coverId = newest.id,
                    newest = newest.sortDate
                )
            )
        }
        return out.sortedWith(
            compareByDescending<FolderInfo> { it.relativePath.equals("DCIM/Camera/", ignoreCase = true) }
                .thenByDescending { it.newest }
        )
    }

    fun folderNameOf(relativePath: String): String =
        relativePath.trimEnd('/').substringAfterLast('/').ifBlank { "Photos" }

    fun isWritableTarget(relativePath: String): Boolean {
        val lower = relativePath.lowercase()
        return lower.startsWith("pictures/") || lower.startsWith("dcim/")
    }

    fun sanitizeName(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim('.', ' ')
        return cleaned.take(60)
    }

    fun pathForName(name: String): String = "Pictures/${sanitizeName(name)}/"
}
