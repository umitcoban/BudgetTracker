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
- Optional network use: public price / rate / index / holiday downloads (see "Network Policy"); user-triggered exchange-rate lookup for foreign-currency expenses and subscriptions

BudgetTracker is a local-only personal finance and monthly budgeting app. It is intended to be deterministic, privacy-friendly, and safe for long-term local financial data.

## Current Build Snapshot

- Root project: `Budget Tracker`
- Main module: `:app`
- Compile SDK: 36
- Min SDK: 33
- Target SDK: 36
- JVM target: 17 (Gradle daemon toolchain: JDK 21 via foojay)
- Gradle: 9.5
- Kotlin: 2.2.10
- KSP: 2.3.2
- Android Gradle Plugin: 9.3.1
- Room: 2.8.4
- Hilt: 2.60.1
- Compose BOM: 2024.05.00
- WorkManager: 2.10.1 (workers get dependencies through a Hilt `@EntryPoint`, no hilt-work)
- Biometric: 1.1.0 (`MainActivity` is a `FragmentActivity` for `BiometricPrompt`)
- ML Kit text recognition (bundled Latin model, offline): 16.0.1
- Source of truth for versions: `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/gradle-daemon-jvm.properties`
- Database name: `budget_tracker_db`
- Room database version: 17
- JSON export schema version: 15

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

Exception: see "Network Policy" below. Public data may be downloaded; nothing about the user is ever sent.

### Network Policy

Optional, public data sources (exchange rates, gold, inflation, public holidays, optional crypto) may be fetched periodically in the background. Rules:

- No personal or financial data is ever sent; no accounts or sessions; requests are plain GETs of public URLs.
- Core features work without network. Every downloaded value has a "last updated" label and a manual-entry alternative.
- Every source can be switched off in Menü → Tercihler → Veri kaynakları (DataStore, device-only).
- Background fetches run through WorkManager with a network constraint (prices every 12 h, holidays every 14 days); failure is silent (last known value stays) and transient errors retry with exponential backoff (max 3 attempts).
- One `MarketDataService` owns all fetching; parsers are pure functions in `core/network/sources/SourceParsers.kt`, tested against real responses in `app/src/test/resources/datasources/`. A changed format must yield an empty result, never a crash.
- User-Agent stays ASCII (TCMB rejects non-ASCII agents with an HTML error page).
- Network / file code never lives in composables (ViewModel → use case / service → repository).
- Sources, URLs, verification results and fragility notes: `docs/DATA_SOURCES.md`.

The original user-triggered exchange-rate lookup (Frankfurter) for foreign-currency expenses and subscriptions is unchanged; manual rate entry must stay available.

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
    capture          notification → expense draft (opt-in NotificationListenerService)
    di
    domain
      calculator
      model
      repository
      usecase
    export
    navigation
    network
      sources        public price / holiday / CPI sources + pure parsers
    ocr              on-device text extraction (images, PDF pages, XLSX, CSV)
    parsing          Turkish numbers/dates, fund table, bank statement, receipt, notification parsers
    security         app lock (PIN hash, lock state)
    ui
      charts
      components
      theme
    util
    widget           home-screen widget (RemoteViews)
    work             WorkManager workers, scheduler, background checks, notifications
  feature
    assets           Varlıklarım + fon içe aktarma
    budgets
    cards
    cashflow
    dashboard
    debt
    expense
    fixedexpenses
    goals            birikim hedefleri + acil durum fonu
    inflation
    insights         tekrarlayan harcama önerileri, bildirim taslakları, işyeri kuralları
    installments
    loans
    lock
    networth
    planner          büyük harcama planlayıcı
    preferences      kilit, veri kaynakları, bildirimler, widget, bildirim yakalama
    quickadd
    receipt          fiş tarama (OCR)
    reports
    salary
    settings         "Menü" sekmesi (hub) + yedekleme
    simulation       "Ya şöyle olsaydı?" + zam planı
    statement        banka ekstresi karşılaştırma
    subscriptions
    templates
    yearend
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
- Fixed recurring expense planning
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
- PDF report export (selected month + 12-month page with charts)
- Raw DB backup/restore
- Full ZIP backup/export/import
- Receipt/photo attachments stored in app-private files
- Assets (gold, currency, funds, deposits, cash/bank balances, crypto, stocks) with live prices, cost/profit and price alerts
- Fund table import (screenshot OCR, PDF, XLSX, CSV, pasted text)
- Purpose-bound savings goals with contributions; emergency-fund indicator
- Manual monthly CPI with real-value calculations
- Big-expense planner with monthly reserve
- Smart cash warning (projected tightest day) and pre-payment / holiday-shift notes
- "What if" simulation and raise planning
- Anomaly and trend warnings, recurring-expense suggestions
- Bank statement comparison and receipt OCR
- Year-end report (screen + PDF page)
- Home-screen widget, app shortcuts, quick add
- App lock (biometric + PIN), FLAG_SECURE option
- Optional notification capture into drafts

## Navigation

Bottom navigation contains:

- `Özet`
- `Harcamalar`
- `Kartlar`
- `Raporlar`
- `Menü` (route `settings`): a hub of tiles (Varlık ve birikim, Planlama ve analiz, Hızlı araçlar), record management, Tercihler and backup/export.

Every secondary screen uses `DetailScaffold` (back arrow "Geri", title + subtitle, optional FAB/snackbar). Screens that can be opened from several places (hub, dashboard chips, widget, notification) go back with `popBackStackOrDashboard()`, which lands on Özet when there is nothing to pop. `MainActivity` accepts a route in the `route` extra (notifications) and the `QUICK_ADD` / `SCAN_RECEIPT` actions (widget, launcher shortcuts).

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
- `FixedExpenseEntity`
- `CreditCardStatementRuleEntity`
- Version 17: `InflationRateEntity` (`inflation_rates`, PK month), `AssetEntity`, `MarketPriceEntity` (PK code), `PriceAlertEntity`, `SavingsGoalEntity`, `GoalContributionEntity`, `PlannedExpenseEntity`, `PublicHolidayEntity` (cache, not exported), `MerchantRuleEntity` (unique pattern), `DismissedSuggestionEntity`, `ExpenseDraftEntity` (not exported)

Migration `16 -> 17` only creates tables (SQL copied from the generated `17.json`) and inserts the 12-month CPI seed with `INSERT OR IGNORE`. Fresh installs seed the same CPI rows in `onCreate`, and a JSON import older than schema 15 re-seeds them after `clearAllTables()`.

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
- Current JSON schema version is 14.
- Attachment metadata is included in schema version 2.
- Credit card statement payment status is included in schema version 3.
- Expense adjustments/refunds are included in schema version 4.
- Foreign-currency expense metadata is included in schema version 5.
- Foreign-currency subscription metadata is included in schema version 6.
- Additional income records are included in schema version 7.
- Fixed recurring expenses are included in schema version 8.
- Expense links to processed fixed expenses are included in schema version 9.
- Subscription price-history currency/rate metadata is included in schema version 10.
- Loan early-close date is included in schema version 11.
- Loan payment records are included in schema version 12.
- Credit-card statement and due-date rules are included in schema version 13.
- Salary rule pay day (`payDay`, nullable) is included in schema version 14.
- Inflation rates, assets, market prices, price alerts, savings goals, goal contributions, planned expenses, merchant rules and dismissed suggestion keys are included in schema version 15. Public holidays (refetchable) and notification drafts (transient) are not exported. Device preferences (lock PIN hash, source toggles, capture allow-list) live in DataStore and are never part of a backup.
- `JsonExportService` writes `SCHEMA_VERSION` (core/export); keep the literal in the `dto.schemaVersion > N` import guard in step.
- Replace-mode imports must be confirmed by the user. JSON import, raw DB restore and full ZIP restore all go through the same confirmation dialog in Settings.
- Exports are written with `encodeDefaults = true`, so `appName` and empty sections are always present in the file.

## Credit Card Statement Rules

Credit-card statement and due dates are historical effective-month rules, not mutable account-wide values.

Rules:

- A rule starts from `effectiveFromMonth`; the latest rule at or before the relevant month applies.
- Saving a new rule must preserve prior rules and historical expense planning.
- Statement day determines the statement-closing month for an expense; due day is resolved from the closing month so changes do not shift older statements.
- A statement period starts the day after the previous month's statement closed **using that month's own rule**, so a statement day that moves between months (11 → 12 → 11) leaves no gap and no overlap.
- The Cards dialog offers "Sadece bu ay için": it saves the rule for the chosen month and a second rule for the following month restoring the previously effective days (unless a rule already starts that month).
- The Cards screen and monthly budget/dashboard projections must use the same rule history.
- Database migration `14 -> 15` creates `credit_card_statement_rules`; JSON schema version 13 exports and restores these rules.

## Salary Rule

Salary is stored as effective-month rules, not as a single mutable value.

Rules:

- A salary rule starts from `effectiveStartMonth`.
- The latest rule whose `effectiveStartMonth <= selectedMonth` is the salary for that month.
- Adding a future raise must create or update a rule for the raise month.
- Past months must not change when a future salary rule is added.
- There must be only one salary rule for the same effective month.
- If the same effective month is saved again, the existing rule is updated instead of creating a duplicate.
- A rule may carry a nullable `payDay` (1–31). It only affects the cash-flow calendar (salary lands on that day; day 1 when null) — monthly totals never depend on it. Database migration `15 -> 16` adds the column.

Example:

```text
2026-05 = 50,000 TL
2026-08 = 70,000 TL

May, June, July 2026 -> 50,000 TL
August 2026 and later -> 70,000 TL
```

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

## Loan Rule

For newly created or edited loans, monthly payment is derived from the principal and installment count:

```text
monthlyPaymentAmount = principalAmount / installmentCount
```

Rules:

- Users must not enter `monthlyPaymentAmount` manually in the loan form.
- The calculation must use `Long` minor units and integer arithmetic; do not use `Double`.
- The UI may show the calculated amount as a read-only preview, but the ViewModel or domain calculator must calculate it again before saving.
- Existing loans retain their stored monthly-payment values until the user edits the record.
- A loan payment is recorded once per loan and payment month. Marking a payment as paid removes only that month's planned loan payment; it must not delete historical payment records.
- Marking a payment as paid also inserts an `Expense` linked through `loanId` (title `"<loan> (n/N)"`, dated on the loan's payment day, category/account from the loan — falling back to the default `Kredi` category and the first bank account for loans saved before the form collected them). This is what keeps paid instalments inside Harcamalar, reports and cash flow; the loan form therefore requires a category and a payment account.

## Budget Warning Rule

`MonthlyBudgetSummary.warnings` is produced by `BudgetWarningRules` in severity order; the Dashboard shows only the first entry, so the order is part of the contract:

1. `NEGATIVE_REMAINING` — remaining after planned fixed payments is below zero
2. `CATEGORY_LIMIT_EXCEEDED` — spending above the category budget
3. `UPCOMING_CARD_PAYMENT` — unpaid statement due within 7 days, or already past due this month
4. `UPCOMING_LOAN_PAYMENT` — same window for loan instalments
5. `POSSIBLE_DUPLICATE_EXPENSE` — same day, same amount, similar title (not linked to a subscription/loan/fixed expense/installment)
6. `CATEGORY_SPENDING_SPIKE` — category at ≥150% of its 3-month average and ≥5% of the month's spending
7. `SUBSCRIPTION_PRICE_INCREASE` — a price-history entry starting this month is higher than the previous one (same currency)
8. `CATEGORY_LIMIT_80_PERCENT` — budget usage between 80% and 100%
9. `POSITIVE_TREND` — a category fell every month for 3 months and by ≥10% overall (good news; `BudgetWarning.isPositive`)

Percentages are integer arithmetic. Upcoming-payment rules only apply when the selected month is the current calendar month. Detection lives in `AnomalyRules`; the Dashboard shows the first non-positive warning with a "+N uyarı daha" expander and uses a positive trend as the good-news insight.

## Monthly Summary Batching Rule

`MonthlyBudgetCalculator.getSummariesForMonths(months)` is the only path that opens database flows; `getSummaryForMonth` wraps it. Screens that need several months (Reports, Dashboard history, PDF) must pass the whole list rather than combining single-month calls. The calculator loads expenses for `[min(months) − 3 baseline months − 2 planning-shift months, max(months)]`; `MAX_PLANNING_MONTH_SHIFT` must be kept in step with the credit-card planning-month logic.

## Cash Flow Rule

The cash-flow calendar lists cash movements, not accounting entries:

- Salary is an inflow on the rule's `payDay` (day 1 when unset) and one-off incomes on their dates.
- Expenses paid from cash/bank accounts are outflows on their dates; credit-card purchases are **not** listed — the card's statement payment on its due date is the outflow.
- Planned subscription, loan and fixed-expense payments appear on their days until processed.

## Assets and Valuation Rule

- Quantities are `Long` at `AssetValuationRules.QUANTITY_SCALE = 1_000_000` (2,5 gr = 2_500_000); prices are TRY at `PRICE_SCALE = 1_000_000` (fund prices carry six decimals: 0,141840 ₺ = 141_840).
- `value = quantity × price` is computed exactly with `BigInteger` and rounded **half-up to kuruş once**. Profit = value − cost; profit percent is basis points, half-up.
- Priced types (gold, currency, fund, crypto, stock) point at a `MarketPrice` by code (`FX:USD`, `GOLD:GRAM`, `FUND:GTL`, `STOCK:…`, `CRYPTO:BTC`). Cash/deposit/other carry their TRY value in `manualValue`. Deposits accrue simple gross interest (principal × rate × days / 365) up to today / maturity.
- Market prices keep `previousPrice` only from the same source, so a TCMB vs Truncgil quote gap is not shown as a move.
- Buying adds quantity and cost; selling lowers cost proportionally (average cost kept).
- Assets marked `isEmergencyFund` feed the emergency fund. Type `CASH` balances are the starting point of the smart cash forecast.
- An asset linked to a savings goal cannot be deleted (deactivate instead). Net worth snapshots stay historical; the Net Varlık dialog only *prefills* a new snapshot from live assets, one snapshot per month.
- Fund import parses broker tables by finding a 3-letter code and the number triple `quantity × price ≈ value` (0,5 % tolerance) — column order does not matter. Account/customer numbers are never stored.

## Inflation Rule

- Monthly CPI is user data (`inflation_rates`, one row per month, re-entry updates). Rates are basis points (%1,84 → 184). Annual rate is optional.
- Compounding uses exact fractions (`InflationRules.factor`) and rounds once when applied. Missing months reuse the last known rate and the result is labelled "tahmini".
- If an annual rate is entered and the twelve monthly rates are complete, a difference above 0,1 point is flagged ("Rakamları kontrol et").
- A reminder fires from the 3rd of each month until the previous month is entered. TCMB's HTML table can be fetched on demand; rows are only saved after the user selects them.

## Savings Goals and Emergency Fund Rule

- A goal's balance is the sum of its contributions (withdrawals are negative), or the live value of a linked asset.
- Required monthly = remaining ÷ months left (inclusive, rounded up); pace = contributions of the last three months ÷ 3; projected finish and delay derive from the pace.
- Leftover suggestions (`SavingsGoalRules.suggestAllocation`) serve higher priority first and share shortfalls proportionally. They are only suggestions; applying them adds contribution records after confirmation — no money moves.
- Goals with contributions cannot be deleted (deactivate instead).
- Emergency fund: average `MonthlyBudgetSummary.essentialExpenseAmount` of the last three months with data (rent/bill/"Aidat" categories, paid and unpaid subscriptions, loans, fixed expenses, installments) vs the value of emergency-fund assets; target months is a device preference (default 6).

## Planning, Forecast and Simulation Rules

- Big expenses (`planned_expenses`): monthly reserve = (amount − saved) ÷ months until due (inclusive, rounded up). Yearly items roll to the next year when completed. They appear in the cash-flow calendar on their due date and never create `Expense` rows.
- `CashFlowProjector` is the single builder of a month's cash-flow events (used by the Cash Flow screen, the smart cash forecast and background checks). Planned events on a weekend/public holiday carry a "bankada X günü işlenir" note (`BusinessDayRules`); amounts and months are not shifted.
- Smart cash forecast (`CashForecastRules`): starts from active `CASH` asset balances, applies planned events from today and actual events after today for this and next month, and reports the lowest balance, the outflow causing it and the next income.
- Simulation (`SimulationRules`) is read-only: 12 future months from `getSummariesForMonths`, plus variable spending equal to the last three months' average `variableExpenseAmount`. Scenarios: cancel subscription, close loan early (lump sum, optional payoff amount), cut a category by %, salary raise, fixed-expense (rent) raise, extra monthly cost/income. "Planla" on a raise writes real rules only after confirmation: salary → new effective-month rule; fixed expense → current record ends the month before and a new record starts with the raised amount.

## Assistant Features Rule

- Recurring suggestions: hand-entered expenses (no subscription/loan/fixed/installment link) with the same normalised title in ≥3 of the last 6 months, latest within the last month, amounts within ±15 % of the median, not matching an existing subscription/fixed-expense title. Dismissed keys are stored and never suggested again. Accepting opens an editable form (default start: next month) — nothing is created silently.
- Merchant rules (`merchant_rules`) learn "normalised merchant → category" whenever the user saves a receipt, statement line, approved draft or titled quick add. Normalisation folds Turkish characters and drops stand-alone numbers ("A101" keeps its digits).
- Receipt scan and statement import run OCR/text extraction on device; files and raw text are never stored. Only user-confirmed expenses are saved, each through `RecordExpenseUseCase` (duplicate check: same day, same amount, similar title). The receipt photo is copied into the normal attachment storage.
- Statement matching: same amount and date ±2 days = matched; similar title within the window but a different amount, or payment/refund lines = mismatch; app expenses in the period missing from the statement are listed separately.
- Notification capture is opt-in, limited to user-selected apps, never reads SMS, ignores OTP/incoming-money texts and stores only a draft (title, amount, date, app label). Drafts never count until approved.

## App Lock, Widget and Background Work

- Lock: PIN (4–8 digits) is stored as salted PBKDF2 hash with its length in DataStore; biometric unlock via `BiometricPrompt` (weak or strong). Delay: immediately / 1 min / 5 min after the app goes to background. Leaving for the app's own pickers/camera does not re-lock. `FLAG_SECURE` is an option. Lock settings are device-only and never backed up.
- Widget (`BudgetWidgetProvider`, RemoteViews) shows the current month's remaining-after-planned-payments and spent ratio from `MonthlyBudgetCalculator`, plus up to three quick-add category chips (chosen in Tercihler). It refreshes when the app goes to background, after quick add / receipt / statement saves and every 3 h.
- Workers: `PriceRefreshWorker` (12 h, then price alerts), `HolidayRefreshWorker` (14 d), `DailyCheckWorker` (enqueued by the 09:00 reminder alarm: inflation reminder, cash shortfall within 14 days, foreign-currency subscription and holiday-shift notes within 3 days, deposit maturity within 3 days), `WeeklySummaryWorker` (Sunday 20:00), `WidgetRefreshWorker`. Each notification type can be switched off.

## Local Reminder Rule

Local payment reminders are scheduled on-device for 09:00. The alarm must be re-scheduled when the device finishes booting, and notification permission must be requested transparently on Android 13 and later.

## Fixed Expense and Savings Suggestion Rule

Fixed expenses are recurring predictable payments that are not subscriptions and do not need subscription price history, cancellation, or mark-as-paid behavior.

Examples:

- Kira
- Aidat
- Sigorta
- Düzenli aile desteği
- Düzenli servis/okul ödemesi

Fixed expenses are stored as planning rules:

```text
fixed_expenses
  title
  amount
  dayOfMonth
  startMonth
  endMonth
  categoryId
  paymentAccountId
  note
  isActive
```

Rules:

- A fixed expense contributes to a month when `selectedMonth >= startMonth` and `endMonth == null || selectedMonth <= endMonth`.
- Inactive fixed expenses do not contribute to projections.
- Fixed expenses are included in Dashboard planned fixed payments and Cash Flow.
- Fixed expenses must not silently create real `Expense` rows.
- The user can explicitly process a fixed expense into expenses for the selected month.
- Processed fixed expense expenses must store `fixedExpenseId`.
- The same fixed expense must not be processed into expenses twice for the same month.
- If a fixed expense has already been processed for the selected month, it should no longer be counted as an unpaid planned fixed expense for that month.
- Export/import must preserve fixed expense records without mutating existing expenses.

Savings suggestion is a read-only estimate, not an automatic transaction:

```text
suggestedSavingAmount = max(
  0,
  (totalIncome - currentExpenses - unpaidSubscriptions - loanPayments - fixedExpenses) / 2
)
```

This keeps the app simple and gives a conservative amount after planned monthly obligations.

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

Subscriptions may store nullable current/default currency metadata:

```text
originalCurrency
exchangeRateToTry
exchangeRateScale
exchangeRateSource
exchangeRateUpdatedAt
```

Subscription price history also stores currency/rate metadata:

```text
originalCurrency
exchangeRateToTry
exchangeRateScale
exchangeRateSource
exchangeRateUpdatedAt
```

For TRY subscription price-history rows, `SubscriptionPriceHistory.amount` is interpreted as TRY minor units.

For foreign-currency price-history rows, `SubscriptionPriceHistory.amount` is interpreted as the original currency minor-unit amount. Monthly subscription calculations should convert it to TRY using the latest available exchange rate. If live rate lookup fails, fall back to the stored/manual rate on the price history, then the subscription.

Currency changes must not reinterpret historical prices. Example:

```text
May 2026 subscription price = 500 TL
July 2026 subscription price = 8 EUR

May and June remain 500 TL.
July and later use 8 EUR converted to TRY.
```

When editing an existing subscription and changing currency, the old amount field should be cleared so an old TRY amount is not accidentally saved as USD/EUR.

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

Net amount must not go below zero. The single implementation is `ExpenseAdjustmentRules.netAmount` / `Expense.netAmount(...)`; monthly summaries, card statements (`CreditCardStatementCalculator`), Cards and Cash Flow all go through it — do not re-derive it locally.

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

Subscription payments are processed into expenses in two ways, both idempotent per subscription and month:

- Explicitly, from the Subscriptions screen ("Harcamalara işle").
- Automatically by `SyncDueSubscriptionExpensesUseCase` on app start and on the Subscriptions screen: every contributing month whose billing day has passed and has no linked expense yet gets one. This is a deliberate product decision (2026-09); it must stay idempotent and must never touch months that already have a linked expense.

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
- Updating an existing category must use Room `@Update`, not `INSERT OR REPLACE`; categories can be referenced by historical expenses and `REPLACE` may trigger a foreign-key-restricted delete.

### Monthly Category Summary Rule

Monthly category summaries must be built from every expense in the selected planning month, not only from categories with a defined category budget.

Rules:

- A category with spending and no budget must still appear in Dashboard and Reports.
- A category with a budget but no spending must also remain visible with a zero amount.
- Credit-card expenses must use their planning/payment month, consistent with the rest of the monthly summary.
- Refund adjustments must reduce the displayed category amount; the amount must not drop below zero.
- When a category budget exists, show both the spending amount and the budget usage percentage.

## Duplicate Prevention Rules

Prevent duplicates for:

- Same subscription payment in the same month
- Duplicate category names
- Same effective-month salary rule
- Same month saving goal
- Same category/month budget
- Same month CPI rate (re-entry updates)
- Same month net worth snapshot (saving again replaces it)
- Same merchant pattern rule (latest category wins, hit count grows)
- Same fund code on import (updates the holding instead of adding a second one)

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

- `AndroidManifest.xml` uses `android:allowBackup="false"` for the privacy-first finance model.
- Room schema export is enabled and schema files should be committed when database versions change.
- Destructive migration fallback is intentionally disabled. New schema changes must provide explicit migrations.
- Release build has R8/minification and resource shrinking enabled.
- Export/import DTOs and Room entities/DAOs have minimal ProGuard keep rules.
- Export/import, full ZIP backup, Room, and Kotlinx Serialization should still be manually tested on the generated minified release APK before relying on it for long-term data.
- Signing config should use Gradle properties or local files, never hardcoded secrets.

## Data Integrity Regression Tests

Current focused regression coverage includes:

- Batched month summaries (`getSummariesForMonths`) equal the per-month results and only load the planning window of expenses.
- Budget warnings order by severity; upcoming card/loan warnings fire only for the month the user is in.
- Category spike detection needs a full three-month baseline and ignores immaterial categories.
- Credit-card statement totals are net of refunds.
- Asset valuation reproduces the broker fund table (31.772,62 ₺, +2.905,97 ₺, %10,07) from parsed rows; large holdings do not overflow.
- CPI seed compounds to %29,72 (published %29,73); estimated months are flagged.
- Source parsers read the real TCMB / Truncgil / gold-api / Nager / TCMB CPI fixtures and return empty results on changed formats.
- Warning order with the new anomaly types; duplicates, subscription increases and positive trends.
- Schema 14 JSON decodes with empty schema-15 sections; schema-15 sections round-trip.
- `MigrationTest.migrate16To17_…` keeps existing rows and seeds CPI (instrumented).

- Fixed expense processing prevents duplicate monthly planned fixed-payment totals.
- Credit-card expenses after statement day move to the next planning/payment month.
- Foreign-currency subscription price history keeps historical TRY prices when future prices switch to EUR/USD.
- Foreign-currency subscription payments preserve original amount, currency, and rate metadata when processed into expenses.
- Old JSON export shapes without newly added nullable fields decode with safe defaults.

New persistent fields should add focused coverage for:

- Existing JSON exports that omit the new field, which must decode to a safe default.
- New JSON exports that preserve the new field through a serialization round trip.
- Any calculator or projection behavior affected by the field.

## Cross-Layer Change Checklist

Use this checklist for every feature that changes a domain model, persisted data, or a financial lifecycle state.

1. Update the domain model, Room entity, and both `toDomain` / `toEntity` mapper directions together.
2. Use named constructor arguments when adding or changing mapper fields. Do not apply broad patches to repeated field blocks without reviewing the exact mapper target afterward.
3. Add the required DAO query and repository behavior, including safe-delete checks for linked historical records.
4. If the Room schema changes, increment the database version, add the explicit migration to `DatabaseModule`, and commit the generated Room schema JSON. Existing rows must receive a safe value, usually a nullable column or a migration backfill.
5. If persisted data is exported, update the export DTO, export service, import service, and supported JSON schema version together. New DTO fields must be nullable or have defaults when old export files lack them.
6. Keep destructive operations explicitly confirmed and ensure that state changes do not silently alter or delete historical expenses.
7. Run `git diff --check` and inspect the changed mapper, migration, DTO, and UI call sites before declaring the work complete.
8. Run `./gradlew test` after implementation. Resolve compilation errors and newly introduced warnings before handoff; retain any unrelated existing warnings as a documented follow-up.

Examples:

- Loan early close: store a nullable close date, mark the loan inactive so future projections stop, preserve historical expenses, prevent deletion when `expenses.loanId` still references the loan, and include the close date in JSON schema version 11.
- A new nullable Room column: migrate with `ALTER TABLE ... ADD COLUMN`, retain all old values, and confirm the new `app/schemas/.../<version>.json` file is generated.

## Migration Verification

`app/src/androidTest/.../MigrationTest.kt` replays the committed schema history (`app/schemas/`) with Room's `MigrationTestHelper` on a device/emulator: it seeds rows at an old version, runs `DatabaseModule.migrations`, and lets Room validate the result. Run it before shipping any schema change:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Note: AGP uninstalls the app from the device after the run, which wipes that device's app data — do not run it against a device whose data you care about.

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
