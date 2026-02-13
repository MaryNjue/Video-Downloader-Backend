package com.example.demo.service

import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit

@Service
class VideoDownloadService {

    private val ytDlpPath = "yt-dlp"
    private val jsRuntime = "node"
    private val timeoutMinutes = 5L

    init {
        checkYtDlpAvailability()
    }

    private fun checkYtDlpAvailability() {
        try {
            val process = ProcessBuilder(ytDlpPath, "--version")
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(10, TimeUnit.SECONDS)

            println("✅ yt-dlp version: ${output.trim()}")
        } catch (e: IOException) {
            throw IllegalStateException("yt-dlp not found", e)
        }
    }

    fun download(url: String): File {
        println("📥 Downloading: $url")

        // For direct MP4 files, use simple HTTP download
        if (url.endsWith(".mp4") || url.endsWith(".webm")) {
            return downloadDirect(url)
        }

        // For YouTube and other sites, use yt-dlp
        return downloadWithYtDlp(url)
    }

    private fun downloadDirect(url: String): File {
        println("🌐 Direct download: $url")

        val tempFile = File.createTempFile("video_", ".mp4")

        try {
            val connection = URL(url).openConnection()
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connectTimeout = 30000
            connection.readTimeout = 120000

            connection.getInputStream().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var total: Long = 0

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        total += read
                        if (total % 1000000 == 0L) println("Downloaded: ${total / 1024}KB")
                    }
                }
            }

            println("✅ Downloaded: ${tempFile.length()} bytes")
            return tempFile

        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private fun downloadWithYtDlp(url: String): File {
        val tempFile = File.createTempFile("video_", ".mp4")
        if (tempFile.exists()) tempFile.delete()

        val useJsRuntime = url.contains("youtube") || url.contains("youtu.be")

        val command = mutableListOf(
            ytDlpPath,
            "--no-check-certificates",
            "--no-warnings",
            "-o", tempFile.absolutePath,
            "--force-overwrites",
            "--newline"
        )

        if (useJsRuntime) {
            command.add("--js-runtimes")
            command.add(jsRuntime)
            command.add("-f")
            command.add("best[ext=mp4]/best")
        }

        command.add(url)

        return executeDownload(command, tempFile)
    }

    private fun executeDownload(command: List<String>, tempFile: File): File {
        println("🚀 Command: ${command.joinToString(" ")}")

        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val reader = process.inputStream.bufferedReader()
            val output = StringBuilder()

            reader.useLines { lines ->
                lines.forEach { line ->
                    println("[yt-dlp] $line")
                    output.appendLine(line)
                }
            }

            val finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES)

            if (!finished) {
                process.destroyForcibly()
                tempFile.delete()
                throw IllegalStateException("Timeout after $timeoutMinutes minutes")
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                throw IllegalStateException("File empty or missing")
            }

            println("✅ Success: ${tempFile.length()} bytes")
            tempFile

        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }
}