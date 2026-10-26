package com.maya.agent.voice.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherToolTest {
    private val sample = """{"timezone_abbreviation":"IST","current":{"time":"2026-10-26T17:30","temperature_2m":31.4,"apparent_temperature":34.0,"relative_humidity_2m":60,"weather_code":2,"wind_speed_10m":8.2},
      "daily":{"temperature_2m_max":[33.1],"temperature_2m_min":[24.0],"precipitation_probability_max":[20]}}"""

    @Test fun `fix freshness uses 15 minute limit`() {
        assertTrue(WeatherTool.isFresh(0))
        assertTrue(WeatherTool.isFresh(15 * 60 * 1000L - 1))
        assertFalse(WeatherTool.isFresh(15 * 60 * 1000L))
        assertFalse(WeatherTool.isFresh(15 * 60 * 1000L + 1))
        assertFalse(WeatherTool.isFresh(-5))
    }

    @Test fun `coordinates are rounded to 2 decimals in the url`() {
        val u = WeatherTool.url(28.613939, 77.209021)
        assertTrue(u, u.contains("latitude=28.61&longitude=77.21"))
        assertFalse(u.contains("28.613939"))
    }

    @Test fun `summary has times and source and no raw json`() {
        val s = WeatherTool.summarize(sample, "26 Oct 2026, 5:32 PM IST")!!
        assertTrue(s.contains("31.4 C"))
        assertTrue(s.contains("partly cloudy"))
        assertTrue(s.contains("2026-10-26T17:30"))
        assertTrue(s.contains("IST"))
        assertTrue(s.contains("Checked by Maya at 26 Oct 2026, 5:32 PM IST"))
        assertTrue(s.contains("Open-Meteo"))
        assertFalse(s.contains("{"))
    }

    @Test fun `unusable payload gives null`() {
        assertNull(WeatherTool.summarize("not json", "x"))
        assertNull(WeatherTool.summarize("{\"current\":{}}", "x"))
    }

    @Test fun `code mapping`() {
        assertEquals("clear sky", WeatherTool.codeText(0))
        assertEquals("thunderstorm", WeatherTool.codeText(95))
        assertNotNull(WeatherTool.codeText(1234))
    }

    @Test fun `named place is unsupported and never uses current location`() = kotlinx.coroutines.test.runTest {
        val out = WeatherTool.run(org.robolectric.RuntimeEnvironment.getApplication(), "{\"place\":\"Delhi\"}", "delhi ka mausam batao")
        assertTrue(out, out.contains("sirf phone ki current location"))
    }

    @Test fun `gate needs a heard weather request and refuses other places`() {
        assertEquals(WeatherTool.Gate.NO_REQUEST_HEARD, WeatherTool.gate(""))
        assertEquals(WeatherTool.Gate.NO_REQUEST_HEARD, WeatherTool.gate("kuch gaana chalao"))
        assertEquals(WeatherTool.Gate.ALLOWED, WeatherTool.gate("maya aaj ka mausam kaisa hai"))
        assertEquals(WeatherTool.Gate.ALLOWED, WeatherTool.gate("what's the weather today"))
        assertEquals(WeatherTool.Gate.ALLOWED, WeatherTool.gate("यहाँ का मौसम बताओ"))
        assertEquals(WeatherTool.Gate.OTHER_PLACE, WeatherTool.gate("delhi ka mausam kaisa hai"))
        assertEquals(WeatherTool.Gate.OTHER_PLACE, WeatherTool.gate("weather in mumbai"))
        assertEquals(WeatherTool.Gate.OTHER_PLACE, WeatherTool.gate("दिल्ली का मौसम"))
    }

    @Test fun `no heard request means no location or network`() = kotlinx.coroutines.test.runTest {
        val out = WeatherTool.run(org.robolectric.RuntimeEnvironment.getApplication(), "{}", "")
        assertTrue(out, out.contains("request nahi suni"))
    }
}
