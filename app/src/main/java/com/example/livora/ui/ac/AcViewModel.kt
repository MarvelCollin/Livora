package com.example.livora.ui.ac

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.livora.data.ir.IrBlasterController
import com.example.livora.data.ir.LgAcIrEncoder
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed
import com.example.livora.data.model.SwingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AcViewModel(application: Application) : AndroidViewModel(application) {

    private val irController = IrBlasterController(application)

    private val _acState = MutableStateFlow(AcState())
    val acState: StateFlow<AcState> = _acState.asStateFlow()

    val isIrAvailable: Boolean get() = irController.isAvailable

    private fun transmitState(state: AcState) {
        viewModelScope.launch(Dispatchers.IO) {
            irController.transmit(LgAcIrEncoder.CARRIER_FREQUENCY, LgAcIrEncoder.encodeState(state))
        }
    }

    private fun transmitPowerOff() {
        viewModelScope.launch(Dispatchers.IO) {
            irController.transmit(LgAcIrEncoder.CARRIER_FREQUENCY, LgAcIrEncoder.encodePowerOff())
        }
    }

    fun togglePower() {
        _acState.update { it.copy(isPoweredOn = !it.isPoweredOn) }
        val current = _acState.value
        if (current.isPoweredOn) transmitState(current) else transmitPowerOff()
    }

    fun increaseTemperature() {
        _acState.update { state ->
            if (state.temperature < AcState.MAX_TEMP) state.copy(temperature = state.temperature + 1)
            else state
        }
        if (_acState.value.isPoweredOn) transmitState(_acState.value)
    }

    fun decreaseTemperature() {
        _acState.update { state ->
            if (state.temperature > AcState.MIN_TEMP) state.copy(temperature = state.temperature - 1)
            else state
        }
        if (_acState.value.isPoweredOn) transmitState(_acState.value)
    }

    fun setMode(mode: AcMode) {
        _acState.update { it.copy(mode = mode) }
        if (_acState.value.isPoweredOn) transmitState(_acState.value)
    }

    fun setFanSpeed(speed: FanSpeed) {
        _acState.update { it.copy(fanSpeed = speed) }
        if (_acState.value.isPoweredOn) transmitState(_acState.value)
    }

    fun setSwingMode(swing: SwingMode) {
        _acState.update { it.copy(swingMode = swing) }
    }

    fun toggleSleepMode() {
        _acState.update { it.copy(isSleepMode = !it.isSleepMode) }
    }

    fun toggleEnergySaving() {
        _acState.update { it.copy(isEnergySaving = !it.isEnergySaving) }
    }

    fun toggleDisplay() {
        _acState.update { it.copy(isDisplayOn = !it.isDisplayOn) }
    }

    fun setTimer(hours: Int) {
        _acState.update { it.copy(timerHours = hours.coerceIn(0, 24)) }
    }

    fun increaseTimer() {
        _acState.update { state ->
            if (state.timerHours < 24) state.copy(timerHours = state.timerHours + 1)
            else state
        }
    }

    fun decreaseTimer() {
        _acState.update { state ->
            if (state.timerHours > 0) state.copy(timerHours = state.timerHours - 1)
            else state
        }
    }
}
