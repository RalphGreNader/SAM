# SAM Phase 3: Android skeleton + Budget

## Status (2026-11-02): built, tested on the emulator, not committed
- Builds pass: `assembleDebug`, `assembleRelease` (R8-minified, about 1.9 MB) and `testDebugUnitTest` (quick-entry parser and server-URL tests).
- Lint is clean apart from intentional "newer version available" notices (those releases are under 7 days old).
- Emulator (`Medium_Phone_API_37.0`): paired against production, used Home and Budget, and the entries showed up in the DB.
- Branding: your koala logo (`art/koala-logo.png`) is the launcher icon, with a monochrome themed-icon variant, and appears on the splash screen in a white circle.

## What's in the app
- **Pair screen (first run only):** server (default `https://samvedamanikam.com/`, https only), username, password, device name.
  - Calls `api/auth/device/register` with `APP=sam`. The password is never stored.
- **Auth (`auth/`, reusable, no SAM types):**
  - `KeystoreCipher` (AES-256-GCM, Android Keystore key) and `DeviceCredentialStore` (encrypted; excluded from backup and device transfer).
  - `DeviceAuthClient`: pair, then a JWT in memory, an OkHttp interceptor and an authenticator that refreshes on 401.
  - If the server no longer knows the device (revoked), the app goes back to the pair screen.
- **Home:** budget card (amount left, red when negative, progress bar, last 2 items) and todos card (overdue / due today / open counts, top 3).
- **Budget:**
  - **Current page:** OneNote layout (title + amount, items, Left) and a category breakdown.
    - Quick entry ("$amount expense", e.g. `17 tacos charlie`) with a live preview, category chips and merchant suggestions.
    - Tap an item to edit or delete it; deleting offers Undo.
    - "Start new page" dialog (amount pre-filled, start date, optional title).
  - **History:** past pages with over/under; tap one for a read-only page with category bars.
  - **Reports:** by month (year selector, year-to-date, per-month category bars) or by year.
- **Settings:** server, user, device name, paired date, "Unpair this phone" (revokes on the server). App version.
- **Todos tab:** placeholder until Phase 5.
- **Offline:** each screen shows its last good response (`JsonCache`) while refreshing. Writes need a connection for now; the Room outbox comes with the widget in Phase 4.

## Changes from the master plan
- **No Hilt:** manual wiring in `AppContainer`. Fewer moving parts and no annotation processor, which also sidesteps AGP 9 + KSP compatibility risk.
- **No Room yet:** a JSON response cache covers offline reads. Room plus the outbox arrive with the widget's offline quick-add in Phase 4.
- **No Vico:** simple Compose category bars instead of a chart library.
- **Server URL is chosen at pairing:** a device is paired to one server, so changing it means unpair and pair again. Settings shows it read-only.
- **Edit by tap, not swipe:** delete is in the edit dialog, with an Undo snackbar.

## Versions (each published 7+ days before use)
AGP 9.4.1, Gradle 9.7.1, Kotlin 2.4.20, Compose BOM 2026.09.00, navigation 2.10.2, lifecycle 2.11.0, activity 1.13.0, core 1.19.1, Retrofit 3.0.0, OkHttp 5.5.0, kotlinx-serialization 1.11.0, coroutines 1.11.0. compileSdk/targetSdk 37, minSdk 26.

## Release signing (do this once, before installing on your real phone)
Updates only install over the old app, and keep the pairing, when they're signed with the **same** key, so create it once and back it up.
1. Create a keystore outside the repo. It asks for a password; pick one and keep it in your password manager.
   ```powershell
   New-Item -ItemType Directory -Force $env:USERPROFILE\keys | Out-Null
   & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore $env:USERPROFILE\keys\sam-release.jks -alias sam -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=SAM"
   ```
2. Create `C:\github\SAM\keystore.properties`. It's git-ignored; never commit it.
   ```properties
   storeFile=C:/Users/sanja/keys/sam-release.jks
   storePassword=YOUR_PASSWORD
   keyAlias=sam
   keyPassword=YOUR_PASSWORD
   ```
3. Back up `sam-release.jks` and the password somewhere safe (not in the repo). If you lose them, updates won't install over the old app: you'd have to uninstall and pair again.
4. Build: `.\gradlew.bat assembleRelease` produces `app\build\outputs\apk\release\SAM-<version>-release.apk`.

## Install on your phone
- **USB:** turn on Developer options → USB debugging, then `adb install -r app\build\outputs\apk\release\SAM-0.3.0-release.apk`.
- **File copy:** copy the APK to the phone, open it, and allow "Install unknown apps" for your file manager.
- Debug and release builds are signed differently. Uninstall a debug build before installing the release build (the first time only).

## Next
- Commit Helios (Phase 2) and SAM (Phases 1–3).
- Phase 4: Glance widget (budget left, due today, + Expense / + Task quick-add), Room outbox for offline adds, share target.
