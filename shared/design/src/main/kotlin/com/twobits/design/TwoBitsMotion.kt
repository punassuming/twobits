package com.twobits.design

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * One shared push/pop slide for every app's nav graph — no per-destination fade overrides.
 */
object TwoBitsMotion {
    val pushEnter: EnterTransition = slideInHorizontally { it } + fadeIn()
    val pushExit: ExitTransition = slideOutHorizontally { -it / 3 } + fadeOut()
    val popEnter: EnterTransition = slideInHorizontally { -it / 3 } + fadeIn()
    val popExit: ExitTransition = slideOutHorizontally { it } + fadeOut()

    const val CONTENT_IN = 180
    const val CONTENT_OUT = 120
}
