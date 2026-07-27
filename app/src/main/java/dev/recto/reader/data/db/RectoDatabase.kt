package dev.recto.reader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BookEntity::class],
    version = 2,
    exportSchema = true
)
abstract class RectoDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

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

        @Volatile
        private var instance: RectoDatabase? = null

        fun get(context: Context): RectoDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RectoDatabase::class.java,
                    "recto.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
