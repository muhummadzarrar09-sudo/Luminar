// app/src/main/java/com/luminar/reader/navigation/Screen.kt
package com.luminar.reader.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Library : Screen("library")
    data object Settings : Screen("settings")

    data object Reader : Screen("reader/{bookId}") {
        const val BOOK_ID_ARG = "bookId"

        fun createRoute(bookId: Long): String = "reader/$bookId"
    }
}
