package com.example.tennisscorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.tennisscorer.navigation.AppNavigation
import com.example.tennisscorer.ui.theme.TennisScorerTheme

class MainActivity : ComponentActivity() {
    private val engine: TennisScoreEngine by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TennisScorerTheme {
                AppNavigation(engine = engine)
            }
        }
    }
}
