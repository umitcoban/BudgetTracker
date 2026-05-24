package com.umit.budgettracker.core.database.mapper

import com.umit.budgettracker.core.database.entity.*
import com.umit.budgettracker.core.domain.model.*
import java.time.LocalDate
import java.time.YearMonth

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    iconName = iconName,
    colorValue = colorValue,
    type = CategoryType.valueOf(type),
    isDefault = isDefault,
    isActive = isActive,
    sortOrder = sortOrder
)

fun Category.toEntity() = CategoryEntity(
    id = id,
    name = name,
    iconName = iconName,
    colorValue = colorValue,
    type = type.name,
    isDefault = isDefault,
    isActive = isActive,
    sortOrder = sortOrder
)

fun PaymentAccountEntity.toDomain() = PaymentAccount(
    id = id,
    name = name,
    type = AccountType.valueOf(type),
    statementDay = statementDay,
    dueDay = dueDay,
    isActive = isActive
)

fun PaymentAccount.toEntity() = PaymentAccountEntity(
    id = id,
    name = name,
    type = type.name,
    statementDay = statementDay,
    dueDay = dueDay,
    isActive = isActive
)

fun CreditCardStatementPaymentEntity.toDomain() = CreditCardStatementPayment(
    id = id,
    accountId = accountId,
    paymentMonth = YearMonth.parse(paymentMonth),
    amountAtPayment = amountAtPayment,
    isPaid = isPaid,
    paidAt = paidAt
)

fun CreditCardStatementPayment.toEntity() = CreditCardStatementPaymentEntity(
    id = id,
    accountId = accountId,
    paymentMonth = paymentMonth.toString(),
    amountAtPayment = amountAtPayment,
    isPaid = isPaid,
    paidAt = paidAt
)

fun SalaryRuleEntity.toDomain() = SalaryRule(
    id = id,
    amount = amount,
    effectiveStartMonth = YearMonth.parse(effectiveStartMonth),
    note = note
)

fun SalaryRule.toEntity() = SalaryRuleEntity(
    id = id,
    amount = amount,
    effectiveStartMonth = effectiveStartMonth.toString(),
    note = note
)

fun MonthlySavingGoalEntity.toDomain() = MonthlySavingGoal(
    yearMonth = YearMonth.parse(yearMonth),
    amount = amount,
    note = note
)

fun MonthlySavingGoal.toEntity() = MonthlySavingGoalEntity(
    yearMonth = yearMonth.toString(),
    amount = amount,
    note = note
)

fun IncomeEntity.toDomain() = Income(
    id = id,
    title = title,
    amount = amount,
    incomeDate = LocalDate.ofEpochDay(incomeDate),
    type = IncomeType.valueOf(type),
    note = note
)

fun Income.toEntity() = IncomeEntity(
    id = id,
    title = title,
    amount = amount,
    incomeDate = incomeDate.toEpochDay(),
    type = type.name,
    note = note
)

fun FixedExpenseEntity.toDomain(category: Category? = null, account: PaymentAccount? = null) = FixedExpense(
    id = id,
    title = title,
    amount = amount,
    dayOfMonth = dayOfMonth,
    startMonth = YearMonth.parse(startMonth),
    endMonth = endMonth?.let { YearMonth.parse(it) },
    categoryId = categoryId,
    paymentAccountId = paymentAccountId,
    note = note,
    isActive = isActive,
    category = category,
    account = account
)

fun FixedExpense.toEntity() = FixedExpenseEntity(
    id = id,
    title = title,
    amount = amount,
    dayOfMonth = dayOfMonth,
    startMonth = startMonth.toString(),
    endMonth = endMonth?.toString(),
    categoryId = categoryId,
    paymentAccountId = paymentAccountId,
    note = note,
    isActive = isActive
)

fun ExpenseEntity.toDomain(category: Category? = null, account: PaymentAccount? = null) = Expense(
    id = id,
    title = title,
    amount = amount,
    expenseDate = LocalDate.ofEpochDay(expenseDate),
    categoryId = categoryId,
    paymentAccountId = paymentAccountId,
    paymentSourceType = AccountType.valueOf(paymentSourceType),
    note = note,
    installmentGroupId = installmentGroupId,
    subscriptionId = subscriptionId,
    loanId = loanId,
    fixedExpenseId = fixedExpenseId,
    originalAmount = originalAmount,
    originalCurrency = originalCurrency,
    exchangeRateToTry = exchangeRateToTry,
    exchangeRateScale = exchangeRateScale,
    exchangeRateSource = exchangeRateSource,
    exchangeRateUpdatedAt = exchangeRateUpdatedAt,
    category = category,
    account = account
)

fun Expense.toEntity() = ExpenseEntity(
    id = id,
    title = title,
    amount = amount,
    expenseDate = expenseDate.toEpochDay(),
    categoryId = categoryId,
    paymentAccountId = paymentAccountId,
    paymentSourceType = paymentSourceType.name,
    note = note,
    installmentGroupId = installmentGroupId,
    subscriptionId = subscriptionId,
    loanId = loanId,
    fixedExpenseId = fixedExpenseId,
    originalAmount = originalAmount,
    originalCurrency = originalCurrency,
    exchangeRateToTry = exchangeRateToTry,
    exchangeRateScale = exchangeRateScale,
    exchangeRateSource = exchangeRateSource,
    exchangeRateUpdatedAt = exchangeRateUpdatedAt
)

fun ExpenseAdjustmentEntity.toDomain() = ExpenseAdjustment(
    id = id,
    expenseId = expenseId,
    amount = amount,
    type = ExpenseAdjustmentType.valueOf(type),
    adjustmentDate = LocalDate.ofEpochDay(adjustmentDate),
    note = note
)

fun ExpenseAdjustment.toEntity() = ExpenseAdjustmentEntity(
    id = id,
    expenseId = expenseId,
    amount = amount,
    type = type.name,
    adjustmentDate = adjustmentDate.toEpochDay(),
    note = note
)

fun SubscriptionEntity.toDomain(category: Category? = null, account: PaymentAccount? = null) = Subscription(
    id = id,
    title = title,
    categoryId = categoryId,
    paymentAccountId = paymentAccountId,
    billingDay = billingDay,
    isActive = isActive,
    note = note,
    cancelledFromMonth = cancelledFromMonth?.let { YearMonth.parse(it) },
    originalCurrency = originalCurrency,
    exchangeRateToTry = exchangeRateToTry,
    exchangeRateScale = exchangeRateScale,
    exchangeRateSource = exchangeRateSource,
    exchangeRateUpdatedAt = exchangeRateUpdatedAt,
    category = category,
    account = account
)

fun Subscription.toEntity() = SubscriptionEntity(
    id = id,
    title = title,
    categoryId = categoryId,
    paymentAccountId = paymentAccountId,
    billingDay = billingDay,
    isActive = isActive,
    note = note,
    cancelledFromMonth = cancelledFromMonth?.toString(),
    originalCurrency = originalCurrency,
    exchangeRateToTry = exchangeRateToTry,
    exchangeRateScale = exchangeRateScale,
    exchangeRateSource = exchangeRateSource,
    exchangeRateUpdatedAt = exchangeRateUpdatedAt
)

fun SubscriptionPriceHistoryEntity.toDomain() = SubscriptionPriceHistory(
    id = id,
    subscriptionId = subscriptionId,
    amount = amount,
    effectiveFromMonth = YearMonth.parse(effectiveFromMonth)
)

fun SubscriptionPriceHistory.toEntity() = SubscriptionPriceHistoryEntity(
    id = id,
    subscriptionId = subscriptionId,
    amount = amount,
    effectiveFromMonth = effectiveFromMonth.toString()
)

fun LoanEntity.toDomain(category: Category? = null, account: PaymentAccount? = null) = Loan(
    id = id,
    title = title,
    principalAmount = principalAmount,
    monthlyPaymentAmount = monthlyPaymentAmount,
    installmentCount = installmentCount,
    startMonth = YearMonth.parse(startMonth),
    paymentDay = paymentDay,
    categoryId = categoryId,
    paymentAccountId = paymentAccountId,
    note = note,
    isActive = isActive,
    category = category,
    account = account
)

fun Loan.toEntity() = LoanEntity(
    id = id,
    title = title,
    principalAmount = principalAmount,
    monthlyPaymentAmount = monthlyPaymentAmount,
    installmentCount = installmentCount,
    startMonth = startMonth.toString(),
    paymentDay = paymentDay,
    categoryId = categoryId,
    paymentAccountId = paymentAccountId,
    note = note,
    isActive = isActive
)

fun CategoryBudgetEntity.toDomain(category: Category? = null) = CategoryBudget(
    id = id,
    categoryId = categoryId,
    yearMonth = YearMonth.parse(yearMonth),
    limitAmount = limitAmount,
    note = note,
    category = category
)

fun CategoryBudget.toEntity() = CategoryBudgetEntity(
    id = id,
    categoryId = categoryId,
    yearMonth = yearMonth.toString(),
    limitAmount = limitAmount,
    note = note
)

fun ExpenseTemplateEntity.toDomain(category: Category? = null, account: PaymentAccount? = null) = ExpenseTemplate(
    id = id,
    title = title,
    defaultAmount = defaultAmount,
    categoryId = categoryId,
    paymentAccountId = paymentAccountId,
    note = note,
    isActive = isActive,
    category = category,
    account = account
)

fun ExpenseTemplate.toEntity() = ExpenseTemplateEntity(
    id = id,
    title = title,
    defaultAmount = defaultAmount,
    categoryId = categoryId,
    paymentAccountId = paymentAccountId,
    note = note,
    isActive = isActive
)

fun DebtRecordEntity.toDomain() = DebtRecord(
    id = id,
    title = title,
    personName = personName,
    amount = amount,
    type = DebtType.valueOf(type),
    dueDate = dueDate?.let { LocalDate.ofEpochDay(it) },
    isPaid = isPaid,
    note = note
)

fun DebtRecord.toEntity() = DebtRecordEntity(
    id = id,
    title = title,
    personName = personName,
    amount = amount,
    type = type.name,
    dueDate = dueDate?.toEpochDay(),
    isPaid = isPaid,
    note = note
)

fun NetWorthSnapshotEntity.toDomain() = NetWorthSnapshot(
    id = id,
    yearMonth = YearMonth.parse(yearMonth),
    cashAmount = cashAmount,
    bankAmount = bankAmount,
    investmentAmount = investmentAmount,
    creditCardDebt = creditCardDebt,
    loanDebt = loanDebt,
    note = note
)

fun NetWorthSnapshot.toEntity() = NetWorthSnapshotEntity(
    id = id,
    yearMonth = yearMonth.toString(),
    cashAmount = cashAmount,
    bankAmount = bankAmount,
    investmentAmount = investmentAmount,
    creditCardDebt = creditCardDebt,
    loanDebt = loanDebt,
    note = note
)

fun InstallmentGroupEntity.toDomain(category: Category? = null, account: PaymentAccount? = null) = InstallmentGroup(
    id = id,
    title = title,
    totalAmount = totalAmount,
    installmentCount = installmentCount,
    startDate = LocalDate.ofEpochDay(startDate),
    categoryId = categoryId,
    paymentAccountId = paymentAccountId,
    note = note,
    category = category,
    account = account
)

fun InstallmentGroup.toEntity() = InstallmentGroupEntity(
    id = id,
    title = title,
    totalAmount = totalAmount,
    installmentCount = installmentCount,
    startDate = startDate.toEpochDay(),
    categoryId = categoryId,
    paymentAccountId = paymentAccountId,
    note = note
)
