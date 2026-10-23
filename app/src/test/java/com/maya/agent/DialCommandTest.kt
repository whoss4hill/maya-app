package com.maya.agent

import com.maya.agent.calls.DialCommand
import com.maya.agent.calls.DialCommand.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DialCommandTest {
    @Test fun parsesIndianMobile() = assertEquals("9876543210", DialCommand.parse("call 98765 43210"))
    @Test fun keepsPlus() = assertEquals("+919876543210", DialCommand.parse("Please dial +91 98765-43210"))
    @Test fun hinglish() = assertEquals("9876543210", DialCommand.parse("is number par call karo 9876543210"))
    @Test fun noKeyword() = assertNull(DialCommand.parse("mera number 9876543210 hai"))
    @Test fun tooShort() = assertNull(DialCommand.parse("call 123"))
    @Test fun tooLong() = assertNull(DialCommand.parse("call 1234567890123456789"))
    @Test fun nameAfter() = assertEquals(Request.Name("mom"), DialCommand.request("call mom"))
    @Test fun nameBefore() = assertEquals(Request.Name("Rahul"), DialCommand.request("Rahul ko call karo"))
    @Test fun numberRequest() = assertEquals(Request.Number("9876543210"), DialCommand.request("call 9876543210"))
    @Test fun callMeIsChat() = assertNull(DialCommand.request("call me later"))
    @Test fun sentenceIsChat() = assertNull(DialCommand.request("mujhe kal doctor ko call karna hai yaad dilana"))
    @Test fun noKeywordIsChat() = assertNull(DialCommand.request("mom is great"))
    @Test fun normalizeMatchesFormats() = assertEquals(DialCommand.normalize("+91 98765 43210"), DialCommand.normalize("098765-43210".drop(1)))
}
