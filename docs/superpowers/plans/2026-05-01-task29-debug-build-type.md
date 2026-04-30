# TASK-29 — Debug build type + BuildConfig flags — Implementation plan

> **For agentic workers:** all build-script and doc changes; no
> production-code changes. No new tests — the task adds compile-time
> scaffolding, not behavior. Acceptance is the build-system smoke
> (both APKs build, applicationIds diverge, BuildConfig fields
> resolve).

**Goal:** Add an explicit `debug` build type with `.debug`
applicationId / versionName suffixes, enable `buildConfig`, declare
three matched custom fields on both build types, and update the
OAuth setup doc to call out the new debug applicationId.

**Architecture:** No code under `app/src/main/java` changes. Build
script gains a `debug { }` block, `release { }` gains three
`buildConfigField(…)` lines, `buildFeatures` flips
`buildConfig = true`. CI workflow gains a release-assembly step.
Docs codify the convention.

**Tech Stack:** AGP 8.2.0 build types · `BuildConfig` (auto-generated
under the `namespace` package).

---

## Files

- Modify: `app/build.gradle.kts:41-49` (buildTypes), 51-53 (buildFeatures)
- Modify: `.github/workflows/ci.yml` (add `:app:assembleRelease` step)
- Modify: `CLAUDE.md` (Build & Test, Status)
- Modify: `docs/GOOGLE_CLOUD_SETUP.md` (Step 5b, Step 6)
- Modify: `docs/BACKLOG.md` (overview row + outcome blockquote +
  prune the addendum's TASK-29-prereq-for-TASK-10 note)

---

## Task 1 — Build script edits

- [ ] **Step 1: Add the `debug` block + extend `release`**

```kotlin
buildTypes {
    debug {
        applicationIdSuffix = ".debug"
        versionNameSuffix = "-debug"
        isDebuggable = true
        buildConfigField("boolean", "ENABLE_SEED_DATA", "true")
        buildConfigField("boolean", "VERBOSE_LOGGING", "true")
        buildConfigField("String", "DRIVE_FOLDER_SUFFIX", "\"_debug\"")
    }
    release {
        isMinifyEnabled = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        if (keystorePropertiesFile.exists()) {
            signingConfig = signingConfigs.getByName("release")
        }
        buildConfigField("boolean", "ENABLE_SEED_DATA", "false")
        buildConfigField("boolean", "VERBOSE_LOGGING", "false")
        buildConfigField("String", "DRIVE_FOLDER_SUFFIX", "\"\"")
    }
}
```

- [ ] **Step 2: Enable `buildConfig`**

```kotlin
buildFeatures {
    viewBinding = true
    buildConfig = true
}
```

- [ ] **Step 3: Build both types**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug :app:assembleRelease
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Verify applicationId suffix on the debug APK**

```
$ANDROID_HOME/build-tools/34.0.0/aapt dump badging app/build/outputs/apk/debug/app-debug.apk | head -1
```

Expected: `package: name='org.spsl.evtracker.debug' versionCode='2' versionName='1.0.1-debug' …`

(If `build-tools/35.0.0` is preferred and present, use it; the
output format is identical.)

- [ ] **Step 5: Confirm `BuildConfig` was generated**

```
find app/build/generated/source/buildConfig -name BuildConfig.java
grep -E "ENABLE_SEED_DATA|VERBOSE_LOGGING|DRIVE_FOLDER_SUFFIX|VERSION_NAME" \
    app/build/generated/source/buildConfig/debug/org/spsl/evtracker/BuildConfig.java
```

Expected: file exists for both debug and release variants; the
generated source contains all six symbols (three custom + two version
+ DEBUG).

---

## Task 2 — CI workflow update

- [ ] **Step 1: Add `:app:assembleRelease` after the JVM unit-tests step in `.github/workflows/ci.yml`**

```yaml
      - name: JVM unit tests
        run: ./gradlew :app:testDebugUnitTest --no-daemon --stacktrace

      - name: Release smoke (unsigned APK — keystore absent in CI)
        run: ./gradlew :app:assembleRelease --no-daemon --stacktrace
```

- [ ] **Step 2: Verify the workflow YAML is well-formed (no actual run)**

```
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"
```

Expected: no traceback. (Optional sanity step; the workflow runs in
GitHub Actions so any malformed YAML breaks CI immediately on push.)

---

## Task 3 — Update `docs/GOOGLE_CLOUD_SETUP.md`

- [ ] **Step 1: Add Step 5b for the debug-suffix client**

After the existing Step 5 (which covers the unsuffixed
`org.spsl.evtracker` client), insert:

```markdown
### Step 5b — Register a third client for the debug `applicationId` suffix

After TASK-29 (merged 2026-05-01) the **debug** build type uses
`applicationId = org.spsl.evtracker.debug` (release stays at
`org.spsl.evtracker`). The OAuth Android client created in Step 5 is
bound to a fixed package name, so debug builds need their own
client:

1. **APIs & Services → Credentials → + Create Credentials → OAuth client ID**
2. **Application type:** Android
3. **Name:** EV Tracker (debug-suffix)
4. **Package name:** `org.spsl.evtracker.debug`
5. **SHA-1:** the same debug keystore SHA-1 from Step 1
6. **Create**

Without this client, Drive sign-in fails on debug builds. Release
builds are unaffected.
```

- [ ] **Step 2: Update Step 6's adb install line**

```diff
-   adb install app/build/outputs/apk/debug/app-debug.apk
+   adb install app/build/outputs/apk/debug/app-debug.apk    # installs as org.spsl.evtracker.debug
```

- [ ] **Step 3: Update the troubleshooting table**

Append a row:

```markdown
| "Sign-in failed" on **debug** specifically (release works) | Missing the `org.spsl.evtracker.debug` OAuth client. Run Step 5b. |
```

---

## Task 4 — Update `CLAUDE.md`

- [ ] **Step 1: Build & Test section — add a paragraph**

After the existing build/test bullets (around line 30), add:

```markdown
### Build types (TASK-29, merged 2026-05-01)

- **Debug** has `applicationIdSuffix = ".debug"` (so the runtime
  package is `org.spsl.evtracker.debug`) and
  `versionNameSuffix = "-debug"`. Debug and release can coexist on
  the same device.
- **`buildConfig = true`** is enabled. `BuildConfig.VERSION_NAME`,
  `VERSION_CODE`, `DEBUG`, `ENABLE_SEED_DATA`, `VERBOSE_LOGGING`,
  `DRIVE_FOLDER_SUFFIX` resolve at compile time. The three custom
  fields are scaffolding — declare consumers in the task that needs
  them and keep the field present on both build types (AGP fails
  the build if either type omits a field the other declares).
- **Drive on debug builds requires a third OAuth Android client** for
  `org.spsl.evtracker.debug` + the debug keystore SHA-1. See
  `docs/GOOGLE_CLOUD_SETUP.md` Step 5b. Until it's registered, Drive
  sign-in fails on debug builds (release is unaffected).
```

- [ ] **Step 2: Status paragraph — append TASK-29**

Add to the comma-separated post-v1 list:

```
**TASK-29** (explicit debug build type with `applicationIdSuffix = ".debug"`,
`versionNameSuffix = "-debug"`, `isDebuggable = true`; `buildConfig = true`
enabled with three scaffolding fields — `ENABLE_SEED_DATA`, `VERBOSE_LOGGING`,
`DRIVE_FOLDER_SUFFIX` — declared on both build types; CI workflow gains a
release-smoke step; `GOOGLE_CLOUD_SETUP.md` Step 5b documents the third OAuth
client needed for Drive on debug builds; merged 2026-05-01).
```

---

## Task 5 — Update `docs/BACKLOG.md`

- [ ] **Step 1: Overview row**

```diff
- | TASK-29 | 🟢 | Add explicit `debug` build type with `applicationIdSuffix` and `BuildConfig` flags | — | ☐ |
+ | TASK-29 | 🟢 | Add explicit `debug` build type with `applicationIdSuffix` and `BuildConfig` flags | — | ☑ |
```

- [ ] **Step 2: Outcome blockquote at the top of the TASK-29 section**

Insert above the original task body:

```markdown
> **Outcome:** added a `debug { }` block to `app/build.gradle.kts`
> with `applicationIdSuffix = ".debug"`, `versionNameSuffix = "-debug"`,
> `isDebuggable = true`. Both `debug` and `release` declare the same
> three custom `BuildConfig` fields (`ENABLE_SEED_DATA`,
> `VERBOSE_LOGGING`, `DRIVE_FOLDER_SUFFIX`) so the scaffolding is
> ready for future consumers. `buildFeatures.buildConfig = true`
> unblocks TASK-10's About screen
> (`BuildConfig.VERSION_NAME` / `VERSION_CODE`).
> `GOOGLE_CLOUD_SETUP.md` gains Step 5b — register a third OAuth
> Android client for `org.spsl.evtracker.debug` — without which
> Drive sign-in fails on debug builds. CI workflow gains
> `:app:assembleRelease` as a release-smoke step (the keystore is
> absent in CI so the APK is unsigned, but R8 + `lintVitalRelease`
> still run). Spec:
> `superpowers/specs/2026-05-01-task29-debug-build-type-design.md`.
> Plan: `superpowers/plans/2026-05-01-task29-debug-build-type.md`.
> The original task text is preserved below for historical context.
```

- [ ] **Step 3: Prune the `Notes for Agents (TASK-22 to TASK-30 addendum)` entries that mention TASK-29 as a prerequisite**

The `TASK-29 prerequisite for TASK-10` and `TASK-29 OAuth implication`
notes are now obsolete (TASK-29 has landed). Replace them with a
single sentence under the existing "obsolete sequencing" blockquote
at the top of the section.

---

## Task 6 — Run full local CI gate

- [ ] **Step 1: ktlint + lint + JVM unit tests**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew ktlintCheck :app:lint :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. JVM count unchanged at 243.

- [ ] **Step 2: Release assembly smoke**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleRelease
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Instrumented test compile**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest
```

Expected: BUILD SUCCESSFUL. (Compile only; running needs an emulator.)

---

## Task 7 — Commit, merge, push, cleanup

- [ ] **Step 1: Stage + commit on `feat/task29-debug-build-type`**

Per repo convention: separate `git add`, `git commit`, `git push`.
Conventional message: `feat(task-29): explicit debug build type +
BuildConfig flags + OAuth doc`.

- [ ] **Step 2: `--no-ff` merge into `main`**

```
git checkout main
git merge --no-ff feat/task29-debug-build-type
```

- [ ] **Step 3: Push**

```
git push origin main
```

- [ ] **Step 4: Delete feature branch**

```
git branch -d feat/task29-debug-build-type
```

---

## Self-review

- **Spec coverage:** every spec section maps to a task above.
- **Placeholders:** none.
- **Field-name consistency:** `ENABLE_SEED_DATA`, `VERBOSE_LOGGING`,
  `DRIVE_FOLDER_SUFFIX` appear identically in build.gradle.kts (×2),
  CLAUDE.md, the spec, and the BACKLOG outcome blockquote.
- **No production-code consumer added.** Intentional — scaffolding
  only. The first consumer (TASK-10's About screen) reads
  `BuildConfig.VERSION_NAME`, which is auto-generated, not one of the
  three custom fields.
- **OAuth blast radius:** existing release OAuth client is untouched;
  release builds keep `applicationId = org.spsl.evtracker` and
  continue working. Only debug builds need the new (Step 5b) client.
