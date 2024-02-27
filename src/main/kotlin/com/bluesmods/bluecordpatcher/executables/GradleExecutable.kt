package com.bluesmods.bluecordpatcher.executables

import com.bluesmods.bluecordpatcher.command.Command
import com.bluesmods.bluecordpatcher.command.OS
import java.io.File

class GradleExecutable(private val gradleProjectHome: File, private val gradleJavaHome: File?)
    : Executable("Gradle Build", "", null) {

    override fun isInstalled(): Boolean = true
    override fun installDelegate() {}

    override fun buildCommand(): Command {
        return Command(File(gradleProjectHome, if (OS.isLinux) "gradlew" else "gradlew.bat")).apply {
            addFile("--project-dir", gradleProjectHome)
            add(":app:assembleDebug")
            if (gradleJavaHome != null && gradleJavaHome.exists()) {
                add("-Dorg.gradle.java.home=${gradleJavaHome.absolutePath}")
            }
        }
    }
}