package com.luminar.reader.data.model

data class SearchResult(
    val bookId: Long,
    val bookTitle: String,
    val pageOrChapter: Int,
    val excerpt: String
)
