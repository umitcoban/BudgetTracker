package com.umit.budgettracker.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.umit.budgettracker.feature.cards.CardsScreen
import com.umit.budgettracker.feature.dashboard.DashboardScreen
import com.umit.budgettracker.feature.expense.ExpenseScreen
import com.umit.budgettracker.feature.fixedexpenses.FixedExpensesScreen
import com.umit.budgettracker.feature.reports.ReportsScreen
import com.umit.budgettracker.feature.salary.SalaryScreen
import com.umit.budgettracker.feature.settings.SettingsScreen
import com.umit.budgettracker.feature.installments.InstallmentsScreen
import com.umit.budgettracker.feature.income.IncomeScreen
import com.umit.budgettracker.feature.subscriptions.SubscriptionsScreen
import com.umit.budgettracker.feature.loans.LoansScreen
import com.umit.budgettracker.feature.cashflow.CashFlowScreen
import com.umit.budgettracker.feature.budgets.CategoryBudgetsScreen
import com.umit.budgettracker.feature.templates.ExpenseTemplatesScreen
import com.umit.budgettracker.feature.debt.DebtScreen
import com.umit.budgettracker.feature.networth.NetWorthScreen
import com.umit.budgettracker.feature.settings.CategoriesScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.umit.budgettracker.feature.assets.AssetsScreen
import com.umit.budgettracker.feature.assets.FundImportScreen
import com.umit.budgettracker.feature.goals.GoalsScreen
import com.umit.budgettracker.feature.inflation.InflationScreen
import com.umit.budgettracker.feature.insights.InsightsScreen
import com.umit.budgettracker.feature.planner.PlannedExpensesScreen
import com.umit.budgettracker.feature.preferences.PreferencesScreen
import com.umit.budgettracker.feature.quickadd.QuickAddScreen
import com.umit.budgettracker.feature.receipt.ReceiptScanScreen
import com.umit.budgettracker.feature.simulation.SimulationScreen
import com.umit.budgettracker.feature.statement.StatementImportScreen
import com.umit.budgettracker.feature.yearend.YearEndScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(navController = navController)
        }
        composable(Screen.Expenses.route) {
            ExpenseScreen(navController = navController)
        }
        composable(Screen.Cards.route) {
            CardsScreen(navController = navController)
        }
        composable(Screen.Reports.route) {
            ReportsScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onNavigate = { navController.navigate(it) })
        }
        composable(Screen.SalaryManagement.route) {
            SalaryScreen(onBack = { navController.popBackStackOrDashboard() })
        }
        composable(Screen.Income.route) {
            IncomeScreen(onBack = { navController.popBackStackOrDashboard() })
        }
        composable(Screen.FixedExpenses.route) {
            FixedExpensesScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Installments.route) {
            InstallmentsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Subscriptions.route) {
            SubscriptionsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Loans.route) {
            LoansScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.CashFlow.route) {
            CashFlowScreen(
                onBack = { navController.popBackStackOrDashboard() },
                onOpenAssets = { navController.navigate(Screen.Assets.route) }
            )
        }
        composable(Screen.CategoryBudgets.route) {
            CategoryBudgetsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.ExpenseTemplates.route) {
            ExpenseTemplatesScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.DebtTracking.route) {
            DebtScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.NetWorth.route) {
            NetWorthScreen(
                onBack = { navController.popBackStackOrDashboard() },
                onOpenAssets = { navController.navigate(Screen.Assets.route) }
            )
        }
        composable(Screen.Categories.route) {
            CategoriesScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Assets.route) {
            AssetsScreen(
                onBack = { navController.popBackStackOrDashboard() },
                onOpenFundImport = { navController.navigate(Screen.FundImport.route) }
            )
        }
        composable(Screen.FundImport.route) {
            FundImportScreen(onBack = { navController.popBackStackOrDashboard() })
        }
        composable(Screen.Goals.route) {
            GoalsScreen(
                onBack = { navController.popBackStackOrDashboard() },
                onOpenAssets = { navController.navigate(Screen.Assets.route) }
            )
        }
        composable(Screen.Inflation.route) {
            InflationScreen(onBack = { navController.popBackStackOrDashboard() })
        }
        composable(Screen.PlannedExpenses.route) {
            PlannedExpensesScreen(onBack = { navController.popBackStackOrDashboard() })
        }
        composable(Screen.Simulation.route) {
            SimulationScreen(onBack = { navController.popBackStackOrDashboard() })
        }
        composable(Screen.Insights.route) {
            InsightsScreen(onBack = { navController.popBackStackOrDashboard() })
        }
        composable(Screen.StatementImport.route) {
            StatementImportScreen(onBack = { navController.popBackStackOrDashboard() })
        }
        composable(Screen.ReceiptScan.route) {
            ReceiptScanScreen(onBack = { navController.popBackStackOrDashboard() })
        }
        composable(Screen.YearEnd.route) {
            YearEndScreen(onBack = { navController.popBackStackOrDashboard() })
        }
        composable(Screen.Preferences.route) {
            PreferencesScreen(onBack = { navController.popBackStackOrDashboard() })
        }
        composable(
            route = Screen.QuickAdd.route,
            arguments = listOf(
                navArgument(Screen.QuickAdd.ARG_CATEGORY) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            QuickAddScreen(onBack = { navController.popBackStackOrDashboard() })
        }
    }
}
