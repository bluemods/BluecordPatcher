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
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.toList
import kotlin.system.exitProcess

object Main {
    private val LOG = LoggerFactory.getLogger(Main::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        LOG.info("-----Bluecord Patcher Startup-----")
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

        LOG.info("Executing gradle build, this can take a while...")
        holder.gradleBuildApk().exitOnFailure("Gradle build")
        holder.decompileApk(
            apkInFile = config.getGradleBuiltApkFile(),
            decompiledOutputDir = config.getDecompiledFile(),
            keepDebugInfo = config.flags.keepDebugInfo
        ).exitOnFailure("Decompile")

        modifyDecompiledFile(config)
        addProtos(holder, config)

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

    private fun addProtos(holder: ExecutableHolder, config: Config) {
        val protoDir = File(config.gradleProjectHome, "app/protos")
        val javaOutDir = File(config.gradleProjectHome, "app/src/main/generated")
        val protoFiles = Files.walk(protoDir.toPath())
            .filter { it.isRegularFile() && it.name.endsWith(".proto") }
            .toList()

        if (protoFiles.isEmpty()) {
            LOG.warn("no proto files")
            return
        }
        LOG.info("Compiling proto files...")

        for (protoFile in protoFiles) {
            holder.protocJava(
                protoFile = protoFile.toFile(),
                baseDir = protoDir,
                javaOutDir = javaOutDir,
            ).exitOnFailure("protoc-java ${protoFile.name}")

           if (config.goProtobufOutDir != null) {
               holder.protocGo(
                   protoFile = protoFile.toFile(),
                   baseDir = protoDir,
                   goOutDir = config.goProtobufOutDir,
               ).exitOnFailure("protoc-go ${protoFile.name}")
           }
        }
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
            LOG.warn("Failed to read ${patchDeleteFile.absolutePath}", e)
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
            val protoDir = File(folderName, "com/google/protobuf")

            if (modsDir.isDirectory) {
                fixKotlinAndCopy(modsDir, blueOutputDir)
            }
            if (kotlinDir.isDirectory) {
                kotlinDir.renameTo(renamedKotlinDir)
                fixKotlinAndCopy(renamedKotlinDir, File(bluecordDir, "_doNotTouch/kotlin2"))
            }
            if (protoDir.isDirectory) {
                fixKotlinAndCopy(protoDir, File(bluecordDir, "_doNotTouch/protobuf"))
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
            fixKotlinFile(smaliFile, false)
        }
        FileUtils.copyDirectory(inputDir, outputDir, false)
    }

    fun fixKotlinFile(smaliFile: File, removeDebugSymbols: Boolean = false) {
        fun String.removeAnnotation(type: String, clsName: String): String {
            var t = this
            while (true) {
                val start = t.indexOf(".annotation $type $clsName")
                if (start == -1) {
                    break
                }
                val end = t.indexOf(".end annotation", start)
                val replace = t.substring(start, end+15)
                t = t.replace(replace, "")
            }
            return t
        }

        val t = smaliFile.readText()
            .replace("Lkotlin/", "Lkotlin2/")
            .replace("kotlin.internal.jdk8.", "kotlin2.internal.jdk8.") // reflection crash fix for random
            .replace("Lkotlinx/", "Lkotlinx2/")
            .replace("Lio/grpc/", "Lio/grpc2/")
            .replace("Lcom/squareup/picasso/", "Lcom/squareup/picasso2/")
            .replace("    .annotation runtime Lkotlin2/jvm/JvmStatic;\n    .end annotation", "")
            .replace("\t.annotation runtime Lkotlin2/jvm/JvmStatic;\n\t.end annotation", "")
            .removeAnnotation("system", "Ldalvik/annotation/MethodParameters;")
            .removeAnnotation("runtime", "Lkotlin2/Metadata;")
            .removeAnnotation("system", "Ldalvik/annotation/SourceDebugExtension;")

        smaliFile.writeText(t)

        if (removeDebugSymbols) {
            smaliFile.writeText(smaliFile.readLines().filter {
                val test = it.trim()
                !test.startsWith(".param ") &&
                        !test.startsWith(".local ") &&
                        !test.startsWith(".end local") &&
                        !test.startsWith(".line ") &&
                        !test.startsWith(".restart ") &&
                        test != "nop"
            }.joinToString("\n").replace("\n{3,}".toRegex(), "\n\n"))
        }
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