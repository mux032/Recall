package com.recall.app.presentation.ui.navigation

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

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
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
        composable(Screen.Home.route) {
            HomeScreen(
                onSearchClick = { navController.navigate(Screen.Search.route) },
                onScreenshotClick = { screenshotId ->
                    navController.navigate(Screen.Detail.createRoute(screenshotId))
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onBackClick = { navController.popBackStack() },
                onScreenshotClick = { screenshotId ->
                    navController.navigate(Screen.Detail.createRoute(screenshotId))
                }
            )
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("screenshotId") { type = NavType.StringType })
        ) {
            DetailScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
