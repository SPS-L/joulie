# Agent Instructions — Build EV Efficiency Tracker APK

This document gives a step-by-step guide for an AI coding agent (or developer) to implement the full application defined in `DESIGN.md` and produce a signed debug APK.

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

The repo contains design docs and Gradle config stubs. The agent must implement all Kotlin source files and XML resources described in `DESIGN.md § 12`.

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
    val createdAt: Long = System.currentTimeMillis()
)
```

**ChargeEvent.kt**
```kotlin
@Entity(
    tableName = "charge_events",
    foreignKeys = [ForeignKey(entity = Car::class, parentColumns = ["id"],
        childColumns = ["carId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("carId"), Index("eventDate")]
)
data class ChargeEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val carId: Int,
    val eventDate: Long,
    val odometerKm: Double,
    val kwhAdded: Double,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
```

**Stats.kt**
```kotlin
data class Stats(
    val label: String,
    val totalKwh: Double,
    val totalDistanceKm: Double,
    val avgEfficiencyKmPerKwh: Double,
    val chargeCount: Int
) {
    val avgConsumptionPer100km: Double
        get() = if (totalDistanceKm > 0) (totalKwh / totalDistanceKm) * 100.0 else 0.0
}
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

    @Query("""SELECT * FROM charge_events WHERE carId = :carId 
              AND eventDate BETWEEN :from AND :to ORDER BY eventDate ASC""")
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

### 2.3 AppDatabase.kt
```kotlin
@Database(entities = [Car::class, ChargeEvent::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun carDao(): CarDao
    abstract fun chargeEventDao(): ChargeEventDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "ev_tracker.db")
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

Key method — compute Stats from a list of sorted events:
```kotlin
fun computeStats(events: List<ChargeEvent>, label: String): Stats {
    if (events.isEmpty()) return Stats(label, 0.0, 0.0, 0.0, 0)
    var totalKwh = 0.0; var totalDist = 0.0
    for (i in 1 until events.size) {
        val dist = events[i].odometerKm - events[i-1].odometerKm
        if (dist > 0) { totalKwh += events[i].kwhAdded; totalDist += dist }
    }
    val avg = if (totalKwh > 0) totalDist / totalKwh else 0.0
    return Stats(label, totalKwh, totalDist, avg, events.size - 1)
}
```

---

## Step 4 — Implement ViewModels

Each ViewModel exposes `StateFlow` or `LiveData` consumed by its Fragment.

### DashboardViewModel.kt
```kotlin
class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ChargeEventRepository(AppDatabase.getInstance(app).chargeEventDao())
    private val _activeCarId = MutableStateFlow<Int>(-1)

    val lastChargeStats: StateFlow<Stats?> = ...  // derived from _activeCarId + DB
    val last7DaysStats: StateFlow<Stats?> = ...
    val last30DaysStats: StateFlow<Stats?> = ...
    val yearStats: StateFlow<Stats?> = ...
    val customStats: MutableStateFlow<Stats?> = MutableStateFlow(null)

    fun setCustomPeriod(from: Long, to: Long) { /* query DB and update customStats */ }
}
```

---

## Step 5 — Implement UI

### 5.1 activity_main.xml

Single `FragmentContainerView` + `BottomNavigationView` with three tabs:
- Dashboard (ic_home)
- Charges log (ic_list)
- Charts (ic_bar_chart)

Top-right: Settings icon button.

### 5.2 fragment_dashboard.xml

- `MaterialToolbar` with car-selector `Spinner`
- `HorizontalScrollView` > `LinearLayout` containing 5 `MaterialCardView` (stats cards)
- `RecyclerView` for recent 5 events
- `FloatingActionButton` (ic_add)

Each stats card (`card_stats.xml`) contains:
- Title (e.g., "Last 30 days")
- Big number: efficiency in km/kWh or mi/kWh
- Secondary row: total kWh · total distance · # charges

### 5.3 fragment_charge_edit.xml

- `TextInputLayout` + `TextInputEditText` for odometer, kWh, note
- `MaterialButton` for date-time picker (shows current selection)
- Save / Cancel buttons

### 5.4 fragment_charts.xml

- `TabLayout` (3 tabs)
- `ViewPager2` with 3 child fragments, each containing one `LineChart`/`BarChart`/`ScatterChart`

### 5.5 Charts implementation (MPAndroidChart)

```kotlin
// Efficiency Trend (LineChart)
val entries = events.mapIndexed { i, e -> Entry(i.toFloat(), e.efficiency.toFloat()) }
val dataSet = LineDataSet(entries, "km/kWh").apply {
    color = Color.parseColor("#1565C0")
    setCircleColor(color)
    lineWidth = 2f
    setDrawValues(false)
}
chart.data = LineData(dataSet)
chart.xAxis.valueFormatter = DateAxisValueFormatter(events)
chart.invalidate()
```

---

## Step 6 — Google Drive Backup

### DriveAuthManager.kt
```kotlin
class DriveAuthManager(private val context: Context) {
    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
        .build()
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
- `suspend fun backupCar(car: Car, events: List<ChargeEvent>)` — serialize to JSON, upload/overwrite file
- `suspend fun restoreAll(): List<Pair<Car, List<ChargeEvent>>>` — list app folder files, download & parse each
- `suspend fun deleteCarBackup(car: Car)` — delete file from Drive

---

## Step 7 — AppPreferences (DataStore)

Keys:
- `distanceUnit`: `"km"` | `"miles"`
- `theme`: `"system"` | `"light"` | `"dark"`
- `activeCarId`: Int (-1 = none)
- `driveEnabled`: Boolean

---

## Step 8 — CsvExporter.kt

```kotlin
object CsvExporter {
    fun export(context: Context, car: Car, events: List<ChargeEvent>, useKm: Boolean): Uri {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "ev_${car.name}_${System.currentTimeMillis()}.csv")
        file.printWriter().use { out ->
            out.println("Date,Odometer (${if (useKm) "km" else "mi"}),kWh Added,Efficiency,Note")
            for (i in events.indices) {
                val odo = if (useKm) events[i].odometerKm else events[i].odometerKm * 0.621371
                val dist = if (i > 0) events[i].odometerKm - events[i-1].odometerKm else 0.0
                val eff = if (dist > 0 && events[i].kwhAdded > 0) dist / events[i].kwhAdded else 0.0
                out.println("${events[i].eventDate},${odo},${events[i].kwhAdded},${eff},${events[i].note}")
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
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

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

## Checklist

- [ ] All Kotlin files implemented (see DESIGN.md § 12 file tree)
- [ ] All XML layouts implemented
- [ ] Room migrations not needed (fresh install, version 1)
- [ ] Nav graph connects all fragments
- [ ] Dark theme tested
- [ ] Drive backup tested with emulator (add fake Google account)
- [ ] CSV export tested — file appears in Downloads
- [ ] All unit tests pass
- [ ] All instrumented tests pass
- [ ] APK installs on API 26+ device/emulator
