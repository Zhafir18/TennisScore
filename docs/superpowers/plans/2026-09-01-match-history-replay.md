# Match History & Replay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add local match history persistence and point-by-point animated replay to TennisScorer using Room SQLite.

**Architecture:** Event sourcing — every point tap is recorded in-memory during a live match and saved to Room on match completion. `ReplayViewModel` rehydrates state by stepping through stored `PointEvent`s with a configurable delay. `HistoryScreen` lists past matches; `ReplayScreen` animates the playback with play/pause and speed controls.

**Tech Stack:** Room 2.6.1, KSP 2.2.10-1.0.28, Kotlin Coroutines + StateFlow, Jetpack Compose, Navigation Compose 2.8.5

## Global Constraints

- Target SDK 37, min SDK 24 (existing `app/build.gradle.kts` values — not the spec's draft values)
- Fully offline — no network calls anywhere
- All colors must use design tokens from `ui/theme/Color.kt` — no inline `Color(0x...)` hex literals
- Room version: 2.6.1. KSP version: 2.2.10-1.0.28 (if Gradle sync fails with a KSP mismatch, check https://github.com/google/ksp/releases for the exact version matching Kotlin 2.2.10)
- No duplicate scoring logic — `fun applyPoint(state: TennisScoreState, playerNum: Int): TennisScoreState` is the single pure function, used by both `TennisScoreEngine` and `ReplayViewModel`
- Compose animation transition durations ≤ 600ms

---

### Task 1: Room Setup + Data Layer

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/example/tennisscorer/data/MatchRecord.kt`
- Create: `app/src/main/java/com/example/tennisscorer/data/PointEvent.kt`
- Create: `app/src/main/java/com/example/tennisscorer/data/MatchDao.kt`
- Create: `app/src/main/java/com/example/tennisscorer/data/TennisScorerDatabase.kt`
- Create: `app/src/main/java/com/example/tennisscorer/data/MatchRepository.kt`
- Test: `app/src/androidTest/java/com/example/tennisscorer/data/MatchDaoTest.kt`

**Interfaces — Produces:**
- `MatchRecord(matchId: Long, p1Name: String, p2Name: String, winnerName: String, p1FinalSets: Int, p2FinalSets: Int, dateTimestamp: Long)`
- `PointEvent(eventId: Long, matchId: Long, pointIndex: Int, playerNum: Int)`
- `TennisScorerDatabase.getInstance(context: Context): TennisScorerDatabase`
- `TennisScorerDatabase.matchDao(): MatchDao`
- `MatchRepository.saveMatch(record: MatchRecord, events: List<PointEvent>)`
- `MatchRepository.getAllMatches(): Flow<List<MatchRecord>>`
- `MatchRepository.getMatchById(matchId: Long): MatchRecord?`
- `MatchRepository.getEventsForMatch(matchId: Long): List<PointEvent>`
- `MatchRepository.deleteMatch(matchId: Long)`

---

- [ ] **Step 1: Add Room + KSP to libs.versions.toml**

Open `gradle/libs.versions.toml`. In the `[versions]` block, add:
```toml
ksp = "2.2.10-1.0.28"
room = "2.6.1"
```

In the `[libraries]` block, add:
```toml
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
```

In the `[plugins]` block, add:
```toml
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 2: Apply KSP plugin and Room deps in app/build.gradle.kts**

In the `plugins { }` block, add as the third entry:
```kotlin
alias(libs.plugins.ksp)
```

In the `dependencies { }` block, add:
```kotlin
implementation(libs.androidx.room.runtime)
implementation(libs.androidx.room.ktx)
ksp(libs.androidx.room.compiler)
implementation(libs.androidx.lifecycle.viewmodel.compose)
```

- [ ] **Step 3: Sync Gradle and verify**

In Android Studio click **Sync Now**, or run:
```
./gradlew dependencies --configuration debugRuntimeClasspath
```
Expected: `BUILD SUCCESSFUL`. If a KSP version mismatch error appears, check https://github.com/google/ksp/releases for the release whose name starts with `2.2.10-`.

- [ ] **Step 4: Write the failing DAO test**

Create `app/src/androidTest/java/com/example/tennisscorer/data/MatchDaoTest.kt`:
```kotlin
package com.example.tennisscorer.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchDaoTest {
    private lateinit var db: TennisScorerDatabase
    private lateinit var dao: MatchDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TennisScorerDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.matchDao()
    }

    @After fun teardown() { db.close() }

    @Test fun insertAndRetrieveMatch() = runBlocking {
        val record = MatchRecord(p1Name = "Alice", p2Name = "Bob",
            winnerName = "Alice", p1FinalSets = 2, p2FinalSets = 0, dateTimestamp = 1000L)
        val matchId = dao.insertMatch(record)
        dao.insertEvents(listOf(
            PointEvent(matchId = matchId, pointIndex = 0, playerNum = 1),
            PointEvent(matchId = matchId, pointIndex = 1, playerNum = 2)
        ))

        val allMatches = dao.getAllMatches().first()
        assertEquals(1, allMatches.size)
        assertEquals("Alice", allMatches[0].p1Name)

        val found = dao.getMatchById(matchId)
        assertNotNull(found)
        assertEquals(matchId, found!!.matchId)

        val events = dao.getEventsForMatch(matchId)
        assertEquals(2, events.size)
        assertEquals(1, events[0].playerNum)
    }

    @Test fun deleteMatchCascadesToEvents() = runBlocking {
        val matchId = dao.insertMatch(MatchRecord(p1Name = "A", p2Name = "B",
            winnerName = "A", p1FinalSets = 2, p2FinalSets = 1, dateTimestamp = 2000L))
        dao.insertEvents(listOf(PointEvent(matchId = matchId, pointIndex = 0, playerNum = 1)))

        dao.deleteMatch(matchId)

        assertEquals(0, dao.getAllMatches().first().size)
        assertEquals(0, dao.getEventsForMatch(matchId).size)
    }
}
```

- [ ] **Step 5: Run test to verify it fails**

```
./gradlew connectedAndroidTest --tests "com.example.tennisscorer.data.MatchDaoTest"
```
Expected: FAIL — `TennisScorerDatabase`, `MatchRecord`, `PointEvent`, `MatchDao` do not exist yet.

- [ ] **Step 6: Create MatchRecord entity**

Create `app/src/main/java/com/example/tennisscorer/data/MatchRecord.kt`:
```kotlin
package com.example.tennisscorer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "match_records")
data class MatchRecord(
    @PrimaryKey(autoGenerate = true) val matchId: Long = 0,
    val p1Name: String,
    val p2Name: String,
    val winnerName: String,
    val p1FinalSets: Int,
    val p2FinalSets: Int,
    val dateTimestamp: Long
)
```

- [ ] **Step 7: Create PointEvent entity**

Create `app/src/main/java/com/example/tennisscorer/data/PointEvent.kt`:
```kotlin
package com.example.tennisscorer.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "point_events",
    foreignKeys = [ForeignKey(
        entity = MatchRecord::class,
        parentColumns = ["matchId"],
        childColumns = ["matchId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class PointEvent(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0,
    val matchId: Long,
    val pointIndex: Int,
    val playerNum: Int
)
```

`onDelete = CASCADE` means deleting a `MatchRecord` automatically deletes its `PointEvent` rows.

- [ ] **Step 8: Create MatchDao**

Create `app/src/main/java/com/example/tennisscorer/data/MatchDao.kt`:
```kotlin
package com.example.tennisscorer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDao {
    @Insert
    suspend fun insertMatch(match: MatchRecord): Long

    @Insert
    suspend fun insertEvents(events: List<PointEvent>)

    @Query("SELECT * FROM match_records ORDER BY dateTimestamp DESC")
    fun getAllMatches(): Flow<List<MatchRecord>>

    @Query("SELECT * FROM match_records WHERE matchId = :matchId")
    suspend fun getMatchById(matchId: Long): MatchRecord?

    @Query("SELECT * FROM point_events WHERE matchId = :matchId ORDER BY pointIndex ASC")
    suspend fun getEventsForMatch(matchId: Long): List<PointEvent>

    @Query("DELETE FROM match_records WHERE matchId = :matchId")
    suspend fun deleteMatch(matchId: Long)
}
```

- [ ] **Step 9: Create TennisScorerDatabase**

Create `app/src/main/java/com/example/tennisscorer/data/TennisScorerDatabase.kt`:
```kotlin
package com.example.tennisscorer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MatchRecord::class, PointEvent::class], version = 1, exportSchema = false)
abstract class TennisScorerDatabase : RoomDatabase() {
    abstract fun matchDao(): MatchDao

    companion object {
        @Volatile private var INSTANCE: TennisScorerDatabase? = null

        fun getInstance(context: Context): TennisScorerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TennisScorerDatabase::class.java,
                    "tennis_scorer.db"
                ).build().also { INSTANCE = it }
            }
    }
}
```

- [ ] **Step 10: Create MatchRepository**

Create `app/src/main/java/com/example/tennisscorer/data/MatchRepository.kt`:
```kotlin
package com.example.tennisscorer.data

import kotlinx.coroutines.flow.Flow

class MatchRepository(private val dao: MatchDao) {

    suspend fun saveMatch(record: MatchRecord, events: List<PointEvent>) {
        val matchId = dao.insertMatch(record)
        dao.insertEvents(events.map { it.copy(matchId = matchId) })
    }

    fun getAllMatches(): Flow<List<MatchRecord>> = dao.getAllMatches()

    suspend fun getMatchById(matchId: Long): MatchRecord? = dao.getMatchById(matchId)

    suspend fun getEventsForMatch(matchId: Long): List<PointEvent> =
        dao.getEventsForMatch(matchId)

    suspend fun deleteMatch(matchId: Long) = dao.deleteMatch(matchId)
}
```

- [ ] **Step 11: Run tests to verify they pass**

```
./gradlew connectedAndroidTest --tests "com.example.tennisscorer.data.MatchDaoTest"
```
Expected: 2 tests PASS.

- [ ] **Step 12: Commit**

```
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/example/tennisscorer/data/ app/src/androidTest/java/com/example/tennisscorer/data/
git commit -m "feat(data): add Room entities, DAO, database, and repository"
```

---

### Task 2: Extract `applyPoint()` + Update TennisScoreEngine

**Files:**
- Modify: `app/src/main/java/com/example/tennisscorer/TennisScoreState.kt`
- Modify: `app/src/main/java/com/example/tennisscorer/TennisScoreEngine.kt`
- Test: `app/src/test/java/com/example/tennisscorer/ApplyPointTest.kt`

**Interfaces:**
- Consumes: `MatchRecord`, `PointEvent`, `MatchRepository` from Task 1
- Produces:
  - `fun applyPoint(state: TennisScoreState, playerNum: Int): TennisScoreState` — top-level pure function, same package as `TennisScoreState`
  - `TennisScoreEngine(repository: MatchRepository)` — updated constructor
  - `TennisScoreEngine.Factory(repository: MatchRepository): ViewModelProvider.Factory`

---

- [ ] **Step 1: Write failing unit tests for applyPoint()**

Create `app/src/test/java/com/example/tennisscorer/ApplyPointTest.kt`:
```kotlin
package com.example.tennisscorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyPointTest {

    private fun state(p1P: Int = 0, p2P: Int = 0, p1G: Int = 0, p2G: Int = 0,
                      p1S: Int = 0, p2S: Int = 0, tb: Boolean = false) =
        TennisScoreState("P1", "P2", p1P, p2P, p1G, p2G, p1S, p2S, tb)

    @Test fun firstPointIncrementsP1() {
        assertEquals(1, applyPoint(state(), 1).p1Points)
        assertEquals(0, applyPoint(state(), 1).p2Points)
    }

    @Test fun gameWonAt40_0() {
        val next = applyPoint(state(p1P = 3, p2P = 0), 1)
        assertEquals(1, next.p1Games)
        assertEquals(0, next.p1Points)
        assertEquals(0, next.p2Points)
    }

    @Test fun deuceResetsTo40_40() {
        // At 40-40 (p1P=3, p2P=3), advantage then opponent wins → back to 40-40
        val adState = applyPoint(state(p1P = 3, p2P = 3), 1) // P1 has AD
        assertEquals(4, adState.p1Points)
        val backToDeuce = applyPoint(adState, 2) // P2 wins → deuce
        assertEquals(3, backToDeuce.p1Points)
        assertEquals(3, backToDeuce.p2Points)
    }

    @Test fun setWonAfterSixGamesWithTwoGameLead() {
        // 5-0, win one game → 6-0 → p1 wins set
        var s = state(p1G = 5, p2G = 0)
        repeat(4) { s = applyPoint(s, 1) }
        assertEquals(1, s.p1Sets)
        assertEquals(0, s.p1Games)
    }

    @Test fun tiebreakStartsAt6_6() {
        var s = state(p1G = 5, p2G = 5)
        repeat(4) { s = applyPoint(s, 1) } // P1 wins game → 6-5
        repeat(4) { s = applyPoint(s, 2) } // P2 wins game → 6-6
        assertTrue(s.isTiebreak)
    }

    @Test fun matchFinishesAfterTwoSets() {
        var s = state(p1S = 1, p2S = 0, p1G = 5, p2G = 0)
        repeat(4) { s = applyPoint(s, 1) }
        assertTrue(s.isMatchFinished)
        assertEquals("P1", s.winnerName)
    }

    @Test fun noPointAfterMatchFinished() {
        val finished = state(p1S = 2).copy(isMatchFinished = true, winnerName = "P1")
        val same = applyPoint(finished, 1)
        assertEquals(finished, same)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew test --tests "com.example.tennisscorer.ApplyPointTest"
```
Expected: FAIL — `applyPoint` does not exist.

- [ ] **Step 3: Add applyPoint() to TennisScoreState.kt**

Add this top-level function at the end of `TennisScoreState.kt`, after the class closing brace:
```kotlin
fun applyPoint(state: TennisScoreState, playerNum: Int): TennisScoreState {
    if (state.isMatchFinished) return state

    var p1P = state.p1Points
    var p2P = state.p2Points
    var p1G = state.p1Games
    var p2G = state.p2Games
    var p1S = state.p1Sets
    var p2S = state.p2Sets
    var isTb = state.isTiebreak

    if (playerNum == 1) p1P++ else p2P++

    if (isTb) {
        if (p1P >= 7 && (p1P - p2P) >= 2) {
            p1S++; p1G = 0; p2G = 0; p1P = 0; p2P = 0; isTb = false
        } else if (p2P >= 7 && (p2P - p1P) >= 2) {
            p2S++; p1G = 0; p2G = 0; p1P = 0; p2P = 0; isTb = false
        }
    } else {
        if (p1P >= 4 && (p1P - p2P) >= 2) {
            p1G++; p1P = 0; p2P = 0
        } else if (p2P >= 4 && (p2P - p1P) >= 2) {
            p2G++; p1P = 0; p2P = 0
        } else if (p1P >= 3 && p2P >= 3 && p1P == p2P && p1P > 3) {
            p1P = 3; p2P = 3
        }
    }

    if (!isTb && p1G == 6 && p2G == 6) {
        isTb = true
    } else if (p1G >= 6 && (p1G - p2G) >= 2) {
        p1S++; p1G = 0; p2G = 0; p1P = 0; p2P = 0; isTb = false
    } else if (p2G >= 6 && (p2G - p1G) >= 2) {
        p2S++; p1G = 0; p2G = 0; p1P = 0; p2P = 0; isTb = false
    }

    var finished = false
    var winner: String? = null
    if (p1S == 2) { finished = true; winner = state.p1Name }
    else if (p2S == 2) { finished = true; winner = state.p2Name }

    return state.copy(
        p1Points = p1P, p2Points = p2P,
        p1Games = p1G, p2Games = p2G,
        p1Sets = p1S, p2Sets = p2S,
        isTiebreak = isTb,
        isMatchFinished = finished,
        winnerName = winner
    )
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
./gradlew test --tests "com.example.tennisscorer.ApplyPointTest"
```
Expected: 7 tests PASS.

- [ ] **Step 5: Rewrite TennisScoreEngine.kt**

Replace the entire content of `TennisScoreEngine.kt`:
```kotlin
package com.example.tennisscorer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.tennisscorer.data.MatchRecord
import com.example.tennisscorer.data.MatchRepository
import com.example.tennisscorer.data.PointEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TennisScoreEngine(private val repository: MatchRepository) : ViewModel() {

    private val _scoreState = MutableStateFlow(TennisScoreState())
    val scoreState: StateFlow<TennisScoreState> = _scoreState.asStateFlow()

    var p1NameInput by mutableStateOf("")
        private set
    var p2NameInput by mutableStateOf("")
        private set

    private val pendingEvents = mutableListOf<PointEvent>()

    fun pointWonBy(playerNum: Int) {
        val current = _scoreState.value
        if (current.isMatchFinished) return
        pendingEvents.add(PointEvent(matchId = 0, pointIndex = pendingEvents.size, playerNum = playerNum))
        val next = applyPoint(current, playerNum)
        _scoreState.value = next
        if (next.isMatchFinished) persistMatch(next)
    }

    private fun persistMatch(finalState: TennisScoreState) {
        val snapshot = pendingEvents.toList()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.saveMatch(
                    MatchRecord(
                        p1Name = finalState.p1Name,
                        p2Name = finalState.p2Name,
                        winnerName = finalState.winnerName ?: "",
                        p1FinalSets = finalState.p1Sets,
                        p2FinalSets = finalState.p2Sets,
                        dateTimestamp = System.currentTimeMillis()
                    ),
                    snapshot
                )
            }
        }
    }

    fun resetMatch() {
        _scoreState.value = TennisScoreState()
        pendingEvents.clear()
    }

    fun resetScore() {
        val current = _scoreState.value
        _scoreState.value = TennisScoreState(p1Name = current.p1Name, p2Name = current.p2Name)
        pendingEvents.clear()
    }

    fun setPlayerNames(p1: String, p2: String) {
        _scoreState.value = _scoreState.value.copy(p1Name = p1, p2Name = p2)
    }

    fun updateP1Name(name: String) { p1NameInput = name }
    fun updateP2Name(name: String) { p2NameInput = name }

    companion object {
        fun Factory(repository: MatchRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { TennisScoreEngine(repository) }
        }
    }
}
```

- [ ] **Step 6: Verify compilation**

```
./gradlew compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```
git add app/src/main/java/com/example/tennisscorer/TennisScoreState.kt app/src/main/java/com/example/tennisscorer/TennisScoreEngine.kt app/src/test/java/com/example/tennisscorer/ApplyPointTest.kt
git commit -m "feat(engine): extract applyPoint(), add event recording and match auto-save"
```

---

### Task 3: HistoryViewModel + MatchHistoryCard + HistoryScreen

**Files:**
- Create: `app/src/main/java/com/example/tennisscorer/ui/viewmodels/HistoryViewModel.kt`
- Create: `app/src/main/java/com/example/tennisscorer/ui/components/MatchHistoryCard.kt`
- Create: `app/src/main/java/com/example/tennisscorer/ui/screens/HistoryScreen.kt`

**Interfaces:**
- Consumes: `MatchRecord`, `MatchRepository` (Task 1)
- Produces:
  - `HistoryViewModel(repository: MatchRepository)` with `val matches: StateFlow<List<MatchRecord>>`, `fun deleteMatch(matchId: Long)`
  - `HistoryViewModel.Factory(repository: MatchRepository): ViewModelProvider.Factory`
  - `MatchHistoryCard(record: MatchRecord, onDelete: () -> Unit, modifier: Modifier = Modifier)`
  - `HistoryScreen(repository: MatchRepository, onMatchClick: (Long) -> Unit, onBack: () -> Unit)`

---

- [ ] **Step 1: Create HistoryViewModel**

Create `app/src/main/java/com/example/tennisscorer/ui/viewmodels/HistoryViewModel.kt`:
```kotlin
package com.example.tennisscorer.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.tennisscorer.data.MatchRecord
import com.example.tennisscorer.data.MatchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: MatchRepository) : ViewModel() {

    val matches: StateFlow<List<MatchRecord>> = repository.getAllMatches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun deleteMatch(matchId: Long) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { repository.deleteMatch(matchId) } }
    }

    companion object {
        fun Factory(repository: MatchRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HistoryViewModel(repository) }
        }
    }
}
```

- [ ] **Step 2: Create MatchHistoryCard**

Create `app/src/main/java/com/example/tennisscorer/ui/components/MatchHistoryCard.kt`:
```kotlin
package com.example.tennisscorer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tennisscorer.data.MatchRecord
import com.example.tennisscorer.ui.theme.CardBg
import com.example.tennisscorer.ui.theme.CyanAccent
import com.example.tennisscorer.ui.theme.Gold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MatchHistoryCard(
    record: MatchRecord,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
        .format(Date(record.dateTimestamp))

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${record.p1Name}  vs  ${record.p2Name}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Sets: ${record.p1FinalSets} – ${record.p2FinalSets}",
                    fontSize = 13.sp,
                    color = CyanAccent
                )
                Text(
                    text = "🏆 ${record.winnerName}",
                    fontSize = 13.sp,
                    color = Gold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = dateStr,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Hapus",
                    tint = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}
```

- [ ] **Step 3: Create HistoryScreen**

Create `app/src/main/java/com/example/tennisscorer/ui/screens/HistoryScreen.kt`:
```kotlin
package com.example.tennisscorer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tennisscorer.data.MatchRecord
import com.example.tennisscorer.data.MatchRepository
import com.example.tennisscorer.ui.components.MatchHistoryCard
import com.example.tennisscorer.ui.theme.AppBg
import com.example.tennisscorer.ui.theme.CyanAccent
import com.example.tennisscorer.ui.viewmodels.HistoryViewModel

@Composable
fun HistoryScreen(
    repository: MatchRepository,
    onMatchClick: (Long) -> Unit,
    onBack: () -> Unit
) {
    val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory(repository))
    val matches by vm.matches.collectAsState()
    var deleteTarget by remember { mutableStateOf<MatchRecord?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(AppBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Riwayat Pertandingan",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            if (matches.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎾", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Belum ada pertandingan",
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(matches, key = { it.matchId }) { record ->
                        MatchHistoryCard(
                            record = record,
                            onDelete = { deleteTarget = record },
                            modifier = Modifier.clickable { onMatchClick(record.matchId) }
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { record ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Hapus Pertandingan?", color = Color.White) },
            text = {
                Text(
                    "${record.p1Name} vs ${record.p2Name} akan dihapus permanen.",
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteMatch(record.matchId); deleteTarget = null }) {
                    Text("Hapus", color = CyanAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Batal", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
}
```

- [ ] **Step 4: Verify compilation**

```
./gradlew compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/example/tennisscorer/ui/viewmodels/HistoryViewModel.kt app/src/main/java/com/example/tennisscorer/ui/components/MatchHistoryCard.kt app/src/main/java/com/example/tennisscorer/ui/screens/HistoryScreen.kt
git commit -m "feat(history): add HistoryViewModel, MatchHistoryCard, and HistoryScreen"
```

---

### Task 4: ReplayViewModel + ReplayScreen

**Files:**
- Create: `app/src/main/java/com/example/tennisscorer/ui/viewmodels/ReplayViewModel.kt`
- Create: `app/src/main/java/com/example/tennisscorer/ui/screens/ReplayScreen.kt`

**Interfaces:**
- Consumes: `applyPoint()` (Task 2), `MatchRepository.getMatchById()` + `getEventsForMatch()` (Task 1)
- Produces:
  - `ReplayViewModel(repository, matchId)` with StateFlows for `replayState`, `isPlaying`, `speedMultiplier`, `isFinished`, `progress`, `matchRecord`
  - `ReplayViewModel.Factory(repository: MatchRepository, matchId: Long): ViewModelProvider.Factory`
  - `ReplayScreen(matchId: Long, repository: MatchRepository, onBack: () -> Unit)`

---

- [ ] **Step 1: Create ReplayViewModel**

Create `app/src/main/java/com/example/tennisscorer/ui/viewmodels/ReplayViewModel.kt`:
```kotlin
package com.example.tennisscorer.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.tennisscorer.TennisScoreState
import com.example.tennisscorer.applyPoint
import com.example.tennisscorer.data.MatchRecord
import com.example.tennisscorer.data.MatchRepository
import com.example.tennisscorer.data.PointEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReplayViewModel(
    private val repository: MatchRepository,
    private val matchId: Long
) : ViewModel() {

    private val _replayState = MutableStateFlow(TennisScoreState())
    val replayState: StateFlow<TennisScoreState> = _replayState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _speedMultiplier = MutableStateFlow(1.0f)
    val speedMultiplier: StateFlow<Float> = _speedMultiplier.asStateFlow()

    private val _isFinished = MutableStateFlow(false)
    val isFinished: StateFlow<Boolean> = _isFinished.asStateFlow()

    // Pair(currentPointIndex, totalPoints)
    private val _progress = MutableStateFlow(0 to 0)
    val progress: StateFlow<Pair<Int, Int>> = _progress.asStateFlow()

    private val _matchRecord = MutableStateFlow<MatchRecord?>(null)
    val matchRecord: StateFlow<MatchRecord?> = _matchRecord.asStateFlow()

    private val _loadError = MutableStateFlow(false)
    val loadError: StateFlow<Boolean> = _loadError.asStateFlow()

    private var events: List<PointEvent> = emptyList()
    private var currentIndex = 0
    private var playJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching {
                val record = repository.getMatchById(matchId)
                if (record == null) { _loadError.value = true; return@runCatching }
                _matchRecord.value = record
                events = repository.getEventsForMatch(matchId)
                if (events.isEmpty()) { _loadError.value = true; return@runCatching }
                _replayState.value = TennisScoreState(p1Name = record.p1Name, p2Name = record.p2Name)
                _progress.value = 0 to events.size
            }.onFailure { _loadError.value = true }
        }
    }

    fun togglePlayPause() {
        if (_isFinished.value) return
        if (_isPlaying.value) {
            playJob?.cancel()
            _isPlaying.value = false
        } else {
            _isPlaying.value = true
            playJob = viewModelScope.launch {
                while (currentIndex < events.size) {
                    val delayMs = (1000L / _speedMultiplier.value).toLong()
                    delay(delayMs)
                    val event = events[currentIndex]
                    _replayState.value = applyPoint(_replayState.value, event.playerNum)
                    currentIndex++
                    _progress.value = currentIndex to events.size
                }
                _isPlaying.value = false
                _isFinished.value = true
            }
        }
    }

    fun setSpeed(multiplier: Float) {
        _speedMultiplier.value = multiplier
    }

    companion object {
        fun Factory(repository: MatchRepository, matchId: Long): ViewModelProvider.Factory =
            viewModelFactory { initializer { ReplayViewModel(repository, matchId) } }
    }
}
```

- [ ] **Step 2: Create ReplayScreen**

Create `app/src/main/java/com/example/tennisscorer/ui/screens/ReplayScreen.kt`:
```kotlin
package com.example.tennisscorer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tennisscorer.data.MatchRepository
import com.example.tennisscorer.ui.components.ScoreBadge
import com.example.tennisscorer.ui.components.WinnerOverlay
import com.example.tennisscorer.ui.theme.ActionBtnBg
import com.example.tennisscorer.ui.theme.AppBg
import com.example.tennisscorer.ui.theme.CyanAccent
import com.example.tennisscorer.ui.theme.ScoreBlue
import com.example.tennisscorer.ui.theme.ScoreRed
import com.example.tennisscorer.ui.viewmodels.ReplayViewModel

@Composable
fun ReplayScreen(
    matchId: Long,
    repository: MatchRepository,
    onBack: () -> Unit
) {
    val vm: ReplayViewModel = viewModel(
        key = "replay_$matchId",
        factory = ReplayViewModel.Factory(repository, matchId)
    )

    val state by vm.replayState.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val speed by vm.speedMultiplier.collectAsState()
    val isFinished by vm.isFinished.collectAsState()
    val progress by vm.progress.collectAsState()
    val record by vm.matchRecord.collectAsState()
    val loadError by vm.loadError.collectAsState()

    if (loadError) {
        Box(modifier = Modifier.fillMaxSize().background(AppBg), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Data tidak tersedia", color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)) {
                    Text("Kembali")
                }
            }
        }
        return
    }

    val totalGames = state.p1Games + state.p2Games + state.p1Sets + state.p2Sets
    val isSwapped = (totalGames % 2 != 0)

    val leftName  = if (!isSwapped) state.p1Name        else state.p2Name
    val leftScore = if (!isSwapped) state.p1DisplayScore else state.p2DisplayScore
    val leftBg    = if (!isSwapped) ScoreRed             else ScoreBlue

    val rightName  = if (!isSwapped) state.p2Name        else state.p1Name
    val rightScore = if (!isSwapped) state.p2DisplayScore else state.p1DisplayScore
    val rightBg    = if (!isSwapped) ScoreBlue            else ScoreRed

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().background(leftBg),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(leftName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    AnimatedContent(
                        targetState = leftScore,
                        transitionSpec = {
                            (slideInVertically(tween(200)) { -it } + fadeIn(tween(200)))
                                .togetherWith(slideOutVertically(tween(200)) { it } + fadeOut(tween(200)))
                                .using(SizeTransform(clip = false))
                        },
                        label = "replayLeft"
                    ) { score ->
                        Text(score, fontSize = 100.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().background(rightBg),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(rightName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    AnimatedContent(
                        targetState = rightScore,
                        transitionSpec = {
                            (slideInVertically(tween(200)) { -it } + fadeIn(tween(200)))
                                .togetherWith(slideOutVertically(tween(200)) { it } + fadeOut(tween(200)))
                                .using(SizeTransform(clip = false))
                        },
                        label = "replayRight"
                    ) { score ->
                        Text(score, fontSize = 100.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
            }
        }

        ScoreBadge(
            p1Sets = state.p1Sets,
            p2Sets = state.p2Sets,
            p1Games = state.p1Games,
            p2Games = state.p2Games,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
        )

        // Top banner
        Text(
            text = "⏪ Replay — ${record?.p1Name ?: ""} vs ${record?.p2Name ?: ""}",
            fontSize = 11.sp,
            color = CyanAccent,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 4.dp)
        )

        // Bottom control bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ActionBtnBg)
            ) {
                Text("← Kembali", fontSize = 12.sp, color = Color.White)
            }

            Button(
                onClick = { vm.togglePlayPause() },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                enabled = !isFinished
            ) {
                Text(if (isPlaying) "⏸ Pause" else "▶ Play", fontSize = 12.sp, color = Color.White)
            }

            listOf(0.5f to "0.5×", 1.0f to "1×", 2.0f to "2×").forEach { (value, label) ->
                FilterChip(
                    selected = speed == value,
                    onClick = { vm.setSpeed(value) },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }

            if (progress.second > 0) {
                Text(
                    text = "Poin ${progress.first} / ${progress.second}",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        if (isFinished) {
            WinnerOverlay(
                winnerName = state.winnerName ?: record?.winnerName ?: "",
                onPlayAgain = onBack
            )
        }
    }
}
```

- [ ] **Step 3: Verify compilation**

```
./gradlew compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```
git add app/src/main/java/com/example/tennisscorer/ui/viewmodels/ReplayViewModel.kt app/src/main/java/com/example/tennisscorer/ui/screens/ReplayScreen.kt
git commit -m "feat(replay): add ReplayViewModel and ReplayScreen with playback controls"
```

---

### Task 5: Navigation + SplashScreen Button + MainActivity Wiring

**Files:**
- Modify: `app/src/main/java/com/example/tennisscorer/navigation/Screen.kt`
- Modify: `app/src/main/java/com/example/tennisscorer/navigation/AppNavigation.kt`
- Modify: `app/src/main/java/com/example/tennisscorer/ui/screens/SplashScreen.kt`
- Modify: `app/src/main/java/com/example/tennisscorer/MainActivity.kt`

**Interfaces:**
- Consumes: `HistoryScreen`, `ReplayScreen` (Tasks 3–4), `MatchRepository`, `TennisScoreEngine.Factory` (Tasks 1–2)
- Produces: fully wired app — all screens navigable, match history saved, replay accessible

---

- [ ] **Step 1: Add History and Replay to Screen.kt**

Replace the entire content of `Screen.kt`:
```kotlin
package com.example.tennisscorer.navigation

sealed class Screen(val route: String) {
    object Splash  : Screen("splash")
    object Input   : Screen("input")
    object Game    : Screen("game")
    object History : Screen("history")
    object Replay  : Screen("replay/{matchId}") {
        fun buildRoute(matchId: Long) = "replay/$matchId"
    }
}
```

- [ ] **Step 2: Update AppNavigation to add History + Replay routes**

Replace the entire content of `AppNavigation.kt`:
```kotlin
package com.example.tennisscorer.navigation

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.tennisscorer.TennisScoreEngine
import com.example.tennisscorer.data.MatchRepository
import com.example.tennisscorer.ui.screens.HistoryScreen
import com.example.tennisscorer.ui.screens.PlayerInputScreen
import com.example.tennisscorer.ui.screens.ReplayScreen
import com.example.tennisscorer.ui.screens.ScoreboardScreen
import com.example.tennisscorer.ui.screens.SplashScreen

@Composable
fun AppNavigation(engine: TennisScoreEngine, repository: MatchRepository) {
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        enterTransition    = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
        exitTransition     = { slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(300)) },
        popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(300)) },
        popExitTransition  = { slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300)) }
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onStart   = { navController.navigate(Screen.Input.route) },
                onHistory = { navController.navigate(Screen.History.route) }
            )
        }
        composable(Screen.Input.route) {
            PlayerInputScreen(
                engine       = engine,
                onBack       = { navController.popBackStack() },
                onStartMatch = { navController.navigate(Screen.Game.route) }
            )
        }
        composable(Screen.Game.route) {
            ScoreboardScreen(
                engine      = engine,
                onBackToInput = { navController.popBackStack() }
            )
        }
        composable(Screen.History.route) {
            HistoryScreen(
                repository   = repository,
                onMatchClick = { matchId -> navController.navigate(Screen.Replay.buildRoute(matchId)) },
                onBack       = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.Replay.route,
            arguments = listOf(navArgument("matchId") { type = NavType.LongType })
        ) { backStackEntry ->
            val matchId = backStackEntry.arguments?.getLong("matchId") ?: return@composable
            ReplayScreen(
                matchId    = matchId,
                repository = repository,
                onBack     = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun LockScreenOrientation(orientation: Int) {
    val context = LocalContext.current
    DisposableEffect(orientation) {
        val activity = context as? ComponentActivity
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = orientation
        onDispose {
            if (originalOrientation != null) {
                activity.requestedOrientation = originalOrientation
            }
        }
    }
}
```

- [ ] **Step 3: Add onHistory parameter to SplashScreen**

In `SplashScreen.kt`, change the function signature from:
```kotlin
fun SplashScreen(onStart: () -> Unit) {
```
to:
```kotlin
fun SplashScreen(onStart: () -> Unit, onHistory: () -> Unit) {
```

Then, after the existing `Button` (the "Start" button), add a `Spacer` and the new `OutlinedButton` inside the same `Column`:
```kotlin
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onHistory,
                modifier = Modifier
                    .width(140.dp)
                    .height(40.dp)
                    .scale(btnScale.value)
                    .graphicsLayer(alpha = btnAlpha.value),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent)
            ) {
                Text(text = "Riwayat", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CyanAccent)
            }
```

Place this code immediately after the closing `}` of the `Button` block (before the `Column`'s closing `}`).

- [ ] **Step 4: Update MainActivity to wire repository and factory**

Replace the entire content of `MainActivity.kt`:
```kotlin
package com.example.tennisscorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.tennisscorer.data.MatchRepository
import com.example.tennisscorer.data.TennisScorerDatabase
import com.example.tennisscorer.navigation.AppNavigation
import com.example.tennisscorer.ui.theme.TennisScorerTheme

class MainActivity : ComponentActivity() {
    private val db by lazy { TennisScorerDatabase.getInstance(applicationContext) }
    private val repository by lazy { MatchRepository(db.matchDao()) }
    private val engine: TennisScoreEngine by viewModels { TennisScoreEngine.Factory(repository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            TennisScorerTheme {
                AppNavigation(engine = engine, repository = repository)
            }
        }
    }
}
```

- [ ] **Step 5: Build and verify no compile errors**

```
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL, `app-debug.apk` generated.

- [ ] **Step 6: Manual smoke test on device/emulator**

1. Launch app — SplashScreen shows "Start" + "Riwayat" buttons.
2. Tap "Riwayat" — HistoryScreen opens, shows "Belum ada pertandingan".
3. Go back, tap "Start", set player names, play a full match to winner.
4. Tap "Riwayat" again — the finished match appears as a card.
5. Tap the card — ReplayScreen opens, tap ▶ Play, watch score animate point by point.
6. Change speed to 2× — replay speeds up.
7. After last point, WinnerOverlay appears.
8. Tap delete icon on history card, confirm dialog, card disappears.

- [ ] **Step 7: Commit**

```
git add app/src/main/java/com/example/tennisscorer/navigation/Screen.kt app/src/main/java/com/example/tennisscorer/navigation/AppNavigation.kt app/src/main/java/com/example/tennisscorer/ui/screens/SplashScreen.kt app/src/main/java/com/example/tennisscorer/MainActivity.kt
git commit -m "feat(nav): wire History and Replay routes, add Riwayat button to SplashScreen"
```

- [ ] **Step 8: Push to GitHub**

```
git push origin master
```
Expected: GitHub Actions picks up the push and builds `tennisscorer.apk`.
