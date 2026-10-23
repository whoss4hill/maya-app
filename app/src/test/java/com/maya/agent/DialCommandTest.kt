package com.maya.agent

import com.maya.agent.calls.DialCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DialCommandTest {
    @Test fun parsesIndianMobile() = assertEquals("9876543210", DialCommand.parse("call 98765 43210"))
    @Test fun keepsPlus() = assertEquals("+919876543210", DialCommand.parse("Please dial +91 98765-43210"))
    @Test fun hinglish() = assertEquals("9876543210", DialCommand.parse("is number par call karo 9876543210"))
    @Test fun noKeyword() = assertNull(DialCommand.parse("mera number 9876543210 hai"))
    @Test fun noNumber() = assertNull(DialCommand.parse("call mom"))
    @Test fun tooShort() = assertNull(DialCommand.parse("call 123"))
    @Test fun tooLong() = assertNull(DialCommand.parse("call 1234567890123456789"))
}
