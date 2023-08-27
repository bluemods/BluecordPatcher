package com.bluesmods.bluecordpatcher.config

import java.io.File

data class Config(
    /**
     * Flags passed in as command line arguments.
     */
    val flags: Flags,

    /**
     * Base directory where the patcher installs its dependencies and patches the APK into.
     */
    val baseDir: File,

    /**
     * The Decompiled Bluecord APK, where patches are merged into and rebuilt.
     */
    val baseDecompiledApk: File,

    /**
     * Info about how to sign the APK.
     */
    val signingInfo: SigningInfo,

    /**
     * The base directory of where the Bluecord gradle project is located.
     */
    val gradleProjectHome: File,

    /**
     * The Java Home to use when building the APK from gradle.
     */
    val gradleJavaHome: File?,
) {
    /**
     * Folder of the decompiled APK built by gradle.
     */
    fun getDecompiledFile(): File = File(baseDir, "decompiled")

    /**
     * File where the built gradle APK is located.
     */
    fun getGradleBuiltApkFile(): File = File(gradleProjectHome, "app/build/outputs/apk/debug/app-debug.apk")

    /**
     * Directory of the patch files.
     */
    fun getPatchDir(): File = File(gradleProjectHome, "patch")

    /**
     * This file contains a list of files to delete in the patched APK, separated by new line.
     */
    fun getPatchFilesToDelete(): File = File(getPatchDir(), "FilesToDelete.txt")

    /**
     * Folder of the recompiled file after patching.
     */
    fun getCompiledApkFile(): File = File(baseDir, "compiled.apk")

    /**
     * Folder of the recompiled, zipaligned, and signed file after patching.
     *
     * This is what's installed to the device over ADB.
     */
    fun getCompiledAlignedApkFile(): File = File(baseDir, "compiled-aligned.apk")

    /**
     * The location of the unmodded Discord APK file, decompiled for comparing purposes.
     */
    fun getStockDiscordDecompiledFile(): File = File(baseDir, "discord-stock-126021")

    /**
     * The location of the unmodded Discord APK file.
     */
    fun getStockDiscordCompiledApkFile(): File = File(baseDir, "discord-stock-126021.apk")

}