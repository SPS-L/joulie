# Sub-project A: Foundation — Design

**Date:** 2026-04-26
**Status:** Draft, awaiting user review
**Sources of truth this design defers to:** `DESIGN.md` (v3) for product/technical spec, `AGENT_INSTRUCTIONS.md` for the larger build playbook, `TEST_PLAN.md` for the test catalog. Where this design narrows scope or makes specific implementation choices, those choices override the broader docs *for Sub-project A only*.

---

## 1. Context — why a "Foundation" sub-project

The repo is in pre-implementation state: full design docs exist, but no Kotlin source has been written. `AGENT_INSTRUCTIONS.md` describes ~10 build steps (scaffold → DI → DB → repos → use cases → UI → Drive backup → CSV → APK build) that, taken together, are too large for a single spec → plan → implementation cycle.

The work has been decomposed into 6 sub-projects, each with its own spec/plan/implementation pass:

| # | Sub-project | What ships |
|---|---|---|
| **A** | **Foundation** *(this spec)* | Hilt + Application + DataStore + Nav graph + 8 Fragment skeletons + functional Wizard + MainActivity gate. Builds and launches. |
| B | Data layer | Room v3 entities/DAOs/migrations + repositories + Migration tests. |
| C | Domain core | `StatsCalculator`, `CostParser`, `UnitConverter`, `DateRangeResolver`, `BackupSerializer` + their use cases. |
| D | Core UI | Dashboard, ChargeEdit, Cars, History — the daily-use loop. |
| E | Backup + Restore | Drive auth, BackupRepository, BackupScheduler/Worker, RestoreBackupUseCase, Settings switch. |
| F | Polish | Charts (MPAndroidChart), CSV export, Manage Locations, theme switcher in Settings. |

This document covers **A only**.

---

## 2. Scope and acceptance criteria

### 2.1 In scope

- Hilt-wired single-Activity app with Navigation Component as the only Activity host.
- All 8 destinations exist as per-screen `Fragment` + `ViewModel` + layout files. Wizard is fully real; the other 7 are placeholders containing only a TextView.
- A real, fully functional 3-page first-boot wizard (Welcome / Metric+Unit / Currency).
- Wizard finish persists `setupComplete=true`, `primaryMetric`, `distanceUnit`, `currency` to DataStore atomically before navigating.
- `MainActivity` reads `setupComplete` at launch and routes to wizard or dashboard accordingly, with no UI flash on first launch.
- Theme preference is read at launch and applied via `AppCompatDelegate`. Default `"system"` means this is a no-op until Sub-project F adds a Settings switch.
- `PreferenceKeys.kt` declares the **full canonical key set** (all 7 keys) so later sub-projects extend a stable contract rather than fork it. `SettingsRepository` only exposes Flow accessors and writers for the 5 keys A actually uses; B and E each add a Flow + writer for their key (`activeCarId`, `driveEnabled`) as a one-line repository extension when their feature lands.

### 2.2 Out of scope (deferred to later sub-projects)

| Concern | Lands in |
|---|---|
| Room database, entities, DAOs, migrations | B |
| `CarRepository`, `ChargeEventRepository`, `LocationRepository`, `BackupRepository` | B (first three), E (last) |
| `StatsCalculator`, `CostParser`, `UnitConverter`, `DateRangeResolver`, `BackupSerializer` | C |
| Use cases (`SaveChargeEvent`, `DeleteChargeEvent`, `ObserveDashboardStats`, `RestoreBackup`, `ExportCsv`) | C / E / F |
| Real UI for Dashboard, ChargeEdit, Cars, History | D |
| Drive auth, backup serialization, BackupScheduler, restore flow | E |
| Settings screen, Charts, CSV export, Manage Locations | F |
| `activeCarId` / `driveEnabled` Flow accessors and writers in `SettingsRepository` | B (activeCarId), E (driveEnabled). The keys themselves are declared in `PreferenceKeys.kt` in A. |
| The Settings → Reset preferences action | F (when Settings UI lands). The gate already supports it; F just flips the flag. |

### 2.3 Acceptance criteria

1. `./gradlew assembleDebug` succeeds. APK at `app/build/outputs/apk/debug/app-debug.apk`.
2. `./gradlew test` passes — `WizardViewModelTest`, `SettingsRepositoryTest`.
3. `./gradlew connectedAndroidTest` passes — `WizardFlowTest`.
4. Manual smoke (fresh install on API 26+ emulator):
   - First launch → Wizard page 1 visible (no Dashboard flash).
   - Walk through all 3 pages, hit Finish → Dashboard placeholder visible.
   - Force-stop, relaunch → Dashboard placeholder visible directly.
   - Clear app data, relaunch, kill mid-wizard (do not press Finish) → next launch shows Wizard page 1 again.

---

## 3. Build configuration changes

### 3.1 Root `build.gradle.kts`

Add Hilt plugin classpath alongside the existing entries:

```kotlin
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.21" apply false
    id("com.google.devtools.ksp") version "1.9.21-1.0.16" apply false
    id("com.google.dagger.hilt.android") version "2.50" apply false
}
```

### 3.2 `app/build.gradle.kts`

Add the Hilt plugin alongside the existing three:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}
```

Add to `dependencies`:

```kotlin
// Hilt — KSP processor to stay consistent with the existing Room-via-KSP choice.
implementation("com.google.dagger:hilt-android:2.50")
ksp("com.google.dagger:hilt-android-compiler:2.50")
implementation("androidx.hilt:hilt-navigation-fragment:1.1.0")

// Splash screen (avoids first-launch flash before wizard gate decides where to go).
implementation("androidx.core:core-splashscreen:1.0.1")

// Hilt test infrastructure.
testImplementation("com.google.dagger:hilt-android-testing:2.50")
kspTest("com.google.dagger:hilt-android-compiler:2.50")
androidTestImplementation("com.google.dagger:hilt-android-testing:2.50")
kspAndroidTest("com.google.dagger:hilt-android-compiler:2.50")
```

The existing Drive/Play-services/Room/MPAndroidChart deps stay as-is. They aren't used by Sub-project A but removing-then-re-adding for B/E/F is churn.

---

## 4. AndroidManifest changes

```xml
<application
    android:name=".EVTrackerApp"
    android:theme="@style/Theme.EVTracker.SplashScreen"
    ... existing attrs unchanged ...>
```

Everything else (Activity, FileProvider, `google_play_services_version` meta-data) stays exactly as it is. The Play Services meta-data is unrelated to Firebase and is needed by `play-services-auth` in Sub-project E.

A new theme `Theme.EVTracker.SplashScreen` (extending `Theme.SplashScreen`) is added in `res/values/themes.xml` and points `postSplashScreenTheme` at the existing `Theme.EVTracker`.

---

## 5. Source tree (additions in this sub-project)

```
app/src/main/java/org/spsl/evtracker/
  EVTrackerApp.kt
  MainActivity.kt
  di/
    AppModule.kt
  data/preferences/
    PreferenceKeys.kt
    SettingsLocalDataSource.kt
  data/repository/
    SettingsRepository.kt
  ui/wizard/
    WizardFragment.kt
    WizardViewModel.kt
    WizardPagerAdapter.kt
    WizardPage1Fragment.kt
    WizardPage2Fragment.kt
    WizardPage3Fragment.kt
  ui/dashboard/    DashboardFragment.kt + DashboardViewModel.kt
  ui/chargeedit/   ChargeEditFragment.kt + ChargeEditViewModel.kt
  ui/cars/         CarsFragment.kt + CarsViewModel.kt
  ui/settings/     SettingsFragment.kt + SettingsViewModel.kt
  ui/charts/       ChartsFragment.kt + ChartsViewModel.kt
  ui/history/      HistoryFragment.kt + HistoryViewModel.kt
  ui/locations/    ManageLocationsFragment.kt + ManageLocationsViewModel.kt

app/src/main/res/
  layout/
    activity_main.xml
    fragment_wizard.xml
    fragment_wizard_page1.xml
    fragment_wizard_page2.xml
    fragment_wizard_page3.xml
    fragment_dashboard.xml
    fragment_charge_edit.xml
    fragment_cars.xml
    fragment_settings.xml
    fragment_charts.xml
    fragment_history.xml
    fragment_manage_locations.xml
  navigation/
    nav_graph.xml
  values/
    currencies.xml         (<string-array name="supported_currencies">)
    themes.xml             (existing — add Theme.EVTracker.SplashScreen)
    strings.xml            (existing — add wizard strings)
```

The 7 placeholder Fragments share an identical body — single `TextView` with the screen name. Each ViewModel is `@HiltViewModel class XViewModel @Inject constructor() : ViewModel()` with an empty body. Sub-projects D/E/F open these existing files and fill them in.

---

## 6. Application class and Hilt wiring

### 6.1 `EVTrackerApp`

```kotlin
@HiltAndroidApp
class EVTrackerApp : Application() {
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        val theme = runBlocking { settingsRepository.theme.first() }
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark"  -> AppCompatDelegate.MODE_NIGHT_YES
                else    -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
```

`runBlocking` for a single-key DataStore read on app launch is acceptable (typical read time < 5 ms; happens before any UI inflates).

### 6.2 `AppModule`

Single Hilt module providing the DataStore singleton:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    private val Context.dataStore by preferencesDataStore(name = "evtracker_prefs")

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore
}
```

`SettingsRepository` is `@Inject constructor`-discoverable — no provider entry needed.

---

## 7. SettingsRepository contract

`PreferenceKeys.kt` declares the **full canonical key set** (all 7 keys from `DESIGN.md §3.3`), even though A only reads/writes 5 of them. This keeps the canonical preferences contract in one place from day 1; later sub-projects consume keys without ever editing this file.

```kotlin
object PreferenceKeys {
    val SETUP_COMPLETE = booleanPreferencesKey("setupComplete")
    val PRIMARY_METRIC = stringPreferencesKey("primaryMetric")
    val DISTANCE_UNIT  = stringPreferencesKey("distanceUnit")
    val CURRENCY       = stringPreferencesKey("currency")
    val ACTIVE_CAR_ID  = intPreferencesKey("activeCarId")     // consumed by B
    val DRIVE_ENABLED  = booleanPreferencesKey("driveEnabled") // consumed by E
    val THEME          = stringPreferencesKey("theme")
}
```

`SettingsRepository` only exposes Flow + writer methods for the 5 keys A actually uses (below). B and E each add a Flow + writer for their key as a one-line repository extension at the time their feature lands.

```kotlin
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val setupComplete: Flow<Boolean> = dataStore.data.map { it[SETUP_COMPLETE] ?: false }
    val primaryMetric: Flow<String>  = dataStore.data.map { it[PRIMARY_METRIC] ?: "km_per_kwh" }
    val distanceUnit: Flow<String>   = dataStore.data.map { it[DISTANCE_UNIT]  ?: "km" }
    val currency: Flow<String>       = dataStore.data.map { it[CURRENCY]       ?: "EUR" }
    val theme: Flow<String>          = dataStore.data.map { it[THEME]          ?: "system" }

    suspend fun completeSetup(metric: String, unit: String, currency: String) {
        dataStore.edit {
            it[PRIMARY_METRIC] = metric
            it[DISTANCE_UNIT]  = unit
            it[CURRENCY]       = currency
            it[SETUP_COMPLETE] = true
        }
    }

    suspend fun setTheme(theme: String) {
        dataStore.edit { it[THEME] = theme }
    }

    /** Used by the Reset preferences action in Sub-project F. Wired into the gate now. */
    suspend fun resetSetupComplete() {
        dataStore.edit { it[SETUP_COMPLETE] = false }
    }
}
```

`completeSetup` writes all 4 wizard keys in a single `edit { }` block — the wizard cannot leave DataStore in a half-configured state.

---

## 8. MainActivity wizard gate

The naive `lifecycleScope.launch { … if (!complete) navigate(…) }` pattern would render the dashboard for one frame before redirecting. Instead, set the nav graph's start destination *before* binding it to the controller:

```kotlin
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController
        val graph = navController.navInflater.inflate(R.navigation.nav_graph)

        val complete = runBlocking { settingsRepository.setupComplete.first() }
        graph.setStartDestination(
            if (complete) R.id.dashboardFragment else R.id.wizardFragment
        )
        navController.graph = graph

        splash.setKeepOnScreenCondition { false }
    }
}
```

`activity_main.xml` is a `FragmentContainerView` with `android:id="@+id/nav_host_fragment"`, `android:name="androidx.navigation.fragment.NavHostFragment"`, and `app:defaultNavHost="true"`. The graph file does **not** declare a `startDestination` attribute — it's set programmatically.

---

## 9. Nav graph

`res/navigation/nav_graph.xml` declares 8 destinations: `wizardFragment`, `dashboardFragment`, `chargeEditFragment`, `carsFragment`, `settingsFragment`, `chartsFragment`, `historyFragment`, `manageLocationsFragment`.

Only one action is wired in A:

```xml
<fragment android:id="@+id/wizardFragment" ...>
    <action
        android:id="@+id/action_wizard_to_dashboard"
        app:destination="@id/dashboardFragment"
        app:popUpTo="@id/wizardFragment"
        app:popUpToInclusive="true"/>
</fragment>
```

`popUpToInclusive="true"` means the user cannot navigate back into the wizard from the dashboard. All other inter-screen actions are deferred to the sub-projects that need them.

---

## 10. Wizard implementation

### 10.1 Architecture

- `WizardFragment` is the host — owns `ViewPager2`, the progress dots (a `TabLayout` mediated against the pager), and the Back / Next / Finish buttons. `WizardFragment` owns the `WizardViewModel` via the standard `by viewModels()` delegate.
- The 3 page Fragments (`WizardPage1Fragment` … `WizardPage3Fragment`) are `@AndroidEntryPoint` and share the host's `WizardViewModel` by scoping to the parent fragment's `ViewModelStore`:

  ```kotlin
  private val viewModel: WizardViewModel by viewModels(
      ownerProducer = { requireParentFragment() }
  )
  ```

  This is **not** `activityViewModels()`. Activity-scoped state would survive the wizard being popped and would re-appear with stale values if the user later triggers Settings → Reset preferences (Sub-project F). Parent-fragment scoping ties the VM lifecycle to `WizardFragment`: when the wizard destination is removed by the `popUpToInclusive=true` action, the host fragment is destroyed and its `ViewModelStore` is cleared. The next time the wizard is opened, page state, metric, unit, and currency are all back to defaults.
- `viewPager.isUserInputEnabled = false` — page changes happen only through the host buttons. This avoids the user swiping past a partially-filled page.
- `WizardPagerAdapter : FragmentStateAdapter` returns the 3 page fragments by position.

### 10.2 `WizardViewModel`

```kotlin
@HiltViewModel
class WizardViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    data class UiState(
        val page: Int = 0,                              // 0..2
        val metric: String = "km_per_kwh",
        val unit: String = "km",
        val currency: String = "EUR"
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun goNext() { _state.update { it.copy(page = (it.page + 1).coerceAtMost(2)) } }
    fun goBack() { _state.update { it.copy(page = (it.page - 1).coerceAtLeast(0)) } }

    fun selectMetric(metric: String) {
        _state.update { current ->
            val forcedUnit = when (metric) {
                "mi_per_kwh" -> "miles"
                "km_per_kwh", "kwh_per_100km" -> "km"
                else -> current.unit
            }
            current.copy(metric = metric, unit = forcedUnit)
        }
    }

    fun selectUnit(unit: String) {
        _state.update { current ->
            val coupledMetric = when {
                unit == "miles" && current.metric != "kwh_per_100km" -> "mi_per_kwh"
                unit == "km"    && current.metric == "mi_per_kwh"    -> "km_per_kwh"
                else -> current.metric
            }
            current.copy(unit = unit, metric = coupledMetric)
        }
    }

    fun selectCurrency(currency: String) { _state.update { it.copy(currency = currency) } }

    suspend fun finish() {
        val s = state.value
        settingsRepository.completeSetup(s.metric, s.unit, s.currency)
    }
}
```

Coupling rules verbatim from `DESIGN.md §3.4`:
- `mi_per_kwh` ⇒ `miles`; `km_per_kwh` and `kwh_per_100km` ⇒ `km` (forced when metric changes).
- Manual unit change: `km` → flips metric to `km_per_kwh` only if the current metric is `mi_per_kwh`. `miles` → flips metric to `mi_per_kwh` only if the current metric is *not* `kwh_per_100km`. `kwh_per_100km` is left alone.

### 10.3 `WizardFragment` button labels

| Page | Back | Next/Finish |
|---|---|---|
| 0 | hidden | "Get Started" |
| 1 | "← Back" | "Next →" |
| 2 | "← Back" | "Finish ✓" |

The Finish handler calls `viewLifecycleOwner.lifecycleScope.launch { viewModel.finish(); findNavController().navigate(R.id.action_wizard_to_dashboard) }`. Navigation happens only after the `completeSetup` suspend returns.

### 10.4 Currency dropdown

`res/values/currencies.xml` declares `<string-array name="supported_currencies">` with the 13 codes from `DESIGN.md §3.2`: EUR, USD, GBP, CHF, JPY, CZK, PLN, HUF, DKK, SEK, NOK, AUD, CAD. Wizard page 3 binds an `AutoCompleteTextView` (Material `ExposedDropdownMenu`) to this array.

Adding a new currency is then a one-line resource edit.

---

## 11. Tests

### 11.1 `WizardViewModelTest` (JVM)

Uses `kotlinx-coroutines-test`. `SettingsRepository` is replaced with a fake that records `completeSetup` invocations and exposes the same Flow surface.

| Test | Scenario | Expected |
|---|---|---|
| `finish_writesAllPrefs` | Set metric=mi_per_kwh, unit=miles, currency=USD; call `finish()` | Fake repo received `completeSetup("mi_per_kwh", "miles", "USD")` |
| `coupling_miPerKwhForcesMiles` | `selectMetric("mi_per_kwh")` from default state | state.unit == "miles" |
| `coupling_kmMetricForcesKm` | From mi_per_kwh state, `selectMetric("km_per_kwh")` | state.unit == "km" |
| `coupling_kwhPer100kmForcesKm` | `selectMetric("kwh_per_100km")` from default | state.unit == "km" |
| `manualUnit_kmFlipsMetricFromMiles` | From mi_per_kwh/miles, `selectUnit("km")` | state.metric == "km_per_kwh" |
| `manualUnit_milesFlipsKmMetric` | From km_per_kwh/km, `selectUnit("miles")` | state.metric == "mi_per_kwh" |
| `manualUnit_doesNotChangeKwhPer100km` | From kwh_per_100km/km, `selectUnit("miles")` | state.metric == "kwh_per_100km" (unchanged) |
| `goNext_clampsAtPage2` | Press next 5 times from page 0 | state.page == 2 |
| `goBack_clampsAtPage0` | Press back 3 times from page 0 | state.page == 0 |

### 11.2 `SettingsRepositoryTest` (JVM)

In-memory DataStore via `PreferenceDataStoreFactory.create(scope = TestScope, produceFile = { tempFolder.newFile() })`.

| Test | Scenario | Expected |
|---|---|---|
| `defaults_areExpected` | Fresh repo, sample each Flow | setupComplete=false, primaryMetric="km_per_kwh", distanceUnit="km", currency="EUR", theme="system" |
| `completeSetup_writesAllFourKeysAtomically` | Call `completeSetup("mi_per_kwh", "miles", "USD")` | All 4 keys present after one `edit{}` block; setupComplete=true |
| `setTheme_persists` | Call `setTheme("dark")` | theme Flow emits "dark" |
| `resetSetupComplete_flipsFlag` | After completeSetup, call `resetSetupComplete()` | setupComplete Flow emits false; other 3 keys unchanged |

### 11.3 `WizardFlowTest` (Espresso, `@HiltAndroidTest`)

Runs against a debug APK on an emulator (API 26+).

| Test | Scenario | Expected |
|---|---|---|
| `firstLaunch_showsWizard` | Clear app data, launch | WizardFragment page 1 visible, no Dashboard flash |
| `completedSetup_skipsWizard` | Pre-seed DataStore with `setupComplete=true`, launch | DashboardFragment placeholder visible directly |
| `finishWizard_landsOnDashboard` | First launch, walk through 3 pages, press Finish | DashboardFragment visible; back press exits app (does not return to wizard) |

We deliberately do **not** include a "force-stop the app process mid-wizard" instrumented test. Force-stopping the target package routinely tears down the instrumentation runner with it on Android, which makes the test flaky-to-non-implementable for reasons unrelated to product behavior. The property that test would assert — *if `finish()` was never called, `setupComplete` is still `false` and the next launch re-shows the wizard* — is already covered by:

- `WizardViewModelTest.finish_writesAllPrefs` (only `finish()` writes `SETUP_COMPLETE`)
- `SettingsRepositoryTest.completeSetup_writesAllFourKeysAtomically` (the four wizard keys land in one `edit{}`, not piecemeal as the user advances pages)
- `WizardFlowTest.firstLaunch_showsWizard` (gate routes to wizard whenever `setupComplete=false`, regardless of how the flag came to be `false`)

Tests use `HiltTestApplication` and a custom test runner. `app/src/androidTest/java/org/spsl/evtracker/HiltTestRunner.kt` extends `AndroidJUnitRunner` and overrides `newApplication` to return `HiltTestApplication`. The runner is registered in `app/build.gradle.kts`:

```kotlin
testInstrumentationRunner = "org.spsl.evtracker.HiltTestRunner"
```

---

## 12. Cleanup actions

No source-tree cleanup is required for Sub-project A. Existing manifest, themes, colors, strings, and `file_paths.xml` are correct as-is.

The `app/google-services.json` file present on some developer machines is already gitignored (`.gitignore:16-20`) and the project never references it (no `google-services` Gradle plugin, no Firebase SDK). Whether it exists locally is the developer's choice and outside the scope of this sub-project. The repo policy is "do not depend on it, do not commit it" — both already hold.

---

## 13. Risks and notes

- **`runBlocking` at app launch.** Used twice: once for theme in `EVTrackerApp.onCreate`, once for `setupComplete` in `MainActivity.onCreate`. Both are single-key DataStore reads; expected total cost is single-digit milliseconds. SplashScreen API hides any visible delay. If profiling later shows this matters, theme can move to a Flow-collected lifecycle observer and the gate can use a conditional start destination via the splash-keep-on-screen condition.
- **Hilt + KSP.** Hilt-via-KSP has been GA since Hilt 2.48. We pin to 2.50. If we hit a known-issue around generated `Hilt_*` classes during incremental builds, the fallback is moving Hilt to kapt while leaving Room on KSP — both processors can coexist.
- **`activeCarId` / `driveEnabled` deferred.** The keys themselves ship in A's `PreferenceKeys.kt`, so the canonical contract is intact. B and E each add a one-line Flow + a one-line writer to `SettingsRepository` when their feature lands; not a refactor.
- **Reset preferences action.** `SettingsRepository.resetSetupComplete()` exists in A but isn't called by any UI yet. F wires the Settings entry. The function is shipped now because it's trivially testable and keeps the repo's public surface stable.
- **Wizard gate edge case — DataStore I/O failure.** If the DataStore read throws (e.g., disk corruption), `runBlocking` will rethrow and crash the app at launch. This matches Android's default behavior for unrecoverable storage failures and is acceptable for A. A graceful fallback (assume `setupComplete=false`, log and continue) can be added in F if telemetry shows it.
