package com.bluesmods.bluecordpatcher

import com.bluesmods.bluecordpatcher.Constants.IGNORED_PATCH_FILES
import com.bluesmods.bluecordpatcher.Constants.MODS_SMALI_DEX_NUMBER
import com.bluesmods.bluecordpatcher.Utils.toDuration
import com.bluesmods.bluecordpatcher.command.ExecutionResult
import com.bluesmods.bluecordpatcher.config.ArgParser
import com.bluesmods.bluecordpatcher.config.Config
import com.bluesmods.bluecordpatcher.diff.DiffPatcher
import com.bluesmods.bluecordpatcher.loader.ExecutableLoader
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.*
import kotlin.io.path.name
import kotlin.system.exitProcess


object Main {
    private val LOG = LoggerFactory.getLogger(Main::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val start = System.currentTimeMillis()
        doWork(ArgParser.parse(*args))
        val end = System.currentTimeMillis()

        LOG.info("Total time: ${toDuration(start, end)}")
    }

    private fun doWork(config: Config) {
        if (config.flags.createPatches && config.flags.quickMode) {
            LOG.error("--patch and -q arguments cannot be used at the same time")
            exitProcess(1)
        }
        if (config.flags.quickMode) {
            if (!config.getCompiledApkFile().exists()) {
                LOG.error("Quick mode cannot run, the previously built APK does not exist.")
                exitProcess(1)
            }
        }

        val holder = ExecutableLoader.load(config)
        holder.installIfNeeded()

        val patcher = DiffPatcher(config, holder)
        patcher.setup()

        if (config.flags.createPatches) {
            patcher.patch()
        }

        holder.gradleBuildApk().exitOnFailure("Gradle build")
        holder.decompileApk(config.getGradleBuiltApkFile(), config.getDecompiledFile(), config.flags.keepDebugInfo).exitOnFailure("Decompile")

        modifyDecompiledFile(config)

        if (config.flags.quickMode) {
            executeQuickMode(config, holder)
        } else {
            injectPatch(config)
            LOG.info("Compiling APK... (this could take a while)")
            holder.compileApk(config.baseDecompiledApk, config.getCompiledApkFile()).exitOnFailure("Compile")
        }

        holder.zipalignApk(config.getCompiledApkFile(), config.getCompiledAlignedApkFile()).exitOnFailure("Zipalign")
        holder.signApk(config.signingInfo, config.getCompiledAlignedApkFile()).exitOnFailure("Sign")
        holder.installApk(config.getCompiledAlignedApkFile()).exitOnFailure("Install")

        // Automatically start the app
        holder.extractBasicPackageInfo(config.getCompiledAlignedApkFile())?.let {
            LOG.info("Launching: ${it.packageName}/${it.launchableActivityClassName}")
            holder.launchApk(it.packageName, it.launchableActivityClassName)
        } ?: run {
            LOG.error("could not extract package info / find entry point to auto launch app")
        }

        // Clean up temporary files...
        config.getCompiledApkFile().delete()
        config.getCompiledAlignedApkIdsigFile().delete()
    }

    private fun injectPatch(config: Config) {
        val start = System.currentTimeMillis()

        val patchDir = config.getPatchDir()
        val patchDeleteFile = config.getPatchFilesToDelete()

        if (!patchDir.isDirectory) {
            LOG.info("Skipping patch injection, patch dir doesn't exist (your local repo may be outdated, git clone the latest one)")
            return
        }

        // Copy patched files
        val decompiledPath = config.baseDecompiledApk.toPath()
        val patchPath = patchDir.toPath()

        Utils.walkDirectory(patchPath) {
            if (it.name in IGNORED_PATCH_FILES) {
                return@walkDirectory
            }

            val patchedOutput: Path = decompiledPath.resolve(patchPath.relativize(it))

            LOG.debug("Copying patch: {} -> {}", it, patchedOutput)

            Files.createDirectories(patchedOutput.parent)
            Files.copy(it, patchedOutput, StandardCopyOption.REPLACE_EXISTING)
        }

        // Delete the files needed to delete
        val filesToDelete: List<File> = try {
            patchDeleteFile.readLines().map { File(config.baseDecompiledApk, it.trim()) }
        } catch (e: IOException) {
            LOG.warn("Failed to read ${patchDeleteFile.absolutePath}")
            emptyList()
        }

        filesToDelete.forEach {
            if (it.exists() && it.isFile) {
                LOG.info("Deleting $it")
                it.delete()
            }
        }

        val end = System.currentTimeMillis()
        LOG.info("Patch injection done in ${toDuration(start, end)}")
    }

    private fun modifyDecompiledFile(config: Config) {
        val start = System.currentTimeMillis()

        val decompiledDir = config.getDecompiledFile()
        val bluecordDir = File(config.baseDecompiledApk, "smali_classes$MODS_SMALI_DEX_NUMBER")

        val blueOutputDir = File(bluecordDir, "mods").apply { deleteRecursively() }

        var i = 0
        while (true) {
            val folderName = File(decompiledDir, if (++i == 1) "smali" else "smali_classes$i")
            if (!folderName.isDirectory) break

            val modsDir = File(folderName, "mods")
            val kotlinDir = File(folderName, "kotlin")
            val renamedKotlinDir = File(folderName, "kotlin2")

            if (modsDir.isDirectory) fixKotlinAndCopy(modsDir, blueOutputDir)
            if (kotlinDir.isDirectory) {
                kotlinDir.renameTo(renamedKotlinDir)
                fixKotlinAndCopy(renamedKotlinDir, File(bluecordDir, "_doNotTouch/kotlin2"))
            }
        }
        val end = System.currentTimeMillis()
        LOG.info("Smali port done in ${toDuration(start, end)}")
    }

    private fun executeQuickMode(config: Config, holder: ExecutableHolder) {
        try {
            val compiledApkPath = config.getCompiledApkFile().toPath()

            val dexFolder = "smali_classes$MODS_SMALI_DEX_NUMBER"
            val dexFile = "classes$MODS_SMALI_DEX_NUMBER.dex"

            val dexOutFile = File(config.baseDir, dexFile)

            holder.smali(
                decompiledDexDir = File(config.baseDecompiledApk, dexFolder),
                dexOutFile = dexOutFile
            ).exitOnFailure("Smali (quick mode)")

            FileSystems.newFileSystem(compiledApkPath, null as ClassLoader?).use { fs ->
                val from = dexOutFile.toPath()
                val to = fs.getPath(dexFile)
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            LOG.error("executeQuickMode", e)
            exitProcess(1)
        }
    }

    private fun fixKotlinAndCopy(inputDir: File, outputDir: File) {
        for (smaliFile in FileUtils.listFiles(inputDir, arrayOf("smali"), true)) {
            smaliFile.writeText(smaliFile.readText()
                .replace("Lkotlin/", "Lkotlin2/")
            )
        }
        FileUtils.copyDirectory(inputDir, outputDir, false)
    }

    fun ExecutionResult.exitOnFailure(debugName: String) = apply {
        if (isSuccessful) {
            LOG.info("{} complete after {}", debugName, toDuration(executionTime))
        } else {
            LOG.warn("Command ${command.name} failed:")
            LOG.warn(string)
            exitProcess(1)
        }
    }
}