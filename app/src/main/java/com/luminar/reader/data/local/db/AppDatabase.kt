// app/src/main/java/com/luminar/reader/data/local/db/AppDatabase.kt
package com.luminar.reader.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.luminar.reader.data.model.Book
import com.luminar.reader.data.model.BookInsight
import com.luminar.reader.data.model.BookToc
import com.luminar.reader.data.model.ReadingProgress

@Database(
    entities = [
        Book::class,
        ReadingProgress::class,
        BookInsight::class,
        BookToc::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookTocDao(): BookTocDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reading_progress ADD COLUMN epubCfi TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `book_toc` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bookId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `pageNumber` INTEGER,
                        `epubHref` TEXT,
                        `level` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_toc_bookId` ON `book_toc` (`bookId`)")
            }
        }
    }
}
