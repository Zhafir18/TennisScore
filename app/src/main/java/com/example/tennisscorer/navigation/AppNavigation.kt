package com.example.tennisscorer.navigation

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.tennisscorer.TennisScoreEngine
import com.example.tennisscorer.data.MatchRepository
import com.example.tennisscorer.ui.screens.CameraScreen
import com.example.tennisscorer.ui.screens.HistoryScreen
import com.example.tennisscorer.ui.screens.PlayerInputScreen
import com.example.tennisscorer.ui.screens.ReplayScreen
import com.example.tennisscorer.ui.screens.ScoreboardScreen
import com.example.tennisscorer.ui.screens.SplashScreen

@Composable
fun AppNavigation(engine: TennisScoreEngine, repository: MatchRepository) {
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        enterTransition    = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
        exitTransition     = { slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(300)) },
        popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(300)) },
        popExitTransition  = { slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300)) }
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onStart        = { navController.navigate(Screen.Input.route) },
                onHistory      = { navController.navigate(Screen.History.route) },
                onBallTracking = { navController.navigate(Screen.BallTracking.route) }
            )
        }
        composable(Screen.Input.route) {
            PlayerInputScreen(
                engine       = engine,
                onBack       = { navController.popBackStack() },
                onStartMatch = { navController.navigate(Screen.Game.route) }
            )
        }
        composable(Screen.Game.route) {
            ScoreboardScreen(
                engine      = engine,
                onBackToInput = { navController.popBackStack() }
            )
        }
        composable(Screen.History.route) {
            HistoryScreen(
                repository   = repository,
                onMatchClick = { matchId -> navController.navigate(Screen.Replay.buildRoute(matchId)) },
                onBack       = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.Replay.route,
            arguments = listOf(navArgument("matchId") { type = NavType.LongType })
        ) { backStackEntry ->
            val matchId = backStackEntry.arguments?.getLong("matchId") ?: return@composable
            ReplayScreen(
                matchId    = matchId,
                repository = repository,
                onBack     = { navController.popBackStack() }
            )
        }
        composable(Screen.BallTracking.route) {
            CameraScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun LockScreenOrientation(orientation: Int) {
    val context = LocalContext.current
    DisposableEffect(orientation) {
        val activity = context as? ComponentActivity
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = orientation
        onDispose {
            if (originalOrientation != null) {
                activity.requestedOrientation = originalOrientation
            }
        }
    }
}
