package com.example.livora.data.ir

import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed
import com.example.livora.data.model.SwingMode

enum class AcChange {
    POWER, TEMPERATURE, MODE, FAN, SWING, SLEEP, ECO, DISPLAY
}

data class AcCapabilities(
    val modes: List<AcMode>,
    val fanSpeeds: List<FanSpeed>,
    val swingModes: List<SwingMode>,
    val minTemp: Int,
    val maxTemp: Int,
    val hasSleep: Boolean = false,
    val hasEco: Boolean = false,
    val hasDisplay: Boolean = false
) {
    fun sanitize(state: AcState): AcState = state.copy(
        temperature = state.temperature.coerceIn(minTemp, maxTemp),
        mode = if (state.mode in modes) state.mode else modes.first(),
        fanSpeed = if (state.fanSpeed in fanSpeeds) state.fanSpeed else fanSpeeds.last(),
        swingMode = if (state.swingMode in swingModes) state.swingMode else SwingMode.OFF,
        isSleepMode = state.isSleepMode && hasSleep,
        isEnergySaving = state.isEnergySaving && hasEco,
        isDisplayOn = if (hasDisplay) state.isDisplayOn else true
    )
}

interface AcProtocol {

    val capabilities: AcCapabilities

    fun encode(state: AcState, previous: AcState, change: AcChange): List<IrSignal>

    fun encodePowerOff(): List<IrSignal> = encode(
        state = AcState(isPoweredOn = false),
        previous = AcState(isPoweredOn = true),
        change = AcChange.POWER
    )
}

internal val ALL_MODES = listOf(AcMode.COOL, AcMode.HEAT, AcMode.DRY, AcMode.FAN, AcMode.AUTO)
internal val ALL_FANS = listOf(FanSpeed.QUIET, FanSpeed.LOW, FanSpeed.MEDIUM, FanSpeed.HIGH, FanSpeed.AUTO)
internal val BASIC_FANS = listOf(FanSpeed.LOW, FanSpeed.MEDIUM, FanSpeed.HIGH, FanSpeed.AUTO)
internal val ALL_SWINGS = listOf(SwingMode.OFF, SwingMode.VERTICAL, SwingMode.HORIZONTAL, SwingMode.BOTH)
internal val VERTICAL_SWINGS = listOf(SwingMode.OFF, SwingMode.VERTICAL)
