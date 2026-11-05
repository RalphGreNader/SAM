---
agent: devin-local
session: rainbow-locket
created: 2026-10-31T05:21:01Z
---
# SAM (Sam All-in-one Management): Helios API + native Android app

Add a SAM module to Helios-Engine.Core (todos, email-to-task ingestion from samhub@samvedamanikam.com, morning digest, OneNote-style budget pages, never-expiring device pairing on an isolated auth scheme) driven by a SmarterASP scheduled task, and build "SAM", a sideloaded Kotlin/Compose Android app with tabs and a Glance home-screen widget, in `C:\github\SAM`, without changing auth for any existing app.

## Status
- Phase 1 (setup) done except DKIM.
- **Phase 2 (backend core + device auth): done and deployed (2026-11-02).** DB scripts run, `DeviceAuth:JwtKey` set locally and on the server, regression checks passed. Helios changes are still **uncommitted**.
- **Phase 3 (Android skeleton + Budget): built and tested on the emulator (2026-11-02).** See `phase-3-android.md`.
- **2026-11-02:** pairing is now shared Helios **device auth** (`device-auth.md`): scheme `Device`, table `HELIOS_DEVICE`, routes `api/auth/device/*` with `APP=sam`, policy `app:sam`. The sections below are updated. Older mentions of `SamDevice`/`SAM_DEVICE` elsewhere refer to the same thing.
- **Setup:**
  - [x] SmarterASP Schedule Tasks available (Premium plan).
  - [x] `samhub@samvedamanikam.com` mailbox created.
  - [x] Helios base URL confirmed: `https://samvedamanikam.com/` (site root). `GET /api/Fulham/events` returned 200 over HTTPS. Swagger returns 404, as expected in Production.
  - [x] Android Studio installed (`C:\Program Files\Android\Android Studio`, SDK at `%LOCALAPPDATA%\Android\Sdk`).
  - [x] Workspace folder is `C:\github\SAM` (remote `https://github.com/RalphGreNader/SAM.git`).
  - [ ] **DKIM** (not in the hosting control panel): log in to webmail `https://mail5011.site4now.net` as `postmaster@samvedamanikam.com` → Settings → Domain Settings → General → DKIM **Enable**. Copy the TXT record name/value into Control Panel → DNS → samvedamanikam.com → Add Record (TXT, TTL 3600), then Save in SmarterMail. Only needed before the Phase 5 digest.

## Environment facts (from you / verified)
| Item | Value |
|---|---|
| Helios API base | `https://samvedamanikam.com/` (Helios at the site root) |
| Webmail | `https://mail5011.site4now.net` |
| IMAP (secure) | `mail5011.site4now.net:993` SSL/TLS |
| SMTP (secure) | `mail5011.site4now.net:587` STARTTLS (465 is implicit SSL, which `System.Net.Mail` doesn't support, so use 587) |
| Mailbox login | full address `samhub@samvedamanikam.com` plus its password (secrets file only) |
| SPF | `v=spf1 a mx include:_spf.site4now.net -all` |
| DMARC | `p=reject; pct=100` (strict) |
| Cold start | the first HTTPS request took **~10.7 s** (app pool spin-up), later ones ~0.15 s |
| HTTP | plain `http://` also returns 200 (no redirect), so SAM must enforce HTTPS itself |

## Naming
- **App name:** SAM, short for **Sam All-in-one Management**. The launcher label is "SAM" and the widget label is "SAM".
- **Android package id:** `com.samvedamanikam.sam`. APK file: `SAM-<version>.apk`.
- **Backend naming:**
  - Tables `SAM_*`, routes `api/sam/*`, controllers `Sam*Controller`, models in `Models/Sam/`.
  - Config section `Sam`, SQL script `Database/sam.sql`.
- **Inbox:** `samhub@samvedamanikam.com`.

## Decisions (from you)
- **Build it ourselves** on Helios-Engine.Core (JWT auth, SQL Server + Dapper, SMTP already there).
- **Capture from the phone** (widget quick-add + share target) as well as by email.
- **Inbox:** the SmarterASP mailbox samhub (not Gmail).
- **Scheduling:** SmarterASP **Schedule Tasks**.
- **Work email is send-only.** The digest is sent to it. The work mailbox is never read. You can email tasks *from* any allowlisted address.
- **Never log in on the phone:** pair once, stay signed in forever.
- **Must not break any other app's authentication** (Freelancer-Vue, FulhamTexas admin, any other `/api/token` client).
- Android: **native Kotlin + Jetpack Compose**, sideloaded signed APK.
- Parsing: rules first, one item per line. LLM layer later, behind an interface.
- **v1 scope:** todos + email ingestion + morning digest, budget pages (replaces OneNote), widget.
- **Out of v1:** incoming/outgoing payments (stay in Excel).
- Budget: OneNote-style **budget pages** that you start manually, no rollover, no history import.

## Auth safety: existing apps are untouched
How auth works today (verified in code):
- `TokenController` (`POST api/token`) issues 20-minute JWTs (issuer `HeliosEngine`, audience `HeliosEngineClient`) plus a refresh token stored in `USERS.REFRESH_TOKEN`.
- **Clients:**
  - Freelancer-Vue (`src/utils/axios.js`, `auth.js`, `Login.vue`): password login + refresh-token interceptor.
  - FulhamTexas admin (`src/composables/useAuth.js`): password login + refresh.
- **Protected APIs:** plain `[Authorize]` (default JWT scheme) on `FulhamAdminController`, `FulhamBingoAdminController`, `InvoiceController`, `CustomerController`, `ServicesController`, `UsersController`. `FulhamAdminController` reads the `userdisplayname` claim.
- Passwords are checked by `PasswordService.ValidateUser`.

Rules SAM follows:
1. **Do not modify** `TokenController`, `PasswordService`, the `USERS` table, the existing JWT bearer registration, its default schemes, or the `Jwt` config.
2. **Separate auth scheme for devices (shared Helios device auth):**
   - Program.cs adds a second, *named* JWT bearer scheme `"Device"`. It has its own signing key `DeviceAuth:JwtKey`, issuer `HeliosEngine`, audience **`HeliosDevice`**, and an `app` claim.
   - The defaults stay `JwtBearerDefaults.AuthenticationScheme`, so every existing `[Authorize]` endpoint still validates only the original tokens.
   - **A phone token can't open Fulham admin, invoices, customers, etc.**
3. SAM controllers use `[Authorize(Policy = "app:sam")]`. It accepts a normal Helios login token (handy for testing) or a device token paired for `sam`; a device paired for another app is rejected.
4. **Pairing doesn't touch `USERS.REFRESH_TOKEN`:** `api/auth/device/register` calls only `PasswordService.ValidateUser`, the same call `/api/token` makes. Web sessions are never invalidated by the phone.
5. **Separate rate-limit policies** (`"device-auth"`, `"sam-jobs"`), so the phone never uses up the shared `"login"` quota.
6. **No CORS change needed:** native apps aren't subject to CORS.
7. **`email.cs`:** the existing `sendEmail` and `SendEmailAsync` signatures and behavior stay the same. SAM adds a **new** overload (HTML + custom SMTP section).
8. **Program.cs changes are additive only:** the new scheme, new service registrations, two new rate-limit policies. Nothing existing is reordered or removed.
9. **HTTPS enforcement scoped to SAM:** plain HTTP is currently served. SAM endpoints (device register/token, jobs/tick) reject non-HTTPS requests (`Request.IsHttps`) with 400. This is done with a small action filter on SAM controllers only, **not** a global `UseHttpsRedirection`, so other apps' behavior is unchanged.

Regression checks (see Verification):
- Freelancer-Vue login, refresh and an invoice call.
- FulhamTexas admin login, refresh and a dashboard call.
- A phone token gets **401 on `api/Fulham/admin/*` and `api/Invoice`**.
- A normal login token still works on SAM endpoints.

## Device pairing (never log in)
Goal: type your username/password **once, on first install**, then never again. That includes the widget and background sync.

1. **First launch:** "Pair this phone" screen (server URL pre-filled with `https://samvedamanikam.com/`). The app calls `POST api/auth/device/register` with `{APP: "sam", USERNAME, PASSWORD, DEVICE_NAME}`.
   - The backend validates the password with `PasswordService.ValidateUser` and creates a `HELIOS_DEVICE` row (`APP_CODE = 'sam'`).
   - It returns `{DEVICE_ID, DEVICE_KEY, APP}`. The device key is 256-bit random, returned **once**, and only its SHA-256 hash is stored.
2. The app stores `deviceId + deviceKey` encrypted with an **Android Keystore** key and excluded from backups (`allowBackup=false` plus data-extraction rules).
3. **Every API call:** the JWT is held in memory. On a 401 (or no token yet), an OkHttp `Authenticator` calls `POST api/auth/device/token` with `{DEVICE_ID, DEVICE_KEY}` and gets a fresh `Device` JWT (60 min), then retries.
   - The Android `auth/` package is kept self-contained (no SAM-specific types) so a future app can reuse it or it can be extracted into a shared module.
   - A mutex prevents races between the app, widget and WorkManager.
   - The device key never rotates or expires.
4. **Revoke (lost phone):** `DELETE api/auth/device/devices/{id}` (with a normal login token) or `UPDATE HELIOS_DEVICE SET REVOKED_YN='Y'`. Takes effect immediately. The phone goes back to the pair screen.
   - Settings → "Unpair this phone" does the same from the app.
   - `LAST_SEEN` shows when each device last connected.
5. **Optional app lock:** fingerprint/face unlock on open, off by default. Never a password.

Later: QR pairing.

## Email deliverability (DMARC p=reject)
- Your domain's DMARC policy is **reject**. Any mail claiming `From: samhub@samvedamanikam.com` that fails SPF/DKIM alignment is **bounced**.
- So the digest **must** be sent through `mail5011.site4now.net` authenticated as samhub. That passes SPF (`include:_spf.site4now.net`), aligned with the From domain. It must **never** go through the existing Gmail `Smtp` config with a samhub From address.
- **Enable DKIM** for the domain in SmarterASP. That keeps the digest passing DMARC even if your work mail system rewrites or forwards it, where SPF alone can break.
- Allowlisted senders that email *into* samhub (personal and work addresses) are unaffected; that's inbound mail.

## SmarterASP Schedule Task: settings to enter
From your screenshot, the form has URL (with an http/https dropdown), **Timeout in seconds** (default 20), **Frequency in minutes** (default 30), and task type.

| Field | Value |
|---|---|
| Protocol | **https://** |
| URL | `samvedamanikam.com/api/sam/jobs/tick?key=<Sam:JobKey>` |
| Timeout in seconds | **120**, or the highest allowed. The cold start alone was ~10.7 s, so the default 20 s is too tight |
| Frequency in minutes | **15** |
| Task Type | **Schedule Task** (not Dedicated Windows Task, which is a paid add-on) |

Tick design, sized for that timeout:
- Each tick:
  1. **Ingests** up to `Sam:MaxMessagesPerTick` (25) new emails. Any remainder waits for the next tick.
  2. **Sends the digest if due:** after `Sam:DigestTime` (06:30 Central) and not yet sent today (`SAM_JOB_RUN`). It arrives between 6:30 and 6:45.
- An internal **time budget** (`Sam:TickBudgetSeconds`, 45) stops work early and returns 200 with counts, so the scheduler never times out mid-run. A per-run lock (a `SAM_JOB_RUN` row update with a lease) prevents overlapping ticks.
- The key is in the query string: long random value, constant-time compare, rate limited, HTTPS only. Idempotent; returns only counts.
- **Bonus:** the 15-minute pings keep the app pool warm, so the SAM app and widget avoid the ~10 s cold start most of the time.
- **Manual triggers:** `POST jobs/ingest` and `POST jobs/digest?force=true` (authorized).
- **Fallback:** cron-job.org or a GitHub Actions cron calling the same URL.

## Budget model (based on your OneNote page)
- A **budget page** has a start amount (e.g. $800), a start date and a title (e.g. "Budget: Nov 1"). You start a new page manually, normally every 2 weeks. Starting one closes the previous page (END_DATE = the day before). Every page starts fresh. The default amount is the last page's amount.
- **Items** on a page are amount, description (e.g. "heb", "tacos charlie") and category (e.g. Groceries). Date defaults to today.
- **Running balance** is start amount minus the sum of items. It can go negative and shows red.
- **Merchant memory:** "heb" pre-fills the category from the last item with the same description. Suggestions appear as you type.
- **Quick entry:** `60 gym` / `$17 tacos charlie` → amount + description, with the category from merchant memory. The same parser is used in the app, the widget and email (`$` prefix).
- **Reports:**
  - Per page: spent, left, category breakdown.
  - **Monthly/yearly:** spending by category, trends, year-to-date. Uses each item's **date**, not its page.
- Default categories (editable): Groceries, Dining, Gas, Entertainment, Gym/Health, Shopping, Coffee, Household, Other.

## Architecture

```
 you (any allowed address) ──email──▶ samhub@samvedamanikam.com (mail5011.site4now.net)
                                                  ▲ IMAP 993
 SmarterASP Schedule Task (https GET every 15 min, timeout 120 s)
        └──▶ https://samvedamanikam.com/api/sam/jobs/tick ──┬─ ingest mail → SAM_* tables
                                                            └─ 06:30 CT digest (once/day) ──SMTP 587 (samhub)──▶ personal + work email (send-only)
 SAM Android app (Compose + Room cache) ──device key → SamDevice JWT──▶ https://samvedamanikam.com/api/sam/* ──▶ SQL Server
   └─ SAM Glance widget (WorkManager refresh)

 Existing apps (Freelancer-Vue, FulhamTexas) ──/api/token (unchanged)──▶ existing controllers (unchanged)
```

## Part A: Backend (Helios-Engine.Core)

Follow existing conventions: Dapper with `SqlConnection(_util.connection())`, UPPERCASE column names in models, secrets in `appsettings.Secrets.json`, and SQL scripts in `Database/` documented in the README.

### A1. Schema: `Database/sam.sql` (new; only creates new SAM_* objects)
- `SAM_CATEGORY` (CATEGORY_ID, NAME, ICON, COLOR, SORT_ORDER, ACTIVE_YN), with seeds.
- `SAM_TODO` (TODO_ID, TITLE, NOTES, DUE_DATE null, PRIORITY, STATUS 'OPEN'|'DONE', SOURCE 'EMAIL'|'APP'|'WIDGET'|'SHARE', SOURCE_MSG_ID, COMPLETED_DATE, CREATE_DATE, UPDATE_DATE)
- `SAM_INBOX_MESSAGE` (MSG_ID, MESSAGE_ID unique, FROM_ADDR, SUBJECT, BODY, RECEIVED_DATE, ITEMS_CREATED, STATUS)
- `SAM_BUDGET_PAGE` (PAGE_ID, TITLE, START_DATE, END_DATE null while active, AMOUNT, NOTES, CREATE_DATE). Filtered unique index on `END_DATE IS NULL`.
- `SAM_EXPENSE` (EXPENSE_ID, PAGE_ID FK, CATEGORY_ID FK, AMOUNT, DESCRIPTION, SPENT_DATE, SOURCE, CREATE_DATE, UPDATE_DATE). Index on SPENT_DATE.
- `SAM_JOB_RUN` (JOB_NAME PK, LAST_RUN, LAST_DIGEST_DATE, LAST_RESULT, LOCKED_UNTIL)
- `SAM_DEVICE` (DEVICE_ID uniqueidentifier PK, USER_NAME, DEVICE_NAME, KEY_HASH, CREATE_DATE, LAST_SEEN, REVOKED_YN)

### A2. Models: `Models/Sam/*.cs` (new)
POCOs plus request DTOs, same pattern as `Models/Fulham/FulhamBingo.cs`.

### A3. Controllers (all new; `[Authorize(AuthenticationSchemes = "Bearer,SamDevice")]` plus the `[SamRequireHttps]` filter unless noted)
- `SamDeviceController`:
  - `POST api/sam/device/register` is `[AllowAnonymous]` with the `"sam-device"` rate limit.
  - `POST api/sam/device/token` is `[AllowAnonymous]` with the `"sam-device"` rate limit.
  - `GET api/sam/devices`, `DELETE api/sam/devices/{id}`, `DELETE api/sam/devices/current`.
- `SamTodoController` at `api/sam/todos`: list (filter status/due), create, update, complete/reopen, delete.
- `SamBudgetController` at `api/sam/budget`:
  - `GET pages`, `GET pages/current`, `GET pages/{id}`
  - `POST pages` (closes the active page in a transaction), `PUT/DELETE pages/{id}`
  - `POST/PUT/DELETE expenses`
  - `GET merchants?q=`
  - `GET reports/monthly?year=`, `GET reports/yearly`, `GET reports/range?from&to`
- `SamCategoryController` at `api/sam/categories`.
- `SamDashboardController` at `GET api/sam/dashboard`: Home tab and widget payload.
- `SamJobsController` at `api/sam/jobs`:
  - `GET tick?key=` is `[AllowAnonymous]` with the `"sam-jobs"` rate limit.
  - `POST ingest` and `POST digest?force=` are authorized.

### A4. Services and filters: `Common/` (all new)
- `SamRequireHttpsAttribute`: action filter that returns 400 when `!Request.IsHttps`. Used on SAM controllers only.
- `SamDeviceTokenService`: creates `SamDevice` JWTs (key `Sam:DeviceJwtKey`, audience `SamDevice`, claims `sub`, `userdisplayname`, `device_id`, `scope=sam`). It also hashes and verifies device keys with a constant-time compare.
- `SamInboxService`: IMAP via **MailKit** (new NuGet dependency; pin a version published 7+ days ago) to `mail5011.site4now.net:993` SSL.
  - Reads unread mail (max per tick), enforces `Sam:AllowedSenders`, dedupes on Message-ID, parses, inserts.
  - Then marks messages read and moves them to "Processed" (the folder is created if missing).
- `ITaskParser` / `RuleTaskParser` (pure, unit-tested):
  - One item per line. Parsing stops at signatures (`--`, `Sent from`, `Get Outlook`, corporate disclaimers) and skips quoted replies.
  - Default: line → todo. An empty body makes the subject a todo.
  - `$17 tacos charlie` / `spent: …` → expense on the current page (merchant memory → keyword rules → "Other"). `!` → high priority.
  - Date hints: `today`, `tomorrow`, weekday names, `M/D`, `by M/D`, stripped from the title.
  - Later: an `LlmTaskParser` behind the same interface.
- `SamDigestService`: HTML email ("SAM: your day, Mon Nov 2") with overdue, due today and undated todos, plus budget left. Sent to `Sam:DigestRecipients` **from samhub via `Sam:Smtp`** (mail5011.site4now.net:587), using a **new** overload in `email.cs`.
- `SamJobsService`: tick logic (lease lock, ingest with a time budget, digest-if-due in the Central time zone).

### A5. Config (additive)
- `appsettings.json` new `Sam` section (non-secret):
  ```json
  "Sam": {
    "Imap": { "Host": "mail5011.site4now.net", "Port": 993, "UseSsl": true },
    "Smtp": { "Host": "mail5011.site4now.net", "Port": 587, "EnableSsl": true, "From": "samhub@samvedamanikam.com" },
    "TimeZone": "Central Standard Time",
    "DigestTime": "06:30",
    "DigestRecipients": [],
    "AllowedSenders": [],
    "MaxMessagesPerTick": 25,
    "TickBudgetSeconds": 45,
    "DeviceTokenMinutes": 60,
    "DeviceAudience": "SamDevice"
  }
  ```
  You fill in `DigestRecipients` (personal + work) and `AllowedSenders` at implementation time.
- `appsettings.Secrets.example.json` additions: `Sam:Imap:User`, `Sam:Imap:Password`, `Sam:Smtp:User`, `Sam:Smtp:Password` (all `samhub@samvedamanikam.com` and its password), `Sam:JobKey`, `Sam:DeviceJwtKey` (32+ chars, different from `Jwt:Key`). Real values go only in `appsettings.Secrets.json` (git-ignored) on your machine and the server.
- `Program.cs` (additive only):
  - `.AddJwtBearer("SamDevice", …)` chained after the existing bearer.
  - `"sam-device"` and `"sam-jobs"` rate-limit policies.
  - SAM service registrations.
- README: a "SAM" section (script to run, endpoints table, config, schedule task settings, DKIM note).

### A6. Scheduling
Add the Schedule Task with the settings in the table above, after deploying phase 5.

## Part B: SAM Android app (`C:\github\SAM`)

### B1. Stack
Kotlin, Jetpack Compose with Material 3 (dark theme by default), Navigation-Compose, Hilt, and these libraries:
- Retrofit + OkHttp + kotlinx.serialization
- Room for the offline cache plus an outbox queue
- WorkManager for sync and widget refresh
- Jetpack Glance for the widget
- Vico for charts
- DataStore with Android Keystore encryption for the device credentials
- androidx.biometric for the optional app lock

minSdk 26.
- Base URL `https://samvedamanikam.com/`, set as a `BuildConfig` field and editable in Settings.
- Network security config: cleartext disabled.
- OkHttp timeouts: 30 s for the occasional cold start. The UI shows cached data while it loads.

### B2. Structure
```
app/src/main/java/com/samvedamanikam/sam/
  data/api   data/db   data/repo        auth/ (DeviceCredentialStore, DeviceAuthenticator, AuthInterceptor)
  parser/ (Kotlin port of quick-entry rules)
  ui/theme   ui/pair   ui/home   ui/todos   ui/budget   ui/settings
  widget/ (SamWidget, receiver, QuickAddActivity)   share/ (ShareReceiverActivity)
```

### B3. Auth on the phone
- **First run only:** the pair screen (server URL pre-filled, username, password, device name). The password is discarded after pairing.
- **Every other launch** goes straight to Home. The JWT is fetched silently with the device key (app, widget, WorkManager).
- If the device was revoked, the app goes back to the pair screen.
- **Settings:** "Unpair this phone", optional "Require fingerprint to open" (off by default).

### B4. Branding
- App label "SAM". A monogram adaptive launcher icon on a dark background, with a matching accent color.
- The splash and pair screens show "SAM · Sam All-in-one Management".

### B5. Tabs (bottom navigation)
1. **Home**: cards for budget left (big number and progress bar, red when negative), due today, overdue.
2. **Todos**: grouped list (Overdue / Today / Upcoming / No date / Done). Swipe to complete, FAB to add, date chips.
3. **Budget**: three sub-tabs.
   - **Current page**, laid out like your OneNote page:
     ```
     Budget: Nov 1                     $800.00
     ───────────────────────────────────────────
     $60   gym              Gym/Health   11/01
     $38   sushi            Dining       11/02
     ...
     ───────────────────────────────────────────
     Left                              $-35.00
     ```
     The quick-entry field takes `17 tacos charlie`, with category chips pre-selected. Tap to edit, swipe to delete. **"Start new page"** asks for the amount (pre-filled) and the start date.
   - **History**: past pages (dates, budget, spent, over/under). Tap one for a read-only view plus a category pie chart.
   - **Reports**: Month/Year toggle, category breakdown, per-category trend, year-to-date totals.
4. **Settings**: server URL, unpair, app lock toggle, categories editor, "Check inbox now", "Send digest now".

The bottom navigation leaves room for a **Payments** tab later.

### B6. Widget and quick look
- Resizable "SAM" Glance widget:
  - **Small:** budget left and the number of todos due today.
  - **Medium:** adds a spent/total bar and the last 2 expenses.
  - **Large:** adds the top 3 todos with tap-to-complete.
- **+ Expense** and **+ Task** open a translucent `QuickAddActivity` dialog over the home screen. Saves go to the Room outbox first, so it's instant even on a cold server.
- Refreshes via WorkManager every 30 min and immediately after any change. Shows cached data offline.
- Share target: shared text lands as todos.

### B7. Build and install
- Signed release APK (`SAM-<version>.apk`) via a Gradle `signingConfig`. The keystore lives outside the repo (git-ignored `keystore.properties`).
- Updates install over the old version (same signing key), so the pairing survives updates. Uninstalling or clearing data requires pairing again.
- Install via file copy or `adb install`, with "install unknown apps" allowed.

### B8. Machine setup needed
Only Java 8 is installed and there's no Android SDK. **Android Studio** is being installed now; it bundles JDK 17+, the SDK and the emulator. git isn't on PATH; use `C:\Users\sanja\AppData\Local\GitHubDesktop\app-3.6.10\resources\app\git\cmd\git.exe` (the version folder changes when GitHub Desktop updates).

## Phases
1. **Setup:** mostly done (see Status). Remaining: Android Studio, DKIM check.
2. **Backend core + device pairing**: A1–A3 (minus jobs), A4 HTTPS filter + device token service, A5. Run the auth regression checks.
3. **SAM skeleton + Budget**: project, branding, pairing, Home, Budget (current page, history, reports).
4. **Widget + quick-add**, plus the share target.
5. **Todos tab, email ingestion, digest and the tick job** (remaining A4, jobs controller), then add the Schedule Task (A6).
6. *(Later)* Payments in/out (replace the Excel), LLM parser, recurring todos, notifications, QR pairing.

## Verification
- Backend: `dotnet build`. Exercise endpoints via Swagger (local) and `Helios-Engine.Core.http` against a dev DB after running `Database/sam.sql`.
- **Auth regression (must pass before deploying):**
  - `git diff` shows no changes to `TokenController.cs`, `PasswordService.cs`, the existing `AddJwtBearer` block, or existing `email.cs` methods.
  - `POST /api/token` password and refresh grants return the same response shape.
  - Freelancer-Vue: log in, load invoices, force a 401 and confirm the refresh works.
  - FulhamTexas admin: log in, load the dashboard, refresh works, change-password still works.
  - A phone (`SamDevice`) token → **401** on `api/Fulham/admin/*`, `api/Invoice`, `api/Customer`.
  - A normal `/api/token` JWT → 200 on `api/sam/*`.
  - Pairing does not change `USERS.REFRESH_TOKEN` (before/after SQL check).
  - `http://` request to `api/sam/device/token` → 400. Existing endpoints over `http://` behave as before.
- New `Helios-Engine.Core.Tests` xUnit project:
  - `RuleTaskParser`: amounts, merchants, dates, prefixes, signatures, replies, disclaimers.
  - Budget logic: new page closes the previous one, negative balances, cross-month totals.
  - Digest-due logic: before/after 6:30, no resend the same day, DST boundaries.
  - Device auth: wrong key → 401, revoked → 401, valid key → JWT, key stored only as a hash.
- Jobs:
  - Send test emails to samhub (allowed/disallowed senders, duplicates, 30+ messages to test the per-tick cap).
  - `tick` with a wrong key → 401, right key → counts.
  - Run a cold-start tick and confirm it finishes well under the 120 s timeout.
  - The digest arrives in personal and work inboxes (check spam/quarantine), and the headers show `dmarc=pass`.
- Android:
  - `./gradlew lint testDebugUnitTest assembleRelease`, plus Kotlin parser tests.
  - Real phone checks: pair once; reboot and confirm no login; leave idle for more than 1 hour and confirm the widget still refreshes; revoke and confirm it returns to the pair screen; offline add then sync; new page; reports match the backend; widget quick-add during a cold start.

## Risks / considerations
- **Never-expiring device key:** anyone holding the unlocked phone can open SAM. Mitigations: phone screen lock, optional fingerprint lock, a Keystore-encrypted key excluded from backups, server-side revoke. Phone tokens are confined to `api/sam/*`.
- **DMARC p=reject:** a misconfigured From/SMTP pairing makes digests bounce silently. Always send via samhub on site4now, and enable DKIM.
- **Cold starts (~10 s):** mitigated by the 15-minute pings, the 120 s scheduler timeout, the tick time budget, and app-side caching/outbox.
- The job key is in the query string, so it may appear in IIS logs. Mitigations: HTTPS, a long random key, rotate if leaked, no data returned, rate limited.
- Work email may quarantine the digest. Your own domain with SPF/DKIM helps. The work mailbox is never read.
- The quick-entry parser exists in C# and Kotlin, with shared test cases.
- Kotlin is new for you. The code will follow standard Android architecture so it stays maintainable.
- Adds the MailKit dependency to Helios.
