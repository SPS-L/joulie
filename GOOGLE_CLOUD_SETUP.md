# Google Cloud Setup — Drive API & OAuth for EV Tracker

This guide walks you through enabling the Google Drive API and creating an OAuth 2.0 Android client so the app can use the hidden App Data folder for backup.

> **Scope used:** `https://www.googleapis.com/auth/drive.appdata`  
> This is a **non-sensitive, non-restricted** scope — no Google OAuth verification required for personal or internal use.

---

## Step 1 — Create a Google Cloud Project

1. Go to [https://console.cloud.google.com/](https://console.cloud.google.com/)
2. Click the project selector (top-left) → **New Project**
3. Name it (e.g. `EV Tracker`) and click **Create**
4. Make sure the new project is selected in the top bar

---

## Step 2 — Enable the Google Drive API

1. In the left menu go to **APIs & Services → Library**
2. Search for **Google Drive API**
3. Click it → **Enable**

---

## Step 3 — Configure the OAuth Consent Screen

1. Go to **APIs & Services → OAuth consent screen**
2. Choose **External** (works for personal use; for internal org use choose Internal)
3. Fill in:
   - **App name:** EV Efficiency Tracker
   - **User support email:** your email
   - **Developer contact email:** your email
4. Click **Save and Continue**
5. On the **Scopes** page click **Add or Remove Scopes**
6. Search for `drive.appdata` and check `../auth/drive.appdata` → **Update** → **Save and Continue**
7. On the **Test users** page add your Google account email (required while app is in Testing status)
8. Click **Save and Continue** → **Back to Dashboard**

> You do **not** need to publish the app or go through verification for personal or lab use. Keep it in **Testing** status.

---

## Step 4 — Create an OAuth 2.0 Android Client ID

1. Go to **APIs & Services → Credentials**
2. Click **+ Create Credentials → OAuth client ID**
3. Application type: **Android**
4. Fill in:
   - **Name:** EV Tracker Android
   - **Package name:** `org.spsl.evtracker`
   - **SHA-1 certificate fingerprint:** (see Step 5 below)
5. Click **Create**
6. Copy the **Client ID** — you will not need to paste it anywhere in code; Google Sign-In resolves it automatically from the package name + SHA-1 combination

---

## Step 5 — Get Your Debug Keystore SHA-1

Run this command on your development machine:

```bash
keytool -list -v \
  -keystore ~/.android/debug.keystore \
  -alias androiddebugkey \
  -storepass android \
  -keypass android
```

On Windows:
```cmd
keytool -list -v ^
  -keystore %USERPROFILE%\.android\debug.keystore ^
  -alias androiddebugkey ^
  -storepass android ^
  -keypass android
```

Copy the **SHA1** fingerprint (format: `AA:BB:CC:...`) and paste it into the Android OAuth client in Step 4.

> For a **release build** you will need to repeat Step 4 with the SHA-1 of your release keystore and create a second OAuth client ID.

---

## Step 6 — Add google-services.json to the Project

1. In Google Cloud Console go to **APIs & Services → Credentials**
2. At the top click **Download OAuth client** (the Android entry you just created)

   *Alternatively:* go to [https://console.firebase.google.com/](https://console.firebase.google.com/), add the same project, register your Android app with package `org.spsl.evtracker` and SHA-1, then download `google-services.json` directly — this is the preferred method.

3. Place the downloaded `google-services.json` file at:
   ```
   EV-android-app/app/google-services.json
   ```
4. The `app/build.gradle.kts` already includes:
   ```kotlin
   apply(plugin = "com.google.gms.google-services")
   ```
   so no further build changes are needed.

> ⚠️ `google-services.json` contains your OAuth client ID but **no private keys**. It is safe to commit for open-source projects. If you prefer to keep it private, add it to `.gitignore` and distribute it separately.

---

## Step 7 — Verify the Setup

1. Build and install the debug APK on a device or emulator that has a Google account signed in
2. In Settings, toggle **Google Drive backup** ON
3. The Google Sign-In consent screen should appear requesting "See and manage files created by EV Tracker" (the `drive.appdata` scope)
4. After granting, the app will upload `evtracker_backup.json` to the hidden App Data folder
5. Verify via the [Google Drive API Try-it tool](https://developers.google.com/workspace/drive/api/reference/rest/v3/files/list):
   - Method: `files.list`
   - Set `spaces = appDataFolder`
   - Click **Execute** — you should see `evtracker_backup.json` listed

---

## Troubleshooting

| Problem | Likely cause | Fix |
|---------|-------------|-----|
| Sign-in fails silently | SHA-1 mismatch | Re-run `keytool`, compare with Cloud Console entry |
| "Access blocked: app not verified" | Consent screen in Testing but your account not in test users | Add your account in OAuth consent screen → Test users |
| `drive.appdata` scope not granted | Scope not added to consent screen | Re-do Step 3.6 |
| Backup file not found after restore | Wrong project / package name | Ensure package name exactly matches `org.spsl.evtracker` |
| Release build can't sign in | Release SHA-1 not registered | Create second OAuth client with release keystore SHA-1 |
