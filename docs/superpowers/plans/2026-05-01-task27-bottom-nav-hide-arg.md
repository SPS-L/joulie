# TASK-27 — Bottom-nav `hideBottomNav` argument — Implementation plan

> **For agentic workers:** small, surgical refactor. The decision moves
> from a Kotlin `setOf(…)` in `MainActivity` to a per-destination
> `<argument>` in `nav_graph.xml`. No JVM tests for the listener —
> the logic is one line of `Bundle.getBoolean` against
> Navigation-Component-provided defaults; the right test layer is
> instrumented.

**Goal:** Replace the hardcoded `hideOn` set in `MainActivity` with a
`hideBottomNav: Boolean` destination argument declared in
`nav_graph.xml`. Add a compiled instrumented test exercising the new
behavior.

**Architecture:** XML edits to four `<fragment>` blocks in
`nav_graph.xml`; one ten-line edit to `MainActivity.onCreate`; one new
instrumented-test file; CLAUDE.md note; BACKLOG.md outcome.

**Tech Stack:** Navigation Component (`androidx.navigation` 2.7.6,
already on classpath) · ViewBinding · Hilt activity-scenario testing
infrastructure that already exists in `androidTest/`.

---

## Files

- Modify: `app/src/main/res/navigation/nav_graph.xml`
- Modify: `app/src/main/java/org/spsl/evtracker/MainActivity.kt:67-76`
- Create: `app/src/androidTest/java/org/spsl/evtracker/MainActivityBottomNavTest.kt`
- Touch: `CLAUDE.md` (Architecture section convention paragraph)
- Touch: `docs/BACKLOG.md` (mark TASK-27 done with outcome blockquote)

---

## Task 1 — Add `hideBottomNav=true` argument to four destinations

- [ ] **Step 1: Edit `nav_graph.xml`**

For each of `wizardFragment`, `chargeEditFragment`, `carsFragment`,
`manageLocationsFragment`, add a child element:

```xml
<argument
    android:name="hideBottomNav"
    app:argType="boolean"
    android:defaultValue="true" />
```

- `wizardFragment`: insert before the existing `<action>`.
- `chargeEditFragment`: insert after the existing `eventId` argument.
- `carsFragment`: convert from self-closing `<fragment …/>` to
  `<fragment …>` + child `<argument/>` + closing `</fragment>`.
- `manageLocationsFragment`: same conversion as `carsFragment`.

The other four destinations (`dashboardFragment`, `historyFragment`,
`chartsFragment`, `settingsFragment`) get **no** argument — their
default `Bundle.getBoolean("hideBottomNav") == false` keeps the nav
visible.

- [ ] **Step 2: Build to validate XML**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. AAPT2 will fail loudly if any
`<argument>` syntax is wrong.

---

## Task 2 — Replace the hardcoded `hideOn` set in `MainActivity`

- [ ] **Step 1: Replace the listener block**

In `MainActivity.kt:67-76`, replace:

```kotlin
binding.bottomNav.setupWithNavController(navController)
val hideOn = setOf(
    R.id.wizardFragment,
    R.id.chargeEditFragment,
    R.id.carsFragment,
    R.id.manageLocationsFragment,
)
navController.addOnDestinationChangedListener { _, dest, _ ->
    binding.bottomNav.isVisible = dest.id !in hideOn
}
```

with:

```kotlin
binding.bottomNav.setupWithNavController(navController)
navController.addOnDestinationChangedListener { _, _, args ->
    val hide = args?.getBoolean("hideBottomNav") ?: false
    binding.bottomNav.isVisible = !hide
}
```

- [ ] **Step 2: Build**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. The unused `dest` parameter is now `_`,
which ktlint accepts.

- [ ] **Step 3: Acceptance grep — no destination IDs in `MainActivity`**

```bash
grep -n "R\.id\." app/src/main/java/org/spsl/evtracker/MainActivity.kt
```

Expected: only the survivors:
- `R.id.nav_host_fragment` (FragmentContainerView lookup)
- `R.id.wizardFragment` (start-destination override in `mountNavGraph`)
- `R.navigation.nav_graph` (nav-inflater)

The four `hideOn` IDs (`chargeEditFragment`, `carsFragment`,
`manageLocationsFragment`) must be **gone** from this file (a fourth
`R.id.wizardFragment` reference inside `hideOn` also disappears; the
remaining `R.id.wizardFragment` use is unrelated, in `mountNavGraph`).

---

## Task 3 — New instrumented test for the visibility behavior

- [ ] **Step 1: Create `MainActivityBottomNavTest.kt`**

Path:
`app/src/androidTest/java/org/spsl/evtracker/MainActivityBottomNavTest.kt`

```kotlin
package org.spsl.evtracker

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityBottomNavTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun bottomNav_visibleOnDashboard_hiddenOnChargeEdit() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                // Wait for nav graph to mount; in CI this is a one-frame
                // wait — Hilt fakes seed setupComplete=true so the start
                // destination resolves to dashboard.
                check(activity.isNavGraphMounted()) {
                    "Nav graph not mounted yet"
                }
                val bottomNav = activity.findViewById<View>(R.id.bottom_nav)
                assertEquals(
                    "dashboard should show bottom nav",
                    View.VISIBLE,
                    bottomNav.visibility,
                )

                val navHost = activity.supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                navHost.navController.navigate(
                    R.id.action_dashboard_to_chargeEdit,
                )
                assertEquals(
                    "chargeEdit should hide bottom nav",
                    View.GONE,
                    bottomNav.visibility,
                )

                navHost.navController.popBackStack()
                assertEquals(
                    "back to dashboard should show bottom nav",
                    View.VISIBLE,
                    bottomNav.visibility,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest
```

Expected: BUILD SUCCESSFUL. The sandbox cannot run instrumented
tests; compile is the gate. Running on an emulator is a follow-up
smoke item alongside the TASK-22 device verification.

---

## Task 4 — Run full local CI gate

- [ ] **Step 1: ktlint**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew ktlintCheck
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Android Lint**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:lint
```

Expected: BUILD SUCCESSFUL. No new lint issues.

- [ ] **Step 3: JVM unit tests**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected: 243 tests pass.

- [ ] **Step 4: Release assembly**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleRelease
```

Expected: BUILD SUCCESSFUL.

---

## Task 5 — CLAUDE.md and BACKLOG.md

- [ ] **Step 1: Append the convention to CLAUDE.md (Architecture section)**

Add immediately below the Narrow domain-interface rule paragraph:

> **Bottom-nav visibility (TASK-27, merged 2026-05-01).** Each
> navigation destination that should hide the global
> `BottomNavigationView` declares
> `<argument android:name="hideBottomNav" app:argType="boolean"
> android:defaultValue="true"/>` in `nav_graph.xml`. `MainActivity`
> reads the argument generically inside
> `addOnDestinationChangedListener` and never references specific
> destination IDs. Destinations that omit the argument default to
> `false` (nav visible). Adding a new full-screen destination is a
> nav-graph edit only — never edit `MainActivity` for this.

Also append `**TASK-27** (…)` to the Status paragraph's
post-v1-backlog list.

- [ ] **Step 2: BACKLOG.md overview row**

```diff
- | TASK-27 | 🟡 | Decouple bottom-nav visibility from hardcoded `hideOn` set in `MainActivity` | — | ☐ |
+ | TASK-27 | 🟡 | Decouple bottom-nav visibility from hardcoded `hideOn` set in `MainActivity` | — | ☑ |
```

- [ ] **Step 3: BACKLOG.md TASK-27 outcome blockquote**

Insert above the original task body:

```markdown
> **Outcome:** the four full-screen destinations (`wizardFragment`,
> `chargeEditFragment`, `carsFragment`, `manageLocationsFragment`)
> declare `hideBottomNav=true` as a destination argument in
> `nav_graph.xml`. `MainActivity.onCreate` reads
> `args.getBoolean("hideBottomNav")` from the
> `addOnDestinationChangedListener` callback and no longer references
> any specific destination ID for visibility decisions. New
> instrumented test
> `app/src/androidTest/.../MainActivityBottomNavTest.kt` exercises
> dashboard → chargeEdit → back transitions. Spec:
> `superpowers/specs/2026-05-01-task27-bottom-nav-hide-arg-design.md`.
> Plan: `superpowers/plans/2026-05-01-task27-bottom-nav-hide-arg.md`.
> The original task text is preserved below for historical context.
```

---

## Task 6 — Commit, merge, push, cleanup

- [ ] **Step 1: Stage + commit on `feat/task27-bottom-nav-hide-arg`**

Per repo convention: separate `git add`, `git commit`, `git push`
steps — no compound git commands. Conventional message:
`refactor(task-27): hideBottomNav destination arg`.

- [ ] **Step 2: `--no-ff` merge into `main`**

```
git checkout main
git merge --no-ff feat/task27-bottom-nav-hide-arg
```

- [ ] **Step 3: Push**

```
git push origin main
```

- [ ] **Step 4: Delete the feature branch**

```
git branch -d feat/task27-bottom-nav-hide-arg
```

---

## Self-review

- **Spec coverage:** every spec section maps to a task above.
- **Placeholders:** none.
- **Argument-name consistency:** `"hideBottomNav"` is used identically
  in nav_graph.xml, MainActivity, the instrumented test assertion
  text, and CLAUDE.md.
- **Risk:** the listener now reads from `args`, which is `null` until
  the start destination resolves. The `args?.getBoolean(...) ?: false`
  default keeps the nav visible during that window — correct, because
  the bottom nav should never be hidden before any destination is
  reached.
