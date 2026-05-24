package com.umit.budgettracker.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Özet", Icons.Default.Dashboard)
    data object Expenses : Screen("expenses", "Harcamalar", Icons.AutoMirrored.Filled.ReceiptLong)
    data object Cards : Screen("cards", "Kartlar", Icons.Default.CreditCard)
    data object Reports : Screen("reports", "Raporlar", Icons.Default.BarChart)
    data object Settings : Screen("settings", "Ayarlar", Icons.Default.Settings)
    data object SalaryManagement : Screen("salary_management", "Maaş Yönetimi", Icons.Default.Payments)
    data object Installments : Screen("installments", "Taksitler", Icons.Default.Inventory)
    data object Subscriptions : Screen("subscriptions", "Abonelikler", Icons.Default.Sync)
    data object Loans : Screen("loans", "Krediler", Icons.Default.AccountBalance)
    data object CashFlow : Screen("cash_flow", "Nakit Akışı", Icons.Default.Event)
    data object CategoryBudgets : Screen("category_budgets", "Kategori Bütçeleri", Icons.Default.PieChart)
    data object ExpenseTemplates : Screen("expense_templates", "Hızlı Harcama Şablonları", Icons.Default.ContentPaste)
    data object DebtTracking : Screen("debt_tracking", "Borç / Alacak", Icons.Default.Handshake)
    data object NetWorth : Screen("net_worth", "Net Varlık", Icons.Default.AccountBalanceWallet)
    data object Categories : Screen("categories", "Kategoriler", Icons.Default.Category)
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Expenses,
    Screen.Cards,
    Screen.Reports,
    Screen.Settings
)
