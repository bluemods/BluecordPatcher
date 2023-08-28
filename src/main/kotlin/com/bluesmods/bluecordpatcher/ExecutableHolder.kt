package com.bluesmods.bluecordpatcher

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
        var args = "d "
        if (!keepDebugInfo) args += " --no-debug-info "
        args += "--force -o \"${decompiledOutputDir.absolutePath}\" \"${apkInFile.absolutePath}\""

        return apkTool.execute(args)
    }

    fun compileApk(decompiledPath: File, apkOutFile: File): ExecutionResult {
        return apkTool.execute("b --use-aapt2 -o \"${apkOutFile.absolutePath}\" \"${decompiledPath.absolutePath}\"")
    }

    fun zipalignApk(apkInFile: File, apkOutFile: File): ExecutionResult {
        return zipAlign.execute("-f 4 \"${apkInFile.absolutePath}\" \"${apkOutFile.absolutePath}\"")
    }

    fun signApk(signingInfo: SigningInfo, apkToSign: File) : ExecutionResult = when(signingInfo) {
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
        val args = "sign " +
                "--key \"${keyFile.absolutePath}\" " +
                "--cert \"${certFile.absolutePath}\" " +
                "--out \"${apkToSign.absolutePath}\" " +
                "\"${apkToSign.absolutePath}\""
        return apkSigner.execute(args)
    }

    private fun signApkWithPassword(keyFile: File, keystorePassword: String, apkToSign: File): ExecutionResult {
        val args = "sign " +
                "--ks \"${keyFile.absolutePath}\" " +
                "--ks-pass pass:\"$keystorePassword\" " +
                "--key-pass pass:\"$keystorePassword\" " +
                "--out \"${apkToSign.absolutePath}\" " +
                "\"${apkToSign.absolutePath}\""
        return apkSigner.execute(args)
    }

    fun installApk(apkFile: File): ExecutionResult {
        return adb.execute("install -r \"${apkFile.absolutePath}\"")
    }

    fun baksmali(decompiledDexDir: File, dexOutFile: File): ExecutionResult {
        return baksmali.execute("d \"${decompiledDexDir.absolutePath}\" -l -o \"${dexOutFile.absolutePath}\"")
    }

    fun smali(decompiledDexDir: File, dexOutFile: File): ExecutionResult {
        return smali.execute("a \"${decompiledDexDir.absolutePath}\" -o \"${dexOutFile.absolutePath}\"")
    }

    fun installIfNeeded() {
        val needToInstall = executableList.filter { !it.isInstalled() }
        if (needToInstall.isEmpty()) return

        println("The following executables need to be downloaded:\n- ${needToInstall.map { it.name + " : " + it.url }.joinToString(separator = "\n- ")}")
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