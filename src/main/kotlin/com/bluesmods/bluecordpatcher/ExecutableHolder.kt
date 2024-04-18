package com.bluesmods.bluecordpatcher

import com.bluesmods.bluecordpatcher.command.ExecutionResult
import com.bluesmods.bluecordpatcher.config.SigningInfo
import com.bluesmods.bluecordpatcher.executables.GradleExecutable
import com.bluesmods.bluecordpatcher.executables.JarExecutable
import com.bluesmods.bluecordpatcher.executables.ZippedExecutable
import java.io.File
import java.lang.IllegalArgumentException
import kotlin.system.exitProcess

class ExecutableHolder(
    private val gradle: GradleExecutable,
    private val adb: ZippedExecutable,
    private val apkSigner: ZippedExecutable,
    private val zipAlign: ZippedExecutable,
    private val smali: JarExecutable,
    private val baksmali: JarExecutable,
    private val apkTool: JarExecutable
) {
    private val executableList = listOf(gradle, adb, apkSigner, zipAlign, smali, baksmali, apkTool)

    fun gradleBuildApk(): ExecutionResult {
        return gradle.execute()
    }

    fun decompileApk(apkInFile: File, decompiledOutputDir: File, keepDebugInfo: Boolean): ExecutionResult {
        return apkTool.execute {
            add("d")
            if (!keepDebugInfo) add("--no-debug-info")
            add("--force")
            addFile("-o", decompiledOutputDir)
            addFile(apkInFile)
        }
    }

    fun compileApk(decompiledPath: File, apkOutFile: File): ExecutionResult {
        return apkTool.execute {
            add("b")
            add("--use-aapt2")
            add("-nc")
            addFile("-o", apkOutFile)
            addFile(decompiledPath)
        }
    }

    fun zipalignApk(apkInFile: File, apkOutFile: File): ExecutionResult {
        return zipAlign.execute {
            add("-f")
            add("-p")
            add("4")
            addFile(apkInFile)
            addFile(apkOutFile)
        }
    }

    fun signApk(signingInfo: SigningInfo, apkToSign: File): ExecutionResult = when (signingInfo) {
        is SigningInfo.PlainSigningInfo -> {
            signApkWithCert(signingInfo.apkSigningKey, signingInfo.apkSigningCert, apkToSign)
        }
        is SigningInfo.PasswordSigningInfo -> {
            signApkWithPassword(signingInfo.apkSigningKey, signingInfo.apkKeyStorePassword, apkToSign)
        }
        else -> {
            throw IllegalArgumentException("Unknown SigningInfo: $signingInfo")
        }
    }

    private fun signApkWithCert(keyFile: File, certFile: File, apkToSign: File): ExecutionResult {
        return apkSigner.execute {
            add("sign")
            addFile("--key", keyFile)
            addFile("--cert", certFile)
            addFile("--out", apkToSign)
            addFile(apkToSign)
        }
    }

    private fun signApkWithPassword(keyFile: File, keystorePassword: String, apkToSign: File): ExecutionResult {
        return apkSigner.execute {
            add("sign")
            addFile("--ks", keyFile)
            add("--ks-pass", "pass:$keystorePassword") // Quotes are required in the terminal, but break the password when called in JVM. Don't use quotes.
            addFile("--out", apkToSign)
            addFile(apkToSign)
        }
    }

    fun installApk(apkFile: File): ExecutionResult {
        return adb.execute {
            add("install")
            add("-r")
            addFile(apkFile)
        }
    }

    fun baksmali(decompiledDexDir: File, dexOutFile: File): ExecutionResult {
        return baksmali.execute {
            add("d")
            addFile(decompiledDexDir)
            add("-l")
            add("-o")
            addFile(dexOutFile)
        }
    }

    fun smali(decompiledDexDir: File, dexOutFile: File): ExecutionResult {
        return smali.execute {
            add("a")
            addFile(decompiledDexDir)
            addFile("-o", dexOutFile)
        }
    }

    fun installIfNeeded() {
        val needToInstall = executableList.filter { !it.isInstalled() }
        if (needToInstall.isEmpty()) return

        println("The following executables need to be downloaded:\n- ${needToInstall.joinToString(separator = "\n- ") { it.name + " : " + it.url }}")
        println("Would you like to do so now? (y/n)")
        val action = readln()
        if (action.equals("y", true)) {
            needToInstall.forEach { it.install() }
        } else {
            println("Install declined, exiting")
            exitProcess(0)
        }
    }
}