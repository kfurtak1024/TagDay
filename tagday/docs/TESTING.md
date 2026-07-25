# TagDay — Testing

What's actually tested, how, and — just as importantly — what's deliberately not,
based on the pattern that emerged across M1–M4 rather than a plan written up front.

## What gets a unit test

- **Repository aggregation/mapping logic** (`TagInstanceRepositoryImpl`,
  `TagRepositoryImpl`): the real logic worth locking down — grouping instances by tag,
  summarizing per type, per-day/per-range bucketing, `.copy()`-based partial updates.
- **Pure, non-Compose utility logic**: date-range/stepping math (`CalendarDateRanges`),
  text parsing (`ParsedTagInput`). Anything with real boundary conditions (month/year
  edges, leap years, empty/edge-case input) that's cheap to verify directly.

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
dependency. Only plain JUnit4 is on the test classpath: no MockK, no Mockito, no
Robolectric. `kotlinx.coroutines.flow.first()` + `runBlocking` work for suspend/`Flow`
assertions because Android Gradle Plugin exposes the app's `implementation`
dependencies (which already pull in `kotlinx-coroutines-core`) to the local unit-test
compile classpath — no extra test dependency needed for that.

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

- **ViewModels** (`CalendarViewModel`, `TagsViewModel`): thin coordinators — wire a
  Repository `Flow` into a `StateFlow`, launch a `viewModelScope` suspend call. No
  branching logic of their own worth isolating from the Repository logic it delegates
  to. Testing them properly would need `kotlinx-coroutines-test` (`Dispatchers.setMain`
  for `viewModelScope`), which isn't part of this project's test setup — not worth
  adding for coordinators this thin. If a ViewModel ever grows real logic of its own
  (see ADR-002's domain-layer trigger condition), that's the point to reconsider.
- **Composables**: no Compose UI testing infrastructure is exercised (the
  `androidx.compose.ui.test.junit4`/`debugImplementation(...ui.test.manifest)`
  dependencies exist from the project template but back `app/src/androidTest`'s
  boilerplate `ExampleInstrumentedTest.kt`, not real tests) — instrumented tests need a
  connected device/emulator, unavailable in this project's usual working environment.
  `@Preview` composables (one "populated" + one "empty" per screen, following
  `CONVENTIONS.md`) are the practical stand-in for visual verification.
- **Anything touching real Android framework APIs directly** (e.g. `ColorPickerDialog`'s
  `android.graphics.Color.colorToHSV`/`HSVToColor`): local JVM unit tests can't call real
  `android.*` classes without Robolectric, which isn't configured. Correctness there
  relies on reading the platform docs/source carefully and Preview-level inspection, not
  a unit test — see ADR-011.
- **Gesture code** (`CalendarContent`'s drag detection): same reasoning as Composables —
  feel/timing/threshold correctness can only really be judged on a device.

`app/src/test/.../ExampleUnitTest.kt` and `app/src/androidTest/.../ExampleInstrumentedTest.kt`
are unmodified Android Studio template boilerplate — harmless, but they don't contribute
real coverage and shouldn't be mistaken for it.
