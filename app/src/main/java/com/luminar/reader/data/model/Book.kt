// app/src/main/java/com/luminar/reader/data/model/Book.kt
package com.luminar.reader.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["filePath"], unique = true)
    ]
)
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val filePath: String,
    val coverPath: String?,
    val format: BookFormat,
    val totalPages: Int,
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long? = null,
    val isAnalyzed: Boolean = false
)
