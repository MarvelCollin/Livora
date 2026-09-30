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

enum class MitsubishiHeavyVariant {
    ZMS_152, ZJS_88
}

class MitsubishiHeavyProtocol(private val variant: MitsubishiHeavyVariant) : AcProtocol {

    override val capabilities = AcCapabilities(
        modes = ALL_MODES,
        fanSpeeds = ALL_FANS,
        swingModes = ALL_SWINGS,
        minTemp = 17,
        maxTemp = 30
    )

    private val frame = ByteFrame(
        frequency = 38000,
        headerMark = 3140,
        headerSpace = 1630,
        timing = BitTiming(mark = 370, oneSpace = 420, zeroSpace = 1220),
        footerMark = 370,
        gap = 100000
    )

    override fun encode(state: AcState, previous: AcState, change: AcChange): List<IrSignal> {
        if (!state.isPoweredOn && change != AcChange.POWER) return emptyList()
        if (change == AcChange.SLEEP || change == AcChange.ECO || change == AcChange.DISPLAY) {
            return emptyList()
        }
        return listOf(frame.encode(buildState(state)))
    }

    internal fun buildState(state: AcState): IntArray =
        if (variant == MitsubishiHeavyVariant.ZMS_152) build152(state) else build88(state)

    private fun modeBits(mode: AcMode): Int = when (mode) {
        AcMode.AUTO -> 0
        AcMode.COOL -> 1
        AcMode.DRY -> 2
        AcMode.FAN -> 3
        AcMode.HEAT -> 4
    }

    private fun build152(state: AcState): IntArray {
        val bytes = IntArray(152 / 8)
        SIGNATURE_152.copyInto(bytes)
        val power = if (state.isPoweredOn) 1 else 0
        bytes[5] = modeBits(state.mode) or (power shl 3)
        bytes[7] = state.temperature.coerceIn(17, 31) - 17
        val fan = when (state.fanSpeed) {
            FanSpeed.AUTO -> 0
            FanSpeed.QUIET -> 6
            FanSpeed.LOW -> 1
            FanSpeed.MEDIUM -> 2
            FanSpeed.HIGH -> 4
        }
        bytes[9] = fan
        val swingVertical = if (state.swingMode.hasVertical) 0 else 6
        bytes[11] = swingVertical shl 5
        bytes[13] = if (state.swingMode.hasHorizontal) 0 else 8
        bytes[15] = 0
        bytes[17] = 0x80
        invertPairs(bytes)
        return bytes
    }

    private fun build88(state: AcState): IntArray {
        val bytes = IntArray(88 / 8)
        SIGNATURE_88.copyInto(bytes)
        val swingVertical = if (state.swingMode.hasVertical) 0b100 else 0b000
        val swingHorizontal = if (state.swingMode.hasHorizontal) 0b1000 else 0b0000
        bytes[5] = ((swingVertical and 1) shl 1) or
            ((swingHorizontal and 0b11) shl 2) or
            (((swingHorizontal shr 2) and 0b11) shl 6)
        val fan = when (state.fanSpeed) {
            FanSpeed.AUTO -> 0
            FanSpeed.QUIET -> 7
            FanSpeed.LOW -> 2
            FanSpeed.MEDIUM -> 3
            FanSpeed.HIGH -> 4
        }
        bytes[7] = ((swingVertical shr 1) shl 3) or (fan shl 5)
        val power = if (state.isPoweredOn) 1 else 0
        bytes[9] = modeBits(state.mode) or (power shl 3) or ((state.temperature.coerceIn(17, 31) - 17) shl 4)
        invertPairs(bytes)
        return bytes
    }

    private fun invertPairs(bytes: IntArray) {
        var index = 3
        while (index + 1 < bytes.size) {
            bytes[index + 1] = bytes[index].inv() and 0xFF
            index += 2
        }
    }

    private companion object {
        val SIGNATURE_152 = intArrayOf(0xAD, 0x51, 0x3C, 0xE5, 0x1A)
        val SIGNATURE_88 = intArrayOf(0xAD, 0x51, 0x3C, 0xD9, 0x26)
    }
}
