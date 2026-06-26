// app/src/main/java/com/luminar/reader/data/local/db/AppDatabase.kt
package com.luminar.reader.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.luminar.reader.data.model.Book
import com.luminar.reader.data.model.BookInsight
import com.luminar.reader.data.model.ReadingProgress

@Database(
    entities = [
        Book::class,
        ReadingProgress::class,
        BookInsight::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reading_progress ADD COLUMN epubCfi TEXT DEFAULT NULL")
            }
        }
    }
}
