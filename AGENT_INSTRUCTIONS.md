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

Clone the repo and verify files:
```bash
git clone git@github.com:SPS-L/EV-android-app.git
cd EV-android-app
```

The repo contains design docs and Gradle config stubs. Implement all Kotlin source files and XML resources described in `DESIGN.md`.

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
    indices = [Index("carId"), Index("eventDate"), Index("chargeType"), Index("location")]
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
@Entity(tableName = "custom_locations")
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
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_cl_label ON custom_locations(label)")
                db.execSQL("ALTER TABLE charge_events ADD COLUMN cost_total   REAL")
                db.execSQL("ALTER TABLE charge_events ADD COLUMN cost_per_kwh REAL")
                db.execSQL("ALTER TABLE charge_events ADD COLUMN currency     TEXT")
                db.execSQL("ALTER TABLE charge_events ADD COLUMN location     TEXT")
                db.execSQL("ALTER TABLE charge_events ADD COLUMN note         TEXT")
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

Key stats computation — always use delta-odometer method:
```kotlin
fun computeStats(events: List<ChargeEvent>, label: String): Stats {
    if (events.size < 2) return Stats(label, 0.0, 0.0, null, 0)
    var totalKwh = 0.0; var totalDist = 0.0
    var totalCost = 0.0; var costCount = 0
    for (i in 1 until events.size) {
        val dist = events[i].odometerKm - events[i-1].odometerKm
        if (dist > 0) {
            totalKwh += events[i].kwhAdded
            totalDist += dist
            events[i].costTotal?.let { totalCost += it; costCount++ }
        }
    }
    val avg = if (totalKwh > 0) totalDist / totalKwh else null
    val costPerKm = if (costCount > 0 && totalDist > 0) totalCost / totalDist else null
    return Stats(label, totalKwh, totalDist, avg, events.size - 1,
        costPerKm, costPerKm?.let { it * 100 })
}
```

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

### DriveAuthManager.kt
```kotlin
class DriveAuthManager(private val context: Context) {
    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestScopes(Scope(DriveScopes.DRIVE_APPDATA)).build()
    val client: GoogleSignInClient = GoogleSignIn.getClient(context, gso)

    suspend fun silentSignIn(): GoogleSignInAccount? = suspendCancellableCoroutine { cont ->
        client.silentSignIn().addOnCompleteListener { task ->
            cont.resume(if (task.isSuccessful) task.result else null)
        }
    }
}
```

### DriveBackupManager.kt

Key methods:
- `suspend fun backup(cars, events, locations)` — serialize to JSON (backup_version=3), upload to App Data folder
- `suspend fun restore(): BackupData?` — download & parse `evtracker_backup.json`
- Auto-trigger via WorkManager `OneTimeWorkRequest` after every charge save

**Backup JSON (v3):**
```json
{
  "backup_version": 3,
  "exported_at": "<ISO8601>",
  "cars": [...],
  "charge_events": [...],
  "custom_locations": [ { "label": "Supercharger A6", "use_count": 4 } ]
}
```

**Restore flow:** fetch file → if exists show dialog "Found backup from [date]. Restore?" → on confirm clear DB and import → on skip keep local data.

---

## Step 8 — CsvExporter.kt

```kotlin
object CsvExporter {
    fun export(context: Context, car: Car, events: List<ChargeEvent>, useKm: Boolean): Uri {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "ev_${car.name}_${System.currentTimeMillis()}.csv")
        file.printWriter().use { out ->
            out.println("Date,Odometer (${if (useKm) "km" else "mi"}),kWh Added,Charge Type,Location,Cost Total,Cost/kWh,Currency,Efficiency,Note")
            events.forEachIndexed { i, e ->
                val odo = if (useKm) e.odometerKm else e.odometerKm * 0.621371
                val dist = if (i > 0) e.odometerKm - events[i-1].odometerKm else 0.0
                val eff = if (dist > 0 && e.kwhAdded > 0) dist / e.kwhAdded else 0.0
                out.println("${e.eventDate},$odo,${e.kwhAdded},${e.chargeType},${e.location ?: ""},${e.costTotal ?: ""},${e.costPerKwh ?: ""},${e.currency ?: ""},$eff,${e.note}")
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
