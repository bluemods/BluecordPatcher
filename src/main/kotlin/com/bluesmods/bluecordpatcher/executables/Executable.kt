package com.bluesmods.bluecordpatcher.executables

import com.bluesmods.bluecordpatcher.Downloader
import com.bluesmods.bluecordpatcher.ExecutionResult
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

abstract class Executable(val name: String, val url: String, val sha384Hash: String? = null) {

    companion object {
        private val LOG = LoggerFactory.getLogger(Executable::class.java)
    }

    /**
     * Returns true if the executable is installed
     */
    abstract fun isInstalled(): Boolean

    @Throws(IOException::class)
    abstract fun installDelegate()

    /**
     * Returns the command to execute
     */
    abstract fun getCommand(args: String): String

    /**
     * Attempts to install the given executable.
     * returns true if the executable is successfully installed.
     */
    fun install(): Boolean {
        if (isInstalled()) return true
        return try {
            LOG.info("Downloading $name...")
            installDelegate()
            LOG.info("$name finished")
            true
        } catch (e: IOException) {
            LOG.warn("Failed to install $name", e)
            false
        }
    }

    /**
     * Executes the command with the given arguments.
     */
    @JvmOverloads
    fun execute(args: String = "") : ExecutionResult = ExecutionResult.get(getCommand(args).trim(), name)

    fun download(outputFile: File) = Downloader.download(url, outputFile, sha384Hash)

}