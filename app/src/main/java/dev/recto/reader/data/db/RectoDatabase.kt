package dev.recto.reader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BookEntity::class],
    version = 1,
    exportSchema = true
)
abstract class RectoDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var instance: RectoDatabase? = null

        fun get(context: Context): RectoDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RectoDatabase::class.java,
                    "recto.db"
                )
                    // No fallbackToDestructiveMigration. Every schema change
                    // gets a real migration; losing a user's library and
                    // reading positions is not an acceptable upgrade path.
                    .build()
                    .also { instance = it }
            }
    }
}
