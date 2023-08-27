package com.bluesmods.bluecordpatcher.executables

import com.bluesmods.bluecordpatcher.Utils
import java.io.File

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
        Utils.unzip(zipFile, zipToPath.toPath())
        zipFile.delete()
    }
}