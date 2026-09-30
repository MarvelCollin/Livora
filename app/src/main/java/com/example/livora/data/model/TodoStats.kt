package com.example.livora.data.model

data class TodoStats(
    val todo: Todo,
    val completions: List<Long>,
    val currentStreak: Int,
    val bestStreak: Int,
    val completionRate: Float,
    val lastCompletedAt: Long?,
    val isDoneCurrentInterval: Boolean,
    val history: List<TodoIntervalStatus>
)

data class DayActivity(
    val dayStart: Long,
    val label: String,
    val tasks: Int,
    val minutes: Int,
    val isToday: Boolean
)

data class TodoIntervalStatus(
    val index: Int,
    val start: Long,
    val end: Long,
    val isCurrent: Boolean,
    val isDone: Boolean,
    val completedAt: Long?
)
