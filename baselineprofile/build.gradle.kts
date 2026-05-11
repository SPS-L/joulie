// TASK-21: Producer module for the Android Baseline Profile.
//
// This is a `com.android.test` module that compiles to its own APK
// (NOT shipped to users). At profile-generation time, the androidx
// macro-benchmark runtime installs this APK alongside :app's
// `nonMinifiedRelease` variant on a device or emulator, drives the
// hot-startup journeys defined in `BaselineProfileGenerator`, and
// records the methods that JIT into a textual profile. The androidx
// baselineprofile gradle plugin then merges that profile into
// `app/src/main/baseline-prof.txt` for ART AOT compilation at install
// time on user devices.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.baselineprofile)
    // Apply the same style gate as :app so producer/benchmark sources
    // ride through `./gradlew ktlintCheck` in CI without a per-module
    // exemption.
    alias(libs.plugins.ktlint)
}

android {
    namespace = "org.spsl.evtracker.baselineprofile"
    compileSdk = 35

    defaultConfig {
        // Baseline-profile capture API. Min SDK 28 covers Android 9+
        // (Pie), the floor for ART's profile-guided AOT compilation.
        // App-side minSdk is still 26 — profiles install on 26/27 too,
        // but those devices don't apply the AOT hint, so generating on
        // them yields nothing useful.
        minSdk = 28
        targetSdk = 35

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    // Required for androidx.baselineprofile 1.3.x with AGP 8.x: the
    // benchmark runtime and the target app run in the same process so
    // UIAutomator can drive an unaltered release build. Without this
    // flag the producer + consumer share no instrumentation context
    // and `startActivityAndWait()` silently times out.
    @Suppress("UnstableApiUsage")
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Producer-side DSL exposes managedDevices / useConnectedDevices /
// enableEmulatorDisplay only; the `automaticGenerationDuringBuild`
// switch lives on the consumer side (`:app`).
baselineProfile {
    // Use the connected device or AVD instead of a Gradle-managed
    // virtual device. Day-to-day generation runs against the local
    // `joulie-test` AVD (see scripts/run-instrumented.sh) which the
    // contributor already has set up; spinning up a separate GMD on
    // every regen adds 2-3 minutes of cold-boot for no benefit.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
