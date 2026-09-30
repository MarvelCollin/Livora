package com.example.livora.data.ir.protocol

import com.example.livora.data.ir.ALL_MODES
import com.example.livora.data.ir.ALL_FANS
import com.example.livora.data.ir.ALL_SWINGS
import com.example.livora.data.ir.AcCapabilities
import com.example.livora.data.ir.AcChange
import com.example.livora.data.ir.AcProtocol
import com.example.livora.data.ir.BitTiming
import com.example.livora.data.ir.ByteFrame
import com.example.livora.data.ir.IrSignal
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed

class MitsubishiProtocol : AcProtocol {

    override val capabilities = AcCapabilities(
        modes = ALL_MODES,
        fanSpeeds = ALL_FANS,
        swingModes = ALL_SWINGS,
        minTemp = 16,
        maxTemp = 30
    )

    private val frame = ByteFrame(
        frequency = 38000,
        headerMark = 3400,
        headerSpace = 1750,
        timing = BitTiming(mark = 450, oneSpace = 1300, zeroSpace = 420),
        footerMark = 440,
        gap = 15500
    )

    override fun encode(state: AcState, previous: AcState, change: AcChange): List<IrSignal> {
        if (!state.isPoweredOn && change != AcChange.POWER) return emptyList()
        if (change == AcChange.SLEEP || change == AcChange.ECO || change == AcChange.DISPLAY) {
            return emptyList()
        }
        return listOf(frame.encode(buildState(state), repeats = 2))
    }

    internal fun buildState(state: AcState): IntArray {
        val bytes = IntArray(STATE_LENGTH)
        bytes[0] = 0x23
        bytes[1] = 0xCB
        bytes[2] = 0x26
        bytes[3] = 0x01
        bytes[4] = 0x00
        bytes[5] = if (state.isPoweredOn) 0x20 else 0x00

        val mode = when (state.mode) {
            AcMode.AUTO -> 0b100
            AcMode.COOL -> 0b011
            AcMode.DRY -> 0b010
            AcMode.HEAT -> 0b001
            AcMode.FAN -> 0b111
        }
        val modeLow = when (state.mode) {
            AcMode.COOL -> 0b0110
            AcMode.DRY -> 0b0010
            AcMode.FAN -> 0b0111
            AcMode.AUTO, AcMode.HEAT -> 0b0000
        }
        bytes[6] = mode shl 3
        bytes[7] = state.temperature.coerceIn(16, 30) - 16

        val wideVane = if (state.swingMode.hasHorizontal) 0b1000 else 0b0011
        bytes[8] = (wideVane shl 4) or modeLow

        val fanAuto = state.fanSpeed == FanSpeed.AUTO
        val fan = when (state.fanSpeed) {
            FanSpeed.AUTO -> 0
            FanSpeed.QUIET -> 5
            FanSpeed.LOW -> 1
            FanSpeed.MEDIUM -> 2
            FanSpeed.HIGH -> 4
        }
        val vane = if (state.swingMode.hasVertical) 0b111 else 0b000
        bytes[9] = fan or (vane shl 3) or (1 shl 6) or (if (fanAuto) 1 shl 7 else 0)
        bytes[16] = vane shl 3

        var sum = 0
        for (index in 0 until STATE_LENGTH - 1) sum += bytes[index]
        bytes[STATE_LENGTH - 1] = sum and 0xFF
        return bytes
    }

    private companion object {
        const val STATE_LENGTH = 18
    }
}
