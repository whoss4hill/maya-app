package com.maya.agent.voice

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Offline wake-word model (Vosk small English-India, Apache-2.0, ~37.6 MB).
 * Downloaded ONLY after the owner confirms in Settings. The zip is checked
 * against a pinned SHA-256 before it is unpacked. Owner can delete it anytime.
 */
object VoskModelStore {
    const val URL_ZIP = "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip"
    const val SHA256 = "20663dcac4d5cb783a579c54d98339344a688e4ec6e1b4a4b059fd1235454cc7"
    const val SIZE_MB = 38
    private const val DIR = "vosk-model"

    fun modelDir(context: Context) = File(context.filesDir, DIR)

    fun isInstalled(context: Context) = File(modelDir(context), "am/final.mdl").exists()

    fun delete(context: Context) { modelDir(context).deleteRecursively() }

    /** Blocking; call from a background thread. Returns null on success, else an error text. */
    fun download(context: Context, onProgress: (Int) -> Unit): String? {
        val tmp = File(context.cacheDir, "vosk-model.zip")
        try {
            val conn = (URL(URL_ZIP).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000; readTimeout = 30000
            }
            if (conn.responseCode != 200) return "Server ne ${conn.responseCode} diya"
            val total = conn.contentLengthLong.coerceAtLeast(1)
            val md = MessageDigest.getInstance("SHA-256")
            var done = 0L
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n); md.update(buf, 0, n); done += n
                        onProgress((done * 100 / total).toInt())
                    }
                }
            }
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            if (hex != SHA256) return "Download could not be verified (checksum mismatch)"
            val target = modelDir(context)
            target.deleteRecursively(); target.mkdirs()
            ZipInputStream(tmp.inputStream()).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    // strip the single top-level folder; refuse path traversal
                    val rel = e.name.substringAfter('/', "")
                    if (rel.isEmpty() || rel.contains("..")) continue
                    val f = File(target, rel)
                    if (e.isDirectory) f.mkdirs() else {
                        f.parentFile?.mkdirs()
                        FileOutputStream(f).use { zin.copyTo(it) }
                    }
                }
            }
            return if (isInstalled(context)) null else "Model unpack did not finish"
        } catch (e: Exception) {
            return "Download fail: ${e.javaClass.simpleName}"
        } finally {
            tmp.delete()
        }
    }

}
