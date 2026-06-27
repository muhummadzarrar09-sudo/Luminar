package com.luminar.reader.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.luminar.reader.data.epub.EpubBookLoader
import com.luminar.reader.data.local.db.BookDao
import com.luminar.reader.data.local.db.PageContentDao
import com.luminar.reader.data.model.BookFormat
import com.luminar.reader.data.model.PageContent
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File

@HiltWorker
class BookAnalysisWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val bookDao: BookDao,
    private val pageContentDao: PageContentDao,
    private val epubBookLoader: EpubBookLoader
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val bookId = inputData.getLong("bookId", -1L)
        if (bookId == -1L) return Result.failure()

        val book = bookDao.getBookById(bookId).first() ?: return Result.failure()
        val file = File(book.filePath)
        if (!file.exists()) return Result.failure()

        val pages = mutableListOf<PageContent>()

        if (book.format == BookFormat.EPUB) {
            val pub = epubBookLoader.openPublication(file) ?: return Result.failure()
            try {
                val spine = pub.readingOrder
                val total = spine.size.coerceAtLeast(1)
                spine.forEachIndexed { chapterIdx, link ->
                    val rawText = link.title ?: "Chapter ${chapterIdx + 1}"
                    val chunks = rawText.chunked(500)
                    chunks.forEachIndexed { cIdx, chunk ->
                        pages.add(PageContent(bookId = bookId, pageOrChapter = chapterIdx, chunkIndex = cIdx, content = chunk))
                    }
                    val progress = ((chapterIdx + 1) * 100) / total
                    bookDao.updateIndexingProgress(bookId, progress)
                }
            } finally {
                pub.close()
            }
        } else {
            val total = book.totalPages.coerceAtLeast(1)
            for (p in 0 until total) {
                val sampleText = "Page $p content for ${book.title}"
                pages.add(PageContent(bookId = bookId, pageOrChapter = p, chunkIndex = 0, content = sampleText))
                val progress = ((p + 1) * 100) / total
                bookDao.updateIndexingProgress(bookId, progress)
            }
        }

        if (pages.isNotEmpty()) {
            pageContentDao.insertAll(pages)
        }
        bookDao.updateIndexingProgress(bookId, 100)
        return Result.success()
    }
}
