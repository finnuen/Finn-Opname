# AGENTS.md

Guidance for anyone — human or automated — changing Finn Opname. Read this
before the first edit; it records the decisions that are not obvious from the
code and the ones that are expensive to get wrong.

## What this app is

An offline Android stock counter: scan a barcode or QR code, or type a code, and
the app counts it on one of several inventory sheets. Kotlin + Jetpack Compose,
Room, CameraX, ML Kit barcode scanning, ZXing for drawing the code images. Single
module, no backend, no network permission.

## Build and test

The Gradle wrapper is committed, so no separate Gradle install is needed:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

- JDK 21 (`JAVA_HOME`) and the Android SDK with API 36 (`ANDROID_HOME`, or
  `local.properties` with `sdk.dir`).
- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Tests: `app/build/test-results/testDebugUnitTest/*.xml`, one file per test
  class.
- Configuration cache is on. A build that suddenly cannot resolve a task is
  usually a stale cache: rerun once before suspecting the code.

## Things that must not change

- **`applicationId`** is `com.aistudio.scancounter.qmrwtp`, while the Kotlin
  namespace is `com.example`. That mismatch is deliberate; changing the ID means
  the store and the phone treat it as a different app and nobody can update.
- **Database name** `scancounter_database` and the entity/table names. Same
  reason: renaming any of them loses every existing count.
- **`debug.keystore` stays out of git.** Debug builds are signed with it, so the
  build machine keeps its copy. A fresh clone must generate its own (alias
  `androiddebugkey`, store/key password `android`).
- **Barcodes encode the code only.** Never the name, never both. A code that
  cannot be encoded shows exactly `Code is incompatible with barcode` (QR:
  `Code is incompatible with QR code`).

## Database changes

Room schema version lives in `data/AppDatabase.kt` (currently **4**). A schema
change means all of the following, in one commit:

1. Bump `@Database(version = …)`.
2. Add a `Migration(old, new)` and register it in `addMigrations(...)`.
3. A new NOT NULL column needs `@ColumnInfo(defaultValue = "0")` on the entity
   field, and the same `DEFAULT 0` in the `ALTER TABLE`. Room validates the
   columns it finds against what the entity declares; a mismatch crashes on
   first open, after the user's data already exists.
4. Add a migration test in `AppDatabaseMigrationTest`. It rebuilds the old schema
   from Room's own `sqlite_master` output and strips the later columns
   (`schemaAt(version)` / `withoutColumn`), so the test cannot drift from the
   real table definitions. Do not hand-write the old `CREATE TABLE`.

Backfills should say what they can honestly know. The v3→v4 migration copies an
item's `updatedAt` into `createdAt` because that was the only time ever stored —
do not invent an earlier creation date.

## Behaviours that were asked for on purpose

Do not "simplify" these away; each came from a user report.

**Scanning** (`ui/ScannerView.kt`, `ui/InventoryViewModel.kt`)

- At most one accepted scan per second, whatever the code
  (`SCAN_INTERVAL_MS = 1000L`). This is a hard rate limit, not a same-code
  debounce.
- A code counts only after three consecutive frames agree on it, arriving within
  900 ms (`StableBarcodeReader`). A repeat of the same code must earn a fresh
  agreement.
- Analysis frames target 1280×720 so dense 1D codes resolve. The camera is
  rebound only when the lens flips; the ML Kit client is created once and closed
  on dispose. Rebinding per recomposition used to restart the preview mid-scan.

**Item detail** (`ui/ItemDetailDialog.kt`)

- Barcode and QR regenerate after typing stops for 1 s
  (`CODE_RENDER_DEBOUNCE_MS`), on `Dispatchers.Default`, with the first render
  immediate so the sheet never opens blank. Regenerating on every keystroke made
  the code field lag.
- Barcode bitmap is 1200×270 to match the on-screen box; QR is 600 px shown at
  132 dp.
- The three-line menu offers **Share image**, **Copy image to clipboard**, and a
  disabled **Screenshot to save** hint at the same text size as the other items.
  Saving to Photos through MediaStore is deliberately gone — it fails on the
  user's device — so do not reintroduce it.
- The sheet is edge-to-edge and pads with `statusBarsPadding()` /
  `navigationBarsPadding()`; keep it compact, with no content hidden behind the
  system bars.

**Theme** (`data/SettingsStore.kt`)

- The dark-mode toggle is persisted in SharedPreferences and restored on launch,
  so it must survive the app being killed. Do not move it back into a plain
  `MutableStateFlow`.

**Sheets** (`ui/InventoryViewModel.kt`, `ui/DrawerContent.kt`)

- The default sheet, and the one made after the last sheet is removed, is called
  `New sheet` (`DEFAULT_SHEET_NAME`).
- Every item write touches its sheet's `updatedAt` (`InventoryRepository`), which
  the drawer shows as "Last modified".
- Item rows show the item's own created/modified times, not the sheet's.

**CSV** (`util/CsvHandler.kt`)

- Header `Name,Code,Quantity,Created,Last modified`; the two dates are per item,
  fixed `yyyy-MM-dd HH:mm` in `Locale.US`.
- The importer ignores the date columns but must still round-trip an exported
  file, quotes and commas included.

## Version and release

- `app/build.gradle.kts` holds `versionCode` and `versionName`; the version is
  two digits (`1.0`). A change bumps the minor number (`1.0` → `1.1`) and
  `versionCode` by one. Keep the running app's displayed version in step with it.
- Commit to `main` and push only when asked. No feature branches, no pull
  requests.
- Release: tag `v<versionName>`, title `Finn Opname v<versionName>`, and exactly
  one asset — the APK, uploaded as `FinnOpname.apk` (no version in the file name,
  so it can be downloaded over the old copy). No source zip, no checksums file.
- The APK is the debug build: there is no upload keystore in the repository and
  the `release` signing config expects one. Sideloading works; Play Store upload
  is not set up.

## Tests

| File | Covers |
| --- | --- |
| `AppDatabaseMigrationTest` | v2→v4 and v3→v4 migrations, against Room-validated schemas |
| `BarcodeUtilTest` | barcode/QR generation and incompatible codes |
| `ExampleUnitTest` | CSV export/import round-trip, per-item date columns |
| `ExampleRobolectricTest` | Compose smoke test of the main screen |
| `ScanConfirmationTest` | the three-frame agreement rule |
| `SettingsStoreTest` | dark mode is remembered across launches |

Run them all before calling a change done; a failing migration test is the one
that would corrupt a user's data.

## Gotchas

- KSP occasionally prints `AWT-EventQueue-0 … ApplicationManager.getApplication()
  is null` during a build. It is noise; the build still succeeds.
- On Windows, device screenshots are 1080×2408, scaled to 900×2000 in reports —
  multiply coordinates by 1.2 before `adb shell input tap`. `adb` lives in
  `%LOCALAPPDATA%\Android\Sdk\platform-tools`.
- Checkpoints of this project live in the sibling `…-checkpoints` folder, one
  zip per prompt that changes files; see the global instructions for the naming
  and the verification step.
