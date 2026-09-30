package com.example.livora.data.ir.protocol

import com.example.livora.data.ir.ALL_FANS
import com.example.livora.data.ir.ALL_MODES
import com.example.livora.data.ir.ALL_SWINGS
import com.example.livora.data.ir.AcCapabilities
import com.example.livora.data.ir.AcChange
import com.example.livora.data.ir.AcProtocol
import com.example.livora.data.ir.BitTiming
import com.example.livora.data.ir.IrPulseBuilder
import com.example.livora.data.ir.IrSignal
import com.example.livora.data.ir.VERTICAL_SWINGS
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed

enum class LgVariant {
    TOGGLE_SWING, EXPLICIT_SWING, LG2
}

class LgProtocol(private val variant: LgVariant) : AcProtocol {

    override val capabilities = AcCapabilities(
        modes = ALL_MODES,
        fanSpeeds = ALL_FANS,
        swingModes = if (variant == LgVariant.TOGGLE_SWING) VERTICAL_SWINGS else ALL_SWINGS,
        minTemp = 16,
        maxTemp = 30,
        hasDisplay = variant != LgVariant.TOGGLE_SWING
    )

    private val isLg2 = variant == LgVariant.LG2
    private val headerMark = if (isLg2) 3200 else 8500
    private val headerSpace = if (isLg2) 9900 else 4250
    private val timing = BitTiming(
        mark = if (isLg2) 480 else 550,
        oneSpace = 1600,
        zeroSpace = 550
    )

    override fun encode(state: AcState, previous: AcState, change: AcChange): List<IrSignal> {
        if (!state.isPoweredOn) {
            return if (change == AcChange.POWER) listOf(signal(OFF_COMMAND)) else emptyList()
        }
        return when (change) {
            AcChange.POWER, AcChange.TEMPERATURE, AcChange.MODE, AcChange.FAN ->
                listOf(signal(stateCode(state)))
            AcChange.SWING -> swingSignals(state, previous)
            AcChange.DISPLAY ->
                if (capabilities.hasDisplay) listOf(signal(LIGHT_TOGGLE)) else emptyList()
            AcChange.SLEEP, AcChange.ECO -> emptyList()
        }
    }

    private fun swingSignals(state: AcState, previous: AcState): List<IrSignal> {
        val vertical = state.swingMode.hasVertical
        val horizontal = state.swingMode.hasHorizontal
        if (variant == LgVariant.TOGGLE_SWING) {
            return if (vertical != previous.swingMode.hasVertical) {
                listOf(signal(SWING_V_TOGGLE))
            } else {
                emptyList()
            }
        }
        val codes = ArrayList<Long>()
        if (vertical != previous.swingMode.hasVertical || horizontal == previous.swingMode.hasHorizontal) {
            codes.add(if (vertical) SWING_V_ON else SWING_V_OFF)
        }
        if (horizontal != previous.swingMode.hasHorizontal || vertical == previous.swingMode.hasVertical) {
            codes.add(if (horizontal) SWING_H_ON else SWING_H_OFF)
        }
        return codes.map { signal(it) }
    }

    private fun stateCode(state: AcState): Long {
        val body = (SIGNATURE shl 20) or
            (modeBits(state.mode) shl 12) or
            ((state.temperature - 15).toLong() shl 8) or
            (fanBits(state.fanSpeed) shl 4)
        return body or checksum(body).toLong()
    }

    private fun modeBits(mode: AcMode): Long = when (mode) {
        AcMode.COOL -> 0
        AcMode.DRY -> 1
        AcMode.FAN -> 2
        AcMode.AUTO -> 3
        AcMode.HEAT -> 4
    }

    private fun fanBits(speed: FanSpeed): Long = when (speed) {
        FanSpeed.QUIET -> 0
        FanSpeed.LOW -> 1
        FanSpeed.MEDIUM -> 2
        FanSpeed.HIGH -> 4
        FanSpeed.AUTO -> 5
    }

    private fun checksum(code: Long): Int {
        var value = code shr 4
        var sum = 0
        repeat(6) {
            sum += (value and 0xFL).toInt()
            value = value shr 4
        }
        return sum and 0xF
    }

    private fun signal(code: Long): IrSignal {
        val builder = IrPulseBuilder(38000)
        builder.header(headerMark, headerSpace)
        builder.bitsMsbFirst(code, 28, timing)
        builder.mark(timing.mark)
        return builder.build()
    }

    companion object {
        private const val SIGNATURE = 0x88L
        private const val OFF_COMMAND = 0x88C0051L
        private const val LIGHT_TOGGLE = 0x88C00A6L
        private const val SWING_V_TOGGLE = 0x8810001L
        private const val SWING_V_ON = 0x8813149L
        private const val SWING_V_OFF = 0x881315AL
        private const val SWING_H_ON = 0x881316BL
        private const val SWING_H_OFF = 0x881317CL
    }
}
