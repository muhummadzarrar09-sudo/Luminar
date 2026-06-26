// app/src/main/java/com/luminar/reader/domain/usecase/ImportBookUseCase.kt
package com.luminar.reader.domain.usecase

import android.content.Context
import android.net.Uri
import com.luminar.reader.data.repository.BookRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ImportBookUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke(uri: Uri): Long {
        val mimeType = context.contentResolver.getType(uri)
        val path = uri.path?.lowercase() ?: ""
        val isEpub = mimeType == "application/epub+zip" || path.endsWith(".epub")
        return if (isEpub) {
            bookRepository.importEpub(uri)
        } else {
            bookRepository.importPdf(uri)
        }
    }
}
