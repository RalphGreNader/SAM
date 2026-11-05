---
agent: devin-local
session: accidental-jute
created: 2026-10-31T14:29:53Z
---
# Generalize SAM device pairing into shared Helios device auth

Turn the SAM-only phone pairing (SamDevice scheme, SAM_DEVICE table, api/sam/device/*) into a reusable Helios "device auth" feature (Device scheme, HELIOS_DEVICE table with an APP_CODE, api/auth/device/*, per-app authorization policies) with SAM as its first consumer, before anything is committed or deployed.

## Status (2026-11-02): implemented, not yet committed or deployed
- `dotnet build` passes and `dotnet test` passes (43 tests).
- Local smoke test, with a temporary key set through an env var:
  - SAM endpoints return 401 without a token or with a bad token.
  - Register with an unknown app → 400 "Unknown app.", and a missing APP → 400.
  - `api/auth/device/devices` without a token → 401, and the old `api/sam/device/token` → 404.
  - Fulham admin → 401, and `/api/token` behaves as before.
- The full device flow (register → token → `app:sam` → revoke) needs `device_auth.sql` run first. Those checks are in your next steps in `phase-2-backend.md`.
- Policies are registered for each code in `DeviceAuth:Apps`. An app's controllers must only use `app:<code>` for listed codes; an unlisted policy name would give a 500.

## Assessment
- **It makes sense, and now is the cheapest time.** Nothing is committed, deployed or paired, `sam.sql` hasn't been run, and the Android client isn't written. Later, it would mean a table migration plus re-pairing phones.
- **It's not difficult:** it's about 90% renames and moves. The token service, key hashing, revocation check and HTTPS filter have no SAM-specific logic today. The only real new piece is an `APP_CODE` on each device and a per-app authorization policy, so a phone paired for app A can't call app B's endpoints.
- **What stays per-app:** each app's data and controllers. Device auth only answers "which user, which device, which app".
- **Honest limits** (unchanged by this work):
  - All apps share the same `USERS` accounts, with no per-app user permissions. Pairing for any app requires a Helios login.
  - The flow is for native/installed apps. Web apps keep using `/api/token`.
- **Mobile side:** future Android apps would reuse the client code (credential store + OkHttp authenticator). In Phase 3 SAM's `auth/` package will be self-contained so it can be extracted into a shared Kotlin module later. That extraction is out of scope now.

## Design
- **Scheme** `"Device"` (const `DeviceAuthService.Scheme`):
  - Own key `DeviceAuth:JwtKey`, issuer `Jwt:Issuer`, audience `DeviceAuth:Audience` (`HeliosDevice`).
  - Claims: `sub`, `userdisplayname`, `device_id`, **`app`** (e.g. `sam`), `jti`.
  - The defaults stay `Bearer`, so device tokens still get 401 on all existing `[Authorize]` endpoints.
- **Per-app policy:** for each code in `DeviceAuth:Apps`, Program.cs registers the policy `app:<code>`:
  - Schemes `Bearer` + `Device`, authenticated user.
  - Assertion `DeviceAuthService.AllowsApp(user, code)`: a normal login token (no `device_id` claim) is allowed; a device token only if `app == code`.
  - Registered via a **separate** `builder.Services.AddAuthorization(o => …)` call; the existing `AddAuthorization()` line stays untouched.
- **Revocation:** still checked per request (`OnTokenValidated`) against `HELIOS_DEVICE.REVOKED_YN`.
- **Missing or weak key:** same safe fallback as today (warning, 503 on pairing only).
- **Endpoints** (`DeviceAuthController`, route `api/auth/device`, `[HttpsOnly]`):
  - `POST register` `{APP, USERNAME, PASSWORD, DEVICE_NAME}`: anonymous, rate limit `device-auth`. APP must be in `DeviceAuth:Apps` (otherwise 400). Calls `PasswordService.ValidateUser` and never touches `REFRESH_TOKEN`.
  - `POST token` `{DEVICE_ID, DEVICE_KEY}`: anonymous, `device-auth` rate limit. The app comes from the device row and must still be enabled.
  - `GET devices?app=`, `DELETE devices/{id}`, `DELETE devices/current`: Bearer or Device token (any app), scoped to the caller's user name.
- **How a future app opts in** (documented in the README):
  1. Add its code to `DeviceAuth:Apps`.
  2. Put `[Authorize(Policy = "app:<code>")]` and `[HttpsOnly]` on its controllers.
  3. Have the client pair with `APP = "<code>"`.

## Implementation steps (Helios-Engine.Core)
1. **Database**
   - New `Database/device_auth.sql` (idempotent), creating `HELIOS_DEVICE`:
     - `DEVICE_ID uniqueidentifier PK`, `APP_CODE varchar(30)`, `USER_NAME`, `DEVICE_NAME`
     - `KEY_HASH varbinary(32)`, `CREATE_DATE`, `LAST_SEEN`, `REVOKED_YN`
     - Index `(USER_NAME, APP_CODE)`.
   - Remove the `SAM_DEVICE` block from `sam.sql` (6 SAM tables remain).
2. **Common**
   - `SamDeviceTokenService.cs` → `DeviceAuthService.cs`: config `DeviceAuth:*`, an `app` parameter on `CreateToken`, and `AllowsApp`, `AppPolicy(code)` and `EnabledApps(config)` helpers.
   - `SamRequireHttpsAttribute.cs` → `HttpsOnlyAttribute.cs` (avoids a clash with MVC's `RequireHttpsAttribute`). Same behavior.
3. **Models:** move the device DTOs and `SamDevice` out of `Models/Sam/Sam.cs` into `Models/DeviceAuth.cs` (`DeviceRegisterRequest` + `APP`, `DeviceRegisterResponse`, `DeviceTokenRequest`, `DeviceTokenResponse`, `HeliosDevice` + `APP_CODE`).
4. **Controllers**
   - `SamDeviceController.cs` → `DeviceAuthController.cs` (`api/auth/device`), not derived from `SamControllerBase`.
   - `SamControllerBase`: `[Authorize(Policy = "app:sam")]` + `[HttpsOnly]`. No other SAM controller changes.
5. **Program.cs** (still additive only compared with `master`)
   - Rename the scheme/vars to `Device`.
   - Rate-limit policy `sam-device` → `device-auth`; keep `sam-jobs`.
   - Add the per-app policy registration.
   - Register `DeviceAuthService` as a singleton.
6. **Config**
   - `appsettings.json`: new `"DeviceAuth": { "Audience": "HeliosDevice", "TokenMinutes": 60, "Apps": ["sam"] }`. Remove `DeviceTokenMinutes` and `DeviceAudience` from `Sam`.
   - Secrets example: `DeviceAuth:JwtKey` replaces `Sam:DeviceJwtKey`.
7. **Tests:** `SamDeviceTokenServiceTests` → `DeviceAuthServiceTests`, plus:
   - The `app` claim is present.
   - `AllowsApp`: a login token is allowed, a `sam` device token is allowed on `sam`, an `other` device token is denied on `sam`.
   - An app not in `DeviceAuth:Apps` is not enabled.
8. **Docs**
   - `.http`: the SAM section uses `api/auth/device/*` with `"APP": "sam"`.
   - README: a new "Device auth (native apps)" section (how it works, the 3-step opt-in, endpoints, `device_auth.sql`). The SAM section points to it.
   - `C:\github\SAM\plan\phase-2-backend.md` + `SAM-plan.md` (B3 pairing URLs, run order: `device_auth.sql` then `sam.sql`, key name `DeviceAuth:JwtKey`).
   - `C:\github\SAM\AGENTS.md`.

## Verification
- `dotnet build` passes and `dotnet test` passes.
- `git diff master` is still additive-only for existing files. `TokenController`, `PasswordService` and `email.cs` are untouched. No references to `SamDevice` or `SAM_DEVICE` remain.
- Local run:
  - `api/sam/dashboard` with no token → 401.
  - `api/auth/device/token` without a key → 503.
  - Fulham admin → 401.
  - `/api/token` behaves as before.
- After you run `device_auth.sql` + `sam.sql` and add `DeviceAuth:JwtKey`:
  1. Register with `APP=sam` → token.
  2. The device token gets 401 on Fulham admin, Invoice and Customer, and 200 on `api/sam/dashboard`.
  3. Register with an unknown APP → 400.
  4. After revoking, the token gets 401.
  5. `USERS.REFRESH_TOKEN` is unchanged.

## Risks / considerations
- A small amount of extra design (one config list, one policy per app) for a second app that doesn't exist yet. Acceptable because the cost is low now and high later.
- A device paired for one app can still list and revoke that user's devices for other apps. That's intended: it's the same person, and it enables "manage my devices" from any app.
- All apps share one device signing key. Per-app keys are possible later if an app ever needs separate trust; not needed now.
