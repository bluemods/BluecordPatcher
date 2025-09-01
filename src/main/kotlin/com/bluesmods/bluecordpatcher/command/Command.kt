package com.bluesmods.bluecordpatcher.command

import org.slf4j.LoggerFactory
import java.io.File

class Command @JvmOverloads constructor(
    val name: String,
    private val waitBeforeRead: Boolean = false
) {
    @JvmOverloads
    constructor(path: File, waitBeforeRead: Boolean = false) : this(path.absolutePath, waitBeforeRead)

    companion object {
        private val LOG = LoggerFactory.getLogger(Command::class.java)
    }

    private val args = arrayListOf<String>()
    private var stdout: ByteArray? = null

    init {
        if (OS.isWindows) {
            args.add("cmd")
            args.add(name)
            args.add("/C")
        } else if (OS.isLinux) {
            args.add(name)
        } else {
            throw IllegalStateException("unknown OS (not Windows or Linux based)")
        }
    }

    fun add(arg: String) = apply {
        args.add(arg)
    }

    fun add(arg: Number) = apply {
        args.add(arg.toString())
    }

    fun add(key: String, value: String) = apply {
        args.add(key)
        args.add(value)
    }

    fun add(key: String, value: Number) = apply {
        args.add(key)
        args.add(value.toString())
    }

    fun addFile(arg: File) = apply {
        args.add(arg.absolutePath)
    }

    fun addFile(key: String, value: File) = apply {
        args.add(key)
        args.add(value.absolutePath)
    }

    fun addAll(vararg args: String) = apply {
        this.args.addAll(args)
    }

    fun addAll(args: Collection<String>) = apply {
        this.args.addAll(args)
    }

    fun setStdIn(bytes: ByteArray) = apply {
        this.stdout = bytes
    }

    fun execute(): ExecutionResult {
        val start = System.currentTimeMillis()
        val process = try {
            // println(args.joinToString(" "))
            ProcessBuilder().command(args).redirectErrorStream(true).start()
        } catch (e: Throwable) {
            LOG.error("failed to build $args", e)
            return ExecutionResult(this, ByteArray(0), -1, 0)
        }
        try {
            stdout?.let {
                process.outputStream.use { os ->
                    os.write(it)
                    os.flush()
                }
            }
            val bytes: ByteArray
            val exitCode: Int

            if (waitBeforeRead) {
                exitCode = process.waitFor()
                bytes = process.inputStream.buffered().use { it.readBytes() }
            } else {
                bytes = process.inputStream.buffered().use { it.readBytes() }
                exitCode = process.waitFor()
            }
            return ExecutionResult(this, bytes, exitCode, System.currentTimeMillis() - start)
        } catch (e: Throwable) {
            LOG.error("failed to exec $args", e)
            return ExecutionResult(this, ByteArray(0), -1, System.currentTimeMillis() - start)
        } finally {
            process.destroy()
        }
    }

    override fun toString(): String = args.joinToString(" ")
}
