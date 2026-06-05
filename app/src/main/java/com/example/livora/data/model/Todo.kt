package com.example.livora.data.model

data class Todo(
    val id: String,
    val title: String,
    val notes: String = "",
    val intervalValue: Int = 1,
    val intervalUnit: TodoIntervalUnit = TodoIntervalUnit.Day,
    val timeOfDay: String? = null,
    val durationValue: Int = 30,
    val durationUnit: TodoDurationUnit = TodoDurationUnit.Minute,
    val hasTimer: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class TodoCompletion(
    val id: String,
    val todoId: String,
    val completedAt: Long
)

enum class TodoIntervalUnit(val label: String) {
    Minute("Minute"),
    Hour("Hour"),
    Day("Day"),
    Week("Week")
}

enum class TodoDurationUnit(val label: String) {
    Minute("Minute"),
    Hour("Hour")
}
