package com.luminar.reader.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "highlights",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class Highlight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val format: BookFormat,
    val pdfPage: Int?,
    val rectLeft: Float?,
    val rectTop: Float?,
    val rectRight: Float?,
    val rectBottom: Float?,
    val epubCfi: String?,
    val epubSelectedText: String?,
    val color: Int,
    val noteText: String?,
    val createdAt: Long = System.currentTimeMillis()
)
