# TASK-22 — SDK 35 upgrade — Implementation plan

> **For agentic workers:** small build-script + one-Activity edit. The
> empirical probe (see spec) already confirmed the toolchain accepts
> compileSdk 35 with no AGP bump. No new tests — the edge-to-edge wiring
> is correct by construction (`WindowInsetsCompat` contract is invariant
> across API levels, no-op on API ≤ 34) and would require an instrumented
> test on an API-35 emulator to validate visually. The existing JVM suite
> (243) covers everything else.

**Goal:** Bump `compileSdk` and `targetSdk` to 35 and add edge-to-edge
insets handling to `MainActivity` so the bottom nav and Snackbars are not
clipped under the gesture-nav bar on Android 15+ devices.

**Architecture:** Two SDK-version edits in `app/build.gradle.kts`; one
`enableEdgeToEdge()` call and one `setOnApplyWindowInsetsListener` block
added to `MainActivity.onCreate`; doc updates.

**Tech Stack:** Kotlin · `androidx.activity:activity` 1.8.0 (transitive,
provides `enableEdgeToEdge()`) · `androidx.core:core-ktx` 1.12.0
(provides `ViewCompat`, `WindowInsetsCompat`, `View.updatePadding`).

---

## Files

- Modify: `app/build.gradle.kts:19,24`
- Modify: `app/src/main/java/org/spsl/evtracker/MainActivity.kt`
- Touch: `CLAUDE.md` (Project paragraph SDK refs, Build & Test "Build Tools 34", Status section)
- Touch: `docs/BACKLOG.md` (mark TASK-22 done with outcome blockquote)

---

## Task 1 — Bump compileSdk and targetSdk to 35

- [ ] **Step 1: Edit `app/build.gradle.kts`**

```kotlin
android {
    namespace = "org.spsl.evtracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.spsl.evtracker"
        minSdk = 26
        targetSdk = 35
        // ...
    }
}
```

- [ ] **Step 2: Build debug APK**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. AGP 8.2 will auto-download `android-35`
under `$ANDROID_HOME/platforms/` if it's not already present (the probe
already cached this locally). No warnings.

- [ ] **Step 3: Lint (sanity, no gate yet)**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:lint
```

Expected: `BUILD SUCCESSFUL`. `app/build/reports/lint-results-debug.xml`
should contain only the existing `LintBaseline` informational marker.

---

## Task 2 — Wire edge-to-edge insets in `MainActivity`

- [ ] **Step 1: Add the import + `enableEdgeToEdge()` call**

```diff
 import android.os.Bundle
+import androidx.activity.enableEdgeToEdge
 import androidx.activity.viewModels
 import androidx.annotation.VisibleForTesting
 import androidx.appcompat.app.AppCompatActivity
+import androidx.core.view.ViewCompat
+import androidx.core.view.WindowInsetsCompat
+import androidx.core.view.updatePadding
 import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
```

(Maintain ktlint-friendly alphabetical import order: `androidx.activity.*`
groups before `androidx.annotation.*`, `androidx.appcompat.*`,
`androidx.core.*`.)

- [ ] **Step 2: Insert `enableEdgeToEdge()` and the listener in `onCreate`**

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    val splash = installSplashScreen()
    splash.setKeepOnScreenCondition {
        mainViewModel.startupState.value is StartupState.Loading
    }
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout(),
        )
        v.updatePadding(
            top = bars.top,
            left = bars.left,
            right = bars.right,
            bottom = bars.bottom,
        )
        WindowInsetsCompat.CONSUMED
    }

    val navHost = supportFragmentManager
        .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
    // ... existing code unchanged
}
```

- [ ] **Step 3: Build debug APK**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Compile instrumented test sources**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest
```

Expected: BUILD SUCCESSFUL. (Running the suite needs an emulator; we only
verify the test compile step.)

---

## Task 3 — Run full local CI gate

- [ ] **Step 1: ktlint**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew ktlintCheck
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Android Lint**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:lint
```

Expected: BUILD SUCCESSFUL with no new lint failures (baseline absorbs
existing offenses; the bump should not introduce new ones).

- [ ] **Step 3: Full JVM unit suite**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected: 243 tests pass.

- [ ] **Step 4: Release assembly**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleRelease
```

Expected: BUILD SUCCESSFUL. R8 minification still works against the new
SDK; if it fails on a missing keep rule for an API-35 class, that's
TASK-17 territory and gets escalated.

---

## Task 4 — Update CLAUDE.md and BACKLOG.md

- [ ] **Step 1: CLAUDE.md Project paragraph**

```diff
- Hilt-based dependency injection. Min SDK 26, target/compile SDK 34, JDK 17.
+ Hilt-based dependency injection. Min SDK 26, target/compile SDK 35, JDK 17.
```

- [ ] **Step 2: CLAUDE.md Build & Test note**

```diff
- Requires `ANDROID_HOME` set and Build Tools 34.
+ Requires `ANDROID_HOME` set and Build Tools 35.
```

- [ ] **Step 3: CLAUDE.md Status paragraph — append TASK-22 to the merged-backlog list**

Add `**TASK-22** (compileSdk + targetSdk bumped to 35; MainActivity
enables edge-to-edge with a system-bars + display-cutout insets listener
on the root LinearLayout; merged 2026-05-01)` to the comma-separated
list.

- [ ] **Step 4: BACKLOG.md overview row**

```diff
- | TASK-22 | 🔴 | Upgrade `targetSdk` and `compileSdk` to API 35 | TASK-16 | ☐ |
+ | TASK-22 | 🔴 | Upgrade `targetSdk` and `compileSdk` to API 35 | TASK-16 | ☑ |
```

- [ ] **Step 5: BACKLOG.md TASK-22 section — add outcome blockquote**

Insert at the top of the section, above the original task body:

```markdown
> **Outcome:** `compileSdk` and `targetSdk` bumped to 35 in
> `app/build.gradle.kts`. AGP 8.2.0 + Gradle 8.4 accept the new SDK with
> no toolchain bump and no warnings (verified with
> `./gradlew :app:assembleDebug` — `android-35` is auto-downloaded).
> `MainActivity.onCreate` now calls `enableEdgeToEdge()` and applies
> `WindowInsetsCompat.Type.systemBars() or displayCutout()` as padding to
> the root `LinearLayout`, keeping the bottom nav and CoordinatorLayout
> Snackbars above the gesture-nav indicator on Android 15+. CI workflow
> `.github/workflows/ci.yml` was unchanged — it has no instrumented-test
> matrix, so the original "bump api-level" step in the backlog text was
> moot. Spec:
> `superpowers/specs/2026-05-01-task22-sdk35-upgrade-design.md`. Plan:
> `superpowers/plans/2026-05-01-task22-sdk35-upgrade.md`.
> The original task text is preserved below for historical context.
```

---

## Task 5 — Commit, merge, push, cleanup

- [ ] **Step 1: Stage + commit**

Per repo rule: separate `git add`, `git commit`, `git push` — no compound
git commands.

Conventional message:
`feat(task-22): bump compileSdk/targetSdk to 35 + edge-to-edge insets`.

- [ ] **Step 2: `--no-ff` merge into `main`**

```
git checkout main
git merge --no-ff feat/task22-sdk35-upgrade
```

- [ ] **Step 3: Push**

```
git push origin main
```

- [ ] **Step 4: Delete feature branch**

```
git branch -d feat/task22-sdk35-upgrade
```

---

## Self-review

- **Spec coverage:** every spec section maps to a task. The CI matrix
  step from the original backlog text is documented as moot.
- **Placeholders:** none.
- **Type consistency:** `WindowInsetsCompat.Type.systemBars()` and
  `displayCutout()` both return `Int` and combine with `or`. The
  `getInsets(...)` call returns `androidx.core.graphics.Insets` whose
  `top/left/right/bottom` are `Int`, matching `View.updatePadding`'s
  signature.
- **Risk:** the edge-to-edge wiring is not exercised by JVM unit tests.
  Real-device verification on API 35 is a follow-up smoke task — the
  installed APK can be sideloaded and visually inspected.
