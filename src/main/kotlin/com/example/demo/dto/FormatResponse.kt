package com.example.demo.dto

data class FormatResponse(
    val formatId: String,
    val ext: String,
    val resolution: String?,
    val filesize: Long?,
    val audioOnly: Boolean
)