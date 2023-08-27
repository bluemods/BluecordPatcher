package com.bluesmods.bluecordpatcher

import org.slf4j.LoggerFactory
import java.io.IOException

data class ExecutionResult(
    /**
     * The command invoked, with its arguments.
     */
    val command: String,

    /**
     * The output from the command, read from stdin and stderr
     */
    val commandOutput: String,
    /**
     * True if the process was successful (exitCode returned 0)
     */
    val isSuccessful: Boolean,

    /**
     * Time taken for the command to execute and return its result (in milliseconds)
     */
    val timeTaken: Long
) {

    fun debugString(debugName: String) = "$debugName done in ${Utils.toDuration(timeTaken)}"

    companion object {
        private val LOG = LoggerFactory.getLogger(ExecutionResult::class.java)

        fun get(command: String, debugName: String) : ExecutionResult {
            LOG.debug("Sending '$command'")

            val start = System.currentTimeMillis()
            val process = createProcess(command)

            val text = process.inputStream.bufferedReader().use { it.readText() }
            val success = process.waitFor() == 0

            val end = System.currentTimeMillis()

            return ExecutionResult(debugName, text, success, end - start)
        }

        @Throws(IOException::class)
        private fun createProcess(command: String): Process {
            val args = ArrayList<String>()
            if (Utils.isLinux) {
                args.add("/bin/bash")
                args.add("-c")
            } else {
                args.add("cmd")
                args.add("/c")
            }
            args.add(command)
            return ProcessBuilder()
                .command(args)
                .redirectErrorStream(true)
                .start()
        }
    }
}