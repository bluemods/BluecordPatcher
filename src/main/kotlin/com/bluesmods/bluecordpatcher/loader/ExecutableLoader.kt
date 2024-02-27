package com.bluesmods.bluecordpatcher.loader

import com.bluesmods.bluecordpatcher.Constants
import com.bluesmods.bluecordpatcher.ExecutableHolder
import com.bluesmods.bluecordpatcher.command.OS
import com.bluesmods.bluecordpatcher.config.Config
import com.bluesmods.bluecordpatcher.executables.GradleExecutable
import com.bluesmods.bluecordpatcher.executables.JarExecutable
import com.bluesmods.bluecordpatcher.executables.ZippedExecutable
import java.io.File

abstract class ExecutableLoader(private val config: Config, protected val baseDir: File) {

    init {
        require(baseDir.exists() || baseDir.mkdirs()) {
            "Could not make directory ${baseDir.absolutePath}"
        }
    }

    abstract fun makeAdb(): ZippedExecutable
    abstract fun makeApkSigner(): ZippedExecutable
    abstract fun makeZipalign(): ZippedExecutable

    private fun makeGradleBuild(): GradleExecutable {
        return GradleExecutable(config.gradleProjectHome, config.gradleJavaHome)
    }

    private fun makeSmali(): JarExecutable {
        val version = "smali-${Constants.SMALI_VERSION}.jar"
        return JarExecutable(File(baseDir, version), "https://bitbucket.org/JesusFreke/smali/downloads/$version")
    }

    private fun makeBaksmali(): JarExecutable {
        val version = "baksmali-${Constants.BAKSMALI_VERSION}.jar"
        return JarExecutable(File(baseDir, version), "https://bitbucket.org/JesusFreke/smali/downloads/$version")
    }

    private fun makeApkTool(): JarExecutable {
        val version = "apktool_${Constants.APKTOOL_VERSION}.jar"
        return JarExecutable(File(baseDir, version), "https://bitbucket.org/iBotPeaches/apktool/downloads/$version")
    }

    companion object {
        @JvmStatic
        fun load(config: Config): ExecutableHolder {
            config.baseDir.mkdirs()
            return makeLoader(config).load()
        }

        private fun makeLoader(config: Config) = if (OS.isLinux) {
            LinuxLoader(config, File(config.baseDir, "linux"))
        } else if (OS.isWindows) {
            WindowsLoader(config, File(config.baseDir, "windows"))
        } else {
            throw IllegalArgumentException("Unsupported OS (${System.getProperty("os.name")}, aborting")
        }
    }

    fun load(): ExecutableHolder {
        return ExecutableHolder(
            makeGradleBuild(),
            makeAdb(),
            makeApkSigner(),
            makeZipalign(),
            makeSmali(),
            makeBaksmali(),
            makeApkTool()
        )
    }
}