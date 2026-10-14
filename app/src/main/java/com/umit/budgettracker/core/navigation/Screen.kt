package com.umit.budgettracker.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Özet", Icons.Default.Dashboard)
    data object Expenses : Screen("expenses", "Harcamalar", Icons.AutoMirrored.Filled.ReceiptLong)
    data object Cards : Screen("cards", "Kartlar", Icons.Default.CreditCard)
    data object Reports : Screen("reports", "Raporlar", Icons.Default.BarChart)
    data object Settings : Screen("settings", "Menü", Icons.Default.GridView)
    data object SalaryManagement : Screen("salary_management", "Maaş Yönetimi", Icons.Default.Payments)
    data object Income : Screen("income", "Ek Gelirler", Icons.Default.Savings)
    data object FixedExpenses : Screen("fixed_expenses", "Sabit Giderler", Icons.Default.EventRepeat)
    data object Installments : Screen("installments", "Taksitler", Icons.Default.Inventory)
    data object Subscriptions : Screen("subscriptions", "Abonelikler", Icons.Default.Sync)
    data object Loans : Screen("loans", "Krediler", Icons.Default.AccountBalance)
    data object CashFlow : Screen("cash_flow", "Nakit Akışı", Icons.Default.Event)
    data object CategoryBudgets : Screen("category_budgets", "Kategori Bütçeleri", Icons.Default.PieChart)
    data object ExpenseTemplates : Screen("expense_templates", "Hızlı Harcama Şablonları", Icons.Default.ContentPaste)
    data object DebtTracking : Screen("debt_tracking", "Borç / Alacak", Icons.Default.Handshake)
    data object NetWorth : Screen("net_worth", "Net Varlık", Icons.Default.AccountBalanceWallet)
    data object Categories : Screen("categories", "Kategoriler", Icons.Default.Category)

    data object Assets : Screen("assets", "Varlıklarım", Icons.Default.Diamond)
    data object FundImport : Screen("fund_import", "Fon İçe Aktar", Icons.Default.DocumentScanner)
    data object Goals : Screen("savings_goals", "Birikim Hedefleri", Icons.Default.Flag)
    data object Inflation : Screen("inflation", "Enflasyon", Icons.AutoMirrored.Filled.TrendingUp)
    data object PlannedExpenses : Screen("planned_expenses", "Büyük Harcamalar", Icons.Default.EventNote)
    data object Simulation : Screen("simulation", "Ya Şöyle Olsaydı?", Icons.Default.Science)
    data object Insights : Screen("insights", "Öneriler ve Taslaklar", Icons.Default.AutoAwesome)
    data object StatementImport : Screen("statement_import", "Ekstre Karşılaştır", Icons.Default.CompareArrows)
    data object ReceiptScan : Screen("receipt_scan", "Fiş Tara", Icons.Default.DocumentScanner)
    data object YearEnd : Screen("year_end", "Yıl Sonu Raporu", Icons.Default.EmojiEvents)
    data object Preferences : Screen("preferences", "Tercihler", Icons.Default.Tune)

    /** Minimal amount-first entry; optional category preselected from a widget chip. */
    data object QuickAdd : Screen("quick_add?categoryId={categoryId}", "Hızlı Ekle", Icons.Default.AddCircle) {
        const val ARG_CATEGORY = "categoryId"
        fun create(categoryId: Long? = null) = if (categoryId == null) "quick_add" else "quick_add?categoryId=$categoryId"
    }
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Expenses,
    Screen.Cards,
    Screen.Reports,
    Screen.Settings
)
