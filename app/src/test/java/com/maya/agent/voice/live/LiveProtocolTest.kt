package com.maya.agent.voice.live

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveProtocolTest {

    @Test
    fun `setup carries constrained-session shape`() {
        val json = LiveProtocol.buildSetup(
            model = "gemini-3.8-live",
            systemInstruction = "You are Maya.",
            voiceName = "Kore",
        )
        val setup = JsonParser.parseString(json).asJsonObject.getAsJsonObject("setup")
        assertEquals("models/gemini-3.8-live", setup.get("model").asString)
        assertEquals("AUDIO", setup.getAsJsonObject("generationConfig")
            .getAsJsonArray("responseModalities")[0].asString)
        assertTrue(setup.has("sessionResumption"))
        assertEquals("You are Maya.", setup.getAsJsonObject("systemInstruction")
            .getAsJsonArray("parts")[0].asJsonObject.get("text").asString)
        assertEquals("Kore", setup.getAsJsonObject("speechConfig")
            .getAsJsonObject("voiceConfig").getAsJsonObject("prebuiltVoiceConfig")
            .get("voiceName").asString)
        // No transcript UI on the voice screen (user decision 19:14): off by default.
        assertFalse(setup.has("outputAudioTranscription"))
    }

    @Test
    fun `setup carries resumption handle on reconnect`() {
        val json = LiveProtocol.buildSetup("m", "s", "Kore", resumptionHandle = "hdl-123")
        val resumption = JsonParser.parseString(json).asJsonObject
            .getAsJsonObject("setup").getAsJsonObject("sessionResumption")
        assertEquals("hdl-123", resumption.get("handle").asString)
    }

    @Test
    fun `audio chunk matches verified wire shape`() {
        val json = LiveProtocol.buildAudioChunk("QUJD")
        val audio = JsonParser.parseString(json).asJsonObject
            .getAsJsonObject("realtimeInput").getAsJsonObject("audio")
        assertEquals("QUJD", audio.get("data").asString)
        assertEquals("audio/pcm;rate=16000", audio.get("mimeType").asString)
    }

    @Test
    fun `tool response matches verified wire shape`() {
        val json = LiveProtocol.buildToolResponse("id1", "setReminder", "{\"result\":\"ok\"}")
        val fr = JsonParser.parseString(json).asJsonObject
            .getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject
        assertEquals("id1", fr.get("id").asString)
        assertEquals("setReminder", fr.get("name").asString)
        assertEquals("ok", fr.getAsJsonObject("response").get("result").asString)
    }

    @Test
    fun `parses setupComplete and turnComplete`() {
        val e1 = LiveProtocol.parseServerMessage("{\"setupComplete\":{}}")
        assertTrue(e1.any { it is LiveProtocol.ServerEvent.SetupComplete })
        val e2 = LiveProtocol.parseServerMessage("{\"serverContent\":{\"turnComplete\":true}}")
        assertTrue(e2.any { it is LiveProtocol.ServerEvent.TurnComplete })
    }

    @Test
    fun `parses interruption for barge-in`() {
        val e = LiveProtocol.parseServerMessage("{\"serverContent\":{\"interrupted\":true}}")
        assertTrue(e.any { it is LiveProtocol.ServerEvent.Interrupted })
    }

    @Test
    fun `parses model audio turn`() {
        val b64 = java.util.Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))
        val e = LiveProtocol.parseServerMessage(
            "{\"serverContent\":{\"modelTurn\":{\"parts\":[{\"inlineData\":{\"data\":\"$b64\",\"mimeType\":\"audio/pcm;rate=24000\"}}]}}}"
        )
        val turn = e.filterIsInstance<LiveProtocol.ServerEvent.ModelTurn>().single()
        assertEquals(1, turn.audioChunks.size)
        assertEquals(3, turn.audioChunks[0].size)
    }

    @Test
    fun `parses tool calls`() {
        val e = LiveProtocol.parseServerMessage(
            "{\"toolCall\":{\"functionCalls\":[{\"id\":\"c1\",\"name\":\"setReminder\",\"args\":{\"delayMinutes\":10}}]}}"
        )
        val tc = e.filterIsInstance<LiveProtocol.ServerEvent.ToolCall>().single()
        assertEquals("setReminder", tc.calls[0].name)
        assertEquals("c1", tc.calls[0].id)
        assertTrue(tc.calls[0].argsJson.contains("delayMinutes"))
    }

    @Test
    fun `parses goAway and resumption update`() {
        assertTrue(LiveProtocol.parseServerMessage("{\"goAway\":{}}")
            .any { it is LiveProtocol.ServerEvent.GoAway })
        val e = LiveProtocol.parseServerMessage("{\"sessionResumptionUpdate\":{\"newHandle\":\"h9\"}}")
        assertEquals("h9", e.filterIsInstance<LiveProtocol.ServerEvent.ResumptionHandle>().single().handle)
    }

    @Test
    fun `unknown payload does not crash`() {
        val e = LiveProtocol.parseServerMessage("not json")
        assertTrue(e.single() is LiveProtocol.ServerEvent.Unknown)
    }
}
