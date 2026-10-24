package com.maya.agent.data

import java.util.Base64

/** Validates and decodes a /v1/image response. Pure JVM so it is unit-testable; no UI. */
object ImageDecode {
    const val MAX_BYTES = 2_000_000

    fun decode(b64: String?, mime: String?): ByteArray? {
        if (b64.isNullOrBlank() || mime != "image/jpeg") return null
        if (b64.length > MAX_BYTES * 4 / 3 + 8) return null
        val bytes = try { Base64.getDecoder().decode(b64.trim()) } catch (e: IllegalArgumentException) { return null }
        if (bytes.size < 4 || bytes.size > MAX_BYTES) return null
        val jpeg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        return if (jpeg) bytes else null
    }
}
