# Google Cloud Setup — Drive API & OAuth for EV Tracker

Follow these steps **in order**. There is one path. Do not skip steps.

> **Scope used:** `drive.appdata` — non-sensitive, no Google verification required.

---

## Step 1 — Get your debug SHA-1 fingerprint

You need this before anything else.

Open a terminal and run:

```bash
keytool -list -v \
  -keystore ~/.android/debug.keystore \
  -alias androiddebugkey \
  -storepass android \
  -keypass android
```

On Windows:
```cmd
keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android
```

Find the line that starts with `SHA1:` and copy the fingerprint. It looks like:
```
SHA1: AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD
```

Keep it handy — you will paste it in Step 3.

---

## Step 2 — Create a Firebase project

1. Go to [https://console.firebase.google.com/](https://console.firebase.google.com/)
2. Click **Add project**
3. Name it `EV Tracker` → click **Continue**
4. Disable Google Analytics (not needed) → click **Create project**
5. Wait for it to provision, then click **Continue**

---

## Step 3 — Register your Android app in Firebase

1. On the Firebase project overview page, click the **Android icon** (‹/›) to add an Android app
2. Fill in:
   - **Android package name:** `org.spsl.evtracker`
   - **App nickname:** EV Tracker (optional)
   - **Debug signing certificate SHA-1:** paste the fingerprint from Step 1
3. Click **Register app**
4. On the next screen click **Download google-services.json**
5. Save the file — you will place it in the project in Step 6
6. Click **Next** → **Next** → **Continue to console** (skip the SDK setup instructions)

---

## Step 4 — Enable the Google Drive API

1. Go to [https://console.cloud.google.com/](https://console.cloud.google.com/)
2. In the project selector (top-left) choose the project named **EV Tracker** (same one Firebase just created)
3. In the left menu go to **APIs & Services → Library**
4. Search for **Google Drive API**
5. Click it → click **Enable**

---

## Step 5 — Configure the OAuth consent screen

1. In Google Cloud Console go to **APIs & Services → OAuth consent screen**
2. Choose **External** → click **Create**
3. Fill in:
   - **App name:** EV Efficiency Tracker
   - **User support email:** your email address
   - **Developer contact email:** your email address
4. Click **Save and Continue**
5. On the **Scopes** screen click **Add or Remove Scopes**
6. In the filter box type `drive.appdata`
7. Check the scope `https://www.googleapis.com/auth/drive.appdata` → click **Update**
8. Click **Save and Continue**
9. On the **Test users** screen click **+ Add users** → enter your Google account email → click **Add**
10. Click **Save and Continue** → **Back to Dashboard**

---

## Step 6 — Place google-services.json in the project

Take the `google-services.json` file you downloaded in Step 3 and copy it to:

```
EV-android-app/app/google-services.json
```

That's it. The build files already reference it.

> `google-services.json` contains no private keys. It is safe to commit to the repo.

---

## Step 7 — Build and verify

1. Build and install the debug APK:
   ```bash
   ./gradlew assembleDebug
   # install on connected device or emulator
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
2. Open the app → go to **Settings** → toggle **Google Drive backup** ON
3. A Google Sign-In screen appears asking for permission to manage EV Tracker files
4. Sign in with the same Google account you added as a test user in Step 5
5. The app will upload `evtracker_backup.json` to the hidden App Data folder

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| "Another project contains an OAuth 2.0 client with this SHA-1 and package name" | Your SHA-1 + package name is already registered in an old Firebase project. Open [https://console.firebase.google.com/](https://console.firebase.google.com/), find that old project, go to **Project Settings → Your apps**, delete the Android app entry there, then retry Step 3. |
| Sign-in fails silently | SHA-1 mismatch. Re-run the `keytool` command, compare the output with what is registered in Firebase Project Settings → Your apps. |
| "Access blocked: app not verified" | Your Google account is not in the test users list. Re-do Step 5.9. |
| `google-services.json` not found at build time | File is missing from `app/` folder. Re-do Step 6. |
| Backup file not found after restore | Wrong package name registered. Must be exactly `org.spsl.evtracker`. |
