package com.example.livora.data.ir.protocol

import com.example.livora.data.ir.ALL_MODES
import com.example.livora.data.ir.ALL_SWINGS
import com.example.livora.data.ir.AcCapabilities
import com.example.livora.data.ir.AcChange
import com.example.livora.data.ir.AcProtocol
import com.example.livora.data.ir.BASIC_FANS
import com.example.livora.data.ir.BitTiming
import com.example.livora.data.ir.ByteFrame
import com.example.livora.data.ir.IrSignal
import com.example.livora.data.ir.reverseByte
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed

class HitachiProtocol : AcProtocol {

    override val capabilities = AcCapabilities(
        modes = ALL_MODES,
        fanSpeeds = BASIC_FANS,
        swingModes = ALL_SWINGS,
        minTemp = 16,
        maxTemp = 30
    )

    private val frame = ByteFrame(
        frequency = 38000,
        headerMark = 3300,
        headerSpace = 1700,
        timing = BitTiming(mark = 400, oneSpace = 1250, zeroSpace = 500),
        footerMark = 400,
        gap = 100000,
        msbFirst = true
    )

    override fun encode(state: AcState, previous: AcState, change: AcChange): List<IrSignal> {
        if (!state.isPoweredOn && change != AcChange.POWER) return emptyList()
        if (change == AcChange.SLEEP || change == AcChange.ECO || change == AcChange.DISPLAY) {
            return emptyList()
        }
        return listOf(frame.encode(buildState(state)))
    }

    internal fun buildState(state: AcState): IntArray {
        val bytes = IntArray(STATE_LENGTH)
        bytes[0] = 0x80
        bytes[1] = 0x08
        bytes[2] = 0x0C
        bytes[3] = 0x02
        bytes[4] = 0xFD
        bytes[5] = 0x80
        bytes[6] = 0x7F
        bytes[7] = 0x88
        bytes[8] = 0x48

        val mode = when (state.mode) {
            AcMode.AUTO -> 2
            AcMode.HEAT -> 3
            AcMode.COOL -> 4
            AcMode.DRY -> 5
            AcMode.FAN -> 0xC
        }
        val temperature = if (state.mode == AcMode.FAN) 64 else state.temperature.coerceIn(16, 32)
        var fan = when (state.fanSpeed) {
            FanSpeed.AUTO -> 1
            FanSpeed.QUIET, FanSpeed.LOW -> 2
            FanSpeed.MEDIUM -> 3
            FanSpeed.HIGH -> 5
        }
        when (state.mode) {
            AcMode.DRY -> fan = fan.coerceIn(2, 3)
            AcMode.FAN -> fan = fan.coerceAtLeast(2)
            else -> Unit
        }

        bytes[9] = if (temperature == 16) 0x80 else 0x10
        bytes[10] = mode.reverseByte()
        bytes[11] = (temperature shl 1).reverseByte()
        bytes[13] = fan.reverseByte()
        bytes[14] = if (state.swingMode.hasVertical) 0x80 else 0x00
        bytes[15] = if (state.swingMode.hasHorizontal) 0x80 else 0x00
        bytes[17] = if (state.isPoweredOn) 0x01 else 0x00
        bytes[24] = 0x80

        var sum = 62
        for (index in 0 until STATE_LENGTH - 1) sum -= bytes[index].reverseByte()
        bytes[STATE_LENGTH - 1] = (sum and 0xFF).reverseByte()
        return bytes
    }

    private companion object {
        const val STATE_LENGTH = 28
    }
}
