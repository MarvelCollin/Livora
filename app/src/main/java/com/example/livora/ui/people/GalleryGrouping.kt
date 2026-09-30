package com.example.livora.ui.people

import android.content.Context
import android.text.format.DateUtils
import com.example.livora.data.people.media.MediaImage
import java.util.Calendar
import java.util.TimeZone

sealed interface GalleryRow {
    val key: String

    class Header(val day: Long, val label: String, val ids: List<Long>) : GalleryRow {
        override val key: String = "h:$day"
    }

    class Photo(val image: MediaImage) : GalleryRow {
        override val key: String = "p:${image.id}"
    }
}

object GalleryGrouping {

    private const val DAY_MS = 86_400_000L

    fun group(context: Context, images: List<MediaImage>): List<GalleryRow> {
        if (images.isEmpty()) return emptyList()
        val zone = TimeZone.getDefault()
        val now = System.currentTimeMillis()
        val today = dayNumber(now, zone)
        val thisYear = Calendar.getInstance().get(Calendar.YEAR)
        val out = ArrayList<GalleryRow>(images.size + 64)
        var start = 0
        while (start < images.size) {
            val day = dayNumber(images[start].sortDate, zone)
            var end = start + 1
            while (end < images.size && dayNumber(images[end].sortDate, zone) == day) end++
            val group = images.subList(start, end)
            out.add(GalleryRow.Header(day, label(context, group[0].sortDate, today - day, thisYear), group.map { it.id }))
            for (image in group) out.add(GalleryRow.Photo(image))
            start = end
        }
        return out
    }

    private fun dayNumber(millis: Long, zone: TimeZone): Long = Math.floorDiv(millis + zone.getOffset(millis), DAY_MS)

    private fun label(context: Context, millis: Long, daysAgo: Long, thisYear: Int): String {
        if (daysAgo == 0L) return "Today"
        if (daysAgo == 1L) return "Yesterday"
        if (daysAgo in 2L..6L) return DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_WEEKDAY)
        val year = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)
        var flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or
            DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_ABBREV_WEEKDAY
        flags = flags or if (year == thisYear) DateUtils.FORMAT_NO_YEAR else DateUtils.FORMAT_SHOW_YEAR
        return DateUtils.formatDateTime(context, millis, flags)
    }
}

fun imagesOfFolder(key: String, all: List<MediaImage>): List<MediaImage> = when {
    key == ALL_PHOTOS_KEY -> all
    key.startsWith("b:") -> {
        val bucket = key.removePrefix("b:").toLongOrNull()
        if (bucket == null) emptyList() else all.filter { it.bucketId == bucket }
    }
    else -> emptyList()
}
