package com.example.livora.data.people.scan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ScanPhase { Idle, Preparing, Scanning, Grouping, Done, Failed }

data class ScanProgress(
    val phase: ScanPhase = ScanPhase.Idle,
    val scanned: Int = 0,
    val total: Int = 0,
    val faces: Int = 0,
    val photosPerSecond: Float = 0f,
    val message: String? = null
) {
    val active: Boolean get() = phase == ScanPhase.Preparing || phase == ScanPhase.Scanning || phase == ScanPhase.Grouping
    val fraction: Float get() = if (total <= 0) 0f else (scanned.toFloat() / total).coerceIn(0f, 1f)
}

object ScanStatus {

    private val state = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = state.asStateFlow()

    fun publish(value: ScanProgress) {
        state.value = value
    }

    fun update(transform: (ScanProgress) -> ScanProgress) {
        state.value = transform(state.value)
    }

    fun reset() {
        state.value = ScanProgress()
    }
}
