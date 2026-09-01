# Match History & Replay Design

## Goal

Save every completed match to local storage and allow users to replay it point-by-point with animated playback, controls, and final winner reveal.

## Architecture

Event sourcing: every point tap during a live match is recorded as a `PointEvent`. On match completion, the full event log plus a `MatchRecord` summary are persisted to a Room (SQLite) database. The replay screen rehydrates the engine state by replaying events sequentially with a configurable delay.

## Tech Stack

- **Room** (AndroidX) — local SQLite ORM, two tables
- **Kotlin Coroutines + StateFlow** — replay playback loop and reactive UI
- **Jetpack Compose** — HistoryScreen and ReplayScreen
- **Navigation Compose** — two new routes added to existing AppNavigation

## Global Constraints

- Target SDK 35, min SDK 26 (matches existing app)
- No network dependency — fully offline
- All colors from `ui/theme/Color.kt` design tokens — no inline `Color(0x...)` hex literals
- Compose animations: transition durations ≤ 600ms (ambient pulse exempted per existing pattern)
- Room version: 2.6.1
- No duplicate scoring logic — extract pure `applyPoint(state, playerNum): TennisScoreState` function reused by both live engine and replay engine

---

## Data Layer

### Entities

**`MatchRecord`** (`match_records` table)
```
matchId: Long (PK, autoGenerate)
p1Name: String
p2Name: String
winnerName: String
p1FinalSets: Int
p2FinalSets: Int
dateTimestamp: Long   // epoch millis, set at match save time
```

**`PointEvent`** (`point_events` table)
```
eventId: Long (PK, autoGenerate)
matchId: Long (FK → MatchRecord.matchId)
pointIndex: Int       // 0-based order of points in the match
playerNum: Int        // 1 or 2
```

### DAO — `MatchDao`

```kotlin
@Insert suspend fun insertMatch(match: MatchRecord): Long
@Insert suspend fun insertEvents(events: List<PointEvent>)
@Query("SELECT * FROM match_records ORDER BY dateTimestamp DESC")
fun getAllMatches(): Flow<List<MatchRecord>>
@Query("SELECT * FROM point_events WHERE matchId = :matchId ORDER BY pointIndex ASC")
suspend fun getEventsForMatch(matchId: Long): List<PointEvent>
@Query("DELETE FROM match_records WHERE matchId = :matchId")
suspend fun deleteMatch(matchId: Long)
@Query("DELETE FROM point_events WHERE matchId = :matchId")
suspend fun deleteEventsForMatch(matchId: Long)
```

### Database — `TennisScorerDatabase`

`RoomDatabase` with `MatchRecord` and `PointEvent` entities. Singleton exposed via `TennisScorerDatabase.getInstance(context)`.

### Repository — `MatchRepository`

Wraps `MatchDao`. Provides:
- `suspend fun saveMatch(record: MatchRecord, events: List<PointEvent>)` — inserts both in one call
- `fun getAllMatches(): Flow<List<MatchRecord>>`
- `suspend fun getEventsForMatch(matchId: Long): List<PointEvent>`
- `suspend fun deleteMatch(matchId: Long)` — deletes record + events

---

## Scoring Logic Extraction

Extract pure function from `TennisScoreEngine.pointWonBy()` to a top-level function in `TennisScoreState.kt`:

```kotlin
fun applyPoint(state: TennisScoreState, playerNum: Int): TennisScoreState
```

No side effects, no ViewModel dependency. Takes current state, returns next state. Both `TennisScoreEngine` and `ReplayViewModel` call this function.

---

## Live Match Recording

`TennisScoreEngine` gains:
- `private val pendingEvents = mutableListOf<PointEvent>()` — in-memory log cleared on `resetMatch()`
- In `pointWonBy()`: append `PointEvent(matchId=0, pointIndex=pendingEvents.size, playerNum=playerNum)` before applying state
- After state update, if `isMatchFinished == true`: call `viewModelScope.launch { repository.saveMatch(...) }` with final state + pendingEvents (matchId backfilled from Room insert return value)
- `resetMatch()` clears `pendingEvents`

`TennisScoreEngine` receives `MatchRepository` via constructor injection using `ViewModelProvider.Factory`.

---

## ViewModels

### `HistoryViewModel`

- Receives `MatchRepository`
- `val matches: StateFlow<List<MatchRecord>>` — collects `repository.getAllMatches()`
- `suspend fun deleteMatch(matchId: Long)` — delegates to repository

### `ReplayViewModel`

- Receives `MatchRepository` + `matchId: Long` (via `SavedStateHandle`)
- On init: load `MatchRecord` and `List<PointEvent>` from Room
- `val replayState: StateFlow<TennisScoreState>` — current replayed score
- `val isPlaying: StateFlow<Boolean>`
- `val speedMultiplier: StateFlow<Float>` — 0.5f / 1.0f / 2.0f
- `val isFinished: StateFlow<Boolean>`
- `fun togglePlayPause()`
- `fun setSpeed(multiplier: Float)`
- Playback coroutine: steps through events, calls `applyPoint()` on each, delays by `(1000L / speedMultiplier).toLong()` ms between points

---

## New Screens

### `HistoryScreen`

- Accessed via "Riwayat" button added to `SplashScreen` (bottom of screen, below Start button)
- `LazyColumn` of `MatchHistoryCard` components
- Each card: `P1Name vs P2Name`, sets score (e.g. "2 – 1"), winner badge, formatted date
- Swipe-to-delete or long-press delete with confirmation dialog
- Empty state: centered text "Belum ada pertandingan" with 🎾 icon
- `HistoryViewModel` provided via `viewModel(factory = ...)`

### `ReplayScreen`

- UI identical to `ScoreboardScreen` layout but in read-only mode (no tap handlers on panels)
- Overlay banner at top: "⏪ Replay — [P1] vs [P2]"
- Bottom control bar:
  - ▶/⏸ Play/Pause button
  - Speed chips: `0.5×` `1×` `2×`
  - Progress indicator: "Poin 12 / 47"
- When `isFinished == true`: show `WinnerOverlay` (existing component, reused as-is)
- `ReplayViewModel` provided via `viewModel(factory = ReplayViewModel.Factory(matchId))`

---

## Navigation

Two new routes in `Screen.kt`:

```kotlin
object History : Screen("history")
data class Replay(val matchId: Long) : Screen("replay/{matchId}") {
    fun route() = "replay/$matchId"
}
```

Added to `NavHost` in `AppNavigation.kt` with existing slide transitions.

Entry point: "Riwayat" `OutlinedButton` added below "Mulai" button in `SplashScreen.kt`.

---

## File Map

| Action | File |
|--------|------|
| Create | `data/MatchRecord.kt` |
| Create | `data/PointEvent.kt` |
| Create | `data/MatchDao.kt` |
| Create | `data/TennisScorerDatabase.kt` |
| Create | `data/MatchRepository.kt` |
| Modify | `TennisScoreState.kt` — add `applyPoint()` |
| Modify | `TennisScoreEngine.kt` — add event recording + save on finish |
| Create | `ui/screens/HistoryScreen.kt` |
| Create | `ui/screens/ReplayScreen.kt` |
| Create | `ui/components/MatchHistoryCard.kt` |
| Create | `ui/viewmodels/HistoryViewModel.kt` |
| Create | `ui/viewmodels/ReplayViewModel.kt` |
| Modify | `navigation/Screen.kt` — add History + Replay routes |
| Modify | `navigation/AppNavigation.kt` — add new composable destinations |
| Modify | `ui/screens/SplashScreen.kt` — add "Riwayat" button |
| Modify | `MainActivity.kt` — provide MatchRepository to ViewModels |
| Modify | `app/build.gradle` — add Room dependencies |

---

## Error Handling

- Room operations run in `viewModelScope` with `Dispatchers.IO`; exceptions caught and logged, UI not crashed
- If `getEventsForMatch()` returns empty list for a valid matchId, `ReplayScreen` shows "Data tidak tersedia" and back button
- `MatchRecord` without corresponding events (edge case from interrupted save) handled gracefully in ReplayViewModel init
