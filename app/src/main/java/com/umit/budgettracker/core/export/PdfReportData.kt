package com.umit.budgettracker.core.export

import com.umit.budgettracker.core.domain.model.CategoryTrend
import com.umit.budgettracker.core.domain.model.MonthlyBudgetSummary

/**
 * Everything the PDF report renders. [history] is chronological and ends with [month];
 * [categoryTrends] covers the same span.
 */
data class PdfReportData(
    val month: MonthlyBudgetSummary,
    val history: List<MonthlyBudgetSummary>,
    val categoryTrends: List<CategoryTrend>
) {
    companion object {
        /** Selected month plus the eleven before it. */
        const val HISTORY_MONTH_COUNT = 12
    }
}
