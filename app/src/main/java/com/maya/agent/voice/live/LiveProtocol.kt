package com.maya.agent.voice.live

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Wire protocol for the Gemini Live API (BidiGenerateContentConstrained over
 * ephemeral tokens), matching the shapes verified end-to-end on 2026-10-24
 * against the real free-tier project and the proven Zoya web client:
 *  - setup: model/generationConfig.responseModalities/sessionResumption/
 *    systemInstruction/speechConfig/outputAudioTranscription
 *  - audio in: realtimeInput.audio {data: base64, mimeType: "audio/pcm;rate=16000"}
 *  - tool out: toolResponse.functionResponses[{name,id,response:{result}}]
 *  - server in: setupComplete / serverContent{modelTurn,interrupted,
 *    outputTranscription,turnComplete} / toolCall / goAway /
 *    sessionResumptionUpdate
 */
object LiveProtocol {

    const val AUDIO_IN_MIME = "audio/pcm;rate=16000"

    fun buildSetup(
        model: String,
        systemInstruction: String,
        voiceName: String,
        enableResumption: Boolean = true,
        enableOutputTranscription: Boolean = false,
        enableInputTranscription: Boolean = false,
        resumptionHandle: String? = null,
        toolsJson: String? = null,
        includeGoogleSearch: Boolean = false,
    ): String {
        val setup = JsonObject().apply {
            addProperty("model", if (model.startsWith("models/")) model else "models/$model")
            add("generationConfig", JsonObject().apply {
                add("responseModalities", com.google.gson.JsonArray().apply { add("AUDIO") })
            })
            if (enableResumption) {
                add("sessionResumption", JsonObject().apply {
                    if (!resumptionHandle.isNullOrBlank()) addProperty("handle", resumptionHandle)
                })
            }
            if (systemInstruction.isNotBlank()) {
                add("systemInstruction", JsonObject().apply {
                    add("parts", com.google.gson.JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", systemInstruction) })
                    })
                })
            }
            if (voiceName.isNotBlank()) {
                add("speechConfig", JsonObject().apply {
                    add("voiceConfig", JsonObject().apply {
                        add("prebuiltVoiceConfig", JsonObject().apply {
                            addProperty("voiceName", voiceName)
                        })
                    })
                })
            }
            if (enableOutputTranscription) add("outputAudioTranscription", JsonObject())
            if (enableInputTranscription) add("inputAudioTranscription", JsonObject())
            if (!toolsJson.isNullOrBlank() || includeGoogleSearch) {
                add("tools", com.google.gson.JsonArray().apply {
                    if (!toolsJson.isNullOrBlank()) {
                        add(JsonObject().apply {
                            add("functionDeclarations", JsonParser.parseString(toolsJson).asJsonArray)
                        })
                    }
                    if (includeGoogleSearch) add(JsonObject().apply { add("googleSearch", JsonObject()) })
                })
            }
            add("realtimeInputConfig", JsonObject().apply {
                add("automaticActivityDetection", JsonObject())
            })
        }
        return JsonObject().apply { add("setup", setup) }.toString()
    }

    fun buildAudioChunk(base64Pcm16: String): String =
        JsonObject().apply {
            add("realtimeInput", JsonObject().apply {
                add("audio", JsonObject().apply {
                    addProperty("data", base64Pcm16)
                    addProperty("mimeType", AUDIO_IN_MIME)
                })
            })
        }.toString()

    fun buildClientText(text: String): String =
        JsonObject().apply {
            add("clientContent", JsonObject().apply {
                add("turns", com.google.gson.JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        add("parts", com.google.gson.JsonArray().apply {
                            add(JsonObject().apply { addProperty("text", text) })
                        })
                    })
                })
                addProperty("turnComplete", true)
            })
        }.toString()

    fun buildToolResponse(callId: String, name: String, resultJson: String): String =
        JsonObject().apply {
            add("toolResponse", JsonObject().apply {
                add("functionResponses", com.google.gson.JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("id", callId)
                        addProperty("name", name)
                        add("response", JsonParser.parseString(resultJson).asJsonObject)
                    })
                })
            })
        }.toString()

    sealed class ServerEvent {
        data object SetupComplete : ServerEvent()
        /** One model turn part batch: raw PCM16 audio bytes (24 kHz) and any text parts. */
        data class ModelTurn(val audioChunks: List<ByteArray>, val textParts: List<String>) : ServerEvent()
        data class OutputTranscript(val text: String) : ServerEvent()
        data class InputTranscript(val text: String) : ServerEvent()
        data object Interrupted : ServerEvent()
        data object TurnComplete : ServerEvent()
        data class ToolCall(val calls: List<FunctionCall>) : ServerEvent()
        data class FunctionCall(val id: String, val name: String, val argsJson: String)
        data object GoAway : ServerEvent()
        data class ResumptionHandle(val handle: String) : ServerEvent()
        data class Unknown(val raw: String) : ServerEvent()
    }

    fun parseServerMessage(raw: String): List<ServerEvent> {
        val root = try { JsonParser.parseString(raw).asJsonObject } catch (e: Exception) {
            return listOf(ServerEvent.Unknown(raw.take(200)))
        }
        val events = mutableListOf<ServerEvent>()
        if (root.has("setupComplete")) events += ServerEvent.SetupComplete
        if (root.has("goAway")) events += ServerEvent.GoAway
        root.getAsJsonObject("sessionResumptionUpdate")?.let { upd ->
            upd.get("newHandle")?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.let {
                events += ServerEvent.ResumptionHandle(it.asString)
            }
        }
        root.getAsJsonObject("toolCall")?.let { tc ->
            val calls = tc.getAsJsonArray("functionCalls")?.map { fc ->
                val o = fc.asJsonObject
                ServerEvent.FunctionCall(
                    id = o.get("id")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                    name = o.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                    argsJson = o.getAsJsonObject("args")?.toString() ?: "{}",
                )
            } ?: emptyList()
            if (calls.isNotEmpty()) events += ServerEvent.ToolCall(calls)
        }
        root.getAsJsonObject("serverContent")?.let { sc ->
            sc.getAsJsonObject("modelTurn")?.getAsJsonArray("parts")?.let { parts ->
                val audio = mutableListOf<ByteArray>()
                val text = mutableListOf<String>()
                for (p in parts) {
                    val part = p.asJsonObject
                    part.getAsJsonObject("inlineData")?.get("data")?.takeIf { it.isJsonPrimitive }?.let {
                        audio += java.util.Base64.getDecoder().decode(it.asString)
                    }
                    part.get("text")?.takeIf { it.isJsonPrimitive }?.let { text += it.asString }
                }
                if (audio.isNotEmpty() || text.isNotEmpty()) events += ServerEvent.ModelTurn(audio, text)
            }
            sc.getAsJsonObject("outputTranscription")?.get("text")?.takeIf { it.isJsonPrimitive }?.let {
                events += ServerEvent.OutputTranscript(it.asString)
            }
            sc.getAsJsonObject("inputTranscription")?.get("text")?.takeIf { it.isJsonPrimitive }?.let {
                events += ServerEvent.InputTranscript(it.asString)
            }
            if (sc.get("interrupted")?.takeIf { it.isJsonPrimitive }?.asBoolean == true) {
                events += ServerEvent.Interrupted
            }
            if (sc.get("turnComplete")?.takeIf { it.isJsonPrimitive }?.asBoolean == true) {
                events += ServerEvent.TurnComplete
            }
        }
        return if (events.isEmpty()) listOf(ServerEvent.Unknown(raw.take(200))) else events
    }
}
