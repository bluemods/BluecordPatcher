package com.bluesmods.bluecordpatcher.command

object OS {
    val isLinux: Boolean
    val isWindows: Boolean

    init {
        val os = System.getProperty("os.name").lowercase()
        isLinux = os.contains("nix") || os.contains("nux") || os.contains("aix")
        isWindows = !isLinux && "windows" == os
    }
}