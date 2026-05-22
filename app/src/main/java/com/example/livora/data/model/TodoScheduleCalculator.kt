package com.example.livora.data.model

object TodoScheduleCalculator {

    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 86_400_000L
    private const val WEEK_MS = 604_800_000L
    private const val HISTORY_SIZE = 14
    private const val MAX_INTERVALS = 3650

    fun intervalMs(todo: Todo): Long {
        val unitMs = when (todo.intervalUnit) {
            TodoIntervalUnit.Minute -> MINUTE_MS
            TodoIntervalUnit.Hour -> HOUR_MS
            TodoIntervalUnit.Day -> DAY_MS
            TodoIntervalUnit.Week -> WEEK_MS
        }
        return unitMs * todo.intervalValue.coerceAtLeast(1)
    }

    fun stats(
        todo: Todo,
        completions: List<Long>,
        now: Long = System.currentTimeMillis()
    ): TodoStats {
        val intervalMs = intervalMs(todo)
        val sortedDesc = completions.sortedDescending()
        val current = currentRange(now, intervalMs)
        val isDoneNow = sortedDesc.any { it in current }
        val streak = computeStreak(sortedDesc, intervalMs, now, todo.createdAt)
        val best = computeBestStreak(sortedDesc.sorted(), intervalMs, todo.createdAt, now)
        val history = history(sortedDesc, intervalMs, now, todo.createdAt)
        val rate = if (history.isEmpty()) 0f
        else history.count { it.isDone }.toFloat() / history.size.toFloat()
        return TodoStats(
            todo = todo,
            completions = sortedDesc,
            currentStreak = streak,
            bestStreak = best,
            completionRate = rate,
            lastCompletedAt = sortedDesc.firstOrNull(),
            isDoneCurrentInterval = isDoneNow,
            history = history
        )
    }

    private fun currentRange(now: Long, intervalMs: Long): LongRange {
        val start = now - intervalMs + 1
        return start..now
    }

    private fun computeStreak(
        sortedDesc: List<Long>,
        intervalMs: Long,
        now: Long,
        createdAt: Long
    ): Int {
        if (intervalMs <= 0) return 0
        var streak = 0
        var i = 0
        var allowSkipCurrent = true
        while (i < MAX_INTERVALS) {
            val end = now - i * intervalMs
            val start = end - intervalMs + 1
            if (end < createdAt) break
            val hit = sortedDesc.any { it in start..end }
            if (hit) {
                streak++
                allowSkipCurrent = false
            } else {
                if (allowSkipCurrent) {
                    allowSkipCurrent = false
                } else {
                    break
                }
            }
            i++
        }
        return streak
    }

    private fun computeBestStreak(
        sortedAsc: List<Long>,
        intervalMs: Long,
        createdAt: Long,
        now: Long
    ): Int {
        if (intervalMs <= 0 || sortedAsc.isEmpty()) return 0
        val totalIntervals = ((now - createdAt) / intervalMs).toInt() + 1
        val length = totalIntervals.coerceIn(1, MAX_INTERVALS)
        val done = BooleanArray(length)
        sortedAsc.forEach { ts ->
            val idx = ((ts - createdAt) / intervalMs).toInt()
            if (idx in 0 until length) done[idx] = true
        }
        var best = 0
        var current = 0
        done.forEach {
            if (it) {
                current++
                if (current > best) best = current
            } else {
                current = 0
            }
        }
        return best
    }

    private fun history(
        sortedDesc: List<Long>,
        intervalMs: Long,
        now: Long,
        createdAt: Long
    ): List<TodoIntervalStatus> {
        if (intervalMs <= 0) return emptyList()
        val result = mutableListOf<TodoIntervalStatus>()
        for (i in 0 until HISTORY_SIZE) {
            val end = now - i * intervalMs
            val start = end - intervalMs + 1
            if (end < createdAt) break
            val completion = sortedDesc.firstOrNull { it in start..end }
            result.add(
                TodoIntervalStatus(
                    index = i,
                    start = start,
                    end = end,
                    isCurrent = i == 0,
                    isDone = completion != null,
                    completedAt = completion
                )
            )
        }
        return result
    }
}
