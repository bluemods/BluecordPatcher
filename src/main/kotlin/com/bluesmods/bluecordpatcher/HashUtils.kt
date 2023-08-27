package com.bluesmods.bluecordpatcher

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

object HashUtils {

    private const val HASH_ALGORITHM = "SHA-384"
    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    fun hashFile(file: File): String {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)

        Files.newInputStream(file.toPath()).use {
            var read: Int
            val buffer = ByteArray(1024 * 64) // 64k buffer

            while (true) {
                read = it.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHexString()
    }

    private fun ByteArray.toHexString(): String {
        val sb = StringBuilder(this.size * 2)
        for (b in this) {
            sb.append(HEX_CHARS[b.toInt() and 240 shr 4])
            sb.append(HEX_CHARS[b.toInt() and 15])
        }
        return sb.toString()
    }
}