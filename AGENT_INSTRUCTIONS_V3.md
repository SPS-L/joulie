# Agent Instructions — v3 Additions

> This file supplements `AGENT_INSTRUCTIONS.md` and `AGENT_INSTRUCTIONS_V2.md`.
> Implement the features in this file **after** completing v1 and v2 steps.
> New in v3: first-boot wizard, location chips + `custom_locations` table, cost=NULL rule, multi-metric display.

---

## Step 1 — Add `custom_locations` table

### Entity
```kotlin
@Entity(tableName = "custom_locations")
data class CustomLocation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val useCount: Int = 1,
    val lastUsed: Long = System.currentTimeMillis()
)
```

### DAO
```kotlin
@Dao
interface CustomLocationDao {
    @Query("SELECT * FROM custom_locations ORDER BY use_count DESC, last_used DESC LIMIT 5")
    fun getTopLocations(): Flow<List<CustomLocation>>

    @Query("SELECT label FROM custom_locations ORDER BY use_count DESC, last_used DESC")
    suspend fun getAllLabels(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(loc: CustomLocation): Long

    @Query("UPDATE custom_locations SET use_count = use_count + 1, last_used = :ts WHERE label = :label")
    suspend fun increment(label: String, ts: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM custom_locations WHERE label = :label")
    suspend fun exists(label: String): Int

    @Delete
    suspend fun delete(loc: CustomLocation)
}
```

### LocationRepository
```kotlin
class LocationRepository(private val dao: CustomLocationDao) {
    val topLocations: Flow<List<CustomLocation>> = dao.getTopLocations()

    suspend fun recordUsage(label: String) {
        if (label.isBlank()) return
        if (dao.exists(label) > 0) {
            dao.increment(label)
        } else {
            dao.insert(CustomLocation(label = label))
        }
    }

    suspend fun delete(loc: CustomLocation) = dao.delete(loc)
    suspend fun getAllLabels() = dao.getAllLabels()
}
```

### Room migration 2 → 3
```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS custom_locations (
                id        INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                label     TEXT    NOT NULL,
                useCount  INTEGER NOT NULL DEFAULT 1,
                lastUsed  INTEGER NOT NULL
            )
        """)
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_cl_label ON custom_locations(label)")
        database.execSQL("ALTER TABLE charge_events ADD COLUMN cost_total    REAL")
        database.execSQL("ALTER TABLE charge_events ADD COLUMN cost_per_kwh  REAL")
        database.execSQL("ALTER TABLE charge_events ADD COLUMN currency      TEXT")
        database.execSQL("ALTER TABLE charge_events ADD COLUMN location      TEXT")
        database.execSQL("ALTER TABLE charge_events ADD COLUMN note          TEXT")
    }
}
```

Register in `AppDatabase.Builder`: `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`

---

## Step 2 — DataStore keys

```kotlin
object PreferenceKeys {
    val SETUP_COMPLETE   = booleanPreferencesKey("setupComplete")
    val PRIMARY_METRIC   = stringPreferencesKey("primaryMetric")
    val DISTANCE_UNIT    = stringPreferencesKey("distanceUnit")
    val CURRENCY         = stringPreferencesKey("currency")
    val ACTIVE_CAR_ID    = intPreferencesKey("activeCarId")
    val DRIVE_ENABLED    = booleanPreferencesKey("driveEnabled")
}
```

---

## Step 3 — WizardViewModel

```kotlin
class WizardViewModel(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {
    var selectedMetric by mutableStateOf("km_per_kwh")
    var selectedUnit   by mutableStateOf("km")
    var selectedCurrency by mutableStateOf("EUR")

    fun finish() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferenceKeys.PRIMARY_METRIC]  = selectedMetric
                prefs[PreferenceKeys.DISTANCE_UNIT]   = selectedUnit
                prefs[PreferenceKeys.CURRENCY]        = selectedCurrency
                prefs[PreferenceKeys.SETUP_COMPLETE]  = true
            }
        }
    }
}
```

---

## Step 4 — WizardFragment (ViewPager2)

Create `fragment_wizard.xml` with:
- `ViewPager2` (fill)
- `TabLayout` (dots indicator, below)
- `MaterialButton` back + next/finish (below tabs)

Create `WizardFragment.kt`:
```kotlin
class WizardFragment : Fragment() {
    private val vm: WizardViewModel by viewModels()
    // ...
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = WizardPagerAdapter(this)
        binding.viewPager.adapter = adapter
        TabLayoutMediator(binding.tabs, binding.viewPager) { _, _ -> }.attach()

        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < 2) binding.viewPager.currentItem = current + 1
            else {
                vm.finish()
                findNavController().navigate(R.id.action_wizard_to_dashboard)
            }
        }
        binding.btnBack.setOnClickListener {
            if (binding.viewPager.currentItem > 0)
                binding.viewPager.currentItem--
        }
    }
}
```

Create 3 inner page fragments: `WizardWelcomePage`, `WizardMetricPage`, `WizardCurrencyPage`.

`WizardMetricPage` uses `RadioGroup` for metric + `MaterialButtonToggleGroup` for unit. Auto-link: selecting `mi/kWh` auto-checks `miles` toggle.

`WizardCurrencyPage` uses `AutoCompleteTextView` (ExposedDropdownMenu) with `R.array.currencies`.

---

## Step 5 — MainActivity first-run routing

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(binding.root)
    val navController = findNavController(R.id.nav_host_fragment)

    lifecycleScope.launch {
        val prefs = dataStore.data.first()
        val done = prefs[PreferenceKeys.SETUP_COMPLETE] ?: false
        if (!done && navController.currentDestination?.id != R.id.wizardFragment) {
            navController.navigate(R.id.wizardFragment)
        }
    }
}
```

---

## Step 6 — Location chips in ChargeEditFragment

```kotlin
// Fixed chips added in XML: Home, Work, Public
// Dynamic chips added in code:
vm.topLocations.observe(viewLifecycleOwner) { locations ->
    binding.chipGroupLocations.removeViews(3, binding.chipGroupLocations.childCount - 4) // keep 3 fixed + Add chip
    locations.forEach { loc ->
        val chip = Chip(requireContext()).apply {
            text = loc.label
            isCheckable = true
            setOnClickListener { binding.etLocation.setText(loc.label) }
        }
        binding.chipGroupLocations.addView(chip, binding.chipGroupLocations.childCount - 1)
    }
}
// "+ Add" chip
binding.chipAdd.setOnClickListener { binding.etLocation.requestFocus() }
```

---

## Step 7 — Cost parsing rule

```kotlin
enum class CostMode { TOTAL, PER_KWH }

fun parseCost(value: Double?, kwh: Double, mode: CostMode): Pair<Double?, Double?> {
    if (value == null || value <= 0.0 || kwh <= 0.0) return Pair(null, null)
    return when (mode) {
        CostMode.TOTAL   -> Pair(value, value / kwh)
        CostMode.PER_KWH -> Pair(value * kwh, value)
    }
}
```

Call this before inserting `ChargeEvent`. Store the returned pair as `costTotal` and `costPerKwh`.

---

## Step 8 — Multi-metric dashboard cards

The dashboard shows all 3 efficiency metrics. The primary metric (from `primaryMetric` pref) gets a large `MaterialCardView`; the other two are smaller side-by-side cards.

```kotlin
data class EfficiencyStats(
    val kmPerKwh: Double?,
    val kwhPer100km: Double?,
    val miPerKwh: Double?,
    val costPerKm: Double?,
    val costPer100km: Double?
)
```

Hide cost cards when `costPerKm == null`.

---

## Step 9 — Build and produce APK

```bash
# From repo root
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

---

## Step 10 — Final checklist

- [ ] `setupComplete` defaults to `false`; wizard shown on first launch
- [ ] Wizard sets all 5 DataStore keys; navigates to Dashboard on Finish
- [ ] `custom_locations` table created in DB version 3
- [ ] Migration 2→3 runs without data loss
- [ ] Home / Work / Public chips always present in charge form
- [ ] Top 5 learned location chips rendered dynamically
- [ ] Cost = 0 or blank → `cost_total = NULL`; excluded from stats
- [ ] Dashboard hides cost rows when no cost data
- [ ] All 3 efficiency metrics visible on dashboard
- [ ] Drive backup JSON version = 3; includes `custom_locations`
