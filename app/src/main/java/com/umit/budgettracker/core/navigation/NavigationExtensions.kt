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
