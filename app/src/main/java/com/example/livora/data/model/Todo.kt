package com.example.livora.data.model

data class Todo(
    val id: String,
    val title: String,
    val notes: String = "",
    val intervalValue: Int = 1,
    val intervalUnit: TodoIntervalUnit = TodoIntervalUnit.Day,
    val durationValue: Int = 30,
    val durationUnit: TodoDurationUnit = TodoDurationUnit.Minute,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
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
