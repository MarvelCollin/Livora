package com.example.livora.data.ir.protocol

import com.example.livora.data.ir.ALL_FANS
import com.example.livora.data.ir.ALL_MODES
import com.example.livora.data.ir.AcCapabilities
import com.example.livora.data.ir.AcChange
import com.example.livora.data.ir.AcProtocol
import com.example.livora.data.ir.BitTiming
import com.example.livora.data.ir.ByteFrame
import com.example.livora.data.ir.IrSignal
import com.example.livora.data.ir.VERTICAL_SWINGS
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed

class ToshibaProtocol : AcProtocol {

    override val capabilities = AcCapabilities(
        modes = ALL_MODES,
        fanSpeeds = ALL_FANS,
        swingModes = VERTICAL_SWINGS,
        minTemp = 17,
        maxTemp = 30,
        hasEco = true
    )

    private val frame = ByteFrame(
        frequency = 38000,
        headerMark = 4400,
        headerSpace = 4300,
        timing = BitTiming(mark = 580, oneSpace = 1600, zeroSpace = 490),
        footerMark = 580,
        gap = 7400,
        msbFirst = true
    )

    override fun encode(state: AcState, previous: AcState, change: AcChange): List<IrSignal> {
        if (!state.isPoweredOn && change != AcChange.POWER) return emptyList()
        if (change == AcChange.SLEEP || change == AcChange.DISPLAY) return emptyList()
        if (change == AcChange.SWING) {
            return listOf(frame.encode(swingState(state), repeats = 2))
        }
        return listOf(frame.encode(mainState(state), repeats = 2))
    }

    internal fun mainState(state: AcState): IntArray {
        val length = if (state.isEnergySaving) LONG_LENGTH else NORMAL_LENGTH
        val bytes = IntArray(length)
        bytes[0] = 0xF2
        bytes[1] = 0x0D
        bytes[2] = length - 6
        bytes[3] = bytes[2].inv() and 0xFF
        bytes[4] = 0x01 or (if (length == LONG_LENGTH) 0x08 else 0x00)
        bytes[5] = (state.temperature.coerceIn(17, 30) - 17) shl 4

        val mode = if (!state.isPoweredOn) {
            7
        } else {
            when (state.mode) {
                AcMode.AUTO -> 0
                AcMode.COOL -> 1
                AcMode.DRY -> 2
                AcMode.HEAT -> 3
                AcMode.FAN -> 4
            }
        }
        val fan = when (state.fanSpeed) {
            FanSpeed.AUTO -> 0
            FanSpeed.QUIET -> 2
            FanSpeed.LOW -> 3
            FanSpeed.MEDIUM -> 4
            FanSpeed.HIGH -> 6
        }
        bytes[6] = mode or (fan shl 5)
        bytes[7] = 0x00
        if (length == LONG_LENGTH) bytes[8] = 0x03
        return finish(bytes)
    }

    internal fun swingState(state: AcState): IntArray {
        val bytes = IntArray(SHORT_LENGTH)
        bytes[0] = 0xF2
        bytes[1] = 0x0D
        bytes[2] = SHORT_LENGTH - 6
        bytes[3] = bytes[2].inv() and 0xFF
        bytes[4] = 0x01 or 0x20
        bytes[5] = if (state.swingMode.hasVertical) 1 else 2
        return finish(bytes)
    }

    private fun finish(bytes: IntArray): IntArray {
        var xor = 0
        for (index in 0 until bytes.size - 1) xor = xor xor bytes[index]
        bytes[bytes.size - 1] = xor
        return bytes
    }

    private companion object {
        const val SHORT_LENGTH = 7
        const val NORMAL_LENGTH = 9
        const val LONG_LENGTH = 10
    }
}
