package com.example.demo.dto

data class VideoInfoResponse(
    val title: String,
    val duration: Int,
    val thumbnail: String?,
    val formats: List<FormatResponse>
)