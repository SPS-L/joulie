import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.roborazzi)
    // TASK-21: consumer side of the Android Baseline Profile pipeline. The
    // plugin reads the generated `baseline-prof.txt` (committed under
    // src/main/) at release-build time and bundles it as the AOT-compile
    // hint for ART. The producer plugin lives on the sibling :baselineprofile
    // module, which generates the txt via macro-benchmark.
    alias(libs.plugins.androidx.baselineprofile)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "org.spsl.evtracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.spsl.evtracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 73
        versionName = "1.13.5"
        testInstrumentationRunner = "org.spsl.evtracker.HiltTestRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

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

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        // Let JVM unit tests call android.util.Log without throwing
        // "Method e in android.util.Log not mocked". DriveBackupRepository
        // logs ERROR-level for non-recoverable failures and the JVM tests
        // exercise those branches.
        unitTests.isReturnDefaultValues = true
        // Robolectric (TASK-35 screenshot baselines) needs the resource
        // table on the test classpath to render Android Views. Without
        // this flag the JVM test run starts without compiled resources
        // and Roborazzi captures empty bitmaps.
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
        error +=
            listOf(
                "HardcodedText",
                "MissingTranslation",
                "TypographyDashes",
                "UnusedResources",
                "ContentDescription",
                "LabelFor",
                "KeyboardInaccessibleWidget",
            )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Bundle Room's exported schema JSONs ($projectDir/schemas/<db-class>/<version>.json)
    // into the androidTest APK so MigrationTestHelper.createDatabase(name, N) can
    // load `AppDatabase/N.json` at runtime. Required by the @AutoMigration tests
    // (TASK-39) — auto-migration validation reads the start-version schema from
    // test assets, and without this srcDir those tests fail with
    // FileNotFoundException for the missing .json.
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    packaging {
        resources {
            // Required by the Drive backup path: google-api-client + google-http-client
            // ship duplicate META-INF/{DEPENDENCIES,LICENSE,NOTICE,INDEX.LIST} entries
            // and the build fails with DuplicatesStrategy.FAIL without these excludes.
            // Removing entries here will surface as a runtime/build error on Drive sync.
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/INDEX.LIST",
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

// TASK-21: consumer-side configuration for the Android Baseline Profile.
// The plugin's `automaticGenerationDuringBuild` flag would regenerate the
// profile on every release `assemble`, which would dominate CI wall-clock
// and produce a constantly-shifting `baseline-prof.txt` diff. We refresh
// it manually on a cadence (see "Baseline profile cadence" in
// `../CLAUDE.md`) and commit the resulting file so release builds are
// reproducible.
baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
}

// TASK-35: keep Roborazzi baselines under src/test/screenshots/ so they
// live next to the test code that produces them and reviewers see the
// PNG diff in the same PR review surface as the test changes.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.vico.views)
    // Drive auth: Authorization API (Identity.getAuthorizationClient) — no Firebase, no google-services.json.
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.fragment)
    implementation(libs.androidx.core.splashscreen)
    // TASK-21: ART reads the bundled Baseline Profile via ProfileInstaller at
    // first launch (or when WorkManager runs ProfileInstallerInitializer on
    // background). Without this dep the committed `baseline-prof.txt` ships
    // but is never compiled into machine code at install time.
    implementation(libs.androidx.profileinstaller)

    // TASK-21: consumer wiring. The androidx.baselineprofile plugin pulls
    // the generated profile out of the :baselineprofile module's build
    // outputs and merges it into the release APK at assemble time.
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.android.compiler)
    // TASK-35: Roborazzi + Robolectric for JVM-side screenshot baselines.
    // Drives Dashboard + Charts fragments through a stubbed-VM render path
    // (no Hilt graph, no WorkManager init) so the PR gate stays clear of
    // the Hilt/Robolectric/WorkManager brittleness pattern that took
    // TASK-58..74 to stabilise on the nightly suite.
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.fragment.testing)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.accessibility)
    androidTestImplementation(libs.androidx.navigation.testing)
    androidTestImplementation(libs.androidx.fragment.testing)
    // fragment-testing 1.6+ ships the EmptyFragmentActivity manifest entry
    // in a companion artifact that must merge into the *app* manifest
    // (debug variant), not the test APK manifest, because the
    // instrumentation runner launches activities under the app's package.
    // Without this, FragmentScenario.launchInContainer fails with
    // "Unable to resolve activity for Intent ... EmptyFragmentActivity".
    debugImplementation(libs.androidx.fragment.testing.manifest)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
}
