# BudgetTracker Codebase Context

This document is the persistent project context for future Codex sessions. Read it before making changes so the project identity, architecture, product rules, and production constraints do not need to be repeated.

## Project Identity

- Project name: BudgetTracker
- Platform: Native Android
- Application ID / package: `com.umit.budgettracker`
- Primary language: Kotlin
- UI: Jetpack Compose with Material 3
- Navigation: Navigation Compose
- Dependency injection: Hilt
- Persistence: Room, SQLite, DataStore, app-private files
- Serialization: Kotlinx Serialization
- App language: Turkish
- Backend: none
- Cloud sync: none
- Analytics: none
- Ads: none
- Network dependency for core features: none
- Optional network use: user-triggered exchange-rate lookup for foreign-currency expenses

BudgetTracker is a local-only personal finance and monthly budgeting app. It is intended to be deterministic, privacy-friendly, and safe for long-term local financial data.

## Current Build Snapshot

- Root project: `Budget Tracker`
- Main module: `:app`
- Compile SDK: 36
- Min SDK: 33
- Target SDK: 36
- JVM target: 17
- Kotlin: 2.0.0
- Android Gradle Plugin: 8.13.2
- Room: 2.6.1
- Hilt: 2.51.1
- Compose BOM: 2024.05.00
- Database name: `budget_tracker_db`
- Room database version: 8
- JSON export schema version: 7

## Non-Negotiable Product Rules

### Local-Only Privacy Model

Do not add:

- Backend services
- Remote databases
- Cloud sync
- Analytics SDKs
- Ad SDKs
- Tracking
- Account systems
- Server-side authentication

All user finance data must remain on the device.

Exception: exchange-rate lookup is allowed as a user-triggered helper for foreign-currency expense entry. It must not create accounts, sync user data, upload financial records, or become required for core usage. If rate lookup fails, the user must be able to enter the exchange rate manually.

### Money Storage

All monetary values must be stored and calculated as `Long` minor units.

For Turkish Lira:

```text
1 TL = 100 kuruş
10.50 TL = 1050L
200.00 TL = 20000L
```

Do not use `Double` for money storage or money calculations.

### User-Facing Language

All user-facing labels, validation messages, errors, confirmations, empty states, and success messages must remain Turkish.

### Destructive Operations

Destructive operations must require explicit confirmation. This includes deleting records, replacing data through import/restore, restoring DB backups, loading full ZIP backups, and deleting attachments.

## Current Package Structure

```text
com.umit.budgettracker
  BudgetTrackerApp.kt
  MainActivity.kt
  core
    database
      dao
      entity
      converter
      repository
    dataimport
    datastore
    di
    domain
      calculator
      model
      repository
      usecase
    export
    navigation
    ui
      theme
    util
  feature
    budgets
    cards
    cashflow
    dashboard
    debt
    expense
    installments
    loans
    networth
    reports
    salary
    settings
    subscriptions
    templates
```

Architectural expectations:

- Compose screens should stay presentation-focused.
- ViewModels should own UI state and user actions.
- Business rules should live in calculators, repositories, or use cases.
- File export/import logic must not live in composables.
- Room entities should not become the only domain model when business logic grows.
- Prefer small incremental changes over broad rewrites.

## Main Product Areas

- Dashboard / monthly overview
- Salary rules and monthly saving goals
- Additional one-off income tracking
- Expense tracking
- Expense categories
- Payment accounts and credit cards
- Credit card statement and due-date tracking
- Installment purchases
- Subscription tracking and subscription price history
- Subscription mark-as-paid flow
- Loans
- Debts and receivables
- Net worth snapshots
- Category budgets
- Expense templates
- Reports and previous-month comparison
- Cash flow calendar and future projections
- JSON export/import
- CSV export
- PDF monthly report export
- Raw DB backup/restore
- Full ZIP backup/export/import
- Receipt/photo attachments stored in app-private files

## Navigation

Bottom navigation contains:

- `Özet`
- `Harcamalar`
- `Kartlar`
- `Raporlar`
- `Ayarlar`

Additional routes include salary management, installments, subscriptions, loans, cash flow, category budgets, expense templates, debt tracking, net worth, and categories.

## Database Model

The Room database currently includes these entities:

- `SalaryRuleEntity`
- `MonthlySavingGoalEntity`
- `CategoryEntity`
- `PaymentAccountEntity`
- `ExpenseEntity`
- `InstallmentGroupEntity`
- `LoanEntity`
- `SubscriptionEntity`
- `SubscriptionPriceHistoryEntity`
- `CategoryBudgetEntity`
- `ExpenseTemplateEntity`
- `DebtRecordEntity`
- `NetWorthSnapshotEntity`
- `ExpenseAttachmentEntity`
- `CreditCardStatementPaymentEntity`
- `ExpenseAdjustmentEntity`
- `IncomeEntity`

Default seeded categories:

```text
Market, Fatura, Kira, Ulaşım, Yakıt, Yemek, Kahve, Teknoloji, Sağlık,
Eğitim, Eğlence, Giyim, Ev, Abonelik, Kredi, Taksit, Tatil, Hediye, Diğer
```

The app also seeds default payment accounts:

- `Nakit`
- `Banka Hesabı`

## Attachment Storage Rule

Receipt/photo attachments must not be stored as Room BLOBs.

Correct storage strategy:

```text
filesDir/attachments/expenses/{expenseId}/receipt_xxx.jpg
```

Room should store only metadata:

- Attachment ID
- Expense ID
- Relative local file path
- File name
- MIME type
- Created timestamp

Backup distinction:

- DB backup: database files only
- Full ZIP backup: `data.json` plus `attachments/`

Expected ZIP shape:

```text
budgettracker_full_backup.zip
  data.json
  attachments/
```

## Export / Import Rules

JSON export/import:

- `appName` must be `BudgetTracker`.
- Unsupported future schema versions must be rejected.
- Current JSON schema version is 7.
- Attachment metadata is included in schema version 2.
- Credit card statement payment status is included in schema version 3.
- Expense adjustments/refunds are included in schema version 4.
- Foreign-currency expense metadata is included in schema version 5.
- Foreign-currency subscription metadata is included in schema version 6.
- Additional income records are included in schema version 7.
- Replace-mode imports must be confirmed by the user.

## Income Records Rule

Salary rules remain the recurring monthly salary source. Additional one-off income must be stored separately as income records:

```text
incomes
  title
  amount
  incomeDate
  type
  note
```

Examples:

- Ek gelir
- Prim
- Freelance
- Satış
- Borç tahsilatı
- Diğer

Monthly summary uses:

```text
totalIncomeAmount = salaryAmount + additionalIncomeAmount
```

Remaining balance calculations must use total income, not salary alone.

## Foreign Currency Expense Rule

Primary reporting currency remains TRY.

The existing `Expense.amount` field must continue to store the TRY minor-unit value used by reports, budgets, dashboards, exports, and card statements.

Foreign-currency expenses may additionally store nullable metadata:

```text
originalAmount
originalCurrency
exchangeRateToTry
exchangeRateScale
exchangeRateSource
exchangeRateUpdatedAt
```

Rules:

- Existing expenses must remain valid without these fields.
- `amount` is always the TRY equivalent in minor units.
- `originalAmount` is stored in the selected currency minor units.
- `exchangeRateToTry` uses fixed-scale integer precision, currently scale `10000`.
- Manual rate entry must be available if network lookup fails.
- Do not use `Double` for persisted money or exchange-rate calculation.
- Optional network source currently uses Frankfurter public API for user-triggered rate lookup.

## Foreign Currency Subscription Rule

Primary reporting currency remains TRY.

Subscriptions may store nullable currency metadata:

```text
originalCurrency
exchangeRateToTry
exchangeRateScale
exchangeRateSource
exchangeRateUpdatedAt
```

For TRY subscriptions, `SubscriptionPriceHistory.amount` is interpreted as TRY minor units.

For foreign-currency subscriptions, `SubscriptionPriceHistory.amount` is interpreted as the original currency minor-unit amount. Monthly subscription calculations should convert it to TRY using the latest available exchange rate. If live rate lookup fails, fall back to the stored/manual rate on the subscription. Existing TRY subscriptions must remain valid without any currency metadata.

When a foreign-currency subscription payment is processed into expenses, the created expense should keep:

```text
amount = calculated TRY minor-unit amount
originalAmount = subscription original currency amount
originalCurrency = subscription currency
exchangeRateToTry / exchangeRateScale / exchangeRateSource / exchangeRateUpdatedAt
```

## Expense Adjustment / Refund Rule

Refunds and partial refunds must not overwrite the original expense row.

Use separate adjustment records linked to the original expense:

```text
expense_adjustments
  expenseId
  amount
  type = REFUND
  adjustmentDate
  note
```

Financial summaries should use net expense amount:

```text
netExpense = expense.amount - linked refund adjustments
```

Net amount must not go below zero.

Full ZIP import:

- ZIP must contain `data.json`.
- `attachments/` is optional.
- ZIP import must use Android Storage Access Framework.
- Validate ZIP before destructive work.
- Protect against Zip Slip.
- If JSON import fails, existing attachments must not be touched.
- If data import succeeds but attachment copy fails, show a clear Turkish partial failure message.

## Subscription Rules

A subscription record is a rule. A monthly subscription payment is planned until the user explicitly marks it as paid or processes it into expenses.

Subscriptions must not silently generate monthly expenses in the background.

When marking a subscription payment as paid for a selected month:

1. Check whether an `Expense` already exists for the same subscription and month.
2. Create an expense only if one does not already exist.
3. Link the created expense with `subscriptionId`.
4. Show a Turkish user-friendly message.

Duplicate prevention message:

```text
Bu abonelik ödemesi zaten harcamalara işlenmiş.
```

Cancellation is represented with:

```kotlin
cancelledFromMonth: YearMonth?
```

If `cancelledFromMonth = 2026-08`, July 2026 is included and August 2026 onward is excluded.

Physical deletion should be blocked when historical expenses are linked through `subscriptionId`.

## Category Rules

Category management should support create, edit, deactivate, and safe delete.

Rules:

- Default categories should not be physically deleted.
- Categories used by historical records should not be physically deleted.
- Used categories may be deactivated.
- Inactive categories should not appear in new expense/category selectors.
- Old expenses should continue to show their category.
- Duplicate category names should be blocked or warned.

## Duplicate Prevention Rules

Prevent duplicates for:

- Same subscription payment in the same month
- Duplicate category names
- Same effective-month salary rule
- Same month saving goal
- Same category/month budget

Expected behavior:

- Saving goal for the same month should update the existing record or warn.
- Category budget for the same category/month should update the existing record or warn.
- Salary rule for the same effective month should update the existing record or warn.
- Subscription mark-as-paid must not create duplicate expenses.

## Important Turkish Messages

Examples:

```text
Dosya okunamadı.
Dosya yazılamadı.
Yedek dosyası geçersiz.
Bu işlem şu anda tamamlanamadı.
Lütfen tüm zorunlu alanları doldurun.
Tutar geçerli değil.
Kategori seçilmelidir.
Ödeme hesabı seçilmelidir.
```

Use proper Turkish characters in UI strings when editing existing Turkish UI files.

## Current Production Readiness Watchlist

These items are known risks or production-hardening targets based on the current codebase:

- `AndroidManifest.xml` currently has `android:allowBackup="true"`. For a privacy-first finance app, production should intentionally decide this and likely use `false`.
- `AppDatabase` currently uses `exportSchema = false`. Production Room schema history should normally be exported and committed.
- `DatabaseModule` currently uses `fallbackToDestructiveMigration()` and `fallbackToDestructiveMigrationOnDowngrade()`. Production builds should avoid destructive migrations for financial data.
- Release build currently has `isMinifyEnabled = false`; production release should test R8/minification and resource shrinking.
- Export/import, full ZIP backup, Room, and Kotlinx Serialization must be tested after minification.
- Signing config should use Gradle properties or local files, never hardcoded secrets.

## Verification Commands

Useful commands:

```bash
./gradlew test
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew bundleRelease
./gradlew clean test assembleRelease bundleRelease
```

For first personal production usage, at minimum:

```bash
./gradlew clean test assembleRelease
```

## Manual Test Scenarios

Subscription price history:

```text
Netflix May 2026 = 200 TL
From June 2026 = 250 TL

Expected:
May 2026 -> 200 TL
June 2026 -> 250 TL
July 2026 -> 250 TL
```

Subscription mark-as-paid:

```text
Selected month: May 2026
Subscription: Netflix
Action: Harcamalara İşle

Expected:
Expense is created once, linked with subscriptionId, and repeating the action does not duplicate it.
```

Full backup import:

```text
1. Create expense with receipt photo.
2. Export full backup ZIP.
3. Modify or delete data.
4. Import full backup ZIP.

Expected:
Expense returns, attachment metadata returns, and the attachment file exists under app-private storage.
```

Backup error handling:

Test corrupted ZIP import, ZIP without `data.json`, invalid JSON, wrong `appName`, unsupported `schemaVersion`, Zip Slip attempts, and missing attachment files.

Expected:

- No crash
- Turkish user-friendly error
- No unexpected destructive partial behavior

## Development Guidelines

When modifying this project:

- Preserve the local-only privacy model.
- Do not introduce backend, cloud sync, analytics, ads, or remote services.
- Do not store money as `Double`.
- Do not store receipt images as Room BLOBs.
- Do not introduce destructive migrations.
- Keep user-facing strings Turkish.
- Add tests for business logic changes.
- Keep export/import backward compatibility in mind.
- Validate backup files before destructive restore.
- Avoid duplicate financial records.
- Keep UI state predictable and lifecycle-safe.
- Use Coroutines and Flow consistently.
- Keep Compose screens clean.
- Preserve Room schema history once schema export is enabled.
- Summarize changed files and reasons after each implementation pass.
