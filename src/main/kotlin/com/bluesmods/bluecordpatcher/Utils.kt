package com.bluesmods.bluecordpatcher

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.text.DecimalFormat
import kotlin.io.path.isDirectory

object Utils {
    val isLinux = System.getProperty("os.name").lowercase() in arrayOf("nix", "nux", "aix")

    fun toDuration(start: Long, end: Long) = toDuration(end - start)
    fun toDuration(duration: Long): String = DecimalFormat("0.0").format(duration / 1000.0) + "s"

    fun walkDirectory(path: Path, fileVisited: (Path) -> Unit) {
        if (path.isDirectory()) {
            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                @Throws(IOException::class)
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    fileVisited.invoke(file)
                    return FileVisitResult.CONTINUE
                }
            })
        } else {
            fileVisited.invoke(path)
        }
    }
}
