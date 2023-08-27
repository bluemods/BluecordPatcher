package com.bluesmods.bluecordpatcher.executables

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

    override fun getCommand(args: String) : String = "\"java -Xmx${MAX_MEMORY_MB}m -jar \"${jarFile.absolutePath}\" $args\""
    override fun isInstalled(): Boolean = jarFile.exists() && jarFile.isFile

    override fun installDelegate() {
        super.download(jarFile)
    }
}