package com.maya.agent.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentRouterTest {

    @Test fun `hinglish timer minutes`() {
        val r = IntentRouter.route("5 minute ka timer laga do")
        assertEquals(MayaIntent.SetTimer(300, null), r)
    }

    @Test fun `english timer seconds`() {
        val r = IntentRouter.route("set a timer for 30 seconds")
        assertEquals(MayaIntent.SetTimer(30, null), r)
    }

    @Test fun `hinglish alarm subah 7 baje`() {
        val r = IntentRouter.route("subah 7 baje ka alarm lagao")
        assertEquals(MayaIntent.SetAlarm(7, 0, null), r)
    }

    @Test fun `evening alarm converts to 24h`() {
        val r = IntentRouter.route("shaam 6 baje ka alarm")
        assertEquals(MayaIntent.SetAlarm(18, 0, null), r)
    }

    @Test fun `alarm with explicit time`() {
        val r = IntentRouter.route("alarm for 6:30 pm")
        assertEquals(MayaIntent.SetAlarm(18, 30, null), r)
    }

    @Test fun `cart order hinglish`() {
        val r = IntentRouter.route("bread mangwa do")
        assertTrue(r is MayaIntent.CartAdd)
        assertEquals("bread", (r as MayaIntent.CartAdd).item)
    }

    @Test fun `cart order with quantity`() {
        val r = IntentRouter.route("ek kilo aata mangwao")
        assertEquals(MayaIntent.CartAdd("ek kilo aata"), r)
    }

    @Test fun `open app`() {
        val r = IntentRouter.route("whatsapp kholo")
        assertEquals(MayaIntent.OpenApp("whatsapp"), r)
    }

    @Test fun `back command`() {
        assertEquals(MayaIntent.GoBack, IntentRouter.route("back jao"))
    }

    @Test fun `tap by text`() {
        val r = IntentRouter.route("login dabao")
        assertEquals(MayaIntent.TapText("login"), r)
    }

    @Test fun `read screen`() {
        assertEquals(MayaIntent.ReadScreen, IntentRouter.route("screen pe kya hai bata"))
    }

    @Test fun `unrelated falls through to chat`() {
        val r = IntentRouter.route("aaj mausam kaisa hai")
        assertTrue(r is MayaIntent.Chat)
    }
}
