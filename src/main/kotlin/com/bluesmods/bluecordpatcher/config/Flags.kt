package com.bluesmods.bluecordpatcher.config

data class Flags(
    /**
     * Rebuilds an existing APK, replacing the mods dex file
     * with the code from the Android Studio project.
     * This option will cut build time significantly when used.
     *
     * This may only be used if this tool built an APK previously with quick mode off,
     * and the only subsequent changes between builds were made in the Android Studio project (Java / Kotlin source files).
     */
    val quickMode: Boolean,

    /**
     * Keeps debug info in the decompiled smali files
     */
    val keepDebugInfo: Boolean,

    /**
     * Enables verbose logging
     */
    val verbose: Boolean,

    /**
     * If true, will create patches in the Bluecord project from a modded Discord APK
     */
    val createPatches: Boolean,

    /**
     * If true, build the beta app and install it.
     */
    val createBetaApk: Boolean
)
