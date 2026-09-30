package com.example.livora.data.ir.protocol

import com.example.livora.data.ir.ALL_MODES
import com.example.livora.data.ir.ALL_SWINGS
import com.example.livora.data.ir.AcCapabilities
import com.example.livora.data.ir.AcChange
import com.example.livora.data.ir.AcProtocol
import com.example.livora.data.ir.BASIC_FANS
import com.example.livora.data.ir.BitTiming
import com.example.livora.data.ir.IrPulseBuilder
import com.example.livora.data.ir.IrSignal
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed

enum class GreeVariant {
    YAW1F, YBOFB
}

class GreeProtocol(private val variant: GreeVariant) : AcProtocol {

    override val capabilities = AcCapabilities(
        modes = ALL_MODES,
        fanSpeeds = BASIC_FANS,
        swingModes = ALL_SWINGS,
        minTemp = 16,
        maxTemp = 30,
        hasSleep = true,
        hasDisplay = true
    )

    private val timing = BitTiming(mark = 620, oneSpace = 1600, zeroSpace = 540)

    override fun encode(state: AcState, previous: AcState, change: AcChange): List<IrSignal> {
        if (!state.isPoweredOn && change != AcChange.POWER) return emptyList()
        if (change == AcChange.ECO) return emptyList()
        return listOf(signal(buildState(state)))
    }

    private fun buildState(state: AcState): IntArray {
        val mode = when (state.mode) {
            AcMode.AUTO -> 0
            AcMode.COOL -> 1
            AcMode.DRY -> 2
            AcMode.FAN -> 3
            AcMode.HEAT -> 4
        }
        val fan = if (state.mode == AcMode.DRY) 1 else when (state.fanSpeed) {
            FanSpeed.AUTO -> 0
            FanSpeed.QUIET, FanSpeed.LOW -> 1
            FanSpeed.MEDIUM -> 2
            FanSpeed.HIGH -> 3
        }
        val temperature = if (state.mode == AcMode.AUTO) 25 else state.temperature.coerceIn(16, 30)
        val power = if (state.isPoweredOn) 1 else 0
        val verticalSwing = if (state.swingMode.hasVertical) 1 else 0
        val horizontalSwing = if (state.swingMode.hasHorizontal) 1 else 0
        val sleep = if (state.isSleepMode) 1 else 0
        val light = if (state.isDisplayOn) 1 else 0
        val modelA = if (state.isPoweredOn && variant == GreeVariant.YAW1F) 1 else 0

        val bytes = IntArray(8)
        bytes[0] = mode or (power shl 3) or (fan shl 4) or (verticalSwing shl 6) or (sleep shl 7)
        bytes[1] = temperature - 16
        bytes[2] = (light shl 5) or (modelA shl 6)
        bytes[3] = 0x50
        bytes[4] = verticalSwing or (horizontalSwing shl 4)
        bytes[5] = 0x20
        bytes[6] = 0
        bytes[7] = checksum(bytes) shl 4
        return bytes
    }

    private fun checksum(bytes: IntArray): Int {
        var sum = 10
        for (index in 0 until 4) sum += bytes[index] and 0xF
        for (index in 4 until 7) sum += bytes[index] shr 4
        return sum and 0xF
    }

    private fun signal(bytes: IntArray): IrSignal {
        val builder = IrPulseBuilder(38000)
        builder.header(9000, 4500)
        builder.bytesLsbFirst(bytes, 0, 4, timing)
        builder.bitsLsbFirst(0b010, 3, timing)
        builder.footer(timing.mark, 19980)
        builder.bytesLsbFirst(bytes, 4, 8, timing)
        builder.footer(timing.mark, 19980)
        return builder.build()
    }
}
