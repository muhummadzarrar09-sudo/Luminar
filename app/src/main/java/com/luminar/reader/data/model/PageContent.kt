package com.luminar.reader.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "page_content",
    indices = [Index("bookId")]
)
data class PageContent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val pageOrChapter: Int,
    val chunkIndex: Int,
    val content: String
)
