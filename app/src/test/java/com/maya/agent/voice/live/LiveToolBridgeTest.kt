package com.maya.agent.voice.live

import com.google.gson.JsonParser
import com.maya.agent.agent.MayaIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveToolBridgeTest {

    private fun call(name: String, args: String) = LiveProtocol.ServerEvent.FunctionCall("id1", name, args)

    @Test
    fun `maps set_timer`() {
        val i = LiveToolBridge.toIntent(call("set_timer", "{\"seconds\":600,\"label\":\"chai\"}"))
        assertEquals(MayaIntent.SetTimer(600, "chai"), i)
    }

    @Test
    fun `rejects invalid timer and alarm values`() {
        assertNull(LiveToolBridge.toIntent(call("set_timer", "{\"seconds\":0}")))
        assertNull(LiveToolBridge.toIntent(call("set_alarm", "{\"hour\":25,\"minute\":0}")))
        assertNull(LiveToolBridge.toIntent(call("set_alarm", "{\"hour\":7}")))
        assertEquals(MayaIntent.SetAlarm(7, 30, null),
            LiveToolBridge.toIntent(call("set_alarm", "{\"hour\":7,\"minute\":30}")))
    }

    @Test
    fun `maps cart through the existing confirm-gated intent`() {
        val i = LiveToolBridge.toIntent(call("add_to_cart", "{\"item\":\"atta 5kg\"}"))
        assertEquals(MayaIntent.CartAdd("atta 5kg"), i)
        assertNull(LiveToolBridge.toIntent(call("add_to_cart", "{\"item\":\"\"}")))
    }

    @Test
    fun `maps navigation and screen tools`() {
        assertEquals(MayaIntent.GoBack, LiveToolBridge.toIntent(call("go_back", "{}")))
        assertEquals(MayaIntent.GoHome, LiveToolBridge.toIntent(call("go_home", "{}")))
                assertEquals(MayaIntent.OpenApp("whatsapp"),
            LiveToolBridge.toIntent(call("open_app", "{\"query\":\"whatsapp\"}")))
        assertEquals(MayaIntent.TapText("Buy now"),
            LiveToolBridge.toIntent(call("tap_text", "{\"text\":\"Buy now\"}")))
        assertNull(LiveToolBridge.toIntent(call("tap_text", "{\"text\":\"\"}")))
        // read_screen stays unbridged until the user explicitly grants screen-content sharing
        assertNull(LiveToolBridge.toIntent(call("read_screen", "{}")))
        // weather/time stays undeclared until terms + grounding semantics are verified
        assertNull(LiveToolBridge.toIntent(call("get_date_time_and_weather", "{}")))
    }

    @Test
    fun `unknown tools are rejected`() {
        assertNull(LiveToolBridge.toIntent(call("call_contact", "{\"target\":\"mom\"}"))) // handled by VoiceCall, not a MayaIntent
        assertNull(LiveToolBridge.toIntent(call("send_message", "{\"to\":\"x\"}")))
        assertNull(LiveToolBridge.toIntent(call("pay", "{\"amount\":100}")))
    }

    @Test
    fun `declarations json is valid and names match mapping`() {
        val decls = JsonParser.parseString(LiveToolBridge.DECLARATIONS_JSON).asJsonArray
        val names = decls.map { it.asJsonObject.get("name").asString }.toSet()
        assertEquals(setOf("set_timer","set_alarm","add_to_cart","open_app","tap_text","go_back","go_home","call_contact"), names)
    }

    @Test
    fun `setup embeds tools when provided`() {
        val json = LiveProtocol.buildSetup("m", "s", "Kore", toolsJson = LiveToolBridge.DECLARATIONS_JSON)
        val tools = JsonParser.parseString(json).asJsonObject.getAsJsonObject("setup").getAsJsonArray("tools")
        assertTrue(tools[0].asJsonObject.getAsJsonArray("functionDeclarations").size() == 8)
        val bare = LiveProtocol.buildSetup("m", "s", "Kore")
        assertTrue(!JsonParser.parseString(bare).asJsonObject.getAsJsonObject("setup").has("tools"))
        val withSearch = JsonParser.parseString(
            LiveProtocol.buildSetup("m", "s", "Kore", includeGoogleSearch = true)
        ).asJsonObject.getAsJsonObject("setup").getAsJsonArray("tools")
        assertTrue(withSearch.any { it.asJsonObject.has("googleSearch") })
        val both = JsonParser.parseString(
            LiveProtocol.buildSetup("m", "s", "Kore", toolsJson = LiveToolBridge.DECLARATIONS_JSON, includeGoogleSearch = true)
        ).asJsonObject.getAsJsonObject("setup").getAsJsonArray("tools")
        assertTrue(both.size() == 2 && both.any { it.asJsonObject.has("googleSearch") }
            && both.any { it.asJsonObject.has("functionDeclarations") })
    }
}
