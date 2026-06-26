// app/src/main/java/com/luminar/reader/data/local/db/Converters.kt
package com.luminar.reader.data.local.db

import androidx.room.TypeConverter
import com.luminar.reader.data.model.BookFormat

class Converters {

    @TypeConverter
    fun fromBookFormat(format: BookFormat): String = format.name

    @TypeConverter
    fun toBookFormat(value: String): BookFormat {
        return runCatching { BookFormat.valueOf(value) }.getOrDefault(BookFormat.PDF)
    }
}
