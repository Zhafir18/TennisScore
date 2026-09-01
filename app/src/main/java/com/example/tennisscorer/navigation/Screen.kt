package com.example.tennisscorer.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Input  : Screen("input")
    object Game   : Screen("game")
}
