package com.example.demo.controller

import com.example.demo.dto.FormatResponse
import com.example.demo.dto.VideoInfoResponse
import com.example.demo.service.VideoDownloadService
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.FileInputStream

@RestController
@RequestMapping("/api/video")
class VideoController(
    private val downloadService: VideoDownloadService
) {

    @GetMapping("/info")
    fun getVideoInfo(@RequestParam url: String): VideoInfoResponse {
        require(url.startsWith("http")) { "Invalid URL" }
        return downloadService.extractInfo(url)
    }

    @GetMapping("/download")
    fun download(@RequestParam url: String): ResponseEntity<InputStreamResource> {
        require(url.startsWith("http")) { "Invalid URL" }

        val file = downloadService.download(url)
        val resource = InputStreamResource(FileInputStream(file))

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${file.name}\"")
            .contentLength(file.length())
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(resource)
    }

    @GetMapping("/download/audio")
    fun downloadAudio(
        @RequestParam url: String,
        @RequestParam(required = false, defaultValue = "mp3") format: String
    ): ResponseEntity<InputStreamResource> {
        require(url.startsWith("http")) { "Invalid URL" }

        val file = downloadService.downloadAudio(url, format)
        val resource = InputStreamResource(FileInputStream(file))

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${file.name}\"")
            .contentLength(file.length())
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(resource)
    }

    @GetMapping("/formats")
    fun getFormats(@RequestParam url: String): List<FormatResponse> {
        require(url.startsWith("http")) { "Invalid URL" }
        return downloadService.getFormats(url)
    }
}