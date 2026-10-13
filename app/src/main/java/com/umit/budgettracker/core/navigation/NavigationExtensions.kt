package com.umit.budgettracker.core.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

/**
 * Switches between bottom-bar destinations without nesting one top-level screen
 * inside another screen's saved back stack.
 */
fun NavController.navigateToTopLevelDestination(screen: Screen) {
    navigate(screen.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/** Returns to Dashboard even when the detail screen was opened from a restored/deep-linked stack. */
fun NavController.navigateBackToDashboard() {
    val returnedToDashboard = popBackStack(Screen.Dashboard.route, inclusive = false)
    if (!returnedToDashboard) {
        navigateToTopLevelDestination(Screen.Dashboard)
    }
}

/**
 * Back for screens that can be opened from several places (hub, dashboard, widget, notification):
 * pop when there is something to return to, otherwise land on Dashboard instead of leaving the app.
 */
fun NavController.popBackStackOrDashboard() {
    if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        navigateToTopLevelDestination(Screen.Dashboard)
    }
}
