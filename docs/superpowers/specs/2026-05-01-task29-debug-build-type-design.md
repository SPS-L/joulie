# TASK-29 — Explicit `debug` build type with `applicationIdSuffix` + `BuildConfig` flags — Design

**Date:** 2026-05-01
**Branch:** `feat/task29-debug-build-type`
**Unblocks:** TASK-10 (About screen, which reads `BuildConfig.VERSION_NAME`)

## Problem

`app/build.gradle.kts:41-49` defines only a `release { … }` block; the
`debug` build type is left as Gradle's implicit default. Three concrete
gaps:

1. **Debug and release share `applicationId`** (`org.spsl.evtracker`)
   so they cannot coexist on the same device — installing the debug
   APK over the released Play APK uninstalls the user's data, and vice
   versa. This is a known annoyance during local testing.
2. **No `BuildConfig` generation.** AGP 8.0+ disabled `buildConfig` by
   default; the project sets `viewBinding = true` in `buildFeatures`
   but never enables `buildConfig`, so `BuildConfig.VERSION_NAME` etc.
   do not exist. TASK-10 (About screen) needs them.
3. **No `BuildConfig` flags** to gate development-only behavior with
   stronger guarantees than `if (BuildConfig.DEBUG)`.

## Goal

Add an explicit `debug { … }` block with `.debug` applicationId/version
suffixes, enable `buildConfig = true`, and seed three custom fields
(`ENABLE_SEED_DATA`, `VERBOSE_LOGGING`, `DRIVE_FOLDER_SUFFIX`) so
future code (TASK-10, dev-only seeders, etc.) has scaffolding ready.

## Design

### 1. `buildTypes` block changes

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

Both types must define every custom field; AGP errors out if the
release build is missing a field that debug declares (and vice versa).

### 2. `buildFeatures` enabling `buildConfig`

```kotlin
buildFeatures {
    viewBinding = true
    buildConfig = true   // unblocks TASK-10's About screen
}
```

`BuildConfig` is generated under the project namespace
(`org.spsl.evtracker.BuildConfig`) regardless of build-type
applicationId — i.e. consumers `import org.spsl.evtracker.BuildConfig`
the same way for debug and release. The `applicationIdSuffix` only
affects the runtime package name, not the `BuildConfig` class location.

### 3. No new consumers in this task

`ENABLE_SEED_DATA`, `VERBOSE_LOGGING`, `DRIVE_FOLDER_SUFFIX` are
scaffolding. This task does not wire any consumers — that's deferred
to the tasks that introduce seed data (none planned), verbose logging
(TASK-07 backup retry might use it), and Drive folder differentiation
(moot anyway because `applicationIdSuffix` already makes Drive's
`appDataFolder` per-applicationId, so debug and release builds
already write to separate hidden folders).

The fields exist as a stable, version-controlled API so future code
can `if (BuildConfig.VERBOSE_LOGGING) …` without adding the
build-script plumbing in the same change. Keeping them removes future
churn at near-zero cost (six lines + matching three release lines).

### 4. OAuth implication (the load-bearing piece)

Drive sign-in uses the Authorization API (`Identity.getAuthorizationClient`),
which binds an OAuth Android client to **package name + signing-cert
SHA-1**. The existing debug OAuth client (Step 5 of `GOOGLE_CLOUD_SETUP.md`)
is registered for `org.spsl.evtracker` + the debug keystore SHA-1.
After this change, the debug build's package name becomes
`org.spsl.evtracker.debug`. Consequence: **Drive sign-in fails on
debug builds until a third OAuth client is registered** for
`org.spsl.evtracker.debug` + the debug keystore SHA-1.

Two responses:

- **Recommended (chosen):** add a "Step 5b" to `GOOGLE_CLOUD_SETUP.md`
  that explicitly instructs the user to register the third OAuth
  Android client. Until they do, Drive backup is unavailable on debug
  builds; release builds (which keep `applicationId =
  org.spsl.evtracker`) are unaffected.
- **Alternative (not chosen):** gate the Drive Settings UI on
  `BuildConfig.DEBUG` to hide the toggle on debug builds. This removes
  the failure mode but also removes the ability to test Drive flows
  on debug builds, which is the more painful regression.

The recommended path keeps Drive available on all builds; the user's
one-time third-client registration is documented up front so it's not
a surprise.

### 5. CI workflow update (TASK-29 step 6)

Currently `.github/workflows/ci.yml` runs `ktlintCheck`, `:app:lint`,
and `:app:testDebugUnitTest`. The `release` build type has its own
risk surface (`isMinifyEnabled = true`, `lintVitalRelease`,
proguard-rules.pro), and after this task adds `BuildConfig` fields,
both build types must declare matching field sets — a regression
where someone adds a debug-only field without the release counterpart
must be caught at PR time. Add `:app:assembleRelease` as a CI step.

CI builds are unsigned (no `keystore.properties` in the
checkout); `assembleRelease` falls back to producing an unsigned APK
in that case, which is exactly right for a smoke gate. The signed
release APK still comes out of `release.yml` on tag pushes.

### 6. Doc updates

- `CLAUDE.md` Build & Test section: note debug + release coexistence,
  the three custom fields' purposes, and the OAuth third-client
  requirement.
- `CLAUDE.md` Status paragraph: append TASK-29.
- `docs/GOOGLE_CLOUD_SETUP.md`: add Step 5b for the
  `org.spsl.evtracker.debug` client, and update Step 6's `adb install`
  hint (debug APK now lands under a different package).

## Out of scope

- **Wiring any of the three custom fields to a consumer.** Deferred to
  the task that needs them.
- **TASK-10 (About screen).** Now unblocked; separate task.
- **Drive backup actually working on debug builds out of the box.** The
  user has to register the third OAuth client themselves; this design
  documents how, but does not register clients on the user's behalf.

## Acceptance

```
$ ./gradlew :app:assembleDebug :app:assembleRelease
BUILD SUCCESSFUL

$ ./gradlew :app:lint :app:testDebugUnitTest ktlintCheck
BUILD SUCCESSFUL

$ aapt dump badging app/build/outputs/apk/debug/app-debug.apk | grep package
package: name='org.spsl.evtracker.debug' versionCode='2' versionName='1.0.1-debug'

$ aapt dump badging app/build/outputs/apk/release/app-release-unsigned.apk | grep package
package: name='org.spsl.evtracker' versionCode='2' versionName='1.0.1'
```

`BuildConfig.VERSION_NAME`, `BuildConfig.VERSION_CODE`,
`BuildConfig.DEBUG`, `BuildConfig.ENABLE_SEED_DATA`,
`BuildConfig.VERBOSE_LOGGING`, `BuildConfig.DRIVE_FOLDER_SUFFIX` all
resolve at compile time under `org.spsl.evtracker.BuildConfig`. The
JVM unit-test count stays at 243 (no behavioral change).
