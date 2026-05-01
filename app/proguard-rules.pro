# R8 / ProGuard keep rules for EV Tracker release builds.
#
# Without this file the release APK strips reflection-only members used by:
#   - Google API Client (com.google.api.client.**) — @Key-annotated fields
#   - Google Drive services (com.google.api.services.drive.**) — model classes
#   - Gson (org.spsl.evtracker.core.model.* DTOs) — @SerializedName fields
#   - MPAndroidChart (com.github.mikephil.charting.**) — defensive
# which crashes the app the moment Drive sync is toggled on.
#
# Hilt and Room ship their own consumer ProGuard rules in their AARs — see the
# evidence comment near the bottom of this file. Do not duplicate those here.

# Preserve generic signatures and annotations Gson / google-http-client need.
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses

# --- Google API Client / google-http-client / Drive services ---------------
# These ship effectively no consumer ProGuard rules.
-keep class com.google.api.client.** { *; }
-keep interface com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keep interface com.google.api.services.drive.** { *; }

# Members annotated with @Key drive JSON reflection.
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
    @com.google.api.client.util.Key <methods>;
}

# Models extend GenericJson / GenericData and use field reflection.
-keepclassmembers class * extends com.google.api.client.json.GenericJson { <fields>; }
-keepclassmembers class * extends com.google.api.client.util.GenericData  { <fields>; }

# Optional/server-side deps pulled in transitively but absent on Android.
-dontwarn org.joda.time.**
-dontwarn javax.annotation.**
-dontwarn javax.xml.**
-dontwarn java.beans.**
-dontwarn org.checkerframework.**
-dontwarn org.jspecify.**
-dontwarn org.ietf.jgss.**
-dontwarn sun.misc.**
-dontwarn com.google.errorprone.**
-dontwarn com.google.api.client.extensions.**
-dontwarn com.google.api.client.http.apache.**
-dontwarn org.apache.http.**
-dontwarn javax.naming.**

# --- Gson ------------------------------------------------------------------
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- App backup DTOs (deserialized by Gson) --------------------------------
-keep class org.spsl.evtracker.core.model.BackupData  { *; }
-keep class org.spsl.evtracker.core.model.BackupData$* { *; }
-keep class org.spsl.evtracker.core.model.CarDto { *; }
-keep class org.spsl.evtracker.core.model.ChargeEventDto { *; }
-keep class org.spsl.evtracker.core.model.CustomLocationDto { *; }

# --- MPAndroidChart --------------------------------------------------------
# MPAndroidChart ships no consumer ProGuard rules. Most of its release surface
# is non-reflective, but renderers and IValueFormatter subclasses can still
# bite under aggressive R8 (instantiation by class name from XML attributes
# and per-platform reflective fallbacks). Keep the whole package defensively.
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# --- Hilt + Room: no app-side rules required -------------------------------
# Both libraries ship comprehensive consumer ProGuard rules with their AARs.
# Verified against build/outputs/mapping/release/configuration.txt (TASK-17):
#   - hilt-android-2.50/proguard.txt          (HiltViewModel / EntryPoint keeps)
#   - hilt-work-1.1.0/proguard.txt            (HiltWorker keeps)
#   - room-runtime-2.6.1/proguard.txt         (RoomDatabase subclass keep)
# Do NOT add app-side -keep rules for dagger.hilt.* or androidx.room.* — the
# AAR rules already cover them; duplicating them only hides drift in the AARs.
