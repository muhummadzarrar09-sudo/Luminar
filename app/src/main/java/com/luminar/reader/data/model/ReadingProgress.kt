// app/src/main/java/com/luminar/reader/data/model/ReadingProgress.kt
package com.luminar.reader.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bookId"])
    ]
)
data class ReadingProgress(
    @PrimaryKey val bookId: Long,
    val currentPage: Int = 0,
    val scrollOffset: Float = 0f,
    val lastReadAt: Long = System.currentTimeMillis(),
    val epubCfi: String? = null,
    val lastZoomLevel: Float = 1.0f
)
