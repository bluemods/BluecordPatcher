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
    private val aapt: ZippedExecutable,
    private val apkSigner: ZippedExecutable,
    private val zipAlign: ZippedExecutable,
    private val smali: JarExecutable,
    private val baksmali: JarExecutable,
    private val apkTool: JarExecutable
) {
    private val executableList = listOf(gradle, adb, aapt, apkSigner, zipAlign, smali, baksmali, apkTool)

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
        is SigningInfo.RotationSigningInfo -> {
            signApkRotation(apkToSign, signingInfo)
        }
        else -> {
            throw IllegalArgumentException("Unknown SigningInfo: $signingInfo")
        }
    }

    private fun signApkRotation(apkToSign: File, rotationSigningInfo: SigningInfo.RotationSigningInfo): ExecutionResult {
        return apkSigner.execute {
            add("sign")
            add("--rotation-min-sdk-version", "28")

            for ((index, signer) in rotationSigningInfo.signers.withIndex()) {
                if (index > 0) {
                    add("--next-signer")
                }
                when (signer) {
                    is SigningInfo.PlainSigningInfo -> {
                        addFile("--key", signer.apkSigningKey)
                        addFile("--cert", signer.apkSigningCert)
                    }
                    is SigningInfo.PasswordSigningInfo -> {
                        addFile("--ks", signer.apkSigningKey)
                        add("--ks-pass", "pass:${signer.apkKeyStorePassword}")
                    }
                    else -> error("unsupported signing type ${signer.javaClass.name}")
                }
            }
            addFile("--lineage", rotationSigningInfo.lineage)
            addFile("--out", apkToSign)
            addFile(apkToSign)
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

    data class BasicApkPackageInfo(
        val packageName: String,
        val launchableActivityClassName: String
    )

    fun extractBasicPackageInfo(apkFile: File): BasicApkPackageInfo? {
        val result = aapt.execute {
            add("dump")
            add("badging")
            addFile(apkFile)
        }
        return if (result.isSuccessful) {
            val packageName = result.lines.first()
                .substringAfter("package: name='")
                .substringBefore('\'')

            val launchableActivityClassName = result.lines.firstOrNull { "launchable-activity:" in it }
                ?.substringAfter("launchable-activity: name='")
                ?.substringBefore('\'')
                ?: return null

            BasicApkPackageInfo(packageName, launchableActivityClassName)
        } else {
            null
        }
    }

    fun launchApk(
        packageName: String,
        activityClassName: String,
        extraStrings: Map<String, String> = emptyMap() // extra strings to pass to the Intent
    ): ExecutionResult {
        // Kill first to ensure proper boot sequence
        adb.execute {
            add("shell")
            add("am")
            add("force-stop")
            add(packageName)
        }
        return adb.execute {
            add("shell")
            add("am")
            add("start")
            add("-n", "$packageName/$activityClassName".replace("""$""", """\$"""))
            extraStrings.forEach { (k, v) ->
                add("--es")
                add(k, v)
            }
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