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

    override fun makeProtoc(): ZippedExecutable {
        val version = Constants.PROTOC_VERSION
        val out = File(baseDir, "protoc")
        val type = when (val arch = System.getProperty("os.arch")) {
            "x86", "i386" -> "x86_32"
            "amd64", "x86_64" -> "x86_64"
            "aarch64" -> "aarch_64"
            "ppc64le" -> "ppcle_64"
            "s390x" -> "s390_64"
            else -> throw IllegalStateException("unsupported architecture for protoc: $arch")
        }
        return ZippedExecutable(
            File(baseDir, version),
            out,
            File(out, "bin/protoc"),
            "https://github.com/protocolbuffers/protobuf/releases/download/v${version}/protoc-${version}-linux-${type}.zip"
        )
    }

    private fun makeBuildToolsExecutable(commandName: String): ZippedExecutable {
        val version = "build-tools_${Constants.ANDROID_BUILD_TOOLS_VERSION}-linux.zip"
        val out = File(baseDir, "build-tools-${Constants.ANDROID_BUILD_TOOLS_VERSION}")

        return ZippedExecutable(
            File(baseDir, version),
            out,
            File(out, "android-${Constants.OS_CODENAME}/$commandName"),
            "https://dl.google.com/android/repository/$version"
        )
    }
}