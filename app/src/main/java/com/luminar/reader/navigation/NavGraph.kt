// app/src/main/java/com/luminar/reader/navigation/NavGraph.kt
package com.luminar.reader.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
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
import com.luminar.reader.presentation.onboarding.OnboardingScreen
import com.luminar.reader.presentation.reader.ReaderScreen
import com.luminar.reader.presentation.settings.SettingsScreen

private const val NAV_DURATION = 350
private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private val EaseInCubic = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)

@Composable
fun LuminarNavGraph(
    hasSeenOnboarding: Boolean,
    onOnboardingComplete: () -> Unit,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = if (hasSeenOnboarding) Screen.Library.route else Screen.Onboarding.route
    ) {
        // ─── Onboarding ──────────────────────────────────
        composable(
            route = Screen.Onboarding.route,
            exitTransition = {
                fadeOut(animationSpec = tween(NAV_DURATION))
            }
        ) {
            OnboardingScreen(
                onComplete = {
                    onOnboardingComplete()
                    navController.navigate(Screen.Library.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // ─── Library ─────────────────────────────────────
        composable(
            route = Screen.Library.route,
            exitTransition = {
                when (targetState.destination.route) {
                    Screen.Reader.route -> slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(NAV_DURATION, easing = EaseInCubic)
                    )
                    else -> fadeOut(animationSpec = tween(NAV_DURATION))
                }
            },
            popEnterTransition = {
                when (initialState.destination.route) {
                    Screen.Reader.route -> slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(NAV_DURATION, easing = EaseOutCubic)
                    )
                    else -> fadeIn(animationSpec = tween(NAV_DURATION))
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

        // ─── Reader ──────────────────────────────────────
        composable(
            route = Screen.Reader.route,
            arguments = listOf(
                navArgument(Screen.Reader.BOOK_ID_ARG) { type = NavType.LongType }
            ),
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(NAV_DURATION, easing = EaseOutCubic)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(NAV_DURATION, easing = EaseInCubic)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(NAV_DURATION, easing = EaseOutCubic)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(NAV_DURATION, easing = EaseInCubic)
                )
            }
        ) {
            ReaderScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }

        // ─── Settings ────────────────────────────────────
        composable(
            route = Screen.Settings.route,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(NAV_DURATION, easing = EaseOutCubic)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(NAV_DURATION, easing = EaseInCubic)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(NAV_DURATION, easing = EaseOutCubic)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(NAV_DURATION, easing = EaseInCubic)
                )
            }
        ) {
            SettingsScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }
    }
}
