// app/src/main/java/com/luminar/reader/domain/usecase/GetBooksUseCase.kt
package com.luminar.reader.domain.usecase

import com.luminar.reader.data.model.Book
import com.luminar.reader.data.repository.BookRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetBooksUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    operator fun invoke(): Flow<List<Book>> = bookRepository.getAllBooks()
}
