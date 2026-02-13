package com.example.demo.controller

import com.example.demo.dto.FormatResponse
import com.example.demo.dto.VideoInfoResponse
import com.example.demo.service.VideoDownloadService
import com.example.demo.service.VideoExtractionService
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/video")
class VideoController(
    private val extractionService: VideoExtractionService,
    private val downloadService: VideoDownloadService
) {

    @GetMapping("/info")
    fun getVideoInfo(@RequestParam url: String): VideoInfoResponse {
        require(url.startsWith("http")) { "Invalid URL" }
        return extractionService.extractInfo(url)
    }

    /**
     * Download best available MP4 video
     */
    @GetMapping("/download")
    fun download(
        @RequestParam url: String
    ): ResponseEntity<InputStreamResource> {

        require(url.startsWith("http")) { "Invalid URL" }

        val file = downloadService.download(url)
        val resource = InputStreamResource(file.inputStream())

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"video.mp4\"")
            .contentLength(file.length())
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(resource)
            .also { file.deleteOnExit() }
    }

    /**
     * Download audio only (mp3 by default)
     */
    @GetMapping("/download/audio")
    fun downloadAudio(
        @RequestParam url: String,
        @RequestParam(required = false, defaultValue = "mp3") format: String
    ): ResponseEntity<InputStreamResource> {

        require(url.startsWith("http")) { "Invalid URL" }

        val file = downloadService.downloadAudio(url, format)
        val resource = InputStreamResource(file.inputStream())

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audio.$format\"")
            .contentLength(file.length())
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(resource)
            .also { file.deleteOnExit() }
    }

    /**
     * Get available formats
     */
    @GetMapping("/formats")
    fun getFormats(@RequestParam url: String): List<FormatResponse> {
        require(url.startsWith("http")) { "Invalid URL" }
        return extractionService.getFormats(url)
    }
}