package com.maya.agent

import com.google.gson.Gson
import com.maya.agent.data.ImageDecode
import com.maya.agent.data.backend.ChatResponse
import com.maya.agent.data.backend.ImageResponse
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class ImageDecodeTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
    private val b64 = Base64.getEncoder().encodeToString(jpeg)

    @Test fun decodesJpeg() = assertArrayEquals(jpeg, ImageDecode.decode(b64, "image/jpeg"))
    @Test fun rejectsNull() = assertNull(ImageDecode.decode(null, "image/jpeg"))
    @Test fun rejectsWrongMime() = assertNull(ImageDecode.decode(b64, "image/svg+xml"))
    @Test fun rejectsNotJpegBytes() = assertNull(ImageDecode.decode(Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4, 5)), "image/jpeg"))
    @Test fun rejectsGarbage() = assertNull(ImageDecode.decode("%%%not base64%%%", "image/jpeg"))
    @Test fun rejectsHuge() = assertNull(ImageDecode.decode("A".repeat(3_000_000), "image/jpeg"))
    @Test fun oldChatShapeStillParses() {
        val r = Gson().fromJson("{\"reply\":\"hi\"}", ChatResponse::class.java)
        assertEquals("hi", r.reply); assertNull(r.image_prompt)
    }
    @Test fun unknownFieldsIgnored() {
        val r = Gson().fromJson("{\"reply\":\"hi\",\"future\":1}", ChatResponse::class.java)
        assertEquals("hi", r.reply)
    }
    @Test fun hintParses() {
        val r = Gson().fromJson("{\"reply\":\"ok\",\"image_prompt\":\"a kite\"}", ChatResponse::class.java)
        assertEquals("a kite", r.image_prompt)
    }
    @Test fun imageResponseParses() {
        val r = Gson().fromJson("{\"image_base64\":\"$b64\",\"mime\":\"image/jpeg\",\"model\":\"m\"}", ImageResponse::class.java)
        assertNotNull(ImageDecode.decode(r.image_base64, r.mime))
    }
}
