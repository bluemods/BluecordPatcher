package com.bluesmods.bluecordpatcher.executables

import com.bluesmods.bluecordpatcher.Utils
import java.io.File

class GradleExecutable(private val gradleProjectHome: File, private val gradleJavaHome: File?) : Executable("Gradle Build", "", null) {
    override fun isInstalled(): Boolean = true
    override fun installDelegate() {}

    override fun getCommand(args: String): String {
        val exePath = File(gradleProjectHome, if (Utils.isLinux) "gradlew" else "gradlew.bat")

        var ret = "\"${exePath.absolutePath} --project-dir \"${gradleProjectHome.absolutePath}\" :app:assembleDebug"
        if (gradleJavaHome != null && gradleJavaHome.exists()) {
            ret += " \"-Dorg.gradle.java.home=${gradleJavaHome.absolutePath}\""
        }
        ret += "\""
        return ret
    }
}