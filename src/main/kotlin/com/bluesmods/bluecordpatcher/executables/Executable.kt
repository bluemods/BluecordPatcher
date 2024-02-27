package com.bluesmods.bluecordpatcher.executables

import com.bluesmods.bluecordpatcher.HashUtils
import com.bluesmods.bluecordpatcher.command.Command
import com.bluesmods.bluecordpatcher.command.ExecutionResult
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
    abstract fun buildCommand(): Command

    /**
     * Attempts to install the given executable.
     * returns true if the executable is successfully installed.
     */
    fun install(): Boolean {
        if (isInstalled()) return true
        return runCatching {
            LOG.info("Downloading $name...")
            installDelegate()
            LOG.info("$name finished")
        }.onFailure {
            LOG.warn("Failed to install $name", it)
        }.isSuccess
    }

    /**
     * Executes the command with the given arguments.
     */
    @JvmOverloads
    fun execute(builder: Command.() -> Unit = {}) : ExecutionResult = buildCommand().apply(builder).execute()

    @Throws(IOException::class)
    fun download(output: File) {
        if (output.exists()) output.delete()
        output.createNewFile()

        var retryNumber = 0

        while (true) {
            try {
                URL(url).openStream().use { Files.copy(it, output.toPath(), StandardCopyOption.REPLACE_EXISTING) }
                break
            } catch (e: IOException) {
                if (++retryNumber == 3) throw e
                LOG.warn("failed to download $url, retrying ($retryNumber/3)")
            }
        }

        if (sha384Hash != null) {
            val computedHash = HashUtils.sha384Hash(output)
            if (sha384Hash != computedHash) {
                throw IOException("File from $url has incorrect hash.\nExpected: $sha384Hash\nReceived: $computedHash")
            }
        }
    }
}