package com.example.demo.service

import com.example.demo.dto.FormatResponse
import com.example.demo.dto.VideoInfoResponse
import org.springframework.stereotype.Service
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

@Service
class VideoExtractionService {

    private val mapper = jacksonObjectMapper()

    /**
     * Extract general video info and available formats (video + audio)
     */
    fun extractInfo(url: String): VideoInfoResponse {
        val process = ProcessBuilder(
            "yt-dlp",
            "--dump-json",
            "--no-warnings",
            "--quiet",
            url
        ).start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()

        process.waitFor(2, TimeUnit.MINUTES)

        if (stderr.isNotBlank()) {
            println("yt-dlp stderr: $stderr")
        }

        if (!stdout.trim().startsWith("{")) {
            throw IllegalStateException("yt-dlp did not return valid JSON")
        }

        val root = mapper.readTree(stdout)

        val formats = root["formats"]
            .filter { it["ext"] != null }
            .filter {
                val ext = it["ext"].asText()
                ext in listOf("mp4", "mkv", "webm", "m4a", "mp3")
            }
            .map {
                FormatResponse(
                    formatId = it["format_id"].asText(),
                    ext = it["ext"].asText(),
                    resolution = it["resolution"]?.asText() ?: "audio only",
                    filesize = it["filesize"]?.asLong(),
                    audioOnly = it["vcodec"]?.asText() == "none"
                )
            }

        return VideoInfoResponse(
            title = root["title"]?.asText() ?: "Unknown Title",
            duration = root["duration"]?.asInt() ?: 0,
            thumbnail = root["thumbnail"]?.asText(),
            formats = formats
        )
    }

    /**
     * List all available formats (simpler endpoint for frontend to select formatId)
     */
    fun getFormats(url: String): List<FormatResponse> {
        val videoInfo = extractInfo(url)
        return videoInfo.formats
    }
}