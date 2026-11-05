# SAM Phase 4: Widget, quick-add, share target, offline outbox

## Status (2026-11-04): built, emulator smoke-tested, not committed
- `lintDebug` is clean (apart from intentional newer-version notices). `assembleDebug` and `assembleRelease` (R8) pass.
- 17 unit tests pass: quick entry, server URL, widget snapshot merge, share text, outbox retry rules.
- Emulator: the share dialog renders ("Best tacos: https://example.com/tacos" became one todo, then cancelled).
- Still to check on the emulator/phone: widget placement and sizes, + Expense / + Task, tap-to-complete, offline add then sync.
- No backend changes were needed: it uses the existing `POST api/sam/todos`, `PUT api/sam/todos/{id}/complete` and `POST api/sam/budget/expenses`.

## What's new
- **Widget "SAM"** (Glance, resizable), with tap-anywhere to open the app:
  - **Small:** budget left, todos due today / overdue, and `+ $` / `+ Todo` buttons.
  - **Medium:** adds the page title, a spent/total bar and the last 2 expenses.
  - **Large:** adds the top 3 todos; tap the circle to complete one.
  - Header shows "syncing N" or "N failed".
- **Quick-add dialog** (`QuickAddActivity`, translucent, over the home screen):
  - **Expense:** "$amount expense" with category chips and a preview.
  - **Todo:** title, No date / Today / Tomorrow, "! Important".
- **Share → "Add to SAM todos"** (`ShareReceiverActivity`): one todo per line. A web page share (subject + URL) becomes one todo. Capped at 25 items of 300 characters.
- **Outbox** (`data/Outbox.kt`, a JSON file in app storage):
  - Widget, share and tap-to-complete changes save instantly, even offline or while the server is asleep.
  - The widget shows them immediately (budget left drops, todo disappears); `WidgetSnapshot` merges pending ops over the cached dashboard.
  - **Sync:** `SyncWorker` (WorkManager, network required) sends ops oldest first, then refreshes the dashboard and the widget.
    - It runs right after each change ("sam-sync") and every 30 minutes ("sam-refresh"), which also keeps the app pool warm.
    - Offline, 401, 408, 429 and 5xx errors retry with backoff. Other 4xx responses (e.g. "Start a budget page first.") move the item to **failed**.
  - **Home** shows "N items waiting to sync" and failed items with **Retry** / **Discard**.
- In-app changes (Budget tab) and Home refreshes also update the widget.
- **Home and widget always agree:** Home renders from the same `WidgetSnapshot` (server data plus pending changes) and reads the shared `AppContainer.dashboard`, so a background sync updates both.
  - Pending items show "(syncing)" in both places, and both say "N waiting to sync".
  - The Budget tab lists queued items as "(syncing)" rows (not editable until saved), counts them in Left, and reloads when the queue empties.
- **Budget tab quick-add works offline:** it tries the server first. If the request never left the phone (no network, DNS failure, connection refused), the item goes to the outbox with SOURCE=APP.
  - Timeouts are not queued, because the server may have saved it; that would risk a duplicate.
  - Edits, deletes and new pages still need a connection.

## Changes from the master plan
- **Outbox is a JSON file, not Room.** It's a short, ordered queue, so a file plus a StateFlow is enough. This avoids adding KSP/Room on top of AGP 9's built-in Kotlin.
- **No server-side dedupe:** if a POST succeeds but the response is lost (e.g. a timeout), the retry could add a duplicate. That's rare; adding a client ID column later would fix it.

## Versions added (7+ days old)
Glance 1.2.0 (Aug 26), WorkManager 2.12.0 (Sep 23).

## Next
- Phase 5: Todos tab, email ingestion (IMAP), morning digest, and the tick job + SmarterASP Schedule Task.
