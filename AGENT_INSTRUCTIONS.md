# Agent Instructions — Build EV Efficiency Tracker APK

This document gives a complete step-by-step guide for an AI coding agent (or developer) to implement the full application defined in `DESIGN.md` and produce a debug APK.

> **Covers all phases:** project scaffold, DB, basic UI, charts, Drive backup, CSV, first-boot wizard, location chips, cost handling, multi-metric display.

---

## Prerequisites

- JDK 17+
- Android SDK (API 26 min, API 34 target, Build Tools 34.0.0)
- Gradle 8.4+
- `ANDROID_HOME` environment variable set
- Internet access (to download Maven dependencies)
- Google Play Services available on target device/emulator (for Drive backup)

---

## Step 1 — Scaffold Project

Verify the working tree:
```bash
git status
```

The repo currently contains only docs and Gradle config — no Kotlin source. Start by creating `app/src/main/java/org/spsl/evtracker/MainActivity.kt`, then implement all other Kotlin source files and XML resources described in `DESIGN.md`.

> **Column-naming convention (critical):** Room generates **camelCase** column names from Kotlin field names by default. This guide uses camelCase consistently in `ALTER TABLE`, `CREATE TABLE`, and `@Query` SQL — do not switch to snake_case unless you also annotate every field with `@ColumnInfo(name = "...")`. The SQL shown in `DESIGN.md §4.1` is illustrative; the actual column names match the entity field names.

---

## Step 2 — Implement Data Layer

### 2.1 Models (`data/model/`)

**Car.kt**
```kotlin
@Entity(tableName = "cars")
data class Car(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val make: String = "",
    val model: String = "",
    val year: Int? = null,
    val batteryKwh: Double? = null,
    val createdAt: Long = System.currentTimeMillis()
)
```

**ChargeEvent.kt**
```kotlin
@Entity(
    tableName = "charge_events",
    foreignKeys = [ForeignKey(entity = Car::class, parentColumns = ["id"],
        childColumns = ["carId"], onDelete = ForeignKey.CASCADE)],
    indices = [
        Index(value = ["carId", "eventDate"]),  // composite — matches dominant range query
        Index("chargeType"),
        Index("location")
    ]
)
data class ChargeEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val carId: Int,
    val eventDate: Long,
    val odometerKm: Double,        // always stored in km; display converts
    val kwhAdded: Double,
    val chargeType: String = "AC", // "AC" | "DC"
    val costTotal: Double? = null, // NULL = excluded from cost stats
    val costPerKwh: Double? = null,
    val currency: String? = null,
    val location: String? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
```

**CustomLocation.kt**
```kotlin
@Entity(
    tableName = "custom_locations",
    indices = [Index(value = ["label"], unique = true)]
)
data class CustomLocation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val useCount: Int = 1,
    val lastUsed: Long = System.currentTimeMillis()
)
```

**Stats.kt**
```kotlin
data class Stats(
    val label: String,
    val totalKwh: Double,
    val totalDistanceKm: Double,
    val avgEfficiencyKmPerKwh: Double?,
    val chargeCount: Int,
    val costPerKm: Double? = null,
    val costPer100km: Double? = null
) {
    val kwhPer100km: Double?
        get() = if (totalDistanceKm > 0) (totalKwh / totalDistanceKm) * 100.0 else null
    val miPerKwh: Double?
        get() = avgEfficiencyKmPerKwh?.let { it * 0.621371 }
}

data class EfficiencyStats(
    val kmPerKwh: Double?,
    val kwhPer100km: Double?,
    val miPerKwh: Double?,
    val costPerKm: Double?,
    val costPer100km: Double?
)
```

### 2.2 DAOs

**CarDao.kt**
```kotlin
@Dao
interface CarDao {
    @Query("SELECT * FROM cars ORDER BY name ASC")
    fun getAllCars(): Flow<List<Car>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(car: Car): Long

    @Update
    suspend fun update(car: Car)

    @Delete
    suspend fun delete(car: Car)
}
```

**ChargeEventDao.kt**
```kotlin
@Dao
interface ChargeEventDao {
    @Query("SELECT * FROM charge_events WHERE carId = :carId ORDER BY eventDate DESC")
    fun getEventsForCar(carId: Int): Flow<List<ChargeEvent>>

    @Query("SELECT * FROM charge_events WHERE carId = :carId AND eventDate BETWEEN :from AND :to ORDER BY eventDate ASC")
    suspend fun getEventsInRange(carId: Int, from: Long, to: Long): List<ChargeEvent>

    @Query("SELECT * FROM charge_events WHERE carId = :carId ORDER BY eventDate ASC")
    suspend fun getAllEventsForCarSorted(carId: Int): List<ChargeEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: ChargeEvent): Long

    @Update
    suspend fun update(event: ChargeEvent)

    @Delete
    suspend fun delete(event: ChargeEvent)

    @Query("DELETE FROM charge_events WHERE carId = :carId")
    suspend fun deleteAllForCar(carId: Int)
}
```

**CustomLocationDao.kt**
```kotlin
@Dao
interface CustomLocationDao {
    @Query("SELECT * FROM custom_locations ORDER BY useCount DESC, lastUsed DESC LIMIT 5")
    fun getTopLocations(): Flow<List<CustomLocation>>

    @Query("SELECT label FROM custom_locations ORDER BY useCount DESC, lastUsed DESC")
    suspend fun getAllLabels(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(loc: CustomLocation): Long

    @Query("UPDATE custom_locations SET useCount = useCount + 1, lastUsed = :ts WHERE label = :label")
    suspend fun increment(label: String, ts: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM custom_locations WHERE label = :label")
    suspend fun exists(label: String): Int

    @Delete
    suspend fun delete(loc: CustomLocation)
}
```

### 2.3 AppDatabase.kt
```kotlin
@Database(
    entities = [Car::class, ChargeEvent::class, CustomLocation::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun carDao(): CarDao
    abstract fun chargeEventDao(): ChargeEventDao
    abstract fun customLocationDao(): CustomLocationDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE charge_events ADD COLUMN chargeType TEXT NOT NULL DEFAULT 'AC'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS custom_locations (
                        id        INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        label     TEXT    NOT NULL,
                        useCount  INTEGER NOT NULL DEFAULT 1,
                        lastUsed  INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_custom_locations_label ON custom_locations(label)")
                db.execSQL("ALTER TABLE charge_events ADD COLUMN costTotal   REAL")
                db.execSQL("ALTER TABLE charge_events ADD COLUMN costPerKwh  REAL")
                db.execSQL("ALTER TABLE charge_events ADD COLUMN currency    TEXT")
                db.execSQL("ALTER TABLE charge_events ADD COLUMN location    TEXT")
                db.execSQL("ALTER TABLE charge_events ADD COLUMN note        TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "ev_tracker.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
    }
}
```

---

## Step 3 — Implement Repositories

### CarRepository.kt
```kotlin
class CarRepository(private val dao: CarDao) {
    val allCars: Flow<List<Car>> = dao.getAllCars()
    suspend fun insert(car: Car) = dao.insert(car)
    suspend fun update(car: Car) = dao.update(car)
    suspend fun delete(car: Car) = dao.delete(car)
}
```

### ChargeEventRepository.kt

Key stats computation — always use delta-odometer method. Single-event periods still report `totalKwh` and `chargeCount`; only efficiency/distance/cost-per-km require ≥ 2 events. Cost stats are suppressed when the period contains more than one currency.

```kotlin
fun computeStats(events: List<ChargeEvent>, label: String): Stats {
    val totalKwhAll = events.sumOf { it.kwhAdded }
    val chargeCount = events.size

    // Need at least 2 events for any delta-based metric.
    if (events.size < 2) {
        return Stats(label, totalKwhAll, 0.0, null, chargeCount, null, null)
    }

    // Cost stats are only meaningful when costed events share a single currency.
    val costedCurrencies = events.mapNotNull { e -> e.costTotal?.let { e.currency } }.distinct()
    val mixedCurrency = costedCurrencies.size > 1

    var pairKwh = 0.0; var totalDist = 0.0
    var totalCost = 0.0; var costCount = 0
    for (i in 1 until events.size) {
        val dist = events[i].odometerKm - events[i-1].odometerKm
        if (dist > 0) {
            pairKwh += events[i].kwhAdded
            totalDist += dist
            if (!mixedCurrency) events[i].costTotal?.let { totalCost += it; costCount++ }
        }
    }
    val avgKmPerKwh = if (pairKwh > 0) totalDist / pairKwh else null
    val costPerKm = if (costCount > 0 && totalDist > 0) totalCost / totalDist else null
    return Stats(
        label = label,
        totalKwh = totalKwhAll,
        totalDistanceKm = totalDist,
        avgEfficiencyKmPerKwh = avgKmPerKwh,
        chargeCount = chargeCount,
        costPerKm = costPerKm,
        costPer100km = costPerKm?.let { it * 100 }
    )
}
```

> **Multi-currency rule:** if any two costed events in the period have different `currency` values, all cost stats return `null`. The Dashboard must show a "Multi-currency period — cost stats hidden" banner instead of the cost cards.

#### Monthly aggregation helper (used by ChartsViewModel)

```kotlin
data class MonthBucket(
    val yearMonth: YearMonth,  // java.time
    val totalKwh: Double,
    val totalCost: Double?,    // null if no costed events or mixed currency
    val currency: String?
)

fun monthlyTotals(events: List<ChargeEvent>): List<MonthBucket> {
    val zone = ZoneId.systemDefault()
    return events.groupBy { YearMonth.from(Instant.ofEpochMilli(it.eventDate).atZone(zone)) }
        .toSortedMap()
        .map { (ym, list) ->
            val currencies = list.mapNotNull { it.currency }.distinct()
            val mixed = currencies.size > 1
            val cost = if (mixed) null
                       else list.mapNotNull { it.costTotal }.takeIf { it.isNotEmpty() }?.sum()
            MonthBucket(ym, list.sumOf { it.kwhAdded }, cost, currencies.singleOrNull())
        }
}
```
The monthly cost bar chart hides any bucket whose `totalCost` is `null`.

### LocationRepository.kt
```kotlin
class LocationRepository(private val dao: CustomLocationDao) {
    val topLocations: Flow<List<CustomLocation>> = dao.getTopLocations()

    suspend fun recordUsage(label: String) {
        if (label.isBlank()) return
        if (dao.exists(label) > 0) dao.increment(label)
        else dao.insert(CustomLocation(label = label))
    }

    suspend fun delete(loc: CustomLocation) = dao.delete(loc)
    suspend fun getAllLabels() = dao.getAllLabels()
}
```

---

## Step 4 — DataStore (Preferences)

```kotlin
object PreferenceKeys {
    val SETUP_COMPLETE   = booleanPreferencesKey("setupComplete")
    val PRIMARY_METRIC   = stringPreferencesKey("primaryMetric")  // km_per_kwh | kwh_per_100km | mi_per_kwh
    val DISTANCE_UNIT    = stringPreferencesKey("distanceUnit")    // km | miles
    val CURRENCY         = stringPreferencesKey("currency")        // EUR, USD, …
    val ACTIVE_CAR_ID    = intPreferencesKey("activeCarId")        // -1 = none
    val DRIVE_ENABLED    = booleanPreferencesKey("driveEnabled")
    val THEME            = stringPreferencesKey("theme")           // system | light | dark
}
```

---

## Step 5 — First-Boot Wizard

### WizardViewModel.kt
```kotlin
class WizardViewModel(private val dataStore: DataStore<Preferences>) : ViewModel() {
    var selectedMetric   by mutableStateOf("km_per_kwh")
    var selectedUnit     by mutableStateOf("km")
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

### WizardFragment.kt (ViewPager2, 3 pages)
```kotlin
class WizardFragment : Fragment() {
    private val vm: WizardViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = WizardPagerAdapter(this)   // 3 pages
        binding.viewPager.adapter = adapter
        TabLayoutMediator(binding.tabs, binding.viewPager) { _, _ -> }.attach()

        binding.btnNext.setOnClickListener {
            val cur = binding.viewPager.currentItem
            if (cur < 2) binding.viewPager.currentItem = cur + 1
            else { vm.finish(); findNavController().navigate(R.id.action_wizard_to_dashboard) }
        }
        binding.btnBack.setOnClickListener {
            if (binding.viewPager.currentItem > 0) binding.viewPager.currentItem--
        }
    }
}
```

Pages: `WizardWelcomePage`, `WizardMetricPage` (RadioGroup + MaterialButtonToggleGroup; selecting mi/kWh auto-checks miles), `WizardCurrencyPage` (ExposedDropdownMenu from `R.array.currencies`).

### MainActivity first-run routing
```kotlin
lifecycleScope.launch {
    val done = dataStore.data.first()[PreferenceKeys.SETUP_COMPLETE] ?: false
    if (!done) findNavController(R.id.nav_host_fragment).navigate(R.id.wizardFragment)
}
```

Settings → Reset preferences sets `SETUP_COMPLETE = false` and navigates to wizard.

---

## Step 6 — Implement UI

### activity_main.xml
Single `FragmentContainerView` + `BottomNavigationView` (Dashboard · Charges · Charts). Top-right Settings icon.

### fragment_dashboard.xml
- `MaterialToolbar` with car `Spinner`
- Filter `ChipGroup`: All / AC / DC
- Period `TabLayout`: Last charge · 7d · 30d · Year · Custom
- **Primary metric** large `MaterialCardView`; two smaller side-by-side cards for other metrics
- Cost row (hidden when all `costTotal IS NULL` for period)
- `RecyclerView` recent 5 events
- `FloatingActionButton` (ic_add)

### fragment_charge_edit.xml
- Date/time `MaterialButton` (default: now)
- Odometer `TextInputLayout` (label adapts to unit pref)
- kWh added `TextInputLayout`
- AC / DC `MaterialButtonToggleGroup`
- Location row: fixed chips (Home, Work, Public) + top-5 dynamic chips + `+ Add` chip → free-text field
- Cost section (collapsed): Total cost / Per kWh toggle + amount field + currency label
- Note `TextInputLayout`

```kotlin
// Dynamic location chips
vm.topLocations.observe(viewLifecycleOwner) { locations ->
    binding.chipGroupLocations.removeViews(3, binding.chipGroupLocations.childCount - 4)
    locations.forEach { loc ->
        val chip = Chip(requireContext()).apply {
            text = loc.label; isCheckable = true
            setOnClickListener { binding.etLocation.setText(loc.label) }
        }
        binding.chipGroupLocations.addView(chip, binding.chipGroupLocations.childCount - 1)
    }
}
binding.chipAdd.setOnClickListener { binding.etLocation.requestFocus() }
```

### Cost parsing (call before every ChargeEvent insert)
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

### fragment_charts.xml
`TabLayout` + `ViewPager2`; 5 child fragments:
1. Line chart: efficiency trend (AC series blue, DC series orange)
2. Bar chart: monthly kWh
3. Bar chart: monthly cost (hidden if no cost data)
4. Pie chart: AC vs DC split
5. Pie chart: location distribution

```kotlin
// Efficiency Trend (LineChart via MPAndroidChart)
val entries = events.mapIndexed { i, e -> Entry(i.toFloat(), e.efficiency.toFloat()) }
val dataSet = LineDataSet(entries, "km/kWh").apply {
    color = Color.parseColor("#1565C0"); setCircleColor(color)
    lineWidth = 2f; setDrawValues(false)
}
chart.data = LineData(dataSet)
chart.xAxis.valueFormatter = DateAxisValueFormatter(events)
chart.invalidate()
```

---

## Step 7 — Google Drive Backup

**Scope:** `https://www.googleapis.com/auth/drive.appdata` (non-sensitive)

> **API note:** the legacy `GoogleSignIn.getClient(...)` API is deprecated. New apps should use the **Authorization API** (`Identity.getAuthorizationClient`) for incremental scopes like `drive.appdata`. No `google-services.json` is required — the OAuth client is bound to the package name + signing certificate SHA-1 in Google Cloud Console.

### DriveAuthManager.kt
```kotlin
class DriveAuthManager(private val activity: ComponentActivity) {
    private val client = Identity.getAuthorizationClient(activity)

    suspend fun authorizeForAppData(): AuthorizationResult = suspendCancellableCoroutine { cont ->
        val request = AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(DriveScopes.DRIVE_APPDATA)))
            .build()
        client.authorize(request)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    fun pendingIntentLauncher(activity: ComponentActivity, onResult: (AuthorizationResult) -> Unit) =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            onResult(client.getAuthorizationResultFromIntent(res.data))
        }
}
```
On the first call, `AuthorizationResult.hasResolution()` will be `true`; launch the contained `PendingIntent` to show consent. Subsequent calls return tokens silently.

### DriveBackupManager.kt

Key methods:
- `suspend fun backup(cars, events, locations)` — serialize to JSON (backup_version=3), upload to App Data folder
- `suspend fun restore(): BackupData?` — download & parse `evtracker_backup.json`
- Auto-trigger via WorkManager **unique** `OneTimeWorkRequest` after every charge save (debounced)

```kotlin
fun enqueueBackup(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)  // do NOT run offline
        .build()
    val request = OneTimeWorkRequestBuilder<DriveBackupWorker>()
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .setInitialDelay(5, TimeUnit.SECONDS)  // small debounce window
        .build()
    WorkManager.getInstance(context)
        .enqueueUniqueWork("drive_backup", ExistingWorkPolicy.REPLACE, request)
}
```
- `enqueueUniqueWork(..., REPLACE, ...)` collapses rapid successive saves into a single backup.
- `NetworkType.CONNECTED` lets WorkManager queue offline saves and run them when the device reconnects.

**Backup JSON (v3):** authoritative per-entity field list lives in `DESIGN.md §8`. Use Gson with `FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES` so the on-disk JSON keys are snake_case (`cost_total`, `use_count`, `last_used`) while the in-memory entity fields stay camelCase. Bumping any entity requires bumping `backup_version` **and** updating §8.

**Restore flow:** fetch file → if exists show dialog "Found backup from [date]. Restore?" → on confirm clear DB and import → on skip keep local data.

---

## Step 8 — CsvExporter.kt

```kotlin
object CsvExporter {
    fun export(context: Context, car: Car, events: List<ChargeEvent>, useKm: Boolean): Uri {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "ev_${car.name}_${System.currentTimeMillis()}.csv")
        val effHeader = if (useKm) "Efficiency (km/kWh)" else "Efficiency (mi/kWh)"
        file.printWriter().use { out ->
            out.println("Date,Odometer (${if (useKm) "km" else "mi"}),kWh Added,Charge Type,Location,Cost Total,Cost/kWh,Currency,$effHeader,Note")
            events.forEachIndexed { i, e ->
                val odo = if (useKm) e.odometerKm else e.odometerKm * 0.621371
                val distKm = if (i > 0) e.odometerKm - events[i-1].odometerKm else 0.0
                val effField = when {
                    distKm <= 0 || e.kwhAdded <= 0 -> ""  // first event or odometer regression: blank, not 0
                    useKm -> (distKm / e.kwhAdded).toString()
                    else -> (distKm * 0.621371 / e.kwhAdded).toString()
                }
                out.println("${e.eventDate},$odo,${e.kwhAdded},${e.chargeType},${e.location ?: ""},${e.costTotal ?: ""},${e.costPerKwh ?: ""},${e.currency ?: ""},$effField,${e.note}")
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
```

---

## Step 9 — Build & Package APK

```bash
cd EV-android-app
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

For release (requires keystore):
```bash
./gradlew assembleRelease
```

---

## Step 10 — Run Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests (requires connected device or emulator API 26+)
./gradlew connectedAndroidTest
```

---

## Final Checklist

- [ ] All Kotlin files implemented per DESIGN.md
- [ ] All XML layouts implemented
- [ ] DB version = 3; migrations 1→2 and 2→3 registered
- [ ] `setupComplete` defaults to `false`; wizard shown on first launch
- [ ] Wizard sets all 5 DataStore keys; navigates to Dashboard on Finish
- [ ] `custom_locations` table created; top-5 chips rendered dynamically
- [ ] Home / Work / Public chips always present in charge form
- [ ] Cost = 0 or blank → `costTotal = NULL`; excluded from all stats
- [ ] Dashboard hides cost rows when no cost data
- [ ] All 3 efficiency metrics visible on dashboard; primary metric highlighted per pref
- [ ] Drive backup JSON version = 3; includes `custom_locations`
- [ ] Nav graph connects all fragments including WizardFragment
- [ ] Dark theme tested
- [ ] Drive backup tested with emulator
- [ ] CSV export tested — file opens in spreadsheet app
- [ ] All unit tests pass
- [ ] All instrumented tests pass
- [ ] APK installs on API 26+ device/emulator
