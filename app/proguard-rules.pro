# R8 / ProGuard keep rules for EV Tracker release builds.
#
# Without this file the release APK strips reflection-only members used by:
#   - Google API Client (com.google.api.client.**) — @Key-annotated fields
#   - Google Drive services (com.google.api.services.drive.**) — model classes
#   - Gson (org.spsl.evtracker.core.model.* DTOs) — @SerializedName fields
# which crashes the app the moment Drive sync is toggled on.
# (MPAndroidChart keep rule removed in TASK-30 / Vico migration; Vico ships
# its own consumer ProGuard rules.)
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

# --- TASK-91 EV-database DTOs (deserialized by Gson) -----------------------
# Without these keeps, R8 obfuscates EvModel / EvModelDatabase enough that
# gson.fromJson() returns a non-null object with an empty `vehicles` list
# on release builds. The Settings "vehicle count" looks fine because it is
# read from a separately-persisted DataStore key (evDbVehicleCount), but
# the in-memory data is unusable, so the Add/Edit Car autocomplete shows
# no suggestions. This was the actual root cause of the TASK-91 "dropdown
# never renders" bug that v1.13.0..v1.13.7 chased through the widget /
# dialog / popup layers without finding.
-keep class org.spsl.evtracker.data.local.evdb.EvModel { *; }
-keep class org.spsl.evtracker.data.local.evdb.EvModelDatabase { *; }

# --- Hilt + Room: no app-side rules required -------------------------------
# Both libraries ship comprehensive consumer ProGuard rules with their AARs.
# Verified against build/outputs/mapping/release/configuration.txt (TASK-17):
#   - hilt-android-2.50/proguard.txt          (HiltViewModel / EntryPoint keeps)
#   - hilt-work-1.1.0/proguard.txt            (HiltWorker keeps)
#   - room-runtime-2.6.1/proguard.txt         (RoomDatabase subclass keep)
# Do NOT add app-side -keep rules for dagger.hilt.* or androidx.room.* — the
# AAR rules already cover them; duplicating them only hides drift in the AARs.
