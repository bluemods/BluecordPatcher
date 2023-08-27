package com.bluesmods.bluecordpatcher.executables

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.exists

class ZippedExecutable(
    /**
     * Location where the zip file is downloaded to.
     */
    private val zipFile: File,

    /**
     * The location where the [zipFile] is extracted to.
     */
    private val zipToPath: File,

    /**
     * The location of the executable to run after extraction.
     */
    private val executableFile: File,

    /**
     * URL where the ZIP file is downloaded from
     */
    zipUrl: String,

    /**
     * SHA-384 hash of the zip file
     */
    zipHash: String? = null
) : Executable(executableFile.name, zipUrl, zipHash) {

    override fun getCommand(args: String): String = executableFile.absolutePath + " " + args

    override fun isInstalled(): Boolean = executableFile.exists() && executableFile.isFile

    override fun installDelegate() {
        super.download(zipFile)
        unzip(zipFile, zipToPath.toPath())
        zipFile.delete()
    }

    @Throws(IOException::class)
    private fun unzip(from: File, to: Path) {
        if (to.exists()) to.toFile().deleteRecursively()
        to.toFile().mkdirs()

        ZipInputStream(from.inputStream()).use { zipIn ->
            while (true) {
                val entry = zipIn.getNextEntry() ?: break

                val resolvedPath = to.toAbsolutePath().resolve(entry.name).normalize()
                if (!resolvedPath.startsWith(to.toAbsolutePath())) {
                    throw RuntimeException("Entry with an illegal path: " + entry.name)
                }
                if (entry.isDirectory) {
                    Files.createDirectories(resolvedPath)
                } else {
                    Files.createDirectories(resolvedPath.parent)
                    Files.copy(zipIn, resolvedPath)
                }
            }
        }
    }
}