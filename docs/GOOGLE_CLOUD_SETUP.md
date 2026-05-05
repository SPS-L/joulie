# Google Cloud Setup, Drive API & OAuth for Joulie

Follow these steps **in order**.

> **Scope used:** `drive.appdata`, non-sensitive, no Google verification required.
>
> **No Firebase, no `google-services.json`.** This app uses the Authorization API (`Identity.getAuthorizationClient`) directly. The OAuth client is bound to your Android package name + signing certificate SHA-1, that is the entire setup.

---

## Step 1, Get your debug SHA-1 fingerprint

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

Keep it handy, you will paste it in Step 4.

---

## Step 2, Create or pick a Google Cloud project

1. Go to [https://console.cloud.google.com/](https://console.cloud.google.com/)
2. Top-left project picker → **New Project**
3. Name it `Joulie` → **Create**
4. Wait for it to provision; make sure it is the active project before continuing

---

## Step 3, Enable the Google Drive API

1. In the left menu go to **APIs & Services → Library**
2. Search for **Google Drive API**
3. Click it → click **Enable**

---

## Step 4, Configure the OAuth consent screen

1. **APIs & Services → OAuth consent screen**
2. Choose **External** → **Create**
3. Fill in:
   - **App name:** Joulie
   - **User support email:** your email address
   - **Developer contact email:** your email address
4. **Save and Continue**
5. **Scopes** → **Add or Remove Scopes** → search `drive.appdata` → check `https://www.googleapis.com/auth/drive.appdata` → **Update**
6. **Save and Continue**
7. **Test users** → **+ Add users** → enter your Google account → **Add**
8. **Save and Continue** → **Back to Dashboard**

You can leave the consent screen in "Testing" status indefinitely, Drive AppData scope is non-sensitive and does not require Google verification.

---

## Step 5, Create the Android OAuth 2.0 Client ID

1. **APIs & Services → Credentials**
2. **+ Create Credentials → OAuth client ID**
3. **Application type:** Android
4. **Name:** Joulie (debug), create one per keystore SHA-1
5. **Package name:** `org.spsl.evtracker`
6. **SHA-1 certificate fingerprint:** paste the value from Step 1
7. **Create**

Repeat this step for the **release** keystore SHA-1, debug and release each need their own client. To get the release keystore's SHA-1:

```bash
keytool -list -v -keystore /path/to/release.jks -alias <your-alias> | grep SHA1
```

Console expects it colon-separated and uppercase, e.g. `A3:9F:ED:12:1D:AE:...`. The same SHA-1 is used for both locally-built signed APKs and APKs produced by the `.github/workflows/release.yml` CI workflow, since CI signs with the same release keystore (uploaded as a base64 secret).

That's the entire OAuth setup. There is no JSON file to download. The Authorization API client looks up the OAuth client at runtime by your app's package name + signing certificate, no `google-services.json`, no Gradle plugin needed.

---

## Step 5b, Register a third client for the debug `applicationId` suffix

After **TASK-29** (merged 2026-05-01) the **debug** build type uses
`applicationId = org.spsl.evtracker.debug` (release stays at
`org.spsl.evtracker`, Step 5 above still applies for release builds).
The OAuth Android client created in Step 5 is bound to a fixed package
name, so debug builds need their own client:

1. **APIs & Services → Credentials → + Create Credentials → OAuth client ID**
2. **Application type:** Android
3. **Name:** Joulie (debug-suffix)
4. **Package name:** `org.spsl.evtracker.debug`
5. **SHA-1:** the same debug keystore SHA-1 from Step 1
6. **Create**

Without this client, Drive sign-in fails on debug builds. Release
builds are unaffected.

---

## Step 6, Build and verify

1. Build and install the debug APK:
   ```bash
   ./gradlew assembleDebug
   adb install app/build/outputs/apk/debug/app-debug.apk    # installs as org.spsl.evtracker.debug
   ```
2. Open the app → **Settings → Google Drive backup** → toggle ON
3. The system Authorization sheet appears asking for permission to manage Joulie files in its hidden folder
4. Sign in with the same Google account you added as a test user in Step 4
5. Save a charge event **or** tap **Settings → Back up now** (TASK-31), then verify the backup file exists by calling Drive `files.list` with `spaces=appDataFolder` (the App Data folder is hidden from the regular Drive UI). Use **Settings → Wipe remote backup** to scrub the file when re-testing the first-time replace-or-skip restore flow.

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| "Sign-in failed" / immediate dismiss | SHA-1 mismatch. Re-run the `keytool` command, compare with what's on the OAuth client in **Credentials**. |
| "Access blocked: app not verified" | Your Google account is not in the test users list. Re-do Step 4.7. |
| Authorization sheet never appears | Wrong package name on the OAuth client. Must be exactly `org.spsl.evtracker`. |
| Switching keystore (debug ↔ release) breaks sign-in | Each keystore SHA-1 needs its own OAuth client. Repeat Step 5 for the release SHA-1. |
| Sign-in fails on **debug** specifically (release works) | Missing the `org.spsl.evtracker.debug` OAuth client. Run Step 5b. |
| Backup file not visible in Drive web UI | Expected, the App Data folder is hidden. Use the Drive API explorer with `spaces=appDataFolder`. |
| Auth was revoked from your Google Account but the app keeps trying to back up | TASK-19 surfaces this: after the next backup attempt the user gets a `backup_auth` notification ("Drive sign-in required, Tap to reconnect"). Tapping deep-links to Settings; toggle Drive off and on to re-authorise. Test users on a fresh device get this card the first time their token can't be silently renewed. |
| Repeated backup failures with no notification | On Android 13+, `POST_NOTIFICATIONS` is gated behind a runtime permission. The app requests it the first time consecutive failures hit the threshold (3) and **never re-prompts after a denial**. To re-grant, go to system **Settings → Apps → Joulie → Notifications** and re-enable. |
