package com.umit.budgettracker.core.di

import com.umit.budgettracker.core.database.repository.*
import com.umit.budgettracker.core.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSalaryRepository(impl: SalaryRepositoryImpl): SalaryRepository

    @Binds
    @Singleton
    abstract fun bindSavingGoalRepository(impl: SavingGoalRepositoryImpl): SavingGoalRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindPaymentAccountRepository(impl: PaymentAccountRepositoryImpl): PaymentAccountRepository

    @Binds
    @Singleton
    abstract fun bindExpenseRepository(impl: ExpenseRepositoryImpl): ExpenseRepository

    @Binds
    @Singleton
    abstract fun bindInstallmentRepository(impl: InstallmentRepositoryImpl): InstallmentRepository

    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(impl: SubscriptionRepositoryImpl): SubscriptionRepository

    @Binds
    @Singleton
    abstract fun bindLoanRepository(impl: LoanRepositoryImpl): LoanRepository

    @Binds
    @Singleton
    abstract fun bindCategoryBudgetRepository(impl: CategoryBudgetRepositoryImpl): CategoryBudgetRepository

    @Binds
    @Singleton
    abstract fun bindExpenseTemplateRepository(impl: ExpenseTemplateRepositoryImpl): ExpenseTemplateRepository

    @Binds
    @Singleton
    abstract fun bindDebtRepository(impl: DebtRepositoryImpl): DebtRepository

    @Binds
    @Singleton
    abstract fun bindNetWorthRepository(impl: NetWorthRepositoryImpl): NetWorthRepository

    @Binds
    @Singleton
    abstract fun bindExpenseAttachmentRepository(impl: ExpenseAttachmentRepositoryImpl): ExpenseAttachmentRepository

    @Binds
    @Singleton
    abstract fun bindCreditCardStatementPaymentRepository(
        impl: CreditCardStatementPaymentRepositoryImpl
    ): CreditCardStatementPaymentRepository

    @Binds
    @Singleton
    abstract fun bindExpenseAdjustmentRepository(
        impl: ExpenseAdjustmentRepositoryImpl
    ): ExpenseAdjustmentRepository
}
