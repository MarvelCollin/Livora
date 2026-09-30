package com.example.livora.data.usage

import android.app.usage.StorageStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.example.livora.data.db.AppDatabase
import java.time.LocalDate
import java.time.ZoneId

class AppUsage(val packageName: String, val label: String, val millis: Long)

class UnusedApp(val packageName: String, val label: String, val lastUsed: Long?, val sizeBytes: Long?)

class UsagePeriod(
    val from: LocalDate,
    val to: LocalDate,
    val dayMillis: Map<Long, Long>,
    val hourMillis: Map<Long, Long>,
    val apps: List<AppUsage>
) {
    val total: Long get() = dayMillis.values.sum()
}

class UsageRepository(private val context: Context, private val db: AppDatabase) {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val pm: PackageManager = context.packageManager
    private val labels = HashMap<String, String?>()

    @Synchronized
    private fun labelOf(packageName: String): String? = labels.getOrPut(packageName) {
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun homePackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return try {
            pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    suspend fun refreshRecent(lookbackDays: Long = 14) {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        val firstDay = today.minusDays(lookbackDays)
        val begin = firstDay.atStartOfDay(zone).toInstant().toEpochMilli() - 6 * 3_600_000L
        val events = ArrayList<RawEvent>()
        val stream = manager.queryEvents(begin, now) ?: return
        val holder = UsageEvents.Event()
        while (stream.hasNextEvent()) {
            stream.getNextEvent(holder)
            when (holder.eventType) {
                UsageMath.RESUMED, UsageMath.PAUSED, UsageMath.STOPPED,
                UsageMath.SCREEN_OFF, UsageMath.KEYGUARD_SHOWN, UsageMath.SHUTDOWN ->
                    events.add(RawEvent(holder.packageName.orEmpty(), holder.className.orEmpty(), holder.eventType, holder.timeStamp))
            }
        }
        val slices = UsageMath.compute(events, now, zone, homePackages())
        val dao = db.usage()

        val fromDay = firstDay.toEpochDay()
        val toDay = today.toEpochDay()
        val existing = dao.days(fromDay, toDay).groupBy({ it.day }, { it.packageName to it.millis })
        val dayRows = ArrayList<UsageDayEntity>()
        slices.days.forEach { (day, perApp) ->
            if (day < fromDay || day > toDay) return@forEach
            val merged = UsageMath.merge(existing[day].orEmpty().toMap(), perApp)
            merged.forEach { (pkg, millis) -> dayRows.add(UsageDayEntity(day, pkg, millis)) }
        }
        dao.putDays(dayRows)

        val fromHour = firstDay.atStartOfDay(zone).toInstant().toEpochMilli() / 3_600_000L
        val toHour = now / 3_600_000L
        val existingHours = dao.hours(fromHour, toHour).associate { it.hourStart to it.millis }
        val hourRows = slices.hours
            .filter { (hour, _) -> hour in fromHour..toHour }
            .map { (hour, millis) -> UsageHourEntity(hour, maxOf(millis, existingHours[hour] ?: 0L)) }
        dao.putHours(hourRows)
    }

    suspend fun load(from: LocalDate, to: LocalDate): UsagePeriod {
        val dao = db.usage()
        val dayMillis = dao.dayTotals(from.toEpochDay(), to.toEpochDay()).associate { it.day to it.millis }
        val apps = dao.appTotals(from.toEpochDay(), to.toEpochDay())
            .mapNotNull { row -> labelOf(row.packageName)?.let { AppUsage(row.packageName, it, row.millis) } }
        val startHour = from.atStartOfDay(zone).toInstant().toEpochMilli() / 3_600_000L
        val endHour = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() / 3_600_000L
        val hourMillis = dao.hours(startHour, endHour).associate { it.hourStart to it.millis }
        return UsagePeriod(from, to, dayMillis, hourMillis, apps)
    }

    suspend fun firstRecordedDay(): LocalDate? = db.usage().firstDay()?.let { LocalDate.ofEpochDay(it) }

    fun lastUsedTimes(): Map<String, Long> {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_YEARLY, now - 400L * 86_400_000L, now).orEmpty()
        val out = HashMap<String, Long>()
        stats.forEach { stat ->
            val used = maxOf(stat.lastTimeUsed, if (Build.VERSION.SDK_INT >= 29) stat.lastTimeVisible else 0L)
            if (used > (out[stat.packageName] ?: 0L)) out[stat.packageName] = used
        }
        return out
    }

    fun unusedApps(lastUsed: Map<String, Long>, days: Int = 30): List<UnusedApp> {
        val now = System.currentTimeMillis()
        val cutoff = now - days * 86_400_000L
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = try {
            pm.queryIntentActivities(launcher, 0).map { it.activityInfo.packageName }.distinct()
        } catch (e: Exception) {
            emptyList()
        }
        val storage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        } else {
            null
        }
        return candidates
            .filter { it != context.packageName }
            .mapNotNull { pkg ->
                val info = try {
                    pm.getPackageInfo(pkg, 0)
                } catch (e: PackageManager.NameNotFoundException) {
                    return@mapNotNull null
                }
                val app = info.applicationInfo ?: return@mapNotNull null
                if (app.flags and ApplicationInfo.FLAG_SYSTEM != 0) return@mapNotNull null
                if (info.firstInstallTime > cutoff) return@mapNotNull null
                val used = lastUsed[pkg]?.takeIf { it > 0 }
                if (used != null && used > cutoff) return@mapNotNull null
                val size = if (storage != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        val stats = storage.queryStatsForPackage(app.storageUuid, pkg, Process.myUserHandle())
                        stats.appBytes + stats.dataBytes + stats.cacheBytes
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
                UnusedApp(pkg, pm.getApplicationLabel(app).toString(), used, size)
            }
            .sortedByDescending { it.sizeBytes ?: 0L }
    }
}
