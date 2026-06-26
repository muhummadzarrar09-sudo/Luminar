// app/src/main/java/com/luminar/reader/domain/usecase/SaveProgressUseCase.kt
package com.luminar.reader.domain.usecase

import com.luminar.reader.data.repository.BookRepository
import javax.inject.Inject

class SaveProgressUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke(
        bookId: Long,
        currentPage: Int,
        scrollOffset: Float = 0f
    ) {
        bookRepository.saveProgress(
            bookId = bookId,
            currentPage = currentPage,
            scrollOffset = scrollOffset
        )
    }
}
