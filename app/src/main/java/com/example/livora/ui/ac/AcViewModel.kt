package com.example.livora.ui.ac

import androidx.lifecycle.ViewModel
import com.example.livora.data.model.AcMode
import com.example.livora.data.model.AcState
import com.example.livora.data.model.FanSpeed
import com.example.livora.data.model.SwingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AcViewModel : ViewModel() {

    private val _acState = MutableStateFlow(AcState())
    val acState: StateFlow<AcState> = _acState.asStateFlow()

    fun togglePower() {
        _acState.update { it.copy(isPoweredOn = !it.isPoweredOn) }
    }

    fun increaseTemperature() {
        _acState.update { state ->
            if (state.temperature < AcState.MAX_TEMP) {
                state.copy(temperature = state.temperature + 1)
            } else {
                state
            }
        }
    }

    fun decreaseTemperature() {
        _acState.update { state ->
            if (state.temperature > AcState.MIN_TEMP) {
                state.copy(temperature = state.temperature - 1)
            } else {
                state
            }
        }
    }

    fun setMode(mode: AcMode) {
        _acState.update { it.copy(mode = mode) }
    }

    fun setFanSpeed(speed: FanSpeed) {
        _acState.update { it.copy(fanSpeed = speed) }
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
            if (state.timerHours < 24) {
                state.copy(timerHours = state.timerHours + 1)
            } else {
                state
            }
        }
    }

    fun decreaseTimer() {
        _acState.update { state ->
            if (state.timerHours > 0) {
                state.copy(timerHours = state.timerHours - 1)
            } else {
                state
            }
        }
    }
}
