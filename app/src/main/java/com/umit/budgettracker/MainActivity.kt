package com.umit.budgettracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.umit.budgettracker.core.datastore.SettingsDataStore
import com.umit.budgettracker.core.domain.usecase.SyncDueSubscriptionExpensesUseCase
import com.umit.budgettracker.core.navigation.NavGraph
import com.umit.budgettracker.core.navigation.Screen
import com.umit.budgettracker.core.navigation.bottomNavItems
import com.umit.budgettracker.core.navigation.navigateToTopLevelDestination
import com.umit.budgettracker.core.reminder.PaymentReminderScheduler
import com.umit.budgettracker.core.security.AppLockManager
import com.umit.budgettracker.core.security.LockState
import com.umit.budgettracker.core.ui.theme.BudgetTrackerTheme
import com.umit.budgettracker.core.work.WorkScheduler
import com.umit.budgettracker.feature.lock.LockScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

// FragmentActivity (a ComponentActivity) is required by BiometricPrompt.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var syncDueSubscriptionExpenses: SyncDueSubscriptionExpensesUseCase
    @Inject lateinit var paymentReminderScheduler: PaymentReminderScheduler
    @Inject lateinit var lockManager: AppLockManager
    @Inject lateinit var settings: SettingsDataStore
    @Inject lateinit var workScheduler: WorkScheduler

    /** Route requested by a widget, shortcut or notification; consumed once by [MainScreen]. */
    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { lockManager.onColdStart() }
        lifecycleScope.launch {
            runCatching { syncDueSubscriptionExpenses() }
        }
        lifecycleScope.launch {
            settings.preferences.map { it.secureScreen }.distinctUntilChanged().collect { secure ->
                if (secure) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        paymentReminderScheduler.scheduleDailyReminder()
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 901)
        }
        if (savedInstanceState == null) handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            BudgetTrackerTheme {
                val lockState by lockManager.state.collectAsState()
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (lockState != LockState.CHECKING) {
                        MainScreen(pendingRoute = pendingRoute)
                    }
                    AnimatedVisibility(
                        visible = lockState == LockState.LOCKED,
                        enter = fadeIn(),
                        exit = fadeOut() + slideOutVertically { -it / 8 }
                    ) {
                        LockScreen(activity = this@MainActivity)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { lockManager.onForeground() }
    }

    override fun onStop() {
        super.onStop()
        lockManager.onBackground()
        // Anything entered this session should be on the home-screen widget.
        workScheduler.updateWidgets()
    }

    // Activity Result API launches go through here; leaving for a picker/camera must not re-lock.
    @Deprecated("Deprecated in Java")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        lockManager.onExternalActivityLaunched()
        @Suppress("DEPRECATION")
        super.startActivityForResult(intent, requestCode, options)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val route = when (intent.action) {
            ACTION_QUICK_ADD -> Screen.QuickAdd.create(intent.getLongExtra(EXTRA_CATEGORY_ID, -1L).takeIf { it > 0L })
            ACTION_SCAN_RECEIPT -> Screen.ReceiptScan.route
            else -> intent.getStringExtra(EXTRA_ROUTE)
        }
        if (route != null) pendingRoute.value = route
    }

    companion object {
        const val ACTION_QUICK_ADD = "com.umit.budgettracker.QUICK_ADD"
        const val ACTION_SCAN_RECEIPT = "com.umit.budgettracker.SCAN_RECEIPT"
        const val EXTRA_CATEGORY_ID = "category_id"
        const val EXTRA_ROUTE = "route"
    }
}

@Composable
fun MainScreen(pendingRoute: MutableStateFlow<String?>) {
    val navController = rememberNavController()
    val requestedRoute by pendingRoute.collectAsState()
    LaunchedEffect(requestedRoute) {
        val route = requestedRoute ?: return@LaunchedEffect
        pendingRoute.value = null
        navController.navigate(route) { launchSingleTop = true }
    }
    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            // Only show bottom bar for main screens
            val showBottomBar = bottomNavItems.any { it.route == currentDestination?.route }

            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            // One line even on 360dp screens ("Harcamalar" used to wrap).
                            label = { Text(screen.title, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigateToTopLevelDestination(screen)
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavGraph(
            navController = navController,
            // Consume what the outer scaffold already padded so screen top bars don't add the
            // status-bar inset a second time.
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
        )
    }
}
