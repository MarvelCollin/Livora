package com.example.livora.data.model

enum class AcMode {
    COOL, HEAT, DRY, FAN, AUTO
}

enum class FanSpeed {
    LOW, MEDIUM, HIGH, AUTO
}

enum class SwingMode {
    OFF, VERTICAL, HORIZONTAL, BOTH
}

data class AcState(
    val isPoweredOn: Boolean = false,
    val temperature: Int = 24,
    val mode: AcMode = AcMode.COOL,
    val fanSpeed: FanSpeed = FanSpeed.AUTO,
    val swingMode: SwingMode = SwingMode.OFF,
    val isSleepMode: Boolean = false,
    val isEnergySaving: Boolean = false,
    val isDisplayOn: Boolean = true,
    val timerHours: Int = 0
) {
    companion object {
        const val MIN_TEMP = 16
        const val MAX_TEMP = 30
    }
}
