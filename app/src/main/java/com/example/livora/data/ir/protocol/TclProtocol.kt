package com.example.livora.data.ir.protocol

import com.example.livora.data.ir.ALL_FANS
import com.example.livora.data.ir.ALL_MODES
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

class TclProtocol : AcProtocol {

    override val capabilities = AcCapabilities(
        modes = ALL_MODES,
        fanSpeeds = ALL_FANS,
        swingModes = ALL_SWINGS,
        minTemp = 16,
        maxTemp = 30,
        hasEco = true,
        hasDisplay = true
    )

    private val frame = ByteFrame(
        frequency = 38000,
        headerMark = 3000,
        headerSpace = 1650,
        timing = BitTiming(mark = 500, oneSpace = 1050, zeroSpace = 325),
        footerMark = 500,
        gap = 100000
    )

    override fun encode(state: AcState, previous: AcState, change: AcChange): List<IrSignal> {
        if (!state.isPoweredOn && change != AcChange.POWER) return emptyList()
        if (change == AcChange.SLEEP) return emptyList()
        return listOf(frame.encode(buildState(state)))
    }

    internal fun buildState(state: AcState): IntArray {
        val bytes = IntArray(STATE_LENGTH)
        bytes[0] = 0x23
        bytes[1] = 0xCB
        bytes[2] = 0x26
        bytes[3] = 0x01
        bytes[4] = 0x00

        val power = if (state.isPoweredOn) 1 else 0
        val light = if (state.isDisplayOn) 0 else 1
        val eco = if (state.isEnergySaving) 1 else 0
        bytes[5] = (power shl 2) or (1 shl 5) or (light shl 6) or (eco shl 7)

        val mode = when (state.mode) {
            AcMode.HEAT -> 1
            AcMode.DRY -> 2
            AcMode.COOL -> 3
            AcMode.FAN -> 7
            AcMode.AUTO -> 8
        }
        bytes[6] = mode
        bytes[7] = 31 - state.temperature.coerceIn(16, 30)

        val fan = when {
            state.mode == AcMode.FAN && state.fanSpeed == FanSpeed.AUTO -> 0b101
            else -> when (state.fanSpeed) {
                FanSpeed.AUTO -> 0b000
                FanSpeed.QUIET -> 0b001
                FanSpeed.LOW -> 0b010
                FanSpeed.MEDIUM -> 0b011
                FanSpeed.HIGH -> 0b101
            }
        }
        val swingVertical = if (state.swingMode.hasVertical) 0b111 else 0b000
        bytes[8] = fan or (swingVertical shl 3)

        val swingHorizontal = if (state.swingMode.hasHorizontal) 1 else 0
        bytes[12] = (swingHorizontal shl 3) or 0x80

        var sum = 0
        for (index in 0 until STATE_LENGTH - 1) sum += bytes[index]
        bytes[STATE_LENGTH - 1] = sum and 0xFF
        return bytes
    }

    private companion object {
        const val STATE_LENGTH = 14
    }
}
