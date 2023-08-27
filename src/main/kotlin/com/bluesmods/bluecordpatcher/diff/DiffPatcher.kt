package com.bluesmods.bluecordpatcher.diff

import com.bluesmods.bluecordpatcher.Downloader
import com.bluesmods.bluecordpatcher.ExecutableHolder
import com.bluesmods.bluecordpatcher.Utils
import com.bluesmods.bluecordpatcher.config.Config
import com.bluesmods.bluecordpatcher.diff.DiffPatcher.CompareResult.*
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.*
import kotlin.io.path.*
import kotlin.system.exitProcess


class DiffPatcher(private val config: Config, private val holder: ExecutableHolder) {

    companion object {
        private val LOG = LoggerFactory.getLogger(DiffPatcher::class.java)

        /** Stock Discord APK for 126.21 - Stable */
        private const val DISCORD_STOCK_APK_URL = "https://pool.apk.aptoide.com/floricraft/com-discord-126021-62830695-9be8c22e12da4fdcfeb09242a4b3a648.apk"
        private const val DISCORD_STOCK_APK_SHA384_HASH = "5c3ae81ea5bb9379ee5f0b484960d887518e7e87223a195a34f61fda6c170c794e8acdda10fd890755ca1474edab0d3d"
    }

    private val patchDir = config.getPatchDir().toPath()
    private val patchDeleteDir = config.getPatchFilesToDelete().toPath()

    /**
     * Downloads the stock Discord APK if not present.
     *
     * If the baseDecompiledApk does not exist yet, this will also decompile it to the correct directory,
     * so it can be automatically patched / transformed into Bluecord.
     */
    fun setup() {
        downloadDiscordApk()
        decompileDiscordApk(config.baseDecompiledApk)
    }

    /**
     * This method does the following:
     *
     * - Downloads the unmodded discord APK and decompiles it
     * - Compares an existing modded decompiled APK, putting the changed files
     *      in the 'patch' directory of the Bluecord git repository
     *
     * Then, these patches are used for the main class.
     */
    fun patch() {
        downloadAndDecompile(config.getStockDiscordDecompiledFile())
        createPatchDirInGradleProject()

        try {
            LOG.info("Creating patch...")
            createPatches()
            LOG.info("Done creating patch (${config.getPatchDir()})")
        } catch (e: PatchException) {
            // Fatal error occurred when making patches.
            // Consider the patch directory invalid at this point,
            // and delete it.
            config.getPatchDir().deleteRecursively()
            LOG.error("PATCH ERROR: ${e.message}", e)
            exitProcess(1)
        }
    }

    private fun downloadAndDecompile(decompileOutput: File) {
        downloadDiscordApk()
        decompileDiscordApk(decompileOutput)
    }

    private fun downloadDiscordApk() {
        val out = config.getStockDiscordCompiledApkFile()
        if (out.exists()) return

        println("The base Discord APK needs to be downloaded for patching.")
        println("Would you like to do so now? (y/n)")

        if (readln().equals("y", true)) {
            LOG.info("Downloading Discord APK... (This could take a while)")
            Downloader.download(DISCORD_STOCK_APK_URL, out, DISCORD_STOCK_APK_SHA384_HASH)
        } else {
            LOG.warn("APK file download declined, exiting process")
            exitProcess(1)
        }
    }

    private fun decompileDiscordApk(decompileOutput: File) {
        if (decompileOutput.exists() && decompileOutput.isDirectory && (decompileOutput.list()?.size ?: 0) >= 12) {
            LOG.debug("Stock APK already decompiled, skipping decompilation")
        } else {
            LOG.info("Decompiling stock Discord APK... (This could take a while)")
            holder.decompileApk(config.getStockDiscordCompiledApkFile(), decompileOutput, true)
        }
    }

    private fun createPatchDirInGradleProject() {
        val patchFile = patchDir.toFile()
        if (!patchFile.exists() && !patchFile.mkdirs()) {
            throw IllegalStateException("Could not find or create patch dir (${patchFile.absolutePath})")
        }
    }

    private fun createPatches() {
        val filesToDelete = arrayListOf<String>()

        val bluecordModdedApk = config.baseDecompiledApk.toPath()
        val stockApk = config.getStockDiscordDecompiledFile().toPath()

        val test = compare(
            File(bluecordModdedApk.toFile(), "smali/c.smali").toPath(),
            File(stockApk.toFile(), "smali/c.smali").toPath(),
        )

        if (test != EQUAL) {
            LOG.error("The stock decompiled APK or the bluecord APK is invalid.\n\n" +
                    "The likely cause is that one of them was decompiled with --no-debug-info. " +
                    "Check the files and try again.")
            exitProcess(0)
        }

        val pathsToCheck = listOf(
            "assets",
            "lib",
            "res",
            "smali",
            "smali_classes2",
            "smali_classes3",
            "smali_classes4/\$r8\$backportedMethods\$utility\$Long\$1\$hashCode.smali", // annoying edge case
            "smali_classes4/b",
            "smali_classes4/z",
            "AndroidManifest.xml",
            "apktool.yml",
        )

        val bluecordPaths = pathsToCheck.map { File(bluecordModdedApk.toFile(), it).toPath() }
        for (path in bluecordPaths) {
            LOG.debug("Walking {}", path)
            Utils.walkDirectory(path) {
                checkDiff(stockApk, bluecordModdedApk, it)
            }
        }

        val reversePaths = pathsToCheck.map { File(stockApk.toFile(), it).toPath() }
        for (path in reversePaths) {
            LOG.debug("Walking {} (reverse)", path)
            Utils.walkDirectory(path) {
                val stockFile: Path = bluecordModdedApk.resolve(stockApk.relativize(it))

                if (!stockFile.exists()) {
                    var relativePath = it.toString()
                        .replace("\\", "/")
                        .replace(config.getStockDiscordDecompiledFile().absolutePath.replace("\\", "/"), "")

                    if (relativePath.startsWith("/")) {
                        relativePath = relativePath.substring(1)
                    }

                    filesToDelete.add(relativePath)
                }
            }
        }
        patchDeleteDir.writeLines(filesToDelete, StandardCharsets.UTF_8)
    }

    private fun checkDiff(stockApk: Path, bluecordModdedApk: Path, bluecordFile: Path) {
        val stockFile: Path = stockApk.resolve(bluecordModdedApk.relativize(bluecordFile))

        val compareResult = compare(stockFile, bluecordFile)

        when (compareResult) {
            EQUAL -> {} // nothing to do
            NOT_EQUAL, STOCK_FILE_NOT_EXISTS -> {
                // LOG.info("Different: {} {} to {}", compareResult, stockFile, bluecordFile)

                val patchOutput = patchDir.resolve(bluecordModdedApk.relativize(bluecordFile))
                Files.createDirectories(patchOutput.parent)
                Files.copy(bluecordFile, patchOutput, StandardCopyOption.REPLACE_EXISTING)
            }
            /*BLUECORD_FILE_NOT_EXISTS -> {
                val patchOutput = patchDir.resolve(bluecordModdedApk.relativize(bluecordFile))
                filesToDelete.add(patchOutput)
                patchOutput.deleteIfExists()
            }*/
        }
    }

    private fun compare(stockFile: Path, bluecordFile: Path) : CompareResult {
        val stockExists = stockFile.exists()
        val bluecordExists = bluecordFile.exists()

        return if (!stockExists && !bluecordExists) {
            val msg = "It looks like you're attempting to do a patch on your first run, which won't work.\n" +
                    "Run this jar WITHOUT the --patch argument first"
            throw PatchException(msg)
        } else if (stockExists && !bluecordExists) {
            throw PatchException("This should be impossible")
        } else if (!stockExists && bluecordExists) {
            STOCK_FILE_NOT_EXISTS
        } else {
            // both files exist
            if (contentEquals(stockFile, bluecordFile)) {
                EQUAL
            } else {
                NOT_EQUAL
            }
        }
    }

    private fun contentEquals(stockFile: Path, bluecordFile: Path): Boolean {
        if (stockFile.fileSize() != bluecordFile.fileSize()) {
            return false
        }

        return stockFile.inputStream(StandardOpenOption.READ)
            .use { stockIs ->
                bluecordFile.inputStream(StandardOpenOption.READ).use { bluecordIs ->
                    IOUtils.contentEquals(stockIs, bluecordIs)
                }
            }
    }

    enum class CompareResult {
        EQUAL,
        NOT_EQUAL,
        STOCK_FILE_NOT_EXISTS,
        // BLUECORD_FILE_NOT_EXISTS
    }

    class PatchException(message: String) : Exception(message)
}