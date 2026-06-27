package com.aistudio.zoya.data.gemini

import android.util.Base64
import android.util.Log
import com.aistudio.zoya.BuildConfig
import com.aistudio.zoya.data.tools.ToolExecutionEngine
import com.aistudio.zoya.domain.model.AssistantState
import com.aistudio.zoya.domain.model.Sentiment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject

class LiveSessionManager(
    private val client: OkHttpClient,
    private val toolEngine: ToolExecutionEngine
) : WebSocketListener() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private val _state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val state: StateFlow<AssistantState> = _state

    private val _volume = MutableStateFlow(0f)
    val volume: StateFlow<Float> = _volume

    private val _pitch = MutableStateFlow(1.0f)
    val pitch: StateFlow<Float> = _pitch

    private val _audioOutput = MutableSharedFlow<ByteArray?>(extraBufferCapacity = 128)
    val audioOutput: Flow<ByteArray?> = _audioOutput

    fun startSession() {
        if (webSocket != null) return

        val request = Request.Builder()
            .url("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=${BuildConfig.GEMINI_API_KEY}")
            .build()

        webSocket = client.newWebSocket(request, this)
        _state.value = AssistantState.Thinking()
    }

    fun stopSession() {
        webSocket?.close(1000, "User stopped")
        webSocket = null
        currentSentiment = Sentiment.Neutral
        _state.value = AssistantState.Idle
    }

    fun sendAudio(audioData: ByteArray) {
        // Calculate volume for user input
        calculateVolume(audioData)
        val message = JSONObject().apply {
            put("realtime_input", JSONObject().apply {
                put("media_chunks", JSONArray().apply {
                    put(JSONObject().apply {
                        put("mime_type", "audio/pcm;rate=16000")
                        put("data", Base64.encodeToString(audioData, Base64.NO_WRAP))
                    })
                })
            })
        }
        webSocket?.send(message.toString())
    }

    fun sendText(text: String) {
        val message = JSONObject().apply {
            put("client_content", JSONObject().apply {
                put("turns", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", text)
                            })
                        })
                    })
                })
                put("turn_complete", true)
            })
        }
        webSocket?.send(message.toString())
    }

    fun setOutputVolume(audioData: ByteArray) {
        calculateVolume(audioData)
    }

    fun setPitch(pitch: Float) {
        _pitch.value = pitch
    }

    private fun calculateVolume(audioData: ByteArray) {
        var sum = 0.0
        for (i in audioData.indices step 2) {
            if (i + 1 < audioData.size) {
                val sample = ((audioData[i + 1].toInt() shl 8) or (audioData[i].toInt() and 0xFF)).toShort()
                sum += sample * sample
            }
        }
        val rms = Math.sqrt(sum / (audioData.size / 2))
        // Normalize RMS to 0.0 - 1.0 range (approximate)
        val normalized = (rms / 3000.0).coerceIn(0.0, 1.0).toFloat()
        _volume.value = normalized
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d("LiveSessionManager", "WebSocket Open")
        val setup = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", "models/gemini-2.0-flash-exp")
                put("generation_config", JSONObject().apply {
                    put("response_modalities", JSONArray().apply { put("AUDIO") })
                })
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "You are Zoya, a helpful assistant. You can express emotions visually by calling the 'updateSentiment' tool. Call it whenever your tone or mood changes (Happy, Sad, Angry, Excited, or Neutral).")
                        })
                    })
                })
                put("tools", JSONArray().apply {
                    put(JSONObject().apply {
                        put("function_declarations", JSONArray().apply {
                            put(createToolJson("updateSentiment", "Update Zoya's visual emotion", listOf("sentiment" to "STRING")))
                            put(createToolJson("launchApp", "Launch an installed app by name", listOf("appName" to "STRING")))
                            put(createToolJson("openApp", "Launch installed apps by package name", listOf("packageName" to "STRING")))
                            put(createToolJson("searchAndCallContact", "Search contacts and initiate call", listOf("contactName" to "STRING")))
                            put(createToolJson("sendWhatsAppMessage", "Open WhatsApp chat", listOf("contactName" to "STRING", "message" to "STRING")))
                            put(createToolJson("sendGmail", "Open Gmail compose", listOf("recipientEmail" to "STRING", "subject" to "STRING", "body" to "STRING")))
                            put(createToolJson("openWebsite", "Open a URL", listOf("url" to "STRING")))
                            put(createToolJson("setAlarm", "Set an alarm", listOf("time" to "STRING", "label" to "STRING")))
                            put(createToolJson("openCamera", "Open camera app", emptyList()))
                            put(createToolJson("openSettings", "Open system settings", emptyList()))
                            put(createToolJson("playYouTube", "Play a video on YouTube", listOf("query" to "STRING")))
                            put(createToolJson("toggleWiFi", "Turn Wi-Fi on or off", listOf("enable" to "BOOLEAN")))
                            put(createToolJson("toggleBluetooth", "Turn Bluetooth on or off", listOf("enable" to "BOOLEAN")))
                            put(createToolJson("setBrightness", "Adjust screen brightness (0-255)", listOf("level" to "NUMBER")))
                            put(createToolJson("setVolume", "Adjust media volume (0-100 percent)", listOf("level" to "NUMBER")))
                            put(createToolJson("toggleDoNotDisturb", "Turn Do Not Disturb on or off", listOf("enable" to "BOOLEAN")))
                            put(createToolJson("getNetworkStatus", "Check the current network connectivity status", emptyList()))
                        })
                    })
                })
            })
        }
        webSocket.send(setup.toString())
        _state.value = AssistantState.Listening
    }

    private fun createToolJson(name: String, desc: String, params: List<Pair<String, String>>): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", desc)
            if (params.isNotEmpty()) {
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        params.forEach { (pName, pType) ->
                            put(pName, JSONObject().apply { put("type", pType) })
                        }
                    })
                    put("required", JSONArray().apply { params.forEach { put(it.first) } })
                })
            }
        }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val json = JSONObject(text)
        if (json.has("server_content")) {
            val serverContent = json.getJSONObject("server_content")
            if (serverContent.has("model_turn")) {
                val modelTurn = serverContent.getJSONObject("model_turn")
                val parts = modelTurn.getJSONArray("parts")
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("inline_data")) {
                        val inlineData = part.getJSONObject("inline_data")
                        val data = Base64.decode(inlineData.getString("data"), Base64.DEFAULT)
                        scope.launch { _audioOutput.emit(data) }
                        _state.value = AssistantState.Speaking(currentSentiment)
                    }
                    if (part.has("function_call")) {
                        handleFunctionCall(part.getJSONObject("function_call"))
                    }
                }
            }
            if (serverContent.has("turn_complete") && serverContent.getBoolean("turn_complete")) {
                currentSentiment = Sentiment.Neutral
                scope.launch { _audioOutput.emit(null) }
                _state.value = AssistantState.Listening
            }
        }
    }

    private var currentSentiment: Sentiment = Sentiment.Neutral

    private fun handleFunctionCall(call: JSONObject) {
        val name = call.getString("name")
        val args = if (call.has("args")) call.getJSONObject("args") else JSONObject()
        
        val result = when (name) {
            "updateSentiment" -> {
                val sentStr = args.optString("sentiment", "Neutral")
                currentSentiment = try {
                    Sentiment.valueOf(sentStr.lowercase().replaceFirstChar { it.uppercase() })
                } catch (e: Exception) {
                    Sentiment.Neutral
                }
                if (_state.value is AssistantState.Speaking) {
                    _state.value = AssistantState.Speaking(currentSentiment)
                } else if (_state.value is AssistantState.Thinking) {
                    _state.value = AssistantState.Thinking(currentSentiment)
                }
                "Sentiment updated to $currentSentiment"
            }
            "launchApp" -> toolEngine.launchAppByName(args.getString("appName"))
            "openApp" -> toolEngine.openApp(args.getString("packageName"))
            "searchAndCallContact" -> toolEngine.searchAndCallContact(args.getString("contactName"))
            "sendWhatsAppMessage" -> toolEngine.sendWhatsAppMessage(args.getString("contactName"), args.getString("message"))
            "sendGmail" -> toolEngine.sendGmail(args.getString("recipientEmail"), args.getString("subject"), args.getString("body"))
            "openWebsite" -> toolEngine.openWebsite(args.getString("url"))
            "setAlarm" -> toolEngine.setAlarm(args.getString("time"), args.getString("label"))
            "openCamera" -> toolEngine.openCamera()
            "openSettings" -> toolEngine.openSettings()
            "playYouTube" -> toolEngine.playYouTube(args.getString("query"))
            "toggleWiFi" -> toolEngine.toggleWiFi(args.getBoolean("enable"))
            "toggleBluetooth" -> toolEngine.toggleBluetooth(args.getBoolean("enable"))
            "setBrightness" -> toolEngine.setBrightness(args.getInt("level"))
            "setVolume" -> toolEngine.setVolume(args.getInt("level"))
            "toggleDoNotDisturb" -> toolEngine.toggleDoNotDisturb(args.getBoolean("enable"))
            "getNetworkStatus" -> toolEngine.getNetworkStatus()
            else -> "Unknown tool: $name"
        }

        val response = JSONObject().apply {
            put("tool_response", JSONObject().apply {
                put("function_responses", JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", name)
                        put("id", call.optString("id", "1"))
                        put("response", JSONObject().apply { put("result", result) })
                    })
                })
            })
        }
        webSocket?.send(response.toString())
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        _state.value = AssistantState.Error(t.message ?: "Unknown error")
        this.webSocket = null
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        this.webSocket = null
        _state.value = AssistantState.Idle
    }
}
