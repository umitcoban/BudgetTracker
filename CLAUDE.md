# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Read first

`CODEBASE_CONTEXT.md` is the authoritative product-rules document (money as `Long` minor units, local-only privacy, Turkish UI, effective-month salary/statement rules, duplicate prevention, export/import schema history, the cross-layer change checklist). Read it before changing any domain model, persisted data, or financial lifecycle. This file does not repeat it.

`gradle/libs.versions.toml` and `app/build.gradle.kts` remain the source of truth for versions; update the "Current Build Snapshot" section there when bumping them.

## Commands

Single-module Android app (`:app`). No lint/ktlint/detekt is configured.

```bash
./gradlew assembleDebug                  # debug APK
./gradlew test                           # all JVM unit tests (debug + release variants)
./gradlew :app:testDebugUnitTest         # unit tests, debug variant only (faster)
./gradlew :app:testDebugUnitTest --tests "com.umit.budgettracker.core.domain.calculator.SalaryRulesTest"
./gradlew :app:testDebugUnitTest --tests "*SubscriptionRulesTest.cancelledFromMonth*"   # single method
./gradlew clean test assembleRelease     # release sanity check (R8 + resource shrinking enabled)
```

Test results land in `app/build/test-results/testDebugUnitTest/*.xml`. Instrumented tests (`androidTest`) are only the template example; all real coverage is JVM unit tests under `app/src/test`.

## Architecture

Package root: `com.umit.budgettracker` (`app/src/main/java/com/umit/budgettracker`). Two top-level packages: `core` (shared infrastructure + domain) and `feature` (one package per screen, each usually just `XxxScreen.kt` + `XxxViewModel.kt`).

### Layering and data flow

```
feature/*/XxxScreen.kt          Compose, presentation only, collects StateFlow
  └─ feature/*/XxxViewModel.kt  @HiltViewModel; owns selectedMonth + uiState
       └─ core/domain/calculator/*   business rules, return Flow<...> built with combine()
       └─ core/domain/usecase/*      mutations with invariants (mark-as-paid, sync)
            └─ core/domain/repository/*Repository   interfaces (domain models only)
                 └─ core/database/repository/*RepositoryImpl   bound in core/di/RepositoryModule
                      └─ core/database/dao/*Dao → entity/*Entity   Room
                         core/database/mapper/Mappers.kt          toDomain()/toEntity() for every entity
```

- Domain models live in `core/domain/model` and are distinct from Room entities. Every entity has a `toDomain`/`toEntity` pair in the single `Mappers.kt`; when adding a field, update model, entity, and both mapper directions together (use named arguments).
- ViewModels follow one pattern: `MutableStateFlow<YearMonth>` for the selected month → `flatMapLatest` → `combine` of repository/calculator flows → `stateIn(viewModelScope, WhileSubscribed(5000), Loading)`. Mutations run in `viewModelScope.launch`.
- `MonthlyBudgetCalculator` is the central aggregator: it combines salary rules, incomes, expenses, budgets, refund adjustments, statement rules/payments, and the pure `calculatePayments` functions of `SubscriptionMonthlyCalculator`, `LoanMonthlyCalculator`, and `FixedExpenseMonthlyCalculator`. Dashboard, Reports, and Category Budgets all derive from its `MonthlyBudgetSummary`, so a change to "what counts in a month" belongs there, not in a ViewModel. Use `getSummariesForMonths(months)` when a screen needs more than one month — it opens one set of Room subscriptions and one exchange-rate lookup per currency; `getSummaryForMonth` is the single-month wrapper. It loads expenses only for `[firstMonth - MAX_PLANNING_MONTH_SHIFT, lastMonth]`, so that constant must track `planningMonth`.
- `BudgetWarningRules` builds `MonthlyBudgetSummary.warnings` (severity-ordered; Dashboard shows only the first). Upcoming-payment warnings depend on `LocalDate.now()`, evaluated inside the calculator's `combine`; keep date-sensitive assertions in `BudgetWarningRulesTest`, which takes `today` explicitly.
- `SalaryRules`, `SubscriptionRules`, `LoanPaymentRules` are pure functions implementing the effective-month semantics; the corresponding `*Test.kt` files are the spec for those rules.

### Persistence

- `core/database/AppDatabase.kt` declares entities and the Room version (currently 15, `exportSchema = true`, schemas committed under `app/schemas/`).
- All migrations are inline objects in `core/di/DatabaseModule.kt` and registered in `addMigrations(...)`. There is no destructive fallback. Use the existing `addNullableColumnIfMissing(db, table, column, type)` helper for new nullable columns.
- Default categories and payment accounts are seeded in the `RoomDatabase.Callback.onCreate` inside `DatabaseModule`.
- Type converters (`core/database/converter/Converters.kt`): `LocalDate` ↔ epoch-day `Long`, `YearMonth` ↔ `"yyyy-MM"` text. Month-keyed queries compare on that text form.
- Preferences (theme, reminders, etc.) go through `core/datastore/SettingsDataStore.kt`.

### Export / import

- `core/export/ExportDto.kt` (DTOs), `JsonExportService.kt`, `CsvExportService.kt`, `PdfExportService.kt`, `FullBackupService.kt` (ZIP with `data.json` + `attachments/`); `core/dataimport/JsonImportService.kt` is the reverse; `core/database/DatabaseBackupService.kt` handles raw DB files.
- The PDF report takes a `PdfReportData` (selected month + 12-month history + category trends, built in `SettingsViewModel` from `getSummariesForMonths`) and draws with `android.graphics` via the helpers in `core/export/PdfCharts.kt` — the Canvas twins of `core/ui/charts`. It cannot run in JVM unit tests; verify on an emulator (Ayarlar → PDF Rapor) and render the file to check layout.
- The JSON schema version is a literal in two places that must move together: `JsonExportService` (`schemaVersion = N`) and the `dto.schemaVersion > N` guard in `JsonImportService`. Import gates each optional section with `if (dto.schemaVersion >= k)`.
- `app/proguard-rules.pro` keeps `core.export.**`, `core.dataimport.**`, `core.database.entity.**`, and `core.database.dao.**` wholesale; new serializable DTOs outside those packages need their own keep rule.

### Navigation

- Routes are `Screen` sealed-class objects in `core/navigation/Screen.kt` (route + Turkish title + icon); `bottomNavItems` selects the five tab destinations. `NavGraph.kt` maps each `Screen` to a composable. `MainActivity` shows the bottom bar only when the current route is in `bottomNavItems`.
- Use `navigateToTopLevelDestination` / `navigateBackToDashboard` from `NavigationExtensions.kt` rather than raw `navigate` for tab and back-to-home moves.

### Other notable pieces

- `core/network/ExchangeRateService.kt` is the only network code (user-triggered Frankfurter lookup); everything else is offline.
- `core/reminder/` — `PaymentReminderScheduler` + `PaymentReminderReceiver` + `BootCompletedReceiver` implement the 09:00 local reminder.
- `core/util/MoneyFormatter.kt` formats/parses TRY kuruş; UI text-field input is parsed here, never with `Double` arithmetic in domain code.
- `core/ui/charts/Charts.kt` holds the Canvas-based charts (donut + legend, stacked bars, sparkline, legend row) — no chart library. They take `Long` amounts, use theme colors, keep 2dp surface gaps between fills, and report tap selection back to the caller, which renders the values as text. Fold donut input to ≤ 6 slices with `foldTail` before calling.

## Conventions worth knowing

- User-facing strings are hardcoded Turkish literals in Kotlin, not `strings.xml` (which only holds the app name). Match that; do not start extracting to resources piecemeal.
- Tests are JUnit4 + `runBlocking`, and use hand-written `Fake*Repository` classes implementing the domain repository interfaces (see `MarkSubscriptionPaymentAsPaidUseCaseTest`) rather than Mockito, even though Mockito is on the classpath.
- `app/src/test/.../com/umityasincoban/` and `androidTest` contain only the Android Studio template examples; real tests live under `com.umit.budgettracker`.
