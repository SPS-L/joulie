pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack scoping removed in TASK-30 (MPAndroidChart -> Vico migration).
        // No remaining JitPack consumer; if a future dep needs JitPack, restore
        // an `exclusiveContent { … filter { includeGroup(…) } }` block here.
    }
}
rootProject.name = "joulie"
include(":app")
// TASK-21: producer module for the Android Baseline Profile. Compiles to a
// separate APK (com.android.test) that the macro-benchmark runtime drives
// against :app via UIAutomator. Profile output lands at
// app/src/main/baseline-prof.txt for AOT compilation at install time.
include(":baselineprofile")
