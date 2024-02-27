package com.bluesmods.bluecordpatcher

import com.bluesmods.bluecordpatcher.command.OS
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.text.DecimalFormat
import java.util.zip.ZipInputStream
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

object Utils {

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

    @Throws(IOException::class)
    fun unzip(zipFile: File, to: Path) {
        if (to.exists()) to.toFile().deleteRecursively()
        to.toFile().mkdirs()

        ZipInputStream(zipFile.inputStream()).use { zipIn ->
            while (true) {
                val entry = zipIn.nextEntry ?: break

                val resolvedPath = to.toAbsolutePath().resolve(entry.name).normalize()
                if (!resolvedPath.startsWith(to.toAbsolutePath())) {
                    throw RuntimeException("Entry with an illegal path: " + entry.name)
                }
                if (entry.isDirectory) {
                    Files.createDirectories(resolvedPath)
                } else {
                    Files.createDirectories(resolvedPath.parent)
                    Files.copy(zipIn, resolvedPath)
                }
            }
        }
    }

    fun setFileExecutable(file: File) = apply {
        if (OS.isLinux) {
            // chmod +x <file>
            file.setReadable(true)
            file.setExecutable(true, true)
        }
    }
}
