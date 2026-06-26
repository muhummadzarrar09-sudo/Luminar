// app/src/main/java/com/luminar/reader/data/model/BookInsight.kt
package com.luminar.reader.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "book_insights",
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
data class BookInsight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val topic: String,
    val pageRefs: String,
    val summary: String,
    val createdAt: Long = System.currentTimeMillis()
)
