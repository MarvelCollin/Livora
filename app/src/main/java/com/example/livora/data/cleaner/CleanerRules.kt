package com.example.livora.data.cleaner

enum class CleanerSource(val id: String, val title: String) {
    All("all", "Everything"),
    Duplicates("duplicates", "Duplicates"),
    Screenshots("screenshots", "Screenshots"),
    Large("large", "Large files"),
    Messaging("messaging", "Messaging media");

    companion object {
        fun of(id: String?): CleanerSource = entries.firstOrNull { it.id == id } ?: All
    }
}

class DuplicateGroup(val original: CleanerFile, val copies: List<CleanerFile>)

class DuplicateResult(val groups: List<DuplicateGroup>) {
    val copies: List<CleanerFile> get() = groups.flatMap { it.copies }
    val bytes: Long get() = copies.sumOf { it.size }
}

object CleanerRules {

    const val LARGE_BYTES = 50L * 1024 * 1024

    private val messagingStarts = listOf("whatsapp", "telegram", "messenger", "wechat", "signal", "viber")
    private val messagingExact = listOf("line", "kakaotalk")

    fun isScreenshot(file: CleanerFile): Boolean =
        file.relativePath.contains("screenshot", ignoreCase = true) ||
            file.name.startsWith("screenshot", ignoreCase = true) ||
            file.name.startsWith("screen_shot", ignoreCase = true) ||
            file.name.startsWith("screen recording", ignoreCase = true) ||
            file.name.startsWith("screenrecorder", ignoreCase = true)

    fun isMessaging(file: CleanerFile): Boolean =
        file.relativePath.split('/').filter { it.isNotBlank() }.any { segment ->
            messagingStarts.any { segment.startsWith(it, ignoreCase = true) } ||
                messagingExact.any { segment.equals(it, ignoreCase = true) }
        }

    fun isLarge(file: CleanerFile): Boolean = file.size >= LARGE_BYTES

    fun matches(source: CleanerSource, file: CleanerFile): Boolean = when (source) {
        CleanerSource.All -> true
        CleanerSource.Screenshots -> isScreenshot(file)
        CleanerSource.Large -> isLarge(file)
        CleanerSource.Messaging -> isMessaging(file)
        CleanerSource.Duplicates -> false
    }

    fun duplicateCandidates(files: List<CleanerFile>): List<List<CleanerFile>> =
        files.filter { it.size > 4096 }
            .groupBy { it.size to it.video }
            .values
            .filter { it.size > 1 }

    fun groupDuplicates(candidates: List<List<CleanerFile>>, hash: (CleanerFile) -> String?): DuplicateResult {
        val groups = ArrayList<DuplicateGroup>()
        candidates.forEach { sameSize ->
            sameSize
                .mapNotNull { file -> hash(file)?.let { it to file } }
                .groupBy({ it.first }, { it.second })
                .values
                .filter { it.size > 1 }
                .forEach { identical ->
                    val ordered = identical.sortedWith(compareBy<CleanerFile> { if (it.dateMs > 0) it.dateMs else Long.MAX_VALUE }.thenBy { it.id })
                    groups.add(DuplicateGroup(ordered.first(), ordered.drop(1)))
                }
        }
        return DuplicateResult(groups.sortedByDescending { it.copies.sumOf { copy -> copy.size } })
    }
}
