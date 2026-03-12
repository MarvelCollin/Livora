package com.example.livora.data.network

import com.example.livora.data.model.WizBulb
import com.example.livora.data.model.WizBulbState
import com.example.livora.util.LivoraLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class WizBulbController {

    companion object {
        private const val TAG = "Livora.WizBulb"
        private const val WIZ_PORT = 38899
        private const val DISCOVERY_TIMEOUT_MS = 3000
        private const val COMMAND_TIMEOUT_MS = 2000
        private const val BUFFER_SIZE = 1024
    }

    suspend fun discoverBulbs(): List<WizBulb> = withContext(Dispatchers.IO) {
        val bulbs = mutableListOf<WizBulb>()
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = DISCOVERY_TIMEOUT_MS

            val registrationMsg = JSONObject().apply {
                put("method", "registration")
                put("params", JSONObject().apply {
                    put("phoneMac", "AABBCCDDEEFF")
                    put("register", false)
                    put("phoneIp", "1.2.3.4")
                    put("id", "1")
                })
            }

            val sendData = registrationMsg.toString().toByteArray()
            val broadcastAddress = InetAddress.getByName("255.255.255.255")
            val sendPacket = DatagramPacket(sendData, sendData.size, broadcastAddress, WIZ_PORT)
            socket.send(sendPacket)

            LivoraLogger.debug(TAG, "Discovery broadcast sent")

            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                try {
                    val receivePacket = DatagramPacket(buffer, buffer.size)
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)
                    val ip = receivePacket.address.hostAddress ?: continue
                    LivoraLogger.debug(TAG, "Discovered device at $ip: $response")

                    val json = JSONObject(response)
                    val result = json.optJSONObject("result")
                    val mac = result?.optString("mac", "") ?: ""
                    val moduleName = result?.optString("moduleName", "") ?: ""

                    if (mac.isNotEmpty()) {
                        val bulb = WizBulb(ip = ip, mac = mac, moduleName = moduleName)
                        if (bulbs.none { it.mac == mac }) {
                            bulbs.add(bulb)
                        }
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    break
                }
            }
        } catch (e: Exception) {
            LivoraLogger.debug(TAG, "Discovery error: ${e.message}")
        } finally {
            socket?.close()
        }
        LivoraLogger.debug(TAG, "Discovered ${bulbs.size} bulb(s)")
        bulbs
    }

    suspend fun getBulbState(ip: String): WizBulbState? = withContext(Dispatchers.IO) {
        try {
            val msg = JSONObject().apply {
                put("method", "getPilot")
                put("params", JSONObject())
            }
            val response = sendUdpCommand(ip, msg.toString())
            if (response != null) {
                val json = JSONObject(response)
                val result = json.optJSONObject("result") ?: return@withContext null
                val state = result.optBoolean("state", false)
                val dimming = result.optInt("dimming", 100)
                val temp = result.optInt("temp", 4000)
                val r = result.optInt("r", 255)
                val g = result.optInt("g", 255)
                val b = result.optInt("b", 255)
                val sceneId = result.optInt("sceneId", 0)
                val hasRgb = result.has("r") && result.has("g") && result.has("b") && !result.has("temp")

                WizBulbState(
                    isPoweredOn = state,
                    brightness = dimming,
                    colorTemp = temp,
                    r = r,
                    g = g,
                    b = b,
                    sceneId = sceneId,
                    useRgb = hasRgb
                )
            } else {
                null
            }
        } catch (e: Exception) {
            LivoraLogger.debug(TAG, "getBulbState error: ${e.message}")
            null
        }
    }

    suspend fun setPower(ip: String, on: Boolean): Boolean = withContext(Dispatchers.IO) {
        val msg = JSONObject().apply {
            put("method", "setPilot")
            put("params", JSONObject().apply {
                put("state", on)
            })
        }
        sendAndCheck(ip, msg.toString())
    }

    suspend fun setBrightness(ip: String, brightness: Int): Boolean = withContext(Dispatchers.IO) {
        val msg = JSONObject().apply {
            put("method", "setPilot")
            put("params", JSONObject().apply {
                put("state", true)
                put("dimming", brightness.coerceIn(
                    WizBulbState.MIN_BRIGHTNESS,
                    WizBulbState.MAX_BRIGHTNESS
                ))
            })
        }
        sendAndCheck(ip, msg.toString())
    }

    suspend fun setColorTemperature(ip: String, temp: Int, brightness: Int): Boolean = withContext(Dispatchers.IO) {
        val msg = JSONObject().apply {
            put("method", "setPilot")
            put("params", JSONObject().apply {
                put("state", true)
                put("temp", temp.coerceIn(
                    WizBulbState.MIN_COLOR_TEMP,
                    WizBulbState.MAX_COLOR_TEMP
                ))
                put("dimming", brightness.coerceIn(
                    WizBulbState.MIN_BRIGHTNESS,
                    WizBulbState.MAX_BRIGHTNESS
                ))
            })
        }
        sendAndCheck(ip, msg.toString())
    }

    suspend fun setRgb(ip: String, r: Int, g: Int, b: Int, brightness: Int): Boolean = withContext(Dispatchers.IO) {
        val msg = JSONObject().apply {
            put("method", "setPilot")
            put("params", JSONObject().apply {
                put("state", true)
                put("r", r.coerceIn(0, 255))
                put("g", g.coerceIn(0, 255))
                put("b", b.coerceIn(0, 255))
                put("dimming", brightness.coerceIn(
                    WizBulbState.MIN_BRIGHTNESS,
                    WizBulbState.MAX_BRIGHTNESS
                ))
            })
        }
        sendAndCheck(ip, msg.toString())
    }

    suspend fun setScene(ip: String, sceneId: Int): Boolean = withContext(Dispatchers.IO) {
        val msg = JSONObject().apply {
            put("method", "setPilot")
            put("params", JSONObject().apply {
                put("state", true)
                put("sceneId", sceneId)
            })
        }
        sendAndCheck(ip, msg.toString())
    }

    private fun sendAndCheck(ip: String, message: String): Boolean {
        val response = sendUdpCommand(ip, message)
        if (response != null) {
            val json = JSONObject(response)
            val success = json.optBoolean("success", false)
            LivoraLogger.debug(TAG, "Command to $ip success=$success")
            return success
        }
        return false
    }

    private fun sendUdpCommand(ip: String, message: String): String? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            socket.soTimeout = COMMAND_TIMEOUT_MS
            val sendData = message.toByteArray()
            val address = InetAddress.getByName(ip)
            val sendPacket = DatagramPacket(sendData, sendData.size, address, WIZ_PORT)
            socket.send(sendPacket)

            val buffer = ByteArray(BUFFER_SIZE)
            val receivePacket = DatagramPacket(buffer, buffer.size)
            socket.receive(receivePacket)
            String(receivePacket.data, 0, receivePacket.length)
        } catch (e: Exception) {
            LivoraLogger.debug(TAG, "UDP command error to $ip: ${e.message}")
            null
        } finally {
            socket?.close()
        }
    }
}
