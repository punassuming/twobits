package dev.scrybe.android.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import dev.scrybe.android.navigation.ScrybeNavHost

@Composable
fun ScrybeApp() {
    val navController = rememberNavController()
    ScrybeNavHost(navController = navController)
}
