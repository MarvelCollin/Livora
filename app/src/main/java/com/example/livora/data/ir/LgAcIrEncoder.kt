package com.example.livora.data.ir

import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed

object LgAcIrEncoder {

    const val CARRIER_FREQUENCY = 38000

    private const val HDR_MARK = 8500
    private const val HDR_SPACE = 4250
    private const val BIT_MARK = 550
    private const val ONE_SPACE = 1650
    private const val ZERO_SPACE = 550

    private const val PREAMBLE = 0x8
    private const val TEMP_OFFSET = 16

    private const val POWER_OFF_CODE = 0x8813054

    private fun modeNibble(mode: AcMode): Int = when (mode) {
        AcMode.AUTO -> 0x0
        AcMode.DRY  -> 0x1
        AcMode.HEAT -> 0x4
        AcMode.COOL -> 0x8
        AcMode.FAN  -> 0xC
    }

    private fun fanNibble(speed: FanSpeed): Int = when (speed) {
        FanSpeed.AUTO   -> 0x0
        FanSpeed.LOW    -> 0x2
        FanSpeed.MEDIUM -> 0x3
        FanSpeed.HIGH   -> 0x4
    }

    private fun checksum(mode: Int, fan: Int, temp: Int): Int =
        (mode + fan + temp) and 0xF

    fun encodeState(state: AcState): IntArray {
        val mode = modeNibble(state.mode)
        val fan  = fanNibble(state.fanSpeed)
        val temp = state.temperature - TEMP_OFFSET
        val chk  = checksum(mode, fan, temp)
        val code = (PREAMBLE shl 24) or
                   (mode    shl 20) or
                   (fan     shl 16) or
                   (temp    shl 12) or
                   chk
        return buildPattern(code)
    }

    fun encodePowerOff(): IntArray = buildPattern(POWER_OFF_CODE)

    private fun buildPattern(code: Int): IntArray {
        val pattern = mutableListOf(HDR_MARK, HDR_SPACE)
        for (i in 27 downTo 0) {
            pattern.add(BIT_MARK)
            pattern.add(if ((code shr i) and 1 == 1) ONE_SPACE else ZERO_SPACE)
        }
        pattern.add(BIT_MARK)
        return pattern.toIntArray()
    }
}
