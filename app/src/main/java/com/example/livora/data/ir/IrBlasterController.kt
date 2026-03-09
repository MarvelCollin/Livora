package com.example.livora.data.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import com.example.livora.util.LivoraLogger

class IrBlasterController(context: Context) {

    private val irManager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val isAvailable: Boolean
        get() = irManager?.hasIrEmitter() == true

    init {
        LivoraLogger.debug(TAG, "ConsumerIrManager resolved: ${irManager != null}")
        LivoraLogger.debug(TAG, "hasIrEmitter: ${irManager?.hasIrEmitter()}")
        if (irManager != null) {
            val freqs = irManager.carrierFrequencies
            if (freqs != null) {
                freqs.forEach { range ->
                    LivoraLogger.debug(TAG, "Supported frequency range: ${range.minFrequency} Hz - ${range.maxFrequency} Hz")
                }
            } else {
                LivoraLogger.debug(TAG, "Carrier frequencies: null")
            }
        }
    }

    fun transmit(frequency: Int, pattern: IntArray) {
        LivoraLogger.debug(TAG, "transmit isAvailable=$isAvailable frequency=$frequency patternLength=${pattern.size}")
        LivoraLogger.debug(TAG, "pattern=${pattern.take(10).joinToString()}, ...")
        if (!isAvailable) {
            LivoraLogger.debug(TAG, "SKIPPED — IR emitter not available")
            return
        }
        try {
            irManager?.transmit(frequency, pattern)
            LivoraLogger.debug(TAG, "transmit SUCCESS")
        } catch (e: Exception) {
            LivoraLogger.debug(TAG, "transmit FAILED: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "Livora.IrBlaster"
    }
}
