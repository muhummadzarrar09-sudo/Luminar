// app/src/main/java/com/luminar/reader/domain/usecase/ImportBookUseCase.kt
package com.luminar.reader.domain.usecase

import android.net.Uri
import com.luminar.reader.data.repository.BookRepository
import javax.inject.Inject

class ImportBookUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke(uri: Uri): Long = bookRepository.importPdf(uri)
}
