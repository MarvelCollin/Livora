package com.example.livora.ui.bulb

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.livora.data.model.Bulb
import com.example.livora.data.model.BulbState
import com.example.livora.data.model.BulbScene
import com.example.livora.data.wiz.BulbController
import com.example.livora.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BulbViewModel(application: Application) : AndroidViewModel(application) {

    private val bulbController = BulbController()
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _discoveredBulbs = MutableStateFlow<List<Bulb>>(emptyList())
    val discoveredBulbs: StateFlow<List<Bulb>> = _discoveredBulbs.asStateFlow()

    private val _connectedBulb = MutableStateFlow<Bulb?>(null)
    val connectedBulb: StateFlow<Bulb?> = _connectedBulb.asStateFlow()

    private val _bulbState = MutableStateFlow(BulbState())
    val bulbState: StateFlow<BulbState> = _bulbState.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isAddingBulb = MutableStateFlow(false)
    val isAddingBulb: StateFlow<Boolean> = _isAddingBulb.asStateFlow()

    init {
        loadSavedBulb()
    }

    private fun loadSavedBulb() {
        val ip = prefs.getString(KEY_BULB_IP, null)
        val mac = prefs.getString(KEY_BULB_MAC, null)
        val moduleName = prefs.getString(KEY_BULB_MODULE, "") ?: ""
        if (ip != null && mac != null) {
            val bulb = Bulb(ip = ip, mac = mac, moduleName = moduleName)
            _connectedBulb.value = bulb
            Logger.debug(TAG, "Loaded saved bulb ${bulb.mac} at ${bulb.ip}")
            refreshBulbState()
        }
    }

    private fun saveBulb(bulb: Bulb) {
        prefs.edit()
            .putString(KEY_BULB_IP, bulb.ip)
            .putString(KEY_BULB_MAC, bulb.mac)
            .putString(KEY_BULB_MODULE, bulb.moduleName)
            .apply()
    }

    private fun clearSavedBulb() {
        prefs.edit()
            .remove(KEY_BULB_IP)
            .remove(KEY_BULB_MAC)
            .remove(KEY_BULB_MODULE)
            .apply()
    }

    fun scanForBulbs() {
        _isScanning.value = true
        viewModelScope.launch {
            val bulbs = bulbController.discoverBulbs()
            _discoveredBulbs.value = bulbs
            _isScanning.value = false
            Logger.debug(TAG, "Scan complete, found ${bulbs.size} bulb(s)")
        }
    }

    fun startBulbSetup() {
        _isAddingBulb.value = true
        scanForBulbs()
    }

    fun cancelBulbSetup() {
        _isAddingBulb.value = false
    }

    fun connectToBulb(bulb: Bulb) {
        _isAddingBulb.value = false
        _connectedBulb.value = bulb
        saveBulb(bulb)
        Logger.debug(TAG, "Connected to bulb ${bulb.mac} at ${bulb.ip}")
        refreshBulbState()
    }

    fun disconnectBulb() {
        _isAddingBulb.value = false
        _connectedBulb.value = null
        _bulbState.value = BulbState()
        clearSavedBulb()
        Logger.debug(TAG, "Disconnected from bulb")
    }

    fun refreshBulbState() {
        val bulb = _connectedBulb.value ?: return
        viewModelScope.launch {
            val state = bulbController.getBulbState(bulb.ip)
            if (state != null) {
                _bulbState.value = state
                Logger.debug(TAG, "Refreshed state: on=${state.isPoweredOn} brightness=${state.brightness}")
            }
        }
    }

    fun togglePower() {
        val bulb = _connectedBulb.value ?: return
        val newPower = !_bulbState.value.isPoweredOn
        _bulbState.update { it.copy(isPoweredOn = newPower) }
        viewModelScope.launch {
            bulbController.setPower(bulb.ip, newPower)
        }
    }

    fun powerOn() {
        val bulb = _connectedBulb.value ?: return
        if (!_bulbState.value.isPoweredOn) {
            _bulbState.update { it.copy(isPoweredOn = true) }
            viewModelScope.launch {
                bulbController.setPower(bulb.ip, true)
            }
        }
    }

    fun powerOff() {
        val bulb = _connectedBulb.value ?: return
        if (_bulbState.value.isPoweredOn) {
            _bulbState.update { it.copy(isPoweredOn = false) }
            viewModelScope.launch {
                bulbController.setPower(bulb.ip, false)
            }
        }
    }

    fun setBrightness(brightness: Int) {
        val bulb = _connectedBulb.value ?: return
        val clamped = brightness.coerceIn(BulbState.MIN_BRIGHTNESS, BulbState.MAX_BRIGHTNESS)
        _bulbState.update { it.copy(brightness = clamped, isPoweredOn = true) }
        viewModelScope.launch {
            bulbController.setBrightness(bulb.ip, clamped)
        }
    }

    fun increaseBrightness() {
        val current = _bulbState.value.brightness
        if (current < BulbState.MAX_BRIGHTNESS) {
            setBrightness(current + 10)
        }
    }

    fun decreaseBrightness() {
        val current = _bulbState.value.brightness
        if (current > BulbState.MIN_BRIGHTNESS) {
            setBrightness(current - 10)
        }
    }

    fun setColorTemperature(temp: Int) {
        val bulb = _connectedBulb.value ?: return
        val clamped = temp.coerceIn(BulbState.MIN_COLOR_TEMP, BulbState.MAX_COLOR_TEMP)
        _bulbState.update { it.copy(colorTemp = clamped, useRgb = false, sceneId = 0, isPoweredOn = true) }
        viewModelScope.launch {
            bulbController.setColorTemperature(bulb.ip, clamped, _bulbState.value.brightness)
        }
    }

    fun setRgbColor(r: Int, g: Int, b: Int) {
        val bulb = _connectedBulb.value ?: return
        _bulbState.update { it.copy(r = r, g = g, b = b, useRgb = true, sceneId = 0, isPoweredOn = true) }
        viewModelScope.launch {
            bulbController.setRgb(bulb.ip, r, g, b, _bulbState.value.brightness)
        }
    }

    fun setScene(scene: BulbScene) {
        val bulb = _connectedBulb.value ?: return
        _bulbState.update { it.copy(sceneId = scene.id, useRgb = false, isPoweredOn = true) }
        viewModelScope.launch {
            bulbController.setScene(bulb.ip, scene.id)
        }
    }

    fun processVoiceCommand(text: String) {
        val lower = text.lowercase()
        Logger.debug(TAG, "Bulb voice command: $lower")
        when {
            lower.contains("light") && (lower.contains("on") || lower.contains("turn on")) -> {
                if (!_bulbState.value.isPoweredOn) togglePower()
            }
            lower.contains("light") && (lower.contains("off") || lower.contains("turn off")) -> {
                if (_bulbState.value.isPoweredOn) togglePower()
            }
            lower.contains("bright") && lower.contains("up") -> increaseBrightness()
            lower.contains("bright") && lower.contains("down") -> decreaseBrightness()
            lower.contains("warm") -> setColorTemperature(2700)
            lower.contains("cool") && lower.contains("white") -> setColorTemperature(6500)
            lower.contains("daylight") -> setColorTemperature(5000)
            else -> {
                val brightnessMatch = Regex("(\\d+)\\s*(%|percent)").find(lower)
                if (brightnessMatch != null) {
                    val value = brightnessMatch.groupValues[1].toIntOrNull()
                    if (value != null) {
                        setBrightness(value.coerceIn(BulbState.MIN_BRIGHTNESS, BulbState.MAX_BRIGHTNESS))
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "Livora.BulbViewModel"
        private const val PREFS_NAME = "livora_bulb_prefs"
        private const val KEY_BULB_IP = "bulb_ip"
        private const val KEY_BULB_MAC = "bulb_mac"
        private const val KEY_BULB_MODULE = "bulb_module"
    }
}
