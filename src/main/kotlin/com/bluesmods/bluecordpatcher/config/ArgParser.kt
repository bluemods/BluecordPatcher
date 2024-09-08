package com.bluesmods.bluecordpatcher.config

import ch.qos.logback.classic.Level
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.internal.DefaultConsole
import org.ini4j.Ini
import org.ini4j.Profile
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

class ArgParser {

    @Parameter(
        names = ["-q", "--quick"],
        description = "Rebuilds an existing APK, replacing the mods dex file " +
                "with the code from the Android Studio project. " +
                "This may only be used if this tool built an APK previously with quick mode off, " +
                "and the only subsequent changes between builds were made in the Android Studio project"
    )
    var quickMode: Boolean = false

    @Parameter(
        names = ["-k", "--keep"],
        description = "Keeps debug lines in the Smali files, useful for debugging stack traces from the app.",
    )
    var keepDebugInfo: Boolean = false

    @Parameter(names = ["-h", "-help", "--help"], description = "Shows help", help = true)
    private var help: Boolean = false

    @Parameter(
        names = ["-v", "--verbose"],
        description = "Enables verbose logging to stdout.",
    )
    var verbose: Boolean = false

    @Parameter(
        names = ["--patch"],
        description = "Create patches in the Bluecord project from a modded Discord APK. " +
                "Use this when you make a change in the decompiled APK itself " +
                "and want to commit it back to the Bluecord repo.",
    )
    var createPatches: Boolean = false

    @Parameter(
        names = ["--beta"],
        description = "Builds and installs the Beta version of the apk as well as the release version (com.bluecordbeta)",
    )
    var createBetaApk: Boolean = false

    @Parameter(
        description = "[path/to/config/file]"
    )
    private var configFile: String = "config.ini"

    override fun toString() = "ArgParser(quickMode=$quickMode, keepDebugInfo=$keepDebugInfo, verbose=$verbose, configFile=$configFile)"

    companion object {
        fun parse(vararg args: String): Config {
            val arguments = ArgParser()

            val commander = JCommander.newBuilder()
                .programName("BluecordPatcher")
                .addObject(arguments)
                .console(DefaultConsole())
                .build()

            commander.parse(*args)

            if (arguments.help) {
                commander.usage()
                exitProcess(0)
            }

            val flags = Flags(arguments.quickMode, arguments.keepDebugInfo, arguments.verbose, arguments.createPatches, arguments.createBetaApk)

            val ini: Ini = try {
                Ini(File(arguments.configFile))
            } catch (e: Exception) {
                println("Invalid config file ${arguments.configFile}\n$e")
                exitProcess(0)
            }

            val config: Profile.Section = ini.getSectionOrThrow("config")
            val baseDir = config.getDirectoryOrCreate("BaseDir")
            val baseDecompiledApk = config.getFile("BaseDecompiledApk")
            if (baseDecompiledApk.exists() && !baseDecompiledApk.isDirectory) {
                throw IllegalArgumentException("BaseDecompiledApk is a file, expected a directory")
            }

            val signingConfig = ini.getSectionOrThrow("sign").parseSigningInfo(ini)
            val gradle = ini.getSectionOrThrow("gradle")
            val gradleProjectHome = gradle.getDirectoryOrThrow("ProjectHome")
            val gradleJavaHome = gradle["JavaHome"]?.let { gradle.getDirectoryOrThrow("JavaHome") }

            if (flags.verbose) {
                setLoggingLevel(Level.DEBUG)
            }
            return Config(flags, baseDir, baseDecompiledApk, signingConfig, gradleProjectHome, gradleJavaHome)
        }

        private fun Profile.Section.parseSigningInfo(ini: Profile): SigningInfo {
            return when (val signingType = getOrThrow("SigningType")) {
                "Password" -> {
                    val apkSigningKey = getFileOrThrow("KeyStoreFile")
                    val apkSigningPassword = getOrThrow("KeyStorePassword")

                    SigningInfo.PasswordSigningInfo(apkSigningKey, File(apkSigningPassword).readText())
                }
                "Key" -> {
                    val apkSigningKey = getFileOrThrow("Key")
                    val apkSigningCertificate = getFileOrThrow("Cert")

                    SigningInfo.PlainSigningInfo(apkSigningKey, apkSigningCertificate)
                }
                "Rotate" -> {
                    val lineage = getFileOrThrow("Lineage")
                    val signers = ArrayList<SigningInfo>()
                    for (i in 1..Int.MAX_VALUE) {
                        val signer = this["Signer$i"]?.let(ini::get) ?: break
                        signers.add(signer.parseSigningInfo(ini))
                    }
                    check(signers.isNotEmpty()) { "No signers found for Rotate SigningType. Specify them using Signer1, Signer2, etc..."}
                    SigningInfo.RotationSigningInfo(lineage, signers)
                }
                else -> {
                    error("Invalid SigningType ($signingType) expected \"Password\", \"Key\", or \"Rotate\"")
                }
            }
        }

        private fun Ini.getSectionOrThrow(key: String) : Profile.Section {
            return this[key] ?: throw IllegalArgumentException("Missing [$key] section in config file.")
        }

        private fun Profile.Section.getFile(key: String): File {
            return File(getOrThrow(key))
        }

        private fun Profile.Section.getFileOrThrow(key: String): File {
            val file = getFile(key)
            if (!file.exists()) {
                throw IllegalArgumentException("$key in ${this.name}: does not exist")
            }
            if (file.isDirectory) {
                throw IllegalArgumentException("$key in ${this.name}: is a directory, expected a file")
            }
            return file
        }

        private fun Profile.Section.getDirectoryOrThrow(key: String): File {
            val file = getFile(key)
            if (!file.exists()) {
                throw IllegalArgumentException("$key in ${this.name}: does not exist")
            }
            if (!file.isDirectory) {
                throw IllegalArgumentException("$key in ${this.name}: is a file, expected a directory")
            }
            return file
        }

        private fun Profile.Section.getDirectoryOrCreate(key: String): File {
            val file = getFile(key)
            if (!file.exists() && !file.mkdirs()) {
                throw IllegalArgumentException("$key in ${this.name}: does not exist and could not create dirs")
            }
            if (!file.isDirectory) {
                throw IllegalArgumentException("$key in ${this.name}: is a file, expected a directory")
            }
            return file
        }

        private fun Profile.Section.getOrThrow(key: String): String {
            return this[key]?.trim() ?: throw IllegalArgumentException("missing $key in ${this.name}")
        }

        @Suppress("SameParameterValue")
        private fun setLoggingLevel(level: Level) {
            try {
                (LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger).setLevel(level)
            } catch (e: Exception) {
                println("setLoggingLevel() failed wtf\n$e")
            }
        }
    }
}
