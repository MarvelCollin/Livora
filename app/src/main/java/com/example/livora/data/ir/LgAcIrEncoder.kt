package com.example.livora.data.ir

import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed
import com.example.livora.util.LivoraLogger

data class IrTimingProfile(
    val name: String,
    val frequency: Int,
    val hdrMark: Int,
    val hdrSpace: Int,
    val bitMark: Int,
    val oneSpace: Int,
    val zeroSpace: Int
)

object LgAcIrEncoder {

    private const val TAG = "Livora.IrEncoder"

    private val LG1 = IrTimingProfile(
        name = "LG1",
        frequency = 38000,
        hdrMark = 8500,
        hdrSpace = 4250,
        bitMark = 550,
        oneSpace = 1600,
        zeroSpace = 550
    )

    private fun modeNibble(mode: AcMode): Int = when (mode) {
        AcMode.COOL -> 0x0
        AcMode.DRY  -> 0x1
        AcMode.FAN  -> 0x2
        AcMode.AUTO -> 0x3
        AcMode.HEAT -> 0x4
    }

    private fun fanNibble(speed: FanSpeed): Int = when (speed) {
        FanSpeed.LOW    -> 0x0
        FanSpeed.MEDIUM -> 0x2
        FanSpeed.HIGH   -> 0x4
        FanSpeed.AUTO   -> 0x5
    }

    private fun calcChecksum(code: Int): Int {
        var sum = 0
        var value = code shr 4
        for (i in 0 until 6) {
            sum += value and 0xF
            value = value shr 4
        }
        return sum and 0xF
    }

    fun encodeState(state: AcState): Pair<Int, IntArray> {
        val mode = modeNibble(state.mode)
        val fan = fanNibble(state.fanSpeed)
        val temp15 = state.temperature - 15
        val codeWithoutChk = (0x88 shl 20) or
                (mode shl 16) or
                (0x0 shl 12) or
                (temp15 shl 8) or
                (fan shl 4)
        val chk = calcChecksum(codeWithoutChk)
        val code = codeWithoutChk or chk
        LivoraLogger.debug(TAG, "encodeState code=0x${code.toString(16)} mode=${state.mode}(0x${mode.toString(16)}) fan=${state.fanSpeed}(0x${fan.toString(16)}) temp=${state.temperature}(nibble=0x${temp15.toString(16)}) chk=0x${chk.toString(16)}")
        return Pair(LG1.frequency, buildPattern(LG1, code))
    }

    fun encodePowerOff(): Pair<Int, IntArray> {
        val code = 0x88C0051
        LivoraLogger.debug(TAG, "encodePowerOff code=0x${code.toString(16)}")
        return Pair(LG1.frequency, buildPattern(LG1, code))
    }

    private fun buildPattern(timing: IrTimingProfile, code: Int): IntArray {
        val pattern = mutableListOf(timing.hdrMark, timing.hdrSpace)
        for (i in 27 downTo 0) {
            pattern.add(timing.bitMark)
            pattern.add(if ((code shr i) and 1 == 1) timing.oneSpace else timing.zeroSpace)
        }
        pattern.add(timing.bitMark)
        val result = pattern.toIntArray()
        val bits = (27 downTo 0).map { if ((code shr it) and 1 == 1) '1' else '0' }.joinToString("")
        LivoraLogger.debug(TAG, "buildPattern timing=${timing.name} pulses=${result.size} bits=$bits")
        return result
    }
}
