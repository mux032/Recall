package com.recall.app.presentation.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.recall.app.presentation.ui.detail.DetailScreen
import com.recall.app.presentation.ui.home.HomeScreen
import com.recall.app.presentation.ui.search.SearchScreen
import com.recall.app.presentation.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search?query={query}") {
        fun createRoute(query: String = "") = "search?query=${Uri.encode(query)}"
    }
    object Settings : Screen("settings")
    object Detail : Screen("detail/{screenshotId}") {
        fun createRoute(screenshotId: String) = "detail/$screenshotId"
    }
}

@Composable
fun RecallNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) { backStackEntry ->
            HomeScreen(
                onSearchClick = { query -> navController.navigate(Screen.Search.createRoute(query)) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onScreenshotClick = { screenshotId ->
                    navController.navigate(Screen.Detail.createRoute(screenshotId))
                },
                navBackStackEntry = backStackEntry
            )
        }

        composable(
            route = Screen.Search.route,
            arguments = listOf(navArgument("query") { defaultValue = "" })
        ) {
            SearchScreen(
                onBackClick = { navController.popBackStack() },
                onScreenshotClick = { screenshotId ->
                    navController.navigate(Screen.Detail.createRoute(screenshotId))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("screenshotId") { type = NavType.StringType })
        ) {
            DetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onScreenshotDeleted = {
                    // Signal HomeScreen to refresh its list before navigating back
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("screenshot_deleted", true)
                    navController.popBackStack()
                }
            )
        }
    }
}
