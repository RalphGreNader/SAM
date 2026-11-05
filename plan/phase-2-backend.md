---
agent: devin-local
session: accidental-jute
created: 2026-10-31T14:29:53Z
---
# SAM Phase 2: Helios backend core + device pairing

Save the SAM master plan into `C:\github\SAM\plan\`, finish the last setup item (DKIM), then build the SAM backend core in Helios-Engine.Core (schema, models, device pairing on an isolated `SamDevice` JWT scheme, todos/budget/categories/dashboard APIs, config, and an xUnit test project) without changing any existing app's authentication.

## Status (2026-11-02): code done, not yet committed or deployed

> **Superseded (2026-11-02):** phone pairing was generalized into shared Helios **device auth**, so other native apps can reuse it. See `device-auth.md`.
> Wherever the text below mentions `SamDevice`, `SAM_DEVICE`, `api/sam/device*`, `Sam:DeviceJwtKey`, `SamDeviceTokenService`, `SamRequireHttps` or `sam-device`, the current names are:
> - Scheme `Device`, table `HELIOS_DEVICE` (with `APP_CODE`).
> - Routes `api/auth/device/*` (register takes `"APP": "sam"`).
> - Config `DeviceAuth:JwtKey` / `DeviceAuth:*`.
> - Classes `DeviceAuthService` and `[HttpsOnly]`.
> - Rate limit `device-auth`.
> - SAM controllers use policy `app:sam`.

Done:
- All code steps (1–6) implemented in `C:\github\Helios-Engine` (uncommitted), then generalized to device auth.
- `dotnet build` passes and `dotnet test` passes (43 tests).
- `git diff` is additive only (163 insertions, 0 deletions). `TokenController.cs`, `PasswordService.cs` and `email.cs` are unchanged.
- Local smoke test (no key configured):
  - `api/sam/dashboard` without a token → 401.
  - `device/token` → 503 "not configured", and the app still starts with a warning.
  - Fulham admin → 401, and `/api/token` behaves as before.

Changes from the plan below:
- `SamCategoryResolver` is a static helper, not a DI service.
- Pure helpers (`SamQuickEntry`, `SamBudgetRules`, plus `Today`/source normalization) live in `Common/SamCommon.cs`.
- Controllers share `Controllers/SamControllerBase.cs`, which carries the auth schemes and `[SamRequireHttps]`.
- **Revocation is immediate:** the SamDevice scheme checks `SAM_DEVICE.REVOKED_YN` on every request (`OnTokenValidated`), instead of waiting up to 60 minutes for the JWT to expire.
- The SamDevice scheme is registered with a separate `AddAuthentication().AddJwtBearer("SamDevice", …)` call, so the existing block is byte-identical.
- Missing/weak `Sam:DeviceJwtKey` → warning plus 503 on pairing only (the safe fallback).
- Deleting the active budget page re-opens the previous page (undo "start new page").
- `SamRequireHttps` is skipped in the Development environment so `http://localhost:5297` works.

Next (you):
1. Back up the production DB, then in SSMS run `Helios-Engine.Core/Database/device_auth.sql` **then** `Database/sam.sql`.
   - Check: `HELIOS_DEVICE` exists, 6 `SAM_*` tables exist, and `SAM_CATEGORY` has 9 rows.
2. Generate a device JWT key and add it as `"DeviceAuth": { "JwtKey": "..." }` in your local **and** server `appsettings.Secrets.json`:
   `[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))`
3. Run locally (`dotnet run`) and walk through the "Device auth" and SAM sections of `Helios-Engine.Core.http`: register with `APP=sam`, token, isolation 401, budget and todos. Then work through the Verification list below.
4. Commit Helios, deploy, and do the post-deploy checks:
   - Freelancer-Vue and FulhamTexas admin still work.
   - `http://` → 400 on `api/auth/device/*` and `api/sam/*`.
5. DKIM (Step 0b).
6. Then Phase 3: the Android skeleton + Budget in `C:\github\SAM`.

## Context
- Master plan: `plan/SAM-plan.md` (all decisions, auth rules, budget model, Android design).
- Phase 1 status (verified on disk):
  - Android Studio is installed (`C:\Program Files\Android\Android Studio`). The SDK is at `%LOCALAPPDATA%\Android\Sdk` with platform-tools, build-tools, platforms, an emulator and system images.
  - `C:\github\SAM` exists and contains only `.git` (remote `https://github.com/RalphGreNader/SAM.git`, branch `main`, no commits yet).
  - Helios repo `C:\github\Helios-Engine` is clean on `master` (last commit `c523e2f fulhamTexas bingo backend code`). No SAM code exists yet.
  - Tooling: .NET SDK 9.0.315, with the ASP.NET Core 8.0.28 runtime (the Core project targets net8.0). git is at `%LOCALAPPDATA%\GitHubDesktop\app-3.6.10\resources\app\git\cmd\git.exe`. The app-3.6.3 path in the old plan is stale.
- Your decisions for this phase:
  - Run Phase 2 now.
  - Plan folder: `C:\github\SAM\plan`.
  - Run `sam.sql` on the **production** SmarterASP DB and test locally against it.
  - Add the xUnit test project now.

## Step 0: Save the plan into the repo
- Create `C:\github\SAM\plan\` containing:
  - `SAM-plan.md`: the master plan, with Status updated (Android Studio done, folder renamed, DKIM how-to, corrected git path).
  - `phase-2-backend.md`: this plan.
- No commit unless you ask. It would be the SAM repo's first commit on `main`.

## Step 0b: DKIM (you; there's no setting for it in the hosting control panel)
SmarterASP enables DKIM inside **SmarterMail webmail**, not in the hosting control panel (KB a2225):
1. Log in to `https://mail5011.site4now.net` as the domain admin, **`postmaster@samvedamanikam.com`**. The postmaster password is under Control Panel → Email → Manage for the domain. Reset it there if unknown.
2. Go to **Settings → Domain Settings → General**. Find **DKIM** and click **Enable**.
3. A popup shows a **Text Record Name** and **Text Record Value**. In the hosting Control Panel → **DNS** → samvedamanikam.com → **Add Record**, set type **TXT**, name and value from the popup, TTL 3600. This assumes the domain uses SmarterASP nameservers; otherwise add it at your DNS provider.
4. Back in SmarterMail, click Save. It checks DNS and turns DKIM on, which may take a while to propagate.
5. Verify later with a digest test email; the headers should show `dkim=pass` and `dmarc=pass`.

This doesn't block Phase 2. It only matters for the digest in Phase 5.

## Step 1: Schema `Helios-Engine.Core/Database/sam.sql` (new, idempotent, creates only SAM_* objects)
Same style as `fulham_bingo.sql`: `IF OBJECT_ID(...) IS NULL`, named constraints, `GO` separators, header comment.
- `SAM_CATEGORY`: `CATEGORY_ID int identity PK`, `NAME nvarchar(50) unique`, `ICON varchar(30)`, `COLOR varchar(9)`, `SORT_ORDER int`, `ACTIVE_YN varchar(1) default 'Y'`. Seed rows inserted only if missing: Groceries, Dining, Gas, Entertainment, Gym/Health, Shopping, Coffee, Household, Other.
- `SAM_TODO`:
  - `TODO_ID`, `TITLE nvarchar(300)`, `NOTES nvarchar(max)`, `DUE_DATE date null`, `PRIORITY tinyint default 0`
  - `STATUS varchar(10)` with CHECK OPEN/DONE
  - `SOURCE varchar(10)` with CHECK EMAIL/APP/WIDGET/SHARE, `SOURCE_MSG_ID int null`
  - `COMPLETED_DATE`, `CREATE_DATE`, `UPDATE_DATE`
  - Index on `(STATUS, DUE_DATE)`.
- `SAM_INBOX_MESSAGE`: created now so Phase 5 needs no schema change. Columns `MSG_ID`, `MESSAGE_ID nvarchar(400)` (unique), `FROM_ADDR`, `SUBJECT`, `BODY`, `RECEIVED_DATE`, `ITEMS_CREATED`, `STATUS`.
- `SAM_BUDGET_PAGE`:
  - `PAGE_ID`, `TITLE nvarchar(100)`, `START_DATE date`, `END_DATE date null`, `AMOUNT decimal(10,2)`, `NOTES`, `CREATE_DATE`
  - Filtered unique index `UX_SAM_BUDGET_PAGE_ACTIVE ON (END_DATE) WHERE END_DATE IS NULL`, so at most one page is active.
- `SAM_EXPENSE`:
  - `EXPENSE_ID`, `PAGE_ID`: FK to SAM_BUDGET_PAGE with `ON DELETE CASCADE`.
  - `CATEGORY_ID`: FK to SAM_CATEGORY.
  - `AMOUNT decimal(10,2)`, `DESCRIPTION nvarchar(200)`, `SPENT_DATE date`, `SOURCE`, `CREATE_DATE`, `UPDATE_DATE`
  - Indexes on `SPENT_DATE`, `PAGE_ID` and `DESCRIPTION` (for merchant memory).
- `SAM_JOB_RUN`: `JOB_NAME varchar(50) PK`, `LAST_RUN`, `LAST_DIGEST_DATE date`, `LAST_RESULT nvarchar(500)`, `LOCKED_UNTIL`. Seeded with row `'tick'`.
- `SAM_DEVICE`:
  - `DEVICE_ID uniqueidentifier PK default NEWID()`, `USER_NAME nvarchar(50)`, `DEVICE_NAME nvarchar(100)`
  - `KEY_HASH varbinary(32)` (SHA-256)
  - `CREATE_DATE`, `LAST_SEEN`, `REVOKED_YN varchar(1) default 'N'`

## Step 2: Models `Helios-Engine.Core/Models/Sam/Sam.cs` (new)
Same pattern as `Models/Fulham/FulhamBingo.cs`:
- Namespace `Helios_Engine.Core.Models`, UPPERCASE properties, DataAnnotations on requests.
- POCOs: `SamCategory`, `SamTodo`, `SamBudgetPage` (plus computed `SPENT`, `LEFT`, `ITEM_COUNT`), `SamExpense` (plus joined `CATEGORY_NAME`), `SamDevice` (no hash in API output), `SamCategoryTotal`, `SamMonthTotal`, `SamDashboard`.
- Requests:
  - Device: `SamDeviceRegisterRequest {USERNAME, PASSWORD, DEVICE_NAME}` → `SamDeviceRegisterResponse {DEVICE_ID, DEVICE_KEY}`. `SamDeviceTokenRequest {DEVICE_ID, DEVICE_KEY}` → `SamDeviceTokenResponse {access_token, token_type, expires_in}`.
  - Todos: `SamTodoSaveRequest`.
  - Budget: `SamPageCreateRequest {TITLE?, AMOUNT?, START_DATE?}`, `SamPageUpdateRequest`, `SamExpenseSaveRequest {PAGE_ID?, CATEGORY_ID?, AMOUNT, DESCRIPTION, SPENT_DATE?}`, `SamQuickEntryRequest {TEXT}`.
  - Categories: `SamCategorySaveRequest`.

## Step 3: Common services and filter (new files in `Common/`)
- `SamRequireHttpsAttribute` (`ActionFilterAttribute`):
  - Returns `400 {message:"HTTPS required"}` when `!context.HttpContext.Request.IsHttps`.
  - Bypassed when `Environment.IsDevelopment()`, so local `http://localhost:5297` testing still works.
  - Applied to SAM controllers only.
- `SamDeviceTokenService`:
  - `NewDeviceKey()`: 32 random bytes as base64url.
  - `HashKey(key)`: SHA-256.
  - `Verify(key, hash)`: `CryptographicOperations.FixedTimeEquals`.
  - `CreateToken(userName, deviceId)`: signed with `Sam:DeviceJwtKey`; issuer `Jwt:Issuer`, audience `Sam:DeviceAudience`; claims `sub`, `userdisplayname`, `device_id`, `scope=sam`, `jti`; lifetime `Sam:DeviceTokenMinutes`.
- `SamQuickEntry` (pure static, used by the budget quick-add now and the email parser in Phase 5):
  - Parses `60 gym`, `$17 tacos charlie` and `17.50 heb` → `(amount, description)`.
  - Returns null for invalid input (no amount, zero or negative, no description).
- `SamBudgetRules` (pure static, for unit tests):
  - `DefaultTitle(startDate)` → "Budget: Nov 1".
  - `PreviousEndDate(newStart)` → `newStart - 1 day`, never before the old page's START_DATE (otherwise 400).
  - `Left(amount, spent)`.
- `SamCategoryResolver`: description → category. Uses merchant memory first (the latest `SAM_EXPENSE` with the same description, case-insensitive), then a small keyword map (e.g. heb/walmart → Groceries, shell/exxon → Gas), then "Other".

## Step 4: Controllers (all new; `[Route("api/sam/...")]`, `[ApiController]`, `[Authorize(AuthenticationSchemes = "Bearer,SamDevice")]`, `[SamRequireHttps]`)
Dapper with `new SqlConnection(_util.connection())`, the same as existing controllers.
- `SamDeviceController` (`api/sam`):
  - `POST device/register`: `[AllowAnonymous]`, `[EnableRateLimiting("sam-device")]`.
    1. Calls `PasswordService.ValidateUser` and returns 401 if it fails.
    2. Inserts `SAM_DEVICE` with the key hash.
    3. Returns the id and key **once**.
    4. Never touches `USERS.REFRESH_TOKEN`.
  - `POST device/token`: `[AllowAnonymous]`, `"sam-device"` rate limit.
    1. Looks up the device. Returns 401 for unknown/revoked devices or a key mismatch, with no detail.
    2. Updates `LAST_SEEN` and returns a JWT.
  - `GET devices`: list without hashes.
  - `DELETE devices/{id}`: revoke (`REVOKED_YN='Y'`).
  - `DELETE devices/current`: revoke the caller's `device_id` claim.
- `SamTodoController` (`api/sam/todos`):
  - `GET ?status=OPEN|DONE|ALL&due=overdue|today|upcoming|none`, ordered by due date.
  - `POST`, `PUT {id}`, `PUT {id}/complete`, `PUT {id}/reopen`, `DELETE {id}`.
  - "Today" is computed in `Sam:TimeZone` (Central).
- `SamBudgetController` (`api/sam/budget`):
  - Pages:
    - `GET pages`: history with spent/left.
    - `GET pages/current`: active page + items + left + category breakdown; 404 when there's no page.
    - `GET pages/{id}`.
    - `POST pages`: in a transaction, closes the active page (`END_DATE = start - 1`), then inserts the new page. AMOUNT defaults to the last page's amount, TITLE to `DefaultTitle`.
    - `PUT pages/{id}`, `DELETE pages/{id}` (cascades to its items).
  - Expenses:
    - `POST expenses`: PAGE_ID defaults to the active page (400 if there's none), category from the resolver when omitted, SPENT_DATE defaults to today in Central time.
    - `POST expenses/quick {TEXT}`: `SamQuickEntry` + resolver, so widget/app/email all share the server-side parse.
    - `PUT expenses/{id}`, `DELETE expenses/{id}`.
  - `GET merchants?q=`: distinct recent descriptions + last category, top 10.
  - Reports, all grouped by `SPENT_DATE`:
    - `GET reports/monthly?year=`: month × category totals.
    - `GET reports/yearly`: year × category totals.
    - `GET reports/range?from=&to=`: category totals.
- `SamCategoryController` (`api/sam/categories`): `GET` (active, by sort order), `POST`, `PUT {id}`, `DELETE {id}`. Delete is a soft delete (`ACTIVE_YN='N'`) so old expenses keep their category.
- `SamDashboardController` (`GET api/sam/dashboard`): Home tab and widget payload.
  - Current page title/amount/spent/left and the last 2 expenses.
  - Open todo counts (overdue, today), the top 3 todos, and the server date.

The jobs controller, IMAP, digest and MailKit wait until Phase 5.

## Step 5: Config and Program.cs (additive only)
- `appsettings.json`: add the `Sam` section from the master plan (A5) with `DigestRecipients`/`AllowedSenders` empty (filled in Phase 5).
- `appsettings.Secrets.example.json`: add `"Sam": { "DeviceJwtKey", "JobKey", "Imap": {User, Password}, "Smtp": {User, Password} }` placeholders.
  - For now you add only `Sam:DeviceJwtKey` to your real `appsettings.Secrets.json`: 32+ random characters, different from `Jwt:Key`. I'll give you a PowerShell one-liner to generate it; the value never goes into tracked files.
- `Program.cs`:
  - Chain `.AddJwtBearer("SamDevice", o => …)` after the existing `.AddJwtBearer(...)`, with its own key, issuer `Jwt:Issuer` and audience `Sam:DeviceAudience`.
  - Fail fast at startup if `Sam:DeviceJwtKey` is missing or shorter than 32 characters. Open question below.
  - Add `"sam-device"` (10 per 5 min per IP) and `"sam-jobs"` (10 per min per IP) rate-limit policies inside the existing `AddRateLimiter`.
  - Register `SamDeviceTokenService` and `SamCategoryResolver`.
  - Nothing else is changed, moved or removed. Default schemes stay `JwtBearerDefaults.AuthenticationScheme`, so every existing `[Authorize]` still accepts only original tokens.
- `Helios-Engine.Core.http`: add a "SAM" section (register, token, todos, budget, dashboard) using a `@samToken` variable.
- `README.md`: a "SAM" section (run `Database/sam.sql`, endpoints table, `Sam` config, auth isolation note).

## Step 6: Test project `Helios-Engine/Helios-Engine.Core.Tests/` (new, a sibling of Core, so the web project's globbing doesn't compile it)
- xUnit, net8.0, `ProjectReference` to `../Helios-Engine.Core/Helios-Engine.Core.csproj`, packages pinned to versions published more than 7 days ago. The old `.sln` only contains the legacy project, so tests run with `dotnet test` on the csproj.
- Tests (pure, no DB):
  - `SamQuickEntryTests`: `60 gym`, `$17 tacos charlie`, `17.50 heb`, `$ 5 coffee`; rejects `gym`, `0 gym`, `-5 x`, `60`, empty.
  - `SamBudgetRulesTests`: default title, previous end date, rejects a start before the old start, negative left.
  - `SamDeviceTokenServiceTests`:
    - Keys are unique and 256-bit; hash verify true/false; the hash is not the key.
    - The token validates with SamDevice parameters, **fails** with the original `Jwt` parameters (isolation proof), and carries `device_id`/`scope`.
  - `SamCategoryResolverTests` (keyword fallback part, with no DB): heb → Groceries, unknown → Other.

## Step 7: Database (you, with my script)
1. Back up the production DB (SmarterASP Control Panel → Databases → Backup).
2. Run `Database/sam.sql` in SSMS against production. It only creates `SAM_*` tables and seeds; it's idempotent.
3. Check: `SELECT name FROM sys.tables WHERE name LIKE 'SAM[_]%'` returns 7 rows, and `SAM_CATEGORY` has 9 rows.

## Verification
- `dotnet build` the Core project and `dotnet test` the tests project, both green.
- `git diff --stat` shows **no** changes to `TokenController.cs`, `PasswordService.cs` or `email.cs`. The `Program.cs` diff only adds lines; the existing `AddJwtBearer` block and defaults are byte-identical.
- Local run (`dotnet run`, Development, against the production DB) using `Helios-Engine.Core.http`:
  1. Note `USERS.REFRESH_TOKEN` for your user.
  2. `POST api/sam/device/register` → id + key. Confirm `REFRESH_TOKEN` is unchanged.
  3. `POST api/sam/device/token` → JWT. A wrong key gets 401. After revoking, it gets 401.
  4. The SamDevice JWT gets **401** on `api/Fulham/admin/dashboard`, `api/Invoice`, `api/Customer`.
  5. A normal `/api/token` JWT gets 200 on `api/sam/dashboard` and `api/sam/todos`.
  6. `POST api/token` password and refresh grants return the same shape as before.
  7. Budget flow: create page $800, quick-add `60 gym` and `$38 sushi`; current page shows left $702; a new page closes the old one; monthly report totals match.
  8. Todos CRUD: complete and reopen.
- After you deploy:
  - Freelancer-Vue login/invoices/refresh and FulhamTexas admin login/dashboard/refresh/change-password still work.
  - `http://samvedamanikam.com/api/sam/device/token` → 400 and `https://…` → 401 (no body), confirming `IsHttps` behind SmarterASP's IIS. Existing endpoints over `http://` behave as before.

## Files
- New:
  - `C:\github\SAM\plan\SAM-plan.md`, `C:\github\SAM\plan\phase-2-backend.md`
  - `Helios-Engine.Core/Database/sam.sql`
  - `Helios-Engine.Core/Models/Sam/Sam.cs`
  - `Helios-Engine.Core/Common/SamRequireHttpsAttribute.cs`, `SamDeviceTokenService.cs`, `SamQuickEntry.cs`, `SamBudgetRules.cs`, `SamCategoryResolver.cs`
  - `Helios-Engine.Core/Controllers/SamDeviceController.cs`, `SamTodoController.cs`, `SamBudgetController.cs`, `SamCategoryController.cs`, `SamDashboardController.cs`
  - `Helios-Engine/Helios-Engine.Core.Tests/` (csproj + 4 test files)
- Modified (additive):
  - `Program.cs`
  - `appsettings.json`
  - `appsettings.Secrets.example.json`
  - `Helios-Engine.Core.http`
  - `README.md`

## Risks / considerations
- **Production DB testing:** a local run writes real rows. Use a test page/todos and delete them afterwards; `sam.sql` never touches existing tables. Back up first.
- `PasswordService.ValidateUser` may re-hash a legacy plain-text password on pairing. That's the same side effect as `/api/token` and harmless.
- `IsHttps` behind SmarterASP IIS (in-process hosting) should reflect real TLS. Verify after deploy (step above). If it's wrong behind a proxy, switch to checking `X-Forwarded-Proto` / `X-ARR-SSL` inside the SAM filter only.
- A startup check for `Sam:DeviceJwtKey` means **the server must have the key in `appsettings.Secrets.json` before deploying**, or the whole API (including Fulham/Freelancer) fails to start. Alternative: register the scheme with a random throwaway key and log a warning when the key is missing, so SAM pairing just fails while other apps keep working. **Recommended: the safe fallback (warning + SAM-only failure).**
- The phase ends with a backend that the Phase 3 Android app consumes. API shapes (UPPERCASE JSON) carry over to Kotlin models.
