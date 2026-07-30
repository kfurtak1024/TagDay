# TagDay — Testing

What's actually tested, how, and — just as importantly — what's deliberately not,
based on the pattern that emerged across M1–M4 rather than a plan written up front.

## What gets a unit test

- **Repository aggregation/mapping logic** (`TagInstanceRepositoryImpl`,
  `TagRepositoryImpl`): the real logic worth locking down — grouping instances by tag,
  summarizing per type, per-day/per-range bucketing, `.copy()`-based partial updates,
  display-order preservation (ADR-023), LIKE-wildcard escaping.
- **ViewModel state and side effects** (`CalendarViewModel`, `TagsViewModel`): the
  delay-delete/undo state machine (ADR-019), the fresh-Valued-tag single-shot signal
  (ADR-021), reorder write-through (ADR-022), zoom clamping and per-zoom time stepping,
  and the rename duplicate-name guard. See § ViewModel tests — this reverses an earlier
  "deliberately not tested" call, per ADR-024.
- **Pure, non-Compose utility logic**: date-range/stepping math (`CalendarDateRanges`),
  text parsing (`ParsedTagInput`), heatmap shading buckets (`alphaForCount`). Anything
  with real boundary conditions (month/year edges, leap years, empty/edge-case input)
  that's cheap to verify directly.

As a rule of thumb: if a bug in it would be silent and hard to notice by eye (off-by-one
in a date range, a grouping key computed wrong), it's worth a test; if it's a thin
pass-through with no branching, it usually isn't (see below).

## The fake-DAO pattern

Every Repository test fakes the **DAO** it wraps, not the Repository itself — an
anonymous `object : XyzDao` implementing only the methods the test actually exercises,
with everything else throwing `NotImplementedError()`:

```kotlin
val dao = object : TagInstanceDao {
    override fun observeForDay(date: Int): Flow<List<TagInstanceWithTag>> = flowOf(rows)
    override fun observeForRange(start: Int, end: Int) = throw NotImplementedError()
    // ...
}
val repository = TagInstanceRepositoryImpl(dao)
```

This exercises the real `XyzRepositoryImpl` — its actual grouping/aggregation code —
against a controlled, in-memory data source, with no Room or Android framework
dependency. The test classpath is deliberately thin: JUnit4 plus
`kotlinx-coroutines-test` (for the ViewModel tests below), and nothing else — no MockK,
no Mockito, no Robolectric, no assertion DSL. `kotlinx.coroutines.flow.first()` +
`runBlocking` work for suspend/`Flow` assertions without any test dependency at all,
because Android Gradle Plugin exposes the app's `implementation` dependencies (which
already pull in `kotlinx-coroutines-core`) to the local unit-test compile classpath.

Where a test needs to assert on what was *written* rather than what was read, the fake
records instead of throwing — `TagRepositoryImplTest.FakeTagDao.lastUpdated`,
`TagInstanceRepositoryImplTest.RecordingDao.inserted`.

## ViewModel tests

Same "fake the collaborator, don't mock it" idea, one layer up: a ViewModel's
collaborators are the repository *interfaces*, so `FakeTagRepository` /
`FakeTagInstanceRepository` (`app/src/test/.../data/repository/`) implement those,
holding their data in a `MutableStateFlow` so writes show up in later emissions the way
Room's `Flow`s would. Two small pieces of scaffolding make this work off-device
(`MainDispatcherRule.kt`):

- **`MainDispatcherRule`** swaps `Dispatchers.Main` for an `UnconfinedTestDispatcher`,
  which `viewModelScope` needs to exist at all in a JVM test. Unconfined (rather than the
  `StandardTestDispatcher` default) means a `viewModelScope.launch` against in-memory
  fakes runs eagerly, so tests can assert immediately after calling a method instead of
  sprinkling `advanceUntilIdle()` everywhere.
- **`TestScope.keepSubscribed(flow)`** holds a subscription open on `backgroundScope` for
  the rest of the test. Every `uiState` here is `SharingStarted.WhileSubscribed`, so with
  no collector the upstream never runs and `.value` stays on the initial state — every
  assertion would silently read the default. It launches on an explicit
  `UnconfinedTestDispatcher(testScheduler)` for the same eagerness reason.

Tests that only assert on repository writes (`addValue`, `createTagAndAdd`) don't need
`keepSubscribed`; anything reading `uiState` does.

## Naming

Descriptive lowerCamelCase, chunked with underscores — not backtick-named
(`` fun `adds tag to day`() ``) and not strict given/when/then. The established shape is
roughly `subjectOrMethod_scenario_outcome`:

```kotlin
fun simple_singleInstance_hasNoCount()
fun rated_allUnrated_fallsBackToSimpleStyleWithoutCrashing()
fun observeRangeGroups_groupsPerDayWithinRange()
fun weekRange_startsMonday_endsSunday()
fun step_month_acrossYearBoundary()
```

## What's deliberately not tested, and why

- **Composables**: no Compose UI testing infrastructure is exercised. The
  `androidx.compose.ui.test.junit4`/`debugImplementation(...ui.test.manifest)`
  dependencies are kept for whenever that changes, but `app/src/androidTest/` is empty —
  instrumented tests need a connected device/emulator, unavailable in this project's
  usual working environment. `@Preview` composables (one "populated" + one "empty" per
  screen, following `CONVENTIONS.md`) are the practical stand-in for visual verification.
  This is the largest remaining coverage gap, and it's an environment limit rather than a
  judgment about value: the `Screen`/`Content` split (ADR-005) exists precisely so the
  content composables *could* be driven by a UI test.
- **Composable-internal state logic**, most notably `ValuedInstanceList`'s drag-reorder
  bookkeeping (swap thresholds, local-vs-persisted order, edge auto-scroll — ADR-022).
  It's real logic with real edge cases, but it's expressed in `remember`ed state inside a
  composable, so testing it needs the Compose test infrastructure above. Extracting it
  into a plain testable state holder is the obvious move if it grows any further.
- **Anything touching real Android framework APIs directly** (e.g. `ColorPickerDialog`'s
  `android.graphics.Color.colorToHSV`/`HSVToColor`): local JVM unit tests can't call real
  `android.*` classes without Robolectric, which isn't configured. Correctness there
  relies on reading the platform docs/source carefully and Preview-level inspection, not
  a unit test — see ADR-011.
- **Gesture code** (`CalendarContent`'s drag detection): same reasoning as Composables —
  feel/timing/threshold correctness can only really be judged on a device.

## Running them

`./gradlew testDebugUnitTest` (what CI runs, alongside `assembleDebug` — see
`BUILD_RELEASE.md`). HTML report at `app/build/reports/tests/testDebugUnitTest/index.html`.

The Android Studio template's `ExampleUnitTest`/`ExampleInstrumentedTest` boilerplate has
been deleted — it asserted `2 + 2 == 4` and the app's package name, contributing nothing
but a misleading test count.
