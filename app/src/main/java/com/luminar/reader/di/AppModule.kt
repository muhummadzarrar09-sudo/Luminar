// app/src/main/java/com/luminar/reader/di/AppModule.kt
package com.luminar.reader.di

import android.content.Context
import androidx.room.Room
import com.luminar.reader.data.local.db.AppDatabase
import com.luminar.reader.data.local.db.BookDao
import com.luminar.reader.data.repository.BookRepository
import com.luminar.reader.data.repository.BookRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import com.luminar.reader.data.local.db.BookmarkDao
import com.luminar.reader.data.local.db.BookTocDao
import com.luminar.reader.data.local.db.DictionaryDao
import com.luminar.reader.data.local.db.HighlightDao
import com.luminar.reader.data.local.db.PageContentDao
import com.luminar.reader.data.local.db.ReadingSessionDao
import com.luminar.reader.network.DictionaryApiService
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "luminar_reader.db"
        )
        .addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9
        )
        .build()
    }

    @Provides
    @Singleton
    fun provideBookDao(database: AppDatabase): BookDao = database.bookDao()

    @Provides
    @Singleton
    fun provideBookTocDao(database: AppDatabase): BookTocDao = database.bookTocDao()

    @Provides
    @Singleton
    fun providePageContentDao(database: AppDatabase): PageContentDao = database.pageContentDao()

    @Provides
    @Singleton
    fun provideReadingSessionDao(database: AppDatabase): ReadingSessionDao = database.readingSessionDao()

    @Provides
    @Singleton
    fun provideHighlightDao(database: AppDatabase): HighlightDao = database.highlightDao()

    @Provides
    @Singleton
    fun provideBookmarkDao(database: AppDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    @Singleton
    fun provideDictionaryDao(database: AppDatabase): DictionaryDao = database.dictionaryDao()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideDictionaryApiService(): DictionaryApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.dictionaryapi.dev/")
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(DictionaryApiService::class.java)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBookRepository(
        implementation: BookRepositoryImpl
    ): BookRepository
}
