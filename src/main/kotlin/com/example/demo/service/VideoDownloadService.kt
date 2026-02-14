package com.example.demo.service

import com.example.demo.dto.FormatResponse
import com.example.demo.dto.VideoInfoResponse
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit

@Service
class VideoDownloadService {

    private val ytDlpPath = "yt-dlp"
    private val jsRuntime = "node"
    private val timeoutMinutes = 5L
    private val objectMapper = ObjectMapper()

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

    // Check if URL needs JavaScript runtime (YouTube)
    private fun needsJsRuntime(url: String): Boolean {
        return url.contains("youtube.com") ||
                url.contains("youtu.be") ||
                url.contains("youtube-nocookie.com")
    }

    fun extractInfo(url: String): VideoInfoResponse {
        println("🔍 Extracting info: $url")

        val useJsRuntime = needsJsRuntime(url)

        val command = mutableListOf(
            ytDlpPath,
            "--dump-json",
            "--no-warnings",
            "--quiet"
        )

        if (useJsRuntime) {
            command.add("--js-runtimes")
            command.add(jsRuntime)
        }

        command.add(url)

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(2, TimeUnit.MINUTES)

        println("yt-dlp output length: ${output.length}")

        if (!output.trim().startsWith("{")) {
            println("ERROR: Invalid JSON response")
            throw IllegalStateException("Invalid response from yt-dlp")
        }

        val root = objectMapper.readTree(output)

        // FIX: Get ALL formats without strict filtering
        val formats = root["formats"]
            ?.filter { it["ext"] != null }
            ?.filter {
                // Skip storyboards (sb0, sb1, sb2, sb3) - they're just thumbnails
                val formatId = it["format_id"]?.asText() ?: ""
                !formatId.startsWith("sb")
            }
            ?.map {
                val formatId = it["format_id"]?.asText() ?: "unknown"
                val ext = it["ext"]?.asText() ?: "mp4"
                val resolution = it["resolution"]?.asText()
                val vcodec = it["vcodec"]?.asText() ?: ""
                val acodec = it["acodec"]?.asText()

                // Audio if no video codec OR has audio bitrate
                val isAudioOnly = vcodec == "none" || acodec != null

                FormatResponse(
                    formatId = formatId,
                    ext = ext,
                    resolution = if (isAudioOnly) "audio only" else (resolution ?: "unknown"),
                    filesize = it["filesize"]?.asLong(),
                    audioOnly = isAudioOnly
                )
            }
            ?.filter { it.resolution != "unknown" }  // Remove unknown resolutions
            ?.sortedWith(
                compareBy(
                    { it.audioOnly },  // false (video) comes before true (audio)
                    {
                        // Sort by resolution quality
                        when (it.resolution) {
                            "144p", "256x144" -> 1
                            "240p", "426x240" -> 2
                            "360p", "640x360" -> 3
                            "480p", "854x480" -> 4
                            "720p", "1280x720" -> 5
                            "1080p", "1920x1080" -> 6
                            "1440p", "2560x1440" -> 7
                            "2160p", "3840x2160" -> 8
                            else -> if (it.audioOnly) 0 else 9
                        }
                    }
                )
            )
            ?.take(8)  // Take top 8 (mix of video + audio)
            ?: emptyList()

        println("✅ Found ${formats.size} formats")
        formats.forEach { println("  - ${it.formatId}: ${it.resolution} (${it.ext})") }

        return VideoInfoResponse(
            title = root["title"]?.asText() ?: "Unknown Title",
            duration = root["duration"]?.asInt() ?: 0,
            thumbnail = root["thumbnail"]?.asText(),
            formats = formats
        )
    }

    fun getFormats(url: String): List<FormatResponse> {
        return extractInfo(url).formats
    }

    fun download(url: String): File {
        println("📥 Downloading video: $url")

        if (url.endsWith(".mp4") || url.endsWith(".webm") || url.endsWith(".mkv")) {
            return downloadDirect(url, "mp4")
        }

        return downloadWithYtDlp(url, "mp4")
    }

    fun downloadAudio(url: String, format: String = "mp3"): File {
        println("🎵 Downloading audio: $url")

        val tempFile = File.createTempFile("audio_", ".$format")
        if (tempFile.exists()) tempFile.delete()

        val useJsRuntime = needsJsRuntime(url)

        val command = mutableListOf(
            ytDlpPath,
            "--no-check-certificates",
            "--no-warnings",
            "-x",
            "--audio-format", format,
            "--audio-quality", "0",
            "-o", tempFile.absolutePath,
            "--force-overwrites",
            "--newline"
        )

        if (useJsRuntime) {
            command.add("--js-runtimes")
            command.add(jsRuntime)
        }

        command.add(url)

        return executeDownload(command, tempFile)
    }

    private fun downloadDirect(url: String, ext: String): File {
        println("🌐 Direct download: $url")

        val tempFile = File.createTempFile("video_", ".$ext")

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

    private fun downloadWithYtDlp(url: String, ext: String): File {
        val tempFile = File.createTempFile("video_", ".$ext")
        if (tempFile.exists()) tempFile.delete()

        val useJsRuntime = needsJsRuntime(url)

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

            reader.useLines { lines ->
                lines.forEach { line ->
                    println("[yt-dlp] $line")
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