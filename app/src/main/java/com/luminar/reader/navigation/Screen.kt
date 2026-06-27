// app/src/main/java/com/luminar/reader/navigation/Screen.kt
package com.luminar.reader.navigation

sealed class Screen(val route: String) {
    data object Library : Screen("library")
    data object Settings : Screen("settings")
    data object Stats : Screen("stats")
    data object Search : Screen("search?bookId={bookId}") {
        fun createRoute(bookId: Long? = null) = if (bookId != null) "search?bookId=$bookId" else "search"
    }

    data object Reader : Screen("reader/{bookId}") {
        const val BOOK_ID_ARG = "bookId"

        fun createRoute(bookId: Long): String = "reader/$bookId"
    }

    data object EpubReader : Screen("epub_reader/{bookId}") {
        const val BOOK_ID_ARG = "bookId"

        fun createRoute(bookId: Long): String = "epub_reader/$bookId"
    }
}
