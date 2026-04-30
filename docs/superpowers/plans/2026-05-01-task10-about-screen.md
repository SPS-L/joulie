# TASK-10 — About / Info screen + launcher icon pack — Implementation plan

> **For agentic workers:** doc-driven feature work. Two halves: (1) icon
> pack drop-in (mostly file copy + small XML rewrites), (2) About
> Fragment + nav wiring + instrumented test. Order chosen so the icon
> pack lands first — the build remains buildable through every checkpoint.

**Goal:** Implement the About / Info screen accessible from Settings,
plus apply the user-provided launcher-icon asset pack.

**Tech Stack:** Fragment + ViewBinding · Material 3 cards ·
`BuildConfig.VERSION_NAME/CODE` · `Intent.ACTION_VIEW` for URLs ·
existing nav graph + Hilt patterns.

---

## Files

### New
- `app/src/main/java/org/spsl/evtracker/ui/about/AboutFragment.kt`
- `app/src/main/res/layout/fragment_about.xml`
- `app/src/androidTest/java/org/spsl/evtracker/ui/about/AboutFragmentTest.kt`
- 20 PNG files: `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher{,_round,_foreground,_background}.png`

### Modified
- `app/src/main/res/values/strings.xml` — about + settings_about strings
- `app/src/main/res/navigation/nav_graph.xml` — `aboutFragment` destination + `action_settings_to_about`
- `app/src/main/res/layout/fragment_settings.xml` — new `row_about` at the bottom
- `app/src/main/java/org/spsl/evtracker/ui/settings/SettingsFragment.kt` — bind `row_about` click
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` — `@drawable/...` → `@mipmap/...`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` — same
- `CLAUDE.md` — Status paragraph appends TASK-10
- `docs/BACKLOG.md` — overview row + outcome blockquote

### Deleted
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/drawable/ic_launcher_background.xml`

---

## Task 1 — Apply the icon asset pack

- [ ] **Step 1: Extract the inner zip into res/**

```bash
unzip -p exported-assets.zip ev_tracker_icons.zip > $TMPDIR/ic.zip
unzip -o $TMPDIR/ic.zip -d app/src/main/res/ \
    'mipmap-mdpi/*' 'mipmap-hdpi/*' 'mipmap-xhdpi/*' 'mipmap-xxhdpi/*' 'mipmap-xxxhdpi/*'
```

(Skips `play_store/` — that's for Play Console upload, not the APK.)

- [ ] **Step 2: Rewrite both `mipmap-anydpi-v26` adaptive-icon XMLs**

`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
```

Identical content for `ic_launcher_round.xml`.

- [ ] **Step 3: Delete the orphaned vector launcher drawables**

```bash
rm app/src/main/res/drawable/ic_launcher_foreground.xml
rm app/src/main/res/drawable/ic_launcher_background.xml
```

(Keep `drawable/ic_spslab_badge.xml` — used by the About screen header.)

- [ ] **Step 4: Build & lint**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug :app:lint
```

Expected: BUILD SUCCESSFUL. `UnusedResources` should not trip on the
deleted vectors (nothing should reference them after Step 2). New
PNGs in mipmap-* land cleanly.

---

## Task 2 — About strings

- [ ] **Step 1: Append a new `<!-- About -->` section before `</resources>`**

The `about_license_body` and `about_disclaimer_body` need
multi-line preservation. Use newline-escaped string content:

```xml
<!-- About / Info screen -->
<string name="about_title">About</string>
<string name="about_version_label">Version %1$s (build %2$d)</string>
<string name="about_acknowledgment_title">Developed by</string>
<string name="about_acknowledgment_lab">Sustainable Power Systems Lab (SPS-Lab)</string>
<string name="about_acknowledgment_cut">Cyprus University of Technology</string>
<string name="about_acknowledgment_location">Limassol, Cyprus</string>
<string name="about_link_sps_lab">sps-lab.org</string>
<string name="about_link_cut">cut.ac.cy</string>
<string name="about_url_sps_lab" translatable="false">https://sps-lab.org</string>
<string name="about_url_cut" translatable="false">https://cut.ac.cy</string>
<string name="about_license_title">License</string>
<string name="about_license_body">…full MIT text…</string>
<string name="about_disclaimer_title">Disclaimer</string>
<string name="about_disclaimer_body">…disclaimer text…</string>
<string name="about_oss_title">Open Source Libraries</string>
<string name="about_oss_body">…five-line list…</string>
<string name="about_open_link_failed">No browser app available to open the link.</string>
<string name="settings_about">About</string>
<string name="settings_about_summary">Version, license, acknowledgments</string>
```

URL strings get `translatable="false"` so they don't get flagged for
translation in TASK-15.

---

## Task 3 — `fragment_about.xml`

CoordinatorLayout with toolbar + nested-scroll body. Each section is a
`MaterialCardView`. Toolbar uses `?attr/actionBarTheme` and a `back`
nav icon. The app-info card includes the SPS-Lab badge (`@drawable/ic_spslab_badge`).

(See full XML in the implementation diff — it's large but
mechanical.)

---

## Task 4 — `AboutFragment.kt`

```kotlin
@AndroidEntryPoint
class AboutFragment : Fragment() {
    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(...): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.versionLabel.text = getString(
            R.string.about_version_label,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        )
        binding.linkSpsLab.setOnClickListener { openUrl(R.string.about_url_sps_lab) }
        binding.linkCut.setOnClickListener { openUrl(R.string.about_url_cut) }
    }

    private fun openUrl(@StringRes urlRes: Int) {
        val url = getString(urlRes)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.about_open_link_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

---

## Task 5 — Nav graph + Settings entry point

- [ ] **Step 1: Add `aboutFragment` destination + action**

In `nav_graph.xml`:

```xml
<fragment
    android:id="@+id/settingsFragment"
    …>
    <action
        android:id="@+id/action_settings_to_manage_locations"
        app:destination="@id/manageLocationsFragment"/>
    <action
        android:id="@+id/action_settings_to_wizard"
        app:destination="@id/wizardFragment"
        app:popUpTo="@id/nav_graph"
        app:popUpToInclusive="true"/>
    <action
        android:id="@+id/action_settings_to_about"
        app:destination="@id/aboutFragment"/>
</fragment>

<fragment
    android:id="@+id/aboutFragment"
    android:name="org.spsl.evtracker.ui.about.AboutFragment"
    android:label="@string/about_title">
    <argument
        android:name="hideBottomNav"
        app:argType="boolean"
        android:defaultValue="true"/>
</fragment>
```

- [ ] **Step 2: Add `row_about` to `fragment_settings.xml`**

Insert after `row_reset_all`. Same LinearLayout pattern as the other
rows:

```xml
<LinearLayout
    android:id="@+id/row_about"
    …>
    <TextView
        android:text="@string/settings_about"
        android:textAppearance="?attr/textAppearanceBodyLarge"/>
    <TextView
        android:text="@string/settings_about_summary"
        android:textAppearance="?attr/textAppearanceBodyMedium"/>
</LinearLayout>
```

- [ ] **Step 3: Bind click in `SettingsFragment.onViewCreated`**

```kotlin
binding.rowAbout.setOnClickListener {
    findNavController().navigate(R.id.action_settings_to_about)
}
```

---

## Task 6 — Instrumented `AboutFragmentTest`

Five test cases per the backlog spec:

```kotlin
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AboutFragmentTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

    @Before fun setUp() { hiltRule.inject() }

    @Test fun versionName_isDisplayed_andNonEmpty() { … }
    @Test fun spsLabText_isVisible() { … }
    @Test fun spsLabUrlLink_isPresent() { … }
    @Test fun licenseCard_containsMit() { … }
    @Test fun disclaimerCard_containsLiability() { … }
}
```

Use `launchFragmentInContainer<AboutFragment>(themeResId = R.style.Theme_EVTracker)`
matching the existing pattern in `SettingsFragmentTest`.

---

## Task 7 — Run full local CI gate

- [ ] ktlint + lint + JVM unit tests + assembleDebug + assembleRelease + assembleDebugAndroidTest

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew \
  ktlintCheck :app:lint :app:testDebugUnitTest \
  :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
```

Expected: all green. JVM count stays at 243.

---

## Task 8 — Update CLAUDE.md and BACKLOG.md

- [ ] CLAUDE.md Status — append TASK-10
- [ ] BACKLOG.md TASK-10 ☐ → ☑ + outcome blockquote

---

## Task 9 — Commit, merge, push, cleanup

Per repo convention: separate `git add`, `git commit`, `git push`. One
feat commit, then `--no-ff` merge into `main`, push, delete branch.

Conventional message: `feat(task-10): About screen + launcher icon pack`.

---

## Self-review

- **Spec coverage:** every spec section maps to a task.
- **Placeholders:** none.
- **Type/binding consistency:** `FragmentAboutBinding` (auto-generated
  from `fragment_about.xml`); `binding.linkSpsLab` matches
  `android:id="@+id/link_sps_lab"`; same for `linkCut`,
  `versionLabel`, `toolbar`.
- **lint risk:** the deleted vector drawables don't appear referenced
  anywhere else in `res/` (verified with `grep -rn ic_launcher_foreground app/src/main/res`
  before deletion).
