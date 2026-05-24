package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.LoanMonthlyPayment
import com.umit.budgettracker.core.domain.repository.LoanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class LoanMonthlyCalculator @Inject constructor(
    private val loanRepository: LoanRepository
) {
    fun getPaymentsForMonth(yearMonth: YearMonth): Flow<List<LoanMonthlyPayment>> {
        return loanRepository.observeActiveLoans().map { loans ->
            loans.mapNotNull { loan ->
                val start = loan.startMonth
                val end = start.plusMonths((loan.installmentCount - 1).toLong())
                
                if (yearMonth.isBefore(start) || yearMonth.isAfter(end)) {
                    null
                } else {
                    val monthsDiff = ChronoUnit.MONTHS.between(start, yearMonth).toInt()
                    val currentInstallment = monthsDiff + 1
                    val remainingInstallments = loan.installmentCount - currentInstallment
                    
                    LoanMonthlyPayment(
                        loanId = loan.id,
                        title = loan.title,
                        amount = loan.monthlyPaymentAmount,
                        paymentDay = loan.paymentDay,
                        currentInstallment = currentInstallment,
                        totalInstallments = loan.installmentCount,
                        remainingAmount = remainingInstallments * loan.monthlyPaymentAmount,
                        categoryId = loan.categoryId,
                        paymentAccountId = loan.paymentAccountId,
                        category = loan.category,
                        account = loan.account
                    )
                }
            }
        }
    }
}
