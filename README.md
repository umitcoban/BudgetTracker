# BudgetTracker

BudgetTracker is a privacy-first, local-only Android personal finance app built with Kotlin, Jetpack Compose, Material 3, Room, Hilt, and DataStore.

The app helps track monthly budgeting, salary rules, saving goals, expenses, categories, credit cards, installments, subscriptions, loans, debts, net worth, reports, and backup/export workflows. All financial data is stored on the device; the project does not use a backend, cloud sync, analytics, ads, or remote tracking.

## GitHub Description

Privacy-first, local-only Android budgeting app built with Kotlin, Jetpack Compose, Room, Hilt, and Material 3.

## Features

- Monthly dashboard with salary, spending, saving goals, and projections
- Expense tracking with categories and payment accounts
- Credit card statement and due-date tracking
- Installment purchase support
- Subscription tracking with price history and mark-as-paid behavior
- Loans, debts/receivables, and net worth snapshots
- Category budgets and monthly reports
- Cash flow calendar for upcoming financial events
- JSON import/export, CSV export, PDF reports, DB backup/restore, and full ZIP backup with attachments
- Receipt/photo attachment metadata stored in Room and files stored in app-private storage

## Tech Stack

- Kotlin
- Native Android
- Jetpack Compose
- Material 3
- Navigation Compose
- Hilt
- Room / SQLite
- DataStore
- Coroutines and Flow
- Kotlinx Serialization
- Android Storage Access Framework

## Privacy Model

BudgetTracker is designed as a local-only finance tracker:

- No backend
- No cloud sync
- No analytics
- No ads
- No account system
- No remote database

## Project Structure

```text
app/src/main/java/com/umit/budgettracker
  core/
    database/
    dataimport/
    datastore/
    di/
    domain/
    export/
    navigation/
    ui/
    util/
  feature/
    dashboard/
    expense/
    cards/
    reports/
    settings/
    subscriptions/
    loans/
    cashflow/
    budgets/
    templates/
    debt/
    networth/
```

For deeper project rules and production notes, see [`CODEBASE_CONTEXT.md`](CODEBASE_CONTEXT.md).

## Build

```bash
./gradlew assembleDebug
```

Run unit tests:

```bash
./gradlew test
```

Release sanity check:

```bash
./gradlew clean test assembleRelease
```

## Important Development Rules

- Store money as `Long` minor units, never as `Double`.
- Keep all user-facing app text in Turkish.
- Keep the app local-only.
- Avoid destructive migrations for production financial data.
- Do not store receipt images as Room BLOBs; store files under app-private storage and keep metadata in Room.
