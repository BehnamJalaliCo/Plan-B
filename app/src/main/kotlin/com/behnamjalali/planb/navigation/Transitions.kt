package com.behnamjalali.planb.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn

/** Calm cross-fade with a hint of depth; disabled entirely when motion is off. */
internal fun navEnter(enabled: Boolean): EnterTransition =
    if (enabled) fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.985f) else EnterTransition.None

internal fun navExit(enabled: Boolean): ExitTransition =
    if (enabled) fadeOut(tween(160)) else ExitTransition.None
