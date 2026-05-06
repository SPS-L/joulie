import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ktlint)
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
        versionCode = 27
        versionName = "1.9.11"
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
        // "Method e in android.util.Log not mocked" — TASK-07's
        // DriveBackupRepository logs ERROR-level for non-recoverable
        // failures, and the JVM tests exercise those branches.
        unitTests.isReturnDefaultValues = true
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
            )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
    implementation(libs.mpandroidchart)
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

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.android.compiler)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.accessibility)
    androidTestImplementation(libs.androidx.navigation.testing)
    androidTestImplementation(libs.androidx.fragment.testing)
    // TASK-50 sub-fix A: fragment-testing 1.6+ split the EmptyFragmentActivity
    // manifest entry into a companion artifact. It must merge into the *app*
    // manifest (debug variant), not the test APK manifest, because the
    // instrumentation runner launches activities under the app's package.
    // Without this, FragmentScenario.launchInContainer fails with
    // "Unable to resolve activity for Intent ... EmptyFragmentActivity".
    debugImplementation(libs.androidx.fragment.testing.manifest)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
}
