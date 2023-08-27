package com.bluesmods.bluecordpatcher

import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.net.ssl.HttpsURLConnection

object Downloader {
    private val LOG = LoggerFactory.getLogger(Downloader::class.java)

    /**
     * Downloads a file from the Internet.
     * TODO: log percent complete of download to stdout by increments of 10%
     *
     * @param url the URL to download from
     * @param output the output file for the contents of the URL.
     */
    @Throws(IOException::class)
    fun download(url: String, output: File, sha384Hash: String? = null) {
        if (output.exists()) output.delete()
        output.createNewFile()

        var retryNumber = 0

        while (true) {
            try {
                val conn = URL(url).openConnection() as HttpsURLConnection
                conn.connect()
                if (conn.responseCode == 200) {
                    conn.inputStream.use { Files.copy(it, output.toPath(), StandardCopyOption.REPLACE_EXISTING) }
                    break
                } else {
                    throw IOException("Unexpected response code ${conn.responseCode} for $url")
                }
            } catch (e: IOException) {
                if (++retryNumber == 3) throw e
                LOG.warn("failed to download $url, retrying ($retryNumber/3)")
            }
        }

        if (sha384Hash != null) {
            val computedHash = HashUtils.hashFile(output)
            if (sha384Hash != computedHash) {
                output.delete()
                throw IOException("File from $url has incorrect SHA-384 hash.\nExpected: $sha384Hash\nReceived: $computedHash")
            }
        }
    }
}