package com.example.tennisscorer.navigation

sealed class Screen(val route: String) {
    object Splash      : Screen("splash")
    object Input       : Screen("input")
    object Game        : Screen("game")
    object History     : Screen("history")
    object Replay      : Screen("replay/{matchId}") {
        fun buildRoute(matchId: Long) = "replay/$matchId"
    }
    object BallTracking : Screen("ball_tracking")
}
