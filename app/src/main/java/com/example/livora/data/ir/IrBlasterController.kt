package com.example.livora.data.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import com.example.livora.util.Logger

class IrBlasterController(context: Context) {

    private val irManager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val isAvailable: Boolean
        get() = irManager?.hasIrEmitter() == true

    init {
        Logger.debug(TAG, "ConsumerIrManager resolved: ${irManager != null}")
        Logger.debug(TAG, "hasIrEmitter: ${irManager?.hasIrEmitter()}")
        if (irManager != null) {
            val freqs = irManager.carrierFrequencies
            if (freqs != null) {
                freqs.forEach { range ->
                    Logger.debug(TAG, "Supported frequency range: ${range.minFrequency} Hz - ${range.maxFrequency} Hz")
                }
            } else {
                Logger.debug(TAG, "Carrier frequencies: null")
            }
        }
    }

    fun transmit(frequency: Int, pattern: IntArray) {
        Logger.debug(TAG, "transmit isAvailable=$isAvailable frequency=$frequency patternLength=${pattern.size}")
        Logger.debug(TAG, "pattern=${pattern.take(10).joinToString()}, ...")
        if (!isAvailable) {
            Logger.debug(TAG, "SKIPPED — IR emitter not available")
            return
        }
        try {
            irManager?.transmit(frequency, pattern)
            Logger.debug(TAG, "transmit SUCCESS")
        } catch (e: Exception) {
            Logger.debug(TAG, "transmit FAILED: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "Livora.IrBlaster"
    }
}
