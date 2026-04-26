# Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Sub-project A from `docs/superpowers/specs/2026-04-26-foundation-design.md` — a buildable, launchable Android app with Hilt + DataStore + Navigation Component, a fully functional 3-page first-boot wizard, a `MainActivity` wizard gate that routes to the wizard or a placeholder Dashboard with no first-launch flash, and 8 placeholder destination Fragments.

**Architecture:** Single-Activity, MVVM, Hilt-injected. Wizard state lives in a `WizardViewModel` scoped to the `WizardFragment`'s `ViewModelStore` (cleared when the wizard destination is popped). Preferences live in `SettingsRepository` over Jetpack DataStore. The wizard gate is implemented by overriding the inflated nav graph's `startDestination` in `MainActivity.onCreate` before binding the graph to the controller.

**Tech Stack:** Kotlin 1.9.21 · Android Gradle 8.2 · Hilt 2.50 (KSP processor) · Jetpack Navigation 2.7.6 · Jetpack DataStore 1.0.0 · `androidx.core:core-splashscreen` 1.0.1 · JUnit 4 with real-collaborator integration tests over in-memory DataStore (no Mockito needed in this sub-project) · Espresso + Hilt testing (instrumented).

**Spec source:** `docs/superpowers/specs/2026-04-26-foundation-design.md` (commit `1279010` or later).

---

## File map

| File | Purpose |
|---|---|
| `build.gradle.kts` | Root project — register the Hilt Gradle plugin. |
| `app/build.gradle.kts` | App module — apply Hilt, add Hilt + SplashScreen + Hilt-test deps, set test runner. |
| `app/src/main/java/org/spsl/evtracker/EVTrackerApp.kt` | `@HiltAndroidApp` Application; reads theme pref and applies `AppCompatDelegate` mode at launch. |
| `app/src/main/java/org/spsl/evtracker/MainActivity.kt` | Single Activity, `@AndroidEntryPoint`. Hosts nav graph, runs wizard gate. |
| `app/src/main/java/org/spsl/evtracker/di/AppModule.kt` | Hilt `@InstallIn(SingletonComponent)` module providing `DataStore<Preferences>`. |
| `app/src/main/java/org/spsl/evtracker/data/preferences/PreferenceKeys.kt` | Canonical key set (all 7 keys from DESIGN §3.3). |
| `app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt` | Flow accessors + writers for the 5 keys A actually uses. |
| `app/src/main/java/org/spsl/evtracker/ui/wizard/WizardViewModel.kt` | Wizard state machine including metric ↔ unit coupling. |
| `app/src/main/java/org/spsl/evtracker/ui/wizard/WizardFragment.kt` | ViewPager2 host with Back/Next/Finish buttons. |
| `app/src/main/java/org/spsl/evtracker/ui/wizard/WizardPagerAdapter.kt` | `FragmentStateAdapter` returning the 3 pages. |
| `app/src/main/java/org/spsl/evtracker/ui/wizard/WizardPage1Fragment.kt` | Welcome page. |
| `app/src/main/java/org/spsl/evtracker/ui/wizard/WizardPage2Fragment.kt` | Metric + unit page. |
| `app/src/main/java/org/spsl/evtracker/ui/wizard/WizardPage3Fragment.kt` | Currency page. |
| `app/src/main/java/org/spsl/evtracker/ui/{dashboard,chargeedit,cars,settings,charts,history,locations}/<Name>Fragment.kt` | Per-screen placeholder Fragment + ViewModel pairs (×7). |
| `app/src/main/res/layout/activity_main.xml` | `FragmentContainerView` for the nav host. |
| `app/src/main/res/layout/fragment_wizard.xml` | Wizard host layout (ViewPager2 + TabLayout dots + buttons). |
| `app/src/main/res/layout/fragment_wizard_page{1,2,3}.xml` | Wizard page contents. |
| `app/src/main/res/layout/fragment_<screen>.xml` | One placeholder layout per screen (×7). |
| `app/src/main/res/navigation/nav_graph.xml` | 8 destinations + `action_wizard_to_dashboard`. |
| `app/src/main/res/values/currencies.xml` | `<string-array name="supported_currencies">` with 13 codes. |
| `app/src/main/res/values/themes.xml` *(modify)* | Add `Theme.EVTracker.SplashScreen`. |
| `app/src/main/res/values/strings.xml` *(modify)* | Add wizard strings. |
| `app/src/main/AndroidManifest.xml` *(modify)* | Set `android:name=".EVTrackerApp"` and the splash theme. |
| `app/src/test/java/org/spsl/evtracker/data/repository/SettingsRepositoryTest.kt` | JVM tests for repo defaults, atomic write, theme persistence, reset. |
| `app/src/test/java/org/spsl/evtracker/ui/wizard/WizardViewModelTest.kt` | JVM tests for finish, coupling rules, page navigation clamps. |
| `app/src/androidTest/java/org/spsl/evtracker/HiltTestRunner.kt` | `AndroidJUnitRunner` subclass that launches `HiltTestApplication`. |
| `app/src/androidTest/java/org/spsl/evtracker/ui/wizard/WizardFlowTest.kt` | Espresso tests for first-launch gate and full wizard walk-through. |

---

## Notes for the worker

> **Sandbox quirks (added retroactively after this plan first ran).** If you are executing this plan inside the Claude Code sandbox, Gradle's default `~/.gradle` is on a read-only filesystem. ALWAYS prefix gradle invocations with `GRADLE_USER_HOME=/tmp/gradle-home` (the directory may not exist yet — `mkdir -p /tmp/gradle-home` first) and pass `dangerouslyDisableSandbox: true` to your Bash tool calls. Plain `./gradlew help` will fail with `Failed to load native library 'libnative-platform.so'` because the cache directory isn't writable. This applies to every `./gradlew …` command shown in the steps below — they're written without the prefix for readability, but you must add it. Outside the sandbox (a regular dev machine), the unprefixed commands work as written.
>
> Per CLAUDE.md: never compound git commands with `&&`/`||`/`;`. Run `git add` and `git commit` as separate Bash calls.
>
> The very first task here also assumes the gradle wrapper exists. In the original execution, the wrapper had to be regenerated (`gradle wrapper --gradle-version 8.4`) as a setup-fix commit before Task 1 could verify its build. If `./gradlew` doesn't exist, regenerate it the same way before proceeding.

---

## Task 1: Build configuration — add Hilt, SplashScreen, test runner

**Files:**
- Modify: `build.gradle.kts` (root)
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add Hilt plugin to the root `build.gradle.kts`**

Replace the entire file with:

```kotlin
// Top-level build file
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.21" apply false
    id("com.google.devtools.ksp") version "1.9.21-1.0.16" apply false
    id("com.google.dagger.hilt.android") version "2.50" apply false
}
```

- [ ] **Step 2: Apply Hilt plugin and add new dependencies in `app/build.gradle.kts`**

Replace the `plugins { ... }` block at the top of `app/build.gradle.kts` with:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}
```

In the same file, replace the `defaultConfig { ... }` block's `testInstrumentationRunner` line:

```kotlin
testInstrumentationRunner = "org.spsl.evtracker.HiltTestRunner"
```

(The runner class itself is created in Task 13; until then `connectedAndroidTest` cannot run, but `assembleDebug` and `test` work fine.)

In the `dependencies { ... }` block of the same file, add these lines (keep all existing dependencies; just append):

```kotlin
implementation("com.google.dagger:hilt-android:2.50")
ksp("com.google.dagger:hilt-android-compiler:2.50")
implementation("androidx.hilt:hilt-navigation-fragment:1.1.0")
implementation("androidx.core:core-splashscreen:1.0.1")
testImplementation("com.google.dagger:hilt-android-testing:2.50")
kspTest("com.google.dagger:hilt-android-compiler:2.50")
androidTestImplementation("com.google.dagger:hilt-android-testing:2.50")
kspAndroidTest("com.google.dagger:hilt-android-compiler:2.50")
```

- [ ] **Step 3: Verify Gradle accepts the configuration**

Run: `./gradlew help`
Expected: `BUILD SUCCESSFUL`. No deprecation or unresolved-plugin errors.

- [ ] **Step 4: Commit**

```bash
git add build.gradle.kts app/build.gradle.kts
git commit -m "build: add Hilt 2.50 (KSP), SplashScreen, Hilt test runner config"
```

---

## Task 2: Application class skeleton, splash theme, manifest update

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/EVTrackerApp.kt`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create `EVTrackerApp` (no theme reading yet — added in Task 5)**

`app/src/main/java/org/spsl/evtracker/EVTrackerApp.kt`:

```kotlin
package org.spsl.evtracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EVTrackerApp : Application()
```

- [ ] **Step 2: Add the SplashScreen theme**

Append to `app/src/main/res/values/themes.xml` *inside* the `<resources>` element (do not change the existing `Theme.EVTracker` style):

```xml
<style name="Theme.EVTracker.SplashScreen" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">?android:colorBackground</item>
    <item name="postSplashScreenTheme">@style/Theme.EVTracker</item>
</style>
```

- [ ] **Step 3: Wire the application class and splash theme in the manifest**

In `app/src/main/AndroidManifest.xml`, change the opening `<application ...>` tag so it includes:

```
android:name=".EVTrackerApp"
android:theme="@style/Theme.EVTracker.SplashScreen"
```

…replacing the existing `android:theme="@style/Theme.EVTracker"` attribute. Keep all other attributes (`allowBackup`, `icon`, `roundIcon`, `label`, `supportsRtl`) and all child elements (Activity, FileProvider, Play Services meta-data) untouched.

- [ ] **Step 4: Verify the build compiles with Hilt code generation**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. Hilt should generate `Hilt_EVTrackerApp` under `app/build/generated/`. If the build fails with `error: [Hilt] Application class …`, the `@HiltAndroidApp` annotation or the manifest `android:name` is wrong.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/EVTrackerApp.kt \
        app/src/main/res/values/themes.xml \
        app/src/main/AndroidManifest.xml
git commit -m "feat(foundation): add HiltAndroidApp + SplashScreen theme"
```

---

## Task 3: PreferenceKeys and DataStore Hilt module

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/data/preferences/PreferenceKeys.kt`
- Create: `app/src/main/java/org/spsl/evtracker/di/AppModule.kt`

- [ ] **Step 1: Create the canonical preference keys file (all 7 keys)**

`app/src/main/java/org/spsl/evtracker/data/preferences/PreferenceKeys.kt`:

```kotlin
package org.spsl.evtracker.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferenceKeys {
    val SETUP_COMPLETE = booleanPreferencesKey("setupComplete")
    val PRIMARY_METRIC = stringPreferencesKey("primaryMetric")
    val DISTANCE_UNIT  = stringPreferencesKey("distanceUnit")
    val CURRENCY       = stringPreferencesKey("currency")
    val ACTIVE_CAR_ID  = intPreferencesKey("activeCarId")     // consumed by Sub-project B
    val DRIVE_ENABLED  = booleanPreferencesKey("driveEnabled") // consumed by Sub-project E
    val THEME          = stringPreferencesKey("theme")
}
```

All 7 keys are declared even though only 5 are consumed in this sub-project — see spec §7.

- [ ] **Step 2: Create the Hilt module that provides DataStore**

`app/src/main/java/org/spsl/evtracker/di/AppModule.kt`:

```kotlin
package org.spsl.evtracker.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "evtracker_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore
}
```

- [ ] **Step 3: Verify the build still passes**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. Hilt code generation runs; no DI graph errors.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/data/preferences/PreferenceKeys.kt \
        app/src/main/java/org/spsl/evtracker/di/AppModule.kt
git commit -m "feat(foundation): add PreferenceKeys + DataStore Hilt module"
```

---

## Task 4: SettingsRepository — TDD

**Files:**
- Create: `app/src/test/java/org/spsl/evtracker/data/repository/SettingsRepositoryTest.kt`
- Create: `app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt`

- [ ] **Step 1: Write the failing test file**

`app/src/test/java/org/spsl/evtracker/data/repository/SettingsRepositoryTest.kt`:

```kotlin
package org.spsl.evtracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher()),
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        repo = SettingsRepository(dataStore)
    }

    @Test
    fun defaults_areExpected() = runTest {
        assertFalse(repo.setupComplete.first())
        assertEquals("km_per_kwh", repo.primaryMetric.first())
        assertEquals("km", repo.distanceUnit.first())
        assertEquals("EUR", repo.currency.first())
        assertEquals("system", repo.theme.first())
    }

    @Test
    fun completeSetup_writesAllFourKeysAtomically() = runTest {
        repo.completeSetup(metric = "mi_per_kwh", unit = "miles", currency = "USD")
        assertEquals("mi_per_kwh", repo.primaryMetric.first())
        assertEquals("miles", repo.distanceUnit.first())
        assertEquals("USD", repo.currency.first())
        assertTrue(repo.setupComplete.first())
    }

    @Test
    fun setTheme_persists() = runTest {
        repo.setTheme("dark")
        assertEquals("dark", repo.theme.first())
    }

    @Test
    fun resetSetupComplete_flipsFlag_butLeavesOtherKeysAlone() = runTest {
        repo.completeSetup(metric = "kwh_per_100km", unit = "km", currency = "GBP")
        repo.resetSetupComplete()
        assertFalse(repo.setupComplete.first())
        assertEquals("kwh_per_100km", repo.primaryMetric.first())
        assertEquals("km", repo.distanceUnit.first())
        assertEquals("GBP", repo.currency.first())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails (compile error — class doesn't exist)**

Run: `./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.data.repository.SettingsRepositoryTest"`
Expected: compilation failure — `SettingsRepository` is unresolved.

- [ ] **Step 3: Implement `SettingsRepository`**

`app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt`:

```kotlin
package org.spsl.evtracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.spsl.evtracker.data.preferences.PreferenceKeys

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val setupComplete: Flow<Boolean> =
        dataStore.data.map { it[PreferenceKeys.SETUP_COMPLETE] ?: false }

    val primaryMetric: Flow<String> =
        dataStore.data.map { it[PreferenceKeys.PRIMARY_METRIC] ?: "km_per_kwh" }

    val distanceUnit: Flow<String> =
        dataStore.data.map { it[PreferenceKeys.DISTANCE_UNIT] ?: "km" }

    val currency: Flow<String> =
        dataStore.data.map { it[PreferenceKeys.CURRENCY] ?: "EUR" }

    val theme: Flow<String> =
        dataStore.data.map { it[PreferenceKeys.THEME] ?: "system" }

    suspend fun completeSetup(metric: String, unit: String, currency: String) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.PRIMARY_METRIC] = metric
            prefs[PreferenceKeys.DISTANCE_UNIT]  = unit
            prefs[PreferenceKeys.CURRENCY]       = currency
            prefs[PreferenceKeys.SETUP_COMPLETE] = true
        }
    }

    suspend fun setTheme(theme: String) {
        dataStore.edit { it[PreferenceKeys.THEME] = theme }
    }

    /** Used by the future Settings → Reset preferences action (Sub-project F). */
    suspend fun resetSetupComplete() {
        dataStore.edit { it[PreferenceKeys.SETUP_COMPLETE] = false }
    }
}
```

- [ ] **Step 4: Run tests; verify all four pass**

Run: `./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.data.repository.SettingsRepositoryTest"`
Expected: 4 tests, 4 passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt \
        app/src/test/java/org/spsl/evtracker/data/repository/SettingsRepositoryTest.kt
git commit -m "feat(foundation): add SettingsRepository with 5 wizard/theme keys"
```

---

## Task 5: Apply theme preference at launch

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/EVTrackerApp.kt`

- [ ] **Step 1: Inject `SettingsRepository` and apply theme in `EVTrackerApp.onCreate`**

Replace the entire contents of `app/src/main/java/org/spsl/evtracker/EVTrackerApp.kt` with:

```kotlin
package org.spsl.evtracker

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.spsl.evtracker.data.repository.SettingsRepository

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

`@Inject` resolves only after `super.onCreate()` because that's when Hilt initializes its component, so the `runBlocking` read must come after `super.onCreate()`.

- [ ] **Step 2: Verify the build still compiles**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/EVTrackerApp.kt
git commit -m "feat(foundation): apply theme preference at app launch"
```

---

## Task 6: WizardViewModel — TDD

**Files:**
- Create: `app/src/test/java/org/spsl/evtracker/ui/wizard/WizardViewModelTest.kt`
- Create: `app/src/main/java/org/spsl/evtracker/ui/wizard/WizardViewModel.kt`

- [ ] **Step 1: Write the failing test file**

The test uses a real `SettingsRepository` over a temp-file DataStore (no mocking). All `selectMetric` / `selectUnit` / page-clamp assertions are pure state checks against `vm.state.value` and don't touch the repo at all; only `finish_writesAllPrefs` exercises the round-trip and asserts the expected DataStore writes via the repo's own Flow accessors.

`app/src/test/java/org/spsl/evtracker/ui/wizard/WizardViewModelTest.kt`:

```kotlin
package org.spsl.evtracker.ui.wizard

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.spsl.evtracker.data.repository.SettingsRepository

class WizardViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: SettingsRepository
    private lateinit var vm: WizardViewModel

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher()),
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        repo = SettingsRepository(dataStore)
        vm = WizardViewModel(repo)
    }

    @Test
    fun finish_writesAllPrefs() = runTest {
        vm.selectMetric("mi_per_kwh")    // forces unit to "miles"
        vm.selectCurrency("USD")
        vm.finish()
        assertEquals("mi_per_kwh", repo.primaryMetric.first())
        assertEquals("miles", repo.distanceUnit.first())
        assertEquals("USD", repo.currency.first())
        assertTrue(repo.setupComplete.first())
    }

    @Test
    fun coupling_miPerKwhForcesMiles() {
        vm.selectMetric("mi_per_kwh")
        assertEquals("miles", vm.state.value.unit)
    }

    @Test
    fun coupling_kmMetricForcesKm() {
        vm.selectMetric("mi_per_kwh")    // unit = "miles"
        vm.selectMetric("km_per_kwh")
        assertEquals("km", vm.state.value.unit)
    }

    @Test
    fun coupling_kwhPer100kmForcesKm() {
        vm.selectMetric("kwh_per_100km")
        assertEquals("km", vm.state.value.unit)
    }

    @Test
    fun manualUnit_kmFlipsMetricFromMiles() {
        vm.selectMetric("mi_per_kwh")
        vm.selectUnit("km")
        assertEquals("km_per_kwh", vm.state.value.metric)
    }

    @Test
    fun manualUnit_milesFlipsKmMetric() {
        // default metric is km_per_kwh
        vm.selectUnit("miles")
        assertEquals("mi_per_kwh", vm.state.value.metric)
    }

    @Test
    fun manualUnit_doesNotChangeKwhPer100km() {
        vm.selectMetric("kwh_per_100km")
        vm.selectUnit("miles")
        assertEquals("kwh_per_100km", vm.state.value.metric)
        assertEquals("miles", vm.state.value.unit)
    }

    @Test
    fun goNext_clampsAtPage2() {
        repeat(5) { vm.goNext() }
        assertEquals(2, vm.state.value.page)
    }

    @Test
    fun goBack_clampsAtPage0() {
        repeat(3) { vm.goBack() }
        assertEquals(0, vm.state.value.page)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.ui.wizard.WizardViewModelTest"`
Expected: compilation failure — `WizardViewModel` is unresolved.

- [ ] **Step 3: Implement `WizardViewModel`**

`app/src/main/java/org/spsl/evtracker/ui/wizard/WizardViewModel.kt`:

```kotlin
package org.spsl.evtracker.ui.wizard

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.spsl.evtracker.data.repository.SettingsRepository

@HiltViewModel
class WizardViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    data class UiState(
        val page: Int = 0,
        val metric: String = "km_per_kwh",
        val unit: String = "km",
        val currency: String = "EUR"
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun goNext() {
        _state.update { it.copy(page = (it.page + 1).coerceAtMost(2)) }
    }

    fun goBack() {
        _state.update { it.copy(page = (it.page - 1).coerceAtLeast(0)) }
    }

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
                unit == "km" && current.metric == "mi_per_kwh"        -> "km_per_kwh"
                else -> current.metric
            }
            current.copy(unit = unit, metric = coupledMetric)
        }
    }

    fun selectCurrency(currency: String) {
        _state.update { it.copy(currency = currency) }
    }

    suspend fun finish() {
        val s = state.value
        settingsRepository.completeSetup(
            metric = s.metric,
            unit = s.unit,
            currency = s.currency
        )
    }
}
```

- [ ] **Step 4: Run tests; verify all 9 pass**

Run: `./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.ui.wizard.WizardViewModelTest"`
Expected: 9 tests, 9 passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/ui/wizard/WizardViewModel.kt \
        app/src/test/java/org/spsl/evtracker/ui/wizard/WizardViewModelTest.kt
git commit -m "feat(wizard): add WizardViewModel with metric/unit coupling"
```

---

## Task 7: Wizard layouts, currencies array, strings

**Files:**
- Create: `app/src/main/res/values/currencies.xml`
- Create: `app/src/main/res/layout/fragment_wizard.xml`
- Create: `app/src/main/res/layout/fragment_wizard_page1.xml`
- Create: `app/src/main/res/layout/fragment_wizard_page2.xml`
- Create: `app/src/main/res/layout/fragment_wizard_page3.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add the supported-currencies string array**

`app/src/main/res/values/currencies.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string-array name="supported_currencies">
        <item>EUR</item>
        <item>USD</item>
        <item>GBP</item>
        <item>CHF</item>
        <item>JPY</item>
        <item>CZK</item>
        <item>PLN</item>
        <item>HUF</item>
        <item>DKK</item>
        <item>SEK</item>
        <item>NOK</item>
        <item>AUD</item>
        <item>CAD</item>
    </string-array>
</resources>
```

- [ ] **Step 2: Add wizard strings**

In `app/src/main/res/values/strings.xml`, append the following entries inside the existing `<resources>` element (do not remove any existing strings):

```xml
<string name="wizard_welcome_title">⚡ EV Efficiency Tracker</string>
<string name="wizard_welcome_body">Let\'s set up your preferences — you can change these later in Settings at any time.</string>
<string name="wizard_metric_question">How do you like to see efficiency?</string>
<string name="wizard_metric_km_per_kwh">km / kWh (distance / energy)</string>
<string name="wizard_metric_kwh_per_100km">kWh / 100 km (energy / distance)</string>
<string name="wizard_metric_mi_per_kwh">mi / kWh (miles)</string>
<string name="wizard_unit_label">Distance unit</string>
<string name="wizard_unit_km">km</string>
<string name="wizard_unit_miles">miles</string>
<string name="wizard_currency_question">What currency do you use for charging costs?</string>
<string name="wizard_currency_hint">Cost entry is optional — leave 0 to skip tracking.</string>
<string name="wizard_button_back">← Back</string>
<string name="wizard_button_next">Next →</string>
<string name="wizard_button_get_started">Get Started</string>
<string name="wizard_button_finish">Finish ✓</string>
<string name="placeholder_dashboard">Dashboard — coming soon</string>
<string name="placeholder_charge_edit">Charge Edit — coming soon</string>
<string name="placeholder_cars">Cars — coming soon</string>
<string name="placeholder_settings">Settings — coming soon</string>
<string name="placeholder_charts">Charts — coming soon</string>
<string name="placeholder_history">History — coming soon</string>
<string name="placeholder_manage_locations">Manage Locations — coming soon</string>
```

- [ ] **Step 3: Create the wizard host layout**

`app/src/main/res/layout/fragment_wizard.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/wizard_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="16dp">

    <com.google.android.material.tabs.TabLayout
        android:id="@+id/wizard_dots"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        app:tabBackground="@android:color/transparent"
        app:tabIndicatorHeight="0dp"
        app:tabGravity="center"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"/>

    <androidx.viewpager2.widget.ViewPager2
        android:id="@+id/wizard_pager"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintTop_toBottomOf="@id/wizard_dots"
        app:layout_constraintBottom_toTopOf="@id/wizard_button_back"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"/>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/wizard_button_back"
        style="@style/Widget.Material3.Button.TextButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/wizard_button_back"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"/>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/wizard_button_next"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/wizard_button_get_started"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"/>

</androidx.constraintlayout.widget.ConstraintLayout>
```

- [ ] **Step 4: Create wizard page 1 layout**

`app/src/main/res/layout/fragment_wizard_page1.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:gravity="center"
    android:orientation="vertical"
    android:padding="32dp">

    <TextView
        android:id="@+id/wizard_page1_title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/wizard_welcome_title"
        android:textAppearance="?attr/textAppearanceHeadlineSmall"/>

    <TextView
        android:id="@+id/wizard_page1_body"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"
        android:gravity="center"
        android:text="@string/wizard_welcome_body"
        android:textAppearance="?attr/textAppearanceBodyMedium"/>

</LinearLayout>
```

- [ ] **Step 5: Create wizard page 2 layout**

`app/src/main/res/layout/fragment_wizard_page2.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="24dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/wizard_metric_question"
            android:textAppearance="?attr/textAppearanceTitleMedium"/>

        <RadioGroup
            android:id="@+id/wizard_page2_metric_group"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:orientation="vertical">

            <RadioButton
                android:id="@+id/wizard_page2_metric_km_per_kwh"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/wizard_metric_km_per_kwh"/>

            <RadioButton
                android:id="@+id/wizard_page2_metric_kwh_per_100km"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/wizard_metric_kwh_per_100km"/>

            <RadioButton
                android:id="@+id/wizard_page2_metric_mi_per_kwh"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/wizard_metric_mi_per_kwh"/>
        </RadioGroup>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="@string/wizard_unit_label"
            android:textAppearance="?attr/textAppearanceTitleMedium"/>

        <com.google.android.material.button.MaterialButtonToggleGroup
            android:id="@+id/wizard_page2_unit_group"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            app:singleSelection="true"
            app:selectionRequired="true"
            xmlns:app="http://schemas.android.com/apk/res-auto">

            <com.google.android.material.button.MaterialButton
                android:id="@+id/wizard_page2_unit_km"
                style="?attr/materialButtonOutlinedStyle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/wizard_unit_km"/>

            <com.google.android.material.button.MaterialButton
                android:id="@+id/wizard_page2_unit_miles"
                style="?attr/materialButtonOutlinedStyle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/wizard_unit_miles"/>
        </com.google.android.material.button.MaterialButtonToggleGroup>

    </LinearLayout>
</ScrollView>
```

- [ ] **Step 6: Create wizard page 3 layout**

`app/src/main/res/layout/fragment_wizard_page3.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="24dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/wizard_currency_question"
        android:textAppearance="?attr/textAppearanceTitleMedium"/>

    <com.google.android.material.textfield.TextInputLayout
        android:id="@+id/wizard_page3_currency_layout"
        style="@style/Widget.Material3.TextInputLayout.OutlinedBox.ExposedDropdownMenu"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp">

        <com.google.android.material.textfield.MaterialAutoCompleteTextView
            android:id="@+id/wizard_page3_currency_input"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="none"
            android:text="EUR"/>
    </com.google.android.material.textfield.TextInputLayout>

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:text="@string/wizard_currency_hint"
        android:textAppearance="?attr/textAppearanceBodySmall"/>

</LinearLayout>
```

- [ ] **Step 7: Verify the build still compiles**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. ViewBinding generates `FragmentWizardBinding`, `FragmentWizardPage1Binding`, `FragmentWizardPage2Binding`, `FragmentWizardPage3Binding`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/res/values/currencies.xml \
        app/src/main/res/values/strings.xml \
        app/src/main/res/layout/fragment_wizard.xml \
        app/src/main/res/layout/fragment_wizard_page1.xml \
        app/src/main/res/layout/fragment_wizard_page2.xml \
        app/src/main/res/layout/fragment_wizard_page3.xml
git commit -m "feat(wizard): add layouts, currencies array, strings"
```

---

## Task 8: Wizard page Fragments

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/ui/wizard/WizardPage1Fragment.kt`
- Create: `app/src/main/java/org/spsl/evtracker/ui/wizard/WizardPage2Fragment.kt`
- Create: `app/src/main/java/org/spsl/evtracker/ui/wizard/WizardPage3Fragment.kt`

- [ ] **Step 1: Create page 1 (Welcome) — shares parent's `WizardViewModel`**

`app/src/main/java/org/spsl/evtracker/ui/wizard/WizardPage1Fragment.kt`:

```kotlin
package org.spsl.evtracker.ui.wizard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import org.spsl.evtracker.databinding.FragmentWizardPage1Binding

@AndroidEntryPoint
class WizardPage1Fragment : Fragment() {

    private var _binding: FragmentWizardPage1Binding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWizardPage1Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

Page 1 has no interactive elements and doesn't need access to the wizard VM. It stays `@AndroidEntryPoint` for symmetry with pages 2 and 3.

- [ ] **Step 2: Create page 2 (Metric + unit) — wires RadioGroup and toggle to the VM**

`app/src/main/java/org/spsl/evtracker/ui/wizard/WizardPage2Fragment.kt`:

```kotlin
package org.spsl.evtracker.ui.wizard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.databinding.FragmentWizardPage2Binding

@AndroidEntryPoint
class WizardPage2Fragment : Fragment() {

    private val viewModel: WizardViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    private var _binding: FragmentWizardPage2Binding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWizardPage2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.wizardPage2MetricGroup.setOnCheckedChangeListener { _, checkedId ->
            val metric = when (checkedId) {
                R.id.wizard_page2_metric_km_per_kwh    -> "km_per_kwh"
                R.id.wizard_page2_metric_kwh_per_100km -> "kwh_per_100km"
                R.id.wizard_page2_metric_mi_per_kwh    -> "mi_per_kwh"
                else -> return@setOnCheckedChangeListener
            }
            if (metric != viewModel.state.value.metric) {
                viewModel.selectMetric(metric)
            }
        }

        binding.wizardPage2UnitGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val unit = when (checkedId) {
                R.id.wizard_page2_unit_km    -> "km"
                R.id.wizard_page2_unit_miles -> "miles"
                else -> return@addOnButtonCheckedListener
            }
            if (unit != viewModel.state.value.unit) {
                viewModel.selectUnit(unit)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val expectedRadio = when (state.metric) {
                        "km_per_kwh"    -> R.id.wizard_page2_metric_km_per_kwh
                        "kwh_per_100km" -> R.id.wizard_page2_metric_kwh_per_100km
                        else            -> R.id.wizard_page2_metric_mi_per_kwh
                    }
                    if (binding.wizardPage2MetricGroup.checkedRadioButtonId != expectedRadio) {
                        binding.wizardPage2MetricGroup.check(expectedRadio)
                    }
                    val expectedToggle = if (state.unit == "miles")
                        R.id.wizard_page2_unit_miles
                    else
                        R.id.wizard_page2_unit_km
                    if (binding.wizardPage2UnitGroup.checkedButtonId != expectedToggle) {
                        binding.wizardPage2UnitGroup.check(expectedToggle)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

- [ ] **Step 3: Create page 3 (Currency) — wires the dropdown to the VM**

`app/src/main/java/org/spsl/evtracker/ui/wizard/WizardPage3Fragment.kt`:

```kotlin
package org.spsl.evtracker.ui.wizard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.spsl.evtracker.R
import org.spsl.evtracker.databinding.FragmentWizardPage3Binding

@AndroidEntryPoint
class WizardPage3Fragment : Fragment() {

    private val viewModel: WizardViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    private var _binding: FragmentWizardPage3Binding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWizardPage3Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currencies = resources.getStringArray(R.array.supported_currencies)
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            currencies
        )
        binding.wizardPage3CurrencyInput.setAdapter(adapter)
        binding.wizardPage3CurrencyInput.setText(viewModel.state.value.currency, false)
        binding.wizardPage3CurrencyInput.setOnItemClickListener { _, _, position, _ ->
            viewModel.selectCurrency(currencies[position])
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

- [ ] **Step 4: Verify the build compiles**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/ui/wizard/WizardPage1Fragment.kt \
        app/src/main/java/org/spsl/evtracker/ui/wizard/WizardPage2Fragment.kt \
        app/src/main/java/org/spsl/evtracker/ui/wizard/WizardPage3Fragment.kt
git commit -m "feat(wizard): add page fragments with parent-scoped VM"
```

---

## Task 9: WizardFragment host + WizardPagerAdapter

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/ui/wizard/WizardPagerAdapter.kt`
- Create: `app/src/main/java/org/spsl/evtracker/ui/wizard/WizardFragment.kt`

- [ ] **Step 1: Create the pager adapter**

`app/src/main/java/org/spsl/evtracker/ui/wizard/WizardPagerAdapter.kt`:

```kotlin
package org.spsl.evtracker.ui.wizard

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class WizardPagerAdapter(host: Fragment) : FragmentStateAdapter(host) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> WizardPage1Fragment()
        1 -> WizardPage2Fragment()
        2 -> WizardPage3Fragment()
        else -> error("Wizard has only 3 pages, got position=$position")
    }
}
```

- [ ] **Step 2: Create the wizard host fragment**

`app/src/main/java/org/spsl/evtracker/ui/wizard/WizardFragment.kt`:

```kotlin
package org.spsl.evtracker.ui.wizard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.databinding.FragmentWizardBinding

@AndroidEntryPoint
class WizardFragment : Fragment() {

    private val viewModel: WizardViewModel by viewModels()

    private var _binding: FragmentWizardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWizardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.wizardPager.adapter = WizardPagerAdapter(this)
        binding.wizardPager.isUserInputEnabled = false

        TabLayoutMediator(binding.wizardDots, binding.wizardPager) { _, _ -> }.attach()

        binding.wizardButtonBack.setOnClickListener { viewModel.goBack() }
        binding.wizardButtonNext.setOnClickListener { onPrimaryButtonClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    if (binding.wizardPager.currentItem != state.page) {
                        binding.wizardPager.setCurrentItem(state.page, true)
                    }
                    binding.wizardButtonBack.visibility =
                        if (state.page == 0) View.INVISIBLE else View.VISIBLE
                    binding.wizardButtonNext.text = when (state.page) {
                        0 -> getString(R.string.wizard_button_get_started)
                        1 -> getString(R.string.wizard_button_next)
                        else -> getString(R.string.wizard_button_finish)
                    }
                }
            }
        }
    }

    private fun onPrimaryButtonClicked() {
        if (viewModel.state.value.page < 2) {
            viewModel.goNext()
        } else {
            binding.wizardButtonNext.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.finish()
                // Real navigation is wired in Task 11 once nav_graph.xml exists.
                android.util.Log.i("WizardFragment", "Wizard finished — navigation wired in Task 11")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.wizardPager.adapter = null
        _binding = null
    }
}
```

The `viewModel.finish()` call is `suspend`; whatever runs after it (the `Log.i` now, the `findNavController().navigate(...)` after Task 11) only fires once DataStore has been written. This guarantees the dashboard never appears with `setupComplete=false` in storage.

- [ ] **Step 3: Verify the build compiles cleanly**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. (Real navigation is wired in Task 11.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/ui/wizard/WizardPagerAdapter.kt \
        app/src/main/java/org/spsl/evtracker/ui/wizard/WizardFragment.kt
git commit -m "feat(wizard): add WizardFragment host + pager adapter"
```

---

## Task 10: Placeholder destination Fragments (×7)

**Files (created in this task):**
- `app/src/main/res/layout/fragment_dashboard.xml`
- `app/src/main/res/layout/fragment_charge_edit.xml`
- `app/src/main/res/layout/fragment_cars.xml`
- `app/src/main/res/layout/fragment_settings.xml`
- `app/src/main/res/layout/fragment_charts.xml`
- `app/src/main/res/layout/fragment_history.xml`
- `app/src/main/res/layout/fragment_manage_locations.xml`
- `app/src/main/java/org/spsl/evtracker/ui/dashboard/DashboardFragment.kt` + `DashboardViewModel.kt`
- `app/src/main/java/org/spsl/evtracker/ui/chargeedit/ChargeEditFragment.kt` + `ChargeEditViewModel.kt`
- `app/src/main/java/org/spsl/evtracker/ui/cars/CarsFragment.kt` + `CarsViewModel.kt`
- `app/src/main/java/org/spsl/evtracker/ui/settings/SettingsFragment.kt` + `SettingsViewModel.kt`
- `app/src/main/java/org/spsl/evtracker/ui/charts/ChartsFragment.kt` + `ChartsViewModel.kt`
- `app/src/main/java/org/spsl/evtracker/ui/history/HistoryFragment.kt` + `HistoryViewModel.kt`
- `app/src/main/java/org/spsl/evtracker/ui/locations/ManageLocationsFragment.kt` + `ManageLocationsViewModel.kt`

All 7 follow the same pattern. Below are full templates for the **dashboard** screen; apply identically to the other 6, substituting the marked tokens.

- [ ] **Step 1: Create the placeholder layout for each screen**

Template for `fragment_dashboard.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:id="@+id/dashboard_placeholder_text"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:text="@string/placeholder_dashboard"
        android:textAppearance="?attr/textAppearanceTitleMedium"/>

</FrameLayout>
```

For each of the other 6 layouts, create the file with the same structure, substituting:

| Layout file | TextView `android:id` | TextView `android:text` |
|---|---|---|
| `fragment_charge_edit.xml` | `@+id/charge_edit_placeholder_text` | `@string/placeholder_charge_edit` |
| `fragment_cars.xml` | `@+id/cars_placeholder_text` | `@string/placeholder_cars` |
| `fragment_settings.xml` | `@+id/settings_placeholder_text` | `@string/placeholder_settings` |
| `fragment_charts.xml` | `@+id/charts_placeholder_text` | `@string/placeholder_charts` |
| `fragment_history.xml` | `@+id/history_placeholder_text` | `@string/placeholder_history` |
| `fragment_manage_locations.xml` | `@+id/manage_locations_placeholder_text` | `@string/placeholder_manage_locations` |

- [ ] **Step 2: Create the placeholder ViewModel for each screen**

Template for `DashboardViewModel.kt`:

```kotlin
package org.spsl.evtracker.ui.dashboard

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor() : ViewModel()
```

Apply identically to:

| File | Package | Class name |
|---|---|---|
| `ui/chargeedit/ChargeEditViewModel.kt` | `org.spsl.evtracker.ui.chargeedit` | `ChargeEditViewModel` |
| `ui/cars/CarsViewModel.kt` | `org.spsl.evtracker.ui.cars` | `CarsViewModel` |
| `ui/settings/SettingsViewModel.kt` | `org.spsl.evtracker.ui.settings` | `SettingsViewModel` |
| `ui/charts/ChartsViewModel.kt` | `org.spsl.evtracker.ui.charts` | `ChartsViewModel` |
| `ui/history/HistoryViewModel.kt` | `org.spsl.evtracker.ui.history` | `HistoryViewModel` |
| `ui/locations/ManageLocationsViewModel.kt` | `org.spsl.evtracker.ui.locations` | `ManageLocationsViewModel` |

- [ ] **Step 3: Create the placeholder Fragment for each screen**

Template for `DashboardFragment.kt`:

```kotlin
package org.spsl.evtracker.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.spsl.evtracker.databinding.FragmentDashboardBinding

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    @Suppress("unused")
    private val viewModel: DashboardViewModel by viewModels()

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

Apply identically to the 6 remaining screens, substituting:

| File | Package | Class name | ViewModel | ViewBinding class |
|---|---|---|---|---|
| `ui/chargeedit/ChargeEditFragment.kt` | `org.spsl.evtracker.ui.chargeedit` | `ChargeEditFragment` | `ChargeEditViewModel` | `FragmentChargeEditBinding` |
| `ui/cars/CarsFragment.kt` | `org.spsl.evtracker.ui.cars` | `CarsFragment` | `CarsViewModel` | `FragmentCarsBinding` |
| `ui/settings/SettingsFragment.kt` | `org.spsl.evtracker.ui.settings` | `SettingsFragment` | `SettingsViewModel` | `FragmentSettingsBinding` |
| `ui/charts/ChartsFragment.kt` | `org.spsl.evtracker.ui.charts` | `ChartsFragment` | `ChartsViewModel` | `FragmentChartsBinding` |
| `ui/history/HistoryFragment.kt` | `org.spsl.evtracker.ui.history` | `HistoryFragment` | `HistoryViewModel` | `FragmentHistoryBinding` |
| `ui/locations/ManageLocationsFragment.kt` | `org.spsl.evtracker.ui.locations` | `ManageLocationsFragment` | `ManageLocationsViewModel` | `FragmentManageLocationsBinding` |

- [ ] **Step 4: Verify the build compiles**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. ViewBinding generates 7 binding classes; Hilt generates 7 `Hilt_<screen>Fragment` classes.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/ui/dashboard \
        app/src/main/java/org/spsl/evtracker/ui/chargeedit \
        app/src/main/java/org/spsl/evtracker/ui/cars \
        app/src/main/java/org/spsl/evtracker/ui/settings \
        app/src/main/java/org/spsl/evtracker/ui/charts \
        app/src/main/java/org/spsl/evtracker/ui/history \
        app/src/main/java/org/spsl/evtracker/ui/locations \
        app/src/main/res/layout/fragment_dashboard.xml \
        app/src/main/res/layout/fragment_charge_edit.xml \
        app/src/main/res/layout/fragment_cars.xml \
        app/src/main/res/layout/fragment_settings.xml \
        app/src/main/res/layout/fragment_charts.xml \
        app/src/main/res/layout/fragment_history.xml \
        app/src/main/res/layout/fragment_manage_locations.xml
git commit -m "feat(foundation): add 7 placeholder destination fragments"
```

---

## Task 11: Nav graph + activity_main + replace temporary log in WizardFragment

**Files:**
- Create: `app/src/main/res/navigation/nav_graph.xml`
- Create: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/java/org/spsl/evtracker/ui/wizard/WizardFragment.kt`

- [ ] **Step 1: Create the navigation graph**

`app/src/main/res/navigation/nav_graph.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_graph"
    app:startDestination="@id/dashboardFragment">

    <fragment
        android:id="@+id/wizardFragment"
        android:name="org.spsl.evtracker.ui.wizard.WizardFragment"
        android:label="Setup">
        <action
            android:id="@+id/action_wizard_to_dashboard"
            app:destination="@id/dashboardFragment"
            app:popUpTo="@id/wizardFragment"
            app:popUpToInclusive="true"/>
    </fragment>

    <fragment
        android:id="@+id/dashboardFragment"
        android:name="org.spsl.evtracker.ui.dashboard.DashboardFragment"
        android:label="Dashboard"/>

    <fragment
        android:id="@+id/chargeEditFragment"
        android:name="org.spsl.evtracker.ui.chargeedit.ChargeEditFragment"
        android:label="Charge"/>

    <fragment
        android:id="@+id/carsFragment"
        android:name="org.spsl.evtracker.ui.cars.CarsFragment"
        android:label="Cars"/>

    <fragment
        android:id="@+id/settingsFragment"
        android:name="org.spsl.evtracker.ui.settings.SettingsFragment"
        android:label="Settings"/>

    <fragment
        android:id="@+id/chartsFragment"
        android:name="org.spsl.evtracker.ui.charts.ChartsFragment"
        android:label="Charts"/>

    <fragment
        android:id="@+id/historyFragment"
        android:name="org.spsl.evtracker.ui.history.HistoryFragment"
        android:label="History"/>

    <fragment
        android:id="@+id/manageLocationsFragment"
        android:name="org.spsl.evtracker.ui.locations.ManageLocationsFragment"
        android:label="Manage Locations"/>

</navigation>
```

- [ ] **Step 2: Create the activity layout**

`app/src/main/res/layout/activity_main.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.fragment.app.FragmentContainerView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_host_fragment"
    android:name="androidx.navigation.fragment.NavHostFragment"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:defaultNavHost="true"/>
```

Note: `app:navGraph` is **deliberately omitted**. `MainActivity.onCreate` inflates the graph itself, optionally overrides the start destination, and then assigns it via `navController.graph = graph`. Letting the FragmentContainerView auto-inflate via `app:navGraph` would cause the graph to be inflated twice and would race with the wizard gate's `setStartDestination` call.

- [ ] **Step 3: Replace the temporary log call in `WizardFragment.kt` with real navigation**

In `app/src/main/java/org/spsl/evtracker/ui/wizard/WizardFragment.kt`, find:

```kotlin
android.util.Log.i("WizardFragment", "Wizard finished — navigation wired in Task 11")
```

Replace with:

```kotlin
findNavController().navigate(R.id.action_wizard_to_dashboard)
```

- [ ] **Step 4: Verify the build compiles**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/navigation/nav_graph.xml \
        app/src/main/res/layout/activity_main.xml \
        app/src/main/java/org/spsl/evtracker/ui/wizard/WizardFragment.kt
git commit -m "feat(foundation): add nav graph + activity layout, wire wizard navigation"
```

---

## Task 12: MainActivity with wizard gate

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/MainActivity.kt`

- [ ] **Step 1: Create `MainActivity`**

`app/src/main/java/org/spsl/evtracker/MainActivity.kt`:

```kotlin
package org.spsl.evtracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.fragment.NavHostFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.spsl.evtracker.data.repository.SettingsRepository

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
        if (!complete) {
            graph.setStartDestination(R.id.wizardFragment)
        }
        navController.graph = graph

        splash.setKeepOnScreenCondition { false }
    }
}
```

The graph already declares `app:startDestination="@id/dashboardFragment"` in XML; the override only fires on first launch.

- [ ] **Step 2: Verify the build and a manual smoke test**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. APK at `app/build/outputs/apk/debug/app-debug.apk`.

If an emulator or device is available, install and walk through the wizard manually:
- Fresh install → wizard page 1 visible.
- Walk through pages, press Finish → "Dashboard — coming soon" placeholder.
- Force-stop and relaunch → "Dashboard — coming soon" directly (no wizard).

If no device is available, the instrumented tests added in Task 14 will cover this.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/MainActivity.kt
git commit -m "feat(foundation): add MainActivity with wizard gate"
```

---

## Task 13: Hilt instrumented test runner

**Files:**
- Create: `app/src/androidTest/java/org/spsl/evtracker/HiltTestRunner.kt`

- [ ] **Step 1: Create the runner**

`app/src/androidTest/java/org/spsl/evtracker/HiltTestRunner.kt`:

```kotlin
package org.spsl.evtracker

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
```

The `testInstrumentationRunner = "org.spsl.evtracker.HiltTestRunner"` line was already added in Task 1, so no further build config change is needed.

- [ ] **Step 2: Verify the runner compiles**

Run: `./gradlew compileDebugAndroidTestKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/org/spsl/evtracker/HiltTestRunner.kt
git commit -m "test(foundation): add Hilt instrumented test runner"
```

---

## Task 14: WizardFlowTest — instrumented gate test

**Files:**
- Create: `app/src/androidTest/java/org/spsl/evtracker/ui/wizard/WizardFlowTest.kt`

- [ ] **Step 1: Write the test**

`app/src/androidTest/java/org/spsl/evtracker/ui/wizard/WizardFlowTest.kt`:

```kotlin
package org.spsl.evtracker.ui.wizard

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoActivityResumedException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.MainActivity
import org.spsl.evtracker.R
import org.spsl.evtracker.data.preferences.PreferenceKeys

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WizardFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        hiltRule.inject()
        // Each test starts from a known-empty DataStore.
        runBlocking { dataStore.edit { it.clear() } }
    }

    @Test
    fun firstLaunch_showsWizard() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.wizard_root)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun completedSetup_skipsWizard() {
        runBlocking {
            dataStore.edit { it[PreferenceKeys.SETUP_COMPLETE] = true }
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.dashboard_placeholder_text)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun finishWizard_landsOnDashboard_andBackPressExitsApp() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Page 1 → Get Started
            onView(withId(R.id.wizard_button_next)).perform(click())
            // Page 2 → Next (default selections are valid)
            onView(withId(R.id.wizard_button_next)).perform(click())
            // Page 3 → Finish
            onView(withId(R.id.wizard_button_next)).perform(click())
            // Dashboard placeholder visible
            onView(withId(R.id.dashboard_placeholder_text)).check(matches(isDisplayed()))
            // Back press exits the app — wizard must NOT be on the back stack
            // (regression guard for losing popUpToInclusive on action_wizard_to_dashboard).
            try {
                Espresso.pressBack()
                throw AssertionError(
                    "Expected NoActivityResumedException — wizard is still on the back stack"
                )
            } catch (expected: NoActivityResumedException) {
                // Pass: there is no destination to pop, so the activity is finishing.
            }
        }
    }
}
```

`hiltRule.inject()` populates `dataStore`; the production `AppModule.provideDataStore` is used (no test-specific override needed). Clearing DataStore in `@Before` makes each test deterministic.

- [ ] **Step 2: Run the instrumented tests**

Make sure an emulator (API 26+) is running, then:

Run: `./gradlew :app:connectedDebugAndroidTest --tests "org.spsl.evtracker.ui.wizard.WizardFlowTest"`
Expected: 3 tests, 3 passing.

If `firstLaunch_showsWizard` fails because the dashboard is shown instead, the wizard gate is broken — verify that `MainActivity.onCreate` reads `setupComplete` *before* `navController.graph = graph`.

If `finishWizard_landsOnDashboard_andBackPressExitsApp` fails on the third click, check that `WizardFragment.onPrimaryButtonClicked` calls `findNavController().navigate(R.id.action_wizard_to_dashboard)` *after* `viewModel.finish()` returns. If the back-press assertion fails (no `NoActivityResumedException`), check that `action_wizard_to_dashboard` in `nav_graph.xml` still has `app:popUpTo="@id/wizardFragment"` and `app:popUpToInclusive="true"`.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/org/spsl/evtracker/ui/wizard/WizardFlowTest.kt
git commit -m "test(foundation): add WizardFlowTest covering gate + wizard walk-through"
```

---

## Task 15: Final acceptance verification

- [ ] **Step 1: Run full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: all tests pass — `SettingsRepositoryTest` (4) + `WizardViewModelTest` (9) = 13 tests.

- [ ] **Step 2: Run full instrumented test suite (emulator required)**

Run: `./gradlew :app:connectedDebugAndroidTest`
Expected: 3 instrumented tests pass.

- [ ] **Step 3: Build the debug APK**

Run: `./gradlew assembleDebug`
Expected: APK produced at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Manual smoke (if device/emulator available)**

Install the APK, then verify each acceptance criterion from spec §2.3:

1. Fresh install → wizard page 1 visible (no Dashboard flash).
2. Walk through all 3 pages, hit Finish → Dashboard placeholder visible.
3. Force-stop, relaunch → Dashboard placeholder visible directly.
4. Clear app data, relaunch, kill mid-wizard (do not press Finish) → next launch shows wizard page 1 again.

Step 4 of this manual check is the human equivalent of the dropped `killMidWizard_reShowsWizard` instrumented test; the JVM tests guarantee the underlying property (`setupComplete` is only ever true when `finish()` was called).

- [ ] **Step 5: Confirm Sub-project A is complete**

If steps 1–4 all pass, Sub-project A meets all acceptance criteria from spec §2.3. No additional commit — this is a verification-only task.

---

## Coverage check (spec → tasks)

| Spec section | Implemented in |
|---|---|
| §2.1 Hilt + DataStore + Nav graph + 8 Fragment skeletons | Tasks 1–3, 7–11 |
| §2.1 Real 3-page wizard | Tasks 6–9 |
| §2.1 Wizard finish writes 4 keys atomically | Task 4 (test) + Task 6 (VM) + Task 9 (UI) |
| §2.1 MainActivity routes with no first-launch flash | Task 12 |
| §2.1 Theme applied at launch | Task 5 |
| §2.1 PreferenceKeys.kt holds all 7 canonical keys | Task 3 |
| §2.3 acceptance criteria 1–4 | Task 15 |
| §3 Build configuration | Task 1 |
| §4 Manifest changes | Task 2 |
| §5 Source tree | Tasks 2, 3, 4, 6, 8, 9, 10, 12 |
| §6 Hilt + DataStore wiring | Tasks 2, 3, 5 |
| §7 SettingsRepository contract | Task 4 |
| §8 MainActivity wizard gate | Task 12 |
| §9 Nav graph (XML keeps `app:startDestination`) | Task 11 |
| §10 Wizard implementation (parent-fragment scoping) | Tasks 6–9 |
| §11.1 WizardViewModelTest | Task 6 |
| §11.2 SettingsRepositoryTest | Task 4 |
| §11.3 WizardFlowTest (3 tests; killMidWizard dropped per spec) | Task 14 |
| §12 No source-tree cleanup | (no task — confirmed in spec) |
