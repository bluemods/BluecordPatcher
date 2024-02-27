package com.bluesmods.bluecordpatcher.command

object OS {

    val isLinux = System.getProperty("os.name").lowercase() in arrayOf("nix", "nux", "aix")
    val isWindows = System.getProperty("os.name").lowercase() == "windows"

}