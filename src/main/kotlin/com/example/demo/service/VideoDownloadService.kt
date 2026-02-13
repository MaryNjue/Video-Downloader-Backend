package com.example.demo.service

import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

@Service
class VideoDownloadService {

    private val ytDlpPath = "yt-dlp"
    private val jsRuntime = "/home/mery/.deno/bin/deno"
    private val timeoutMinutes = 10L

    init {
        checkYtDlpAvailability()
        checkJsRuntimeAvailability()
    }

    private fun checkYtDlpAvailability() {
        try {
            val process = ProcessBuilder(ytDlpPath, "--version")
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor(10, TimeUnit.SECONDS)

            if (!exitCode || process.exitValue() != 0) {
                throw IllegalStateException("yt-dlp not found")
            }

            println("✅ yt-dlp version: ${output.trim()}")
        } catch (e: IOException) {
            throw IllegalStateException("yt-dlp not found in PATH", e)
        }
    }

    private fun checkJsRuntimeAvailability() {
        try {
            val process = ProcessBuilder(jsRuntime, "--version")
                .redirectErrorStream(true)
                .start()

            val finished = process.waitFor(10, TimeUnit.SECONDS)
            val output = process.inputStream.bufferedReader().readText()

            if (!finished || process.exitValue() != 0) {
                throw IllegalStateException("$jsRuntime not found")
            }

            println("✅ Deno available: ${output.trim()}")
        } catch (e: IOException) {
            throw IllegalStateException("Deno not found at $jsRuntime", e)
        }
    }

    fun download(url: String): File {
        println("📥 Downloading video: $url")

        // Create temp file in current directory for debugging
        val tempFile = File(System.getProperty("java.io.tmpdir"), "video_${System.currentTimeMillis()}.mp4")

        val command = listOf(
            ytDlpPath,
            "--js-runtimes", jsRuntime,
            "--no-check-certificates",
            "--no-warnings",
            "--no-playlist",
            "-f", "best[ext=mp4]/best",
            "-o", tempFile.absolutePath,
            "--no-overwrites",
            "--newline",
            url
        )

        return executeDownload(command, tempFile, "video")
    }

    fun downloadAudio(url: String, format: String = "mp3"): File {
        println("🎵 Downloading audio: $url")

        val tempFile = File(System.getProperty("java.io.tmpdir"), "audio_${System.currentTimeMillis()}.$format")

        val command = listOf(
            ytDlpPath,
            "--js-runtimes", jsRuntime,
            "--no-check-certificates",
            "--no-warnings",
            "--no-playlist",
            "-x",
            "--audio-format", format,
            "--audio-quality", "0",
            "-o", tempFile.absolutePath,
            "--no-overwrites",
            "--newline",
            url
        )

        return executeDownload(command, tempFile, "audio")
    }

    private fun executeDownload(command: List<String>, tempFile: File, type: String): File {
        println("🚀 Command: ${command.joinToString(" ")}")
        println("📁 Output file: ${tempFile.absolutePath}")

        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val reader = process.inputStream.bufferedReader()
            val output = StringBuilder()
            var lastLine = ""

            reader.useLines { lines ->
                lines.forEach { line ->
                    println("yt-dlp: $line")
                    output.appendLine(line)
                    lastLine = line
                }
            }

            val finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES)

            if (!finished) {
                process.destroyForcibly()
                tempFile.delete()
                throw IllegalStateException("Download timed out after $timeoutMinutes minutes")
            }

            val exitCode = process.exitValue()

            // List files in temp directory for debugging
            val tempDir = File(System.getProperty("java.io.tmpdir"))
            val files = tempDir.listFiles { f -> f.name.startsWith(type) && f.name.endsWith(".mp4") || f.name.endsWith(".mp3") }
            println("📂 Files in temp dir: ${files?.joinToString { it.name + "(" + it.length() + "b)" } ?: "none"}")

            if (exitCode != 0) {
                tempFile.delete()
                throw IllegalStateException("Download failed (exit $exitCode). Last line: $lastLine. Full output: ${output.toString()}")
            }

            if (!tempFile.exists()) {
                throw IllegalStateException("File not found at ${tempFile.absolutePath}. Files in temp: ${files?.joinToString { it.name } ?: "none"}")
            }

            if (tempFile.length() == 0L) {
                tempFile.delete()
                throw IllegalStateException("Download completed but file is empty. Last output: $lastLine")
            }

            println("✅ $type downloaded: ${tempFile.length()} bytes to ${tempFile.absolutePath}")
            tempFile

        } catch (e: Exception) {
            println("❌ Exception during download: ${e.message}")
            e.printStackTrace()
            tempFile.delete()
            throw e
        }
    }
}