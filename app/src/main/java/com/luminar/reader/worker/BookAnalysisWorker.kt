// app/src/main/java/com/luminar/reader/worker/BookAnalysisWorker.kt
package com.luminar.reader.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.luminar.reader.data.repository.BookRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint

@Suppress("unused")
@HiltWorker
class BookAnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val bookRepository: BookRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // Phase 3: extract text, call Ollama, store insights
        return Result.success()
    }
}
