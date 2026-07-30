package com.fearmikey.projectreporter.data.model

enum class ExportFormat {
    PDF, WORD
}

data class ExportOptions(
    val format: ExportFormat = ExportFormat.PDF,
    val photosPerPage: Int = 1, // 1, 2, or 4
    val includeNotes: Boolean = true,
    val includeTimestamp: Boolean = true
)
