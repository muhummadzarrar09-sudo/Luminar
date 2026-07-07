// app/src/main/java/com/luminar/reader/data/local/db/AppDatabase.kt
package com.luminar.reader.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.luminar.reader.data.model.Book
import com.luminar.reader.data.model.Bookmark
import com.luminar.reader.data.model.BookInsight
import com.luminar.reader.data.model.ReadingProgress

@Database(
    entities = [
        Book::class,
        ReadingProgress::class,
        BookInsight::class,
        Bookmark::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
}
