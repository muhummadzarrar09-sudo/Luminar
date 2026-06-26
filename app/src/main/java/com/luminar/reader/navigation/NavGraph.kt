// app/src/main/java/com/luminar/reader/navigation/NavGraph.kt
package com.luminar.reader.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.luminar.reader.presentation.library.LibraryScreen
import com.luminar.reader.presentation.reader.ReaderScreen
import com.luminar.reader.presentation.settings.SettingsScreen

private const val NAV_ANIMATION_DURATION_MILLIS = 300

@Composable
fun LuminarNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Library.route
    ) {
        composable(
            route = Screen.Library.route,
            exitTransition = {
                when (targetState.destination.route) {
                    Screen.Reader.route -> slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(
                            durationMillis = NAV_ANIMATION_DURATION_MILLIS,
                            easing = FastOutSlowInEasing
                        )
                    )

                    else -> fadeOut(
                        animationSpec = tween(
                            durationMillis = NAV_ANIMATION_DURATION_MILLIS,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            },
            popEnterTransition = {
                when (initialState.destination.route) {
                    Screen.Reader.route -> slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(
                            durationMillis = NAV_ANIMATION_DURATION_MILLIS,
                            easing = FastOutSlowInEasing
                        )
                    )

                    else -> fadeIn(
                        animationSpec = tween(
                            durationMillis = NAV_ANIMATION_DURATION_MILLIS,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            }
        ) {
            LibraryScreen(
                onOpenBook = { bookId ->
                    navController.navigate(Screen.Reader.createRoute(bookId)) {
                        launchSingleTop = true
                    }
                },
                onOpenSettings = {
                    navController.navigate(Screen.Settings.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Screen.Reader.route,
            arguments = listOf(
                navArgument(Screen.Reader.BOOK_ID_ARG) {
                    type = NavType.LongType
                }
            ),
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(
                        durationMillis = NAV_ANIMATION_DURATION_MILLIS,
                        easing = FastOutSlowInEasing
                    )
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(
                        durationMillis = NAV_ANIMATION_DURATION_MILLIS,
                        easing = FastOutSlowInEasing
                    )
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(
                        durationMillis = NAV_ANIMATION_DURATION_MILLIS,
                        easing = FastOutSlowInEasing
                    )
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(
                        durationMillis = NAV_ANIMATION_DURATION_MILLIS,
                        easing = FastOutSlowInEasing
                    )
                )
            }
        ) {
            ReaderScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }

        composable(
            route = Screen.Settings.route,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(
                        durationMillis = NAV_ANIMATION_DURATION_MILLIS,
                        easing = FastOutSlowInEasing
                    )
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(
                        durationMillis = NAV_ANIMATION_DURATION_MILLIS,
                        easing = FastOutSlowInEasing
                    )
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(
                        durationMillis = NAV_ANIMATION_DURATION_MILLIS,
                        easing = FastOutSlowInEasing
                    )
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(
                        durationMillis = NAV_ANIMATION_DURATION_MILLIS,
                        easing = FastOutSlowInEasing
                    )
                )
            }
        ) {
            SettingsScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }
    }
}
