// app/src/main/java/com/luminar/reader/data/repository/BookRepository.kt
package com.luminar.reader.data.repository

import android.net.Uri
import com.luminar.reader.data.epub.EpubChapter
import com.luminar.reader.data.model.Book
import com.luminar.reader.data.model.ReadingProgress
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<List<Book>>
    fun getBookById(id: Long): Flow<Book?>
    fun getAllProgress(): Flow<List<ReadingProgress>>
    fun getProgress(bookId: Long): Flow<ReadingProgress?>
    suspend fun importPdf(uri: Uri): Long
    suspend fun importFile(uri: Uri): Long
    suspend fun readTextContent(book: Book): String
    suspend fun readDocumentContent(book: Book): String
    suspend fun readEpubChapters(book: Book): List<EpubChapter>
    suspend fun getComicImagePaths(book: Book): List<String>
    fun getComicImageBytes(book: Book, entryPath: String): ByteArray
    suspend fun deleteBook(book: Book)
    suspend fun saveProgress(bookId: Long, currentPage: Int, scrollOffset: Float = 0f)
    suspend fun markBookOpened(bookId: Long)
}
