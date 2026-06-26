// app/src/main/java/com/luminar/reader/presentation/reader/ReaderInputController.kt
package com.luminar.reader.presentation.reader

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class ReaderCommand {
    PreviousPage,
    NextPage
}

@Singleton
class ReaderInputController @Inject constructor() {

    private val _commands = MutableSharedFlow<ReaderCommand>(
        extraBufferCapacity = 8
    )

    val commands = _commands.asSharedFlow()

    @Volatile
    var isReaderOpen: Boolean = false

    fun previousPage() {
        _commands.tryEmit(ReaderCommand.PreviousPage)
    }

    fun nextPage() {
        _commands.tryEmit(ReaderCommand.NextPage)
    }
}
