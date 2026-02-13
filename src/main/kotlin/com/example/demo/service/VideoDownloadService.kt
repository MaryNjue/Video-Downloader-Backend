package com.example.demo.service

import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

@Service
class VideoDownloadService {

    private val ytDlpPath = "yt-dlp"
    private val jsRuntime = "node"  // Changed from "/home/mery/.deno/bin/deno"
    private val timeoutMinutes = 15L

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

            println("✅ Node.js available: ${output.trim()}")
        } catch (e: IOException) {
            throw IllegalStateException("Node.js not found in PATH", e)
        }
    }

    fun download(url: String): File {
        println("📥 Downloading video: $url")

        val tempFile = File.createTempFile("video_", ".mp4")

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

        val tempFile = File.createTempFile("audio_", ".$format")

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

        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val reader = process.inputStream.bufferedReader()
            val output = StringBuilder()

            reader.useLines { lines ->
                lines.forEach { line ->
                    println("yt-dlp: $line")
                    output.appendLine(line)
                }
            }

            val finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES)

            if (!finished) {
                process.destroyForcibly()
                tempFile.delete()
                throw IllegalStateException("Download timed out after $timeoutMinutes minutes")
            }

            val exitCode = process.exitValue()

            if (exitCode != 0) {
                tempFile.delete()
                throw IllegalStateException("Download failed (exit $exitCode): ${output.toString()}")
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                throw IllegalStateException("Download completed but file is empty")
            }

            println("✅ $type downloaded: ${tempFile.length()} bytes")
            tempFile

        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }
}