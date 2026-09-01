package com.example.tennisscorer.navigation

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.tennisscorer.TennisScoreEngine
import com.example.tennisscorer.ui.screens.PlayerInputScreen
import com.example.tennisscorer.ui.screens.ScoreboardScreen
import com.example.tennisscorer.ui.screens.SplashScreen

@Composable
fun AppNavigation(engine: TennisScoreEngine) {
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        enterTransition = {
            slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))
        },
        exitTransition = {
            slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300))
        }
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(onStart = { navController.navigate(Screen.Input.route) })
        }
        composable(Screen.Input.route) {
            PlayerInputScreen(
                engine = engine,
                onBack = { navController.popBackStack() },
                onStartMatch = { navController.navigate(Screen.Game.route) }
            )
        }
        composable(Screen.Game.route) {
            ScoreboardScreen(
                engine = engine,
                onBackToInput = { navController.popBackStack() }
            )
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
