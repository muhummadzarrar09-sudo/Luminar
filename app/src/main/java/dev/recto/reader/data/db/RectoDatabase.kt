package dev.recto.reader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        CollectionEntity::class,
        BookCollectionCrossRef::class
    ],
    version = 4,
    exportSchema = true
)
abstract class RectoDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    abstract fun collectionDao(): CollectionDao

    companion object {

        /**
         * v1 -> v2: books can now be stored as our own copy rather than only
         * referenced by SAF URI, because share-sheet and "Open with" URIs
         * grant transient permission that dies with the activity.
         *
         * A real migration, not fallbackToDestructiveMigration. There are
         * already books and reading positions in v1 on at least one phone,
         * and wiping someone's library to save ten lines of SQL is exactly
         * the upgrade path this project promised not to take.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN localPath TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN contentHash TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_books_contentHash ON books(contentHash)")
            }
        }

        /**
         * v2 -> v3: title/author matching columns, so the same book arriving
         * as a different file is recognised instead of imported twice.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN matchTitle TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN matchAuthor TEXT")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_books_matchTitle_matchAuthor " +
                        "ON books(matchTitle, matchAuthor)"
                )
                // Backfill existing rows so books added before this version
                // still take part in duplicate detection.
                db.execSQL(
                    "UPDATE books SET matchTitle = " +
                        "TRIM(LOWER(REPLACE(REPLACE(REPLACE(title, '.', ' '), ',', ' '), '!', ' ')))"
                )
                db.execSQL(
                    "UPDATE books SET matchAuthor = " +
                        "TRIM(LOWER(REPLACE(REPLACE(REPLACE(author, '.', ' '), ',', ' '), '!', ' ')))" +
                        " WHERE author IS NOT NULL"
                )
            }
        }

        /**
         * v3 -> v4: collections (Kindle-style shelves).
         *
         * The join table cascades on delete for both sides, so removing a book
         * or a shelf cannot leave orphan membership rows. Column order and
         * index names must match exactly what Room generates for the entities,
         * or validation fails at open time.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `collections` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_collections_name` " +
                        "ON `collections` (`name`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `book_collections` (
                        `bookId` INTEGER NOT NULL,
                        `collectionId` INTEGER NOT NULL,
                        PRIMARY KEY(`bookId`, `collectionId`),
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE ,
                        FOREIGN KEY(`collectionId`) REFERENCES `collections`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_book_collections_collectionId` " +
                        "ON `book_collections` (`collectionId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_book_collections_bookId` " +
                        "ON `book_collections` (`bookId`)"
                )
            }
        }

        @Volatile
        private var instance: RectoDatabase? = null

        fun get(context: Context): RectoDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RectoDatabase::class.java,
                    "recto.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    // Be explicit: ON DELETE CASCADE is a no-op unless the
                    // SQLite connection has foreign keys switched on.
                    .setForeignKeyConstraintsEnabled(true)
                    .build()
                    .also { instance = it }
            }
    }
}
