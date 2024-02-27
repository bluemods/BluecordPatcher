package com.bluesmods.bluecordpatcher.command

import java.io.IOException

class ExecutionResult(
    val command: Command,
    val bytes: ByteArray,
    val exitCode: Int,
    val executionTime: Long
) {
    val string: String
        get() = String(bytes)

    val lines: List<String>
        get() = string.lines()

    val isSuccessful: Boolean
        get() = exitCode == 0

    @Throws(IOException::class)
    fun throwOnFailure(): ExecutionResult = apply {
        if (!isSuccessful) {
            throw IOException("command failed; exitCode=$exitCode, response=$string")
        }
    }
}
