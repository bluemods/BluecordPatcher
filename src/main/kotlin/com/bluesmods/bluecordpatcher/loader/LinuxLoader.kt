package com.bluesmods.bluecordpatcher.loader

import com.bluesmods.bluecordpatcher.Constants
import com.bluesmods.bluecordpatcher.config.Config
import com.bluesmods.bluecordpatcher.executables.ZippedExecutable
import java.io.File

class LinuxLoader(config: Config, baseDir: File) : ExecutableLoader(config, baseDir) {
    override fun makeAdb(): ZippedExecutable {
        val version = "platform-tools_${Constants.ANDROID_PLATFORM_TOOLS_VERSION}-linux.zip"
        val out = File(baseDir, "platform-tools")

        return ZippedExecutable(
            File(baseDir, version),
            out,
            File(out, "platform-tools/adb"),
            "https://dl.google.com/android/repository/$version"
        )
    }

    override fun makeAapt(): ZippedExecutable {
        return makeBuildToolsExecutable("aapt")
    }

    override fun makeApkSigner(): ZippedExecutable {
        return makeBuildToolsExecutable("apksigner")
    }

    override fun makeZipalign(): ZippedExecutable {
        return makeBuildToolsExecutable("zipalign")
    }

    private fun makeBuildToolsExecutable(commandName: String): ZippedExecutable {
        val version = "build-tools_${Constants.ANDROID_BUILD_TOOLS_VERSION}-linux.zip"
        val out = File(baseDir, "build-tools")

        return ZippedExecutable(
            File(baseDir, version),
            out,
            File(out, "android-UpsideDownCake/$commandName"),
            "https://dl.google.com/android/repository/$version"
        )
    }
}