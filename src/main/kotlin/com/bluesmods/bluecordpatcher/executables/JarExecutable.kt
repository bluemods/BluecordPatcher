package com.bluesmods.bluecordpatcher.executables

import com.bluesmods.bluecordpatcher.command.Command
import java.io.File

open class JarExecutable(
    private val jarFile: File,
    jarUrl: String,
    jarHash: String? = null
) : Executable(jarFile.name, jarUrl, jarHash) {

    companion object {
        // TODO: Add detection for low memory devices?
        private const val MAX_MEMORY_MB = 2048
    }

    override fun buildCommand() : Command = Command("java")
        .add("-Xmx${MAX_MEMORY_MB}m")
        .addFile("-jar", jarFile)

    override fun isInstalled(): Boolean = jarFile.exists() && jarFile.isFile

    override fun installDelegate() {
        super.download(jarFile)
    }
}