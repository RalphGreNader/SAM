# SAM (Sam All-in-one Management)

- Plans: `plan/SAM-plan.md` (master) and `plan/phase-*.md` (per phase, with status at the top).
- Backend lives in `C:\github\Helios-Engine\Helios-Engine\Helios-Engine.Core` (ASP.NET Core 8, Dapper, SQL Server). SAM code: `Controllers/Sam*`, `Common/Sam*`, `Models/Sam/`, `Database/sam.sql`.
  - Phone pairing is shared Helios device auth (reusable by other native apps): `Common/DeviceAuthService.cs`, `Common/HttpsOnlyAttribute.cs`, `Controllers/DeviceAuthController.cs` (`api/auth/device/*`), `Models/DeviceAuth.cs`, `Database/device_auth.sql` (`HELIOS_DEVICE`). SAM's app code is `sam` (policy `app:sam`, config `DeviceAuth:Apps`).
  - Build: `dotnet build` in `Helios-Engine.Core`
  - Tests: `dotnet test` in `C:\github\Helios-Engine\Helios-Engine\Helios-Engine.Core.Tests`
  - Run: `dotnet run --launch-profile http` → `http://localhost:5297` (Development; uses `appsettings.Secrets.json`, which points at the production DB)
- Never modify `TokenController`, `PasswordService`, the existing `AddJwtBearer` block or `email.cs` methods; device auth is the separate `Device` scheme.
- Android app (this repo): Kotlin + Compose, package `com.samvedamanikam.sam`, manual DI in `AppContainer`, reusable auth in `auth/`.
  - Use Android Studio's JDK: set `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr` for command-line builds.
  - Build/test: `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug` (release: `assembleRelease`, signed only when `keystore.properties` exists).
  - Emulator: AVD `Medium_Phone_API_37.0`; install with `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\SAM-<version>-debug.apk`.
  - Dependency versions must be published 7+ days before adding (see `plan/phase-3-android.md`).
  - Don't write `/*` inside KDoc comments (e.g. `api/sam/*`): Kotlin treats it as a nested comment opener.
- git is not on PATH: `%LOCALAPPDATA%\GitHubDesktop\app-<version>\resources\app\git\cmd\git.exe` (currently app-3.6.10).
- Android Studio: `C:\Program Files\Android\Android Studio`; SDK: `%LOCALAPPDATA%\Android\Sdk`.
