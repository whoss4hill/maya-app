package com.maya.agent

import com.maya.agent.calls.PhoneLinkSettings
import com.maya.agent.calls.PhoneLinkSettings.Status
import com.maya.agent.data.backend.TelegramStatus
import com.maya.agent.data.telegram.ChatIdInput
import com.maya.agent.data.telegram.toUi
import org.junit.Assert.*
import org.junit.Test

class TelegramControlTest {
    @Test fun chatIdAcceptsOnlyPositiveDigits() {
        assertEquals(6358090699L, ChatIdInput.parse(" 6358090699 "))
        assertNull(ChatIdInput.parse("")); assertNull(ChatIdInput.parse("-100123")); assertNull(ChatIdInput.parse("12a4"))
        assertNull(ChatIdInput.parse("0")); assertNull(ChatIdInput.parse("1234567890123456")); assertNull(ChatIdInput.parse("@name"))
    }
    @Test fun statusMasksChat() {
        val u = TelegramStatus(true, true, "0699").toUi()
        assertEquals("\u2022\u2022\u2022\u20220699", u.maskedChat); assertTrue(u.bound)
        assertNull(TelegramStatus(false, true, null).toUi().maskedChat)
    }
    @Test fun phoneLinkRules() {
        assertFalse(PhoneLinkSettings.canEnable(false)); assertTrue(PhoneLinkSettings.canEnable(true))
        assertEquals(Status.OFF, PhoneLinkSettings.status(false, true, true))
        assertEquals(Status.NEEDS_PERMISSION, PhoneLinkSettings.status(true, false, false))
        assertEquals(Status.WAITING, PhoneLinkSettings.status(true, true, false))
        assertEquals(Status.CONNECTED, PhoneLinkSettings.status(true, true, true))
    }
}
