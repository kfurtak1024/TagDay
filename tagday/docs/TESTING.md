# TagDay — Testing

What's actually tested, how, and — just as importantly — what's deliberately not,
based on the pattern that emerged across M1–M4 rather than a plan written up front.

## What gets a unit test

- **DAO SQL** (`TagDaoTest`, `TagInstanceDaoTest`): the queries themselves, against a real
  in-memory SQLite via Robolectric (ADR-040). Everything else fakes the DAO, so until these
  existed the SQL was only ever a compiler-checked string — `COLLATE NOCASE`, the `ESCAPE`
  clause, `OnConflictStrategy.IGNORE` (ADR-037), `COUNT(DISTINCT date)` (F9) and the cascading
  delete were all unverified.
- **Repository aggregation/mapping logic** (`TagInstanceRepositoryImpl`,
  `TagRepositoryImpl`): the real logic worth locking down — grouping instances by tag,
  summarizing per type, per-day/per-range bucketing, `.copy()`-based partial updates,
  display-order preservation (ADR-023), LIKE-wildcard escaping.
- **ViewModel state and side effects** (`CalendarViewModel`, `TagsViewModel`): the
  delay-delete/undo state machine (ADR-019), the fresh-Valued-tag single-shot signal
  (ADR-021), reorder write-through (ADR-022), zoom clamping and per-zoom time stepping,
  and the rename duplicate-name guard. See § ViewModel tests — this reverses an earlier
  "deliberately not tested" call, per ADR-024.
- **Composable semantics and callback routing** (`WeekContentTest`, `DayContentTest`,
  `TagQuickEntryBarTest`, `InstanceListSheetTest`, `MonthContentTest`, `YearContentTest`,
  `TagsContentTest`): what a screen reader is told, which callback a control fires and with
  *which* argument. The recurring bug class here is a control that works but reports the wrong
  thing — a row's action closing over the wrong tag, a heatmap cell whose count only ever
  existed as background alpha (F14), a star row that read out "1 stars, 2 stars…" and never the
  actual rating (F16). See § Compose tests for the mechanics and the sharp edges.
- **Pure, non-Compose utility logic**: date-range/stepping math (`CalendarDateRanges`),
  text parsing (`ParsedTagInput`), heatmap shading buckets (`alphaForCount`), period
  labels (`CalendarPeriodLabels` — the week case straddles months and years, ADR-033),
  quick-entry dispatch for an existing tag (`QuickEntryAction` — extracted from the Composable
  specifically so ADR-034's table could be tested here rather than not at all).
  Anything with real boundary conditions (month/year edges, leap years, empty/edge-case
  input) that's cheap to verify directly.

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
- **Turbine** (`app.cash.turbine`) for assertions about the emissions a flow makes *along the
  way*, rather than where it settled. `CalendarViewModelTest.everyEmission_pairsTheDataWithItsOwnQuery`
  is the case that needs it: the bug it guards (BACKLOG F5) was only ever visible in an
  intermediate emission — `.value` was always correct afterwards. It replaced a hand-rolled
  `collectInto` helper that did a quarter of the same job.

  Turbine is deliberately **not** used everywhere. Most tests here assert on a `StateFlow`'s
  settled `.value` after calling a method, which is the clearer shape for that question;
  rewriting them into `awaitItem()` sequences would add conflation and ordering concerns
  without answering anything better.
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

- **Most composables**, still — but this is no longer an environment limit, and the claim
  that used to sit here ("instrumented tests need a connected device/emulator, unavailable in
  this project's usual working environment") was **wrong**, see § Instrumented tests below.
  `@Preview` composables (one "populated" + one "empty" per screen, following
  `CONVENTIONS.md`) remain the stand-in for anything not yet covered. The `Screen`/`Content`
  split (ADR-005) exists precisely so the content composables can be driven by a UI test.
- **The drag gesture itself** in `ReorderableInstanceList` (swap thresholds, local-vs-persisted
  order, edge auto-scroll — ADR-022): it depends on real pointer arbitration between
  `draggable` and the list's own scroll, which took five attempts to get right on hardware and
  isn't something a JVM simulation should be trusted to judge. What *is* covered is the same
  list's discrete move-up/move-down accessibility actions, which run the same `move`/`commit`
  path the drop does — so the ordering bookkeeping and the `sortOrder` renumbering are tested
  even though the gesture that usually drives them isn't (`InstanceListSheetTest`).
- **Anything inside an `AlertDialog`** — the rename dialog, the color picker, the delete
  confirmation, the period row's date picker. Not a judgement call: they can't run here at all,
  see § `AlertDialog` doesn't work below. `TagsContentTest` drives `TagsContent` in its
  `pendingDelete == null` state only for this reason. `ColorPickerDialog`'s
  `android.graphics.Color.colorToHSV`/`HSVToColor` would be reachable under Robolectric — the
  old claim here that `android.*` needs a device was wrong even before Robolectric was
  configured — but the dialog wrapping them is not, so ADR-011 stays Preview-verified.
- **Gesture code** (`CalendarContent`'s drag detection): same reasoning as Composables —
  feel/timing/threshold correctness can only really be judged on a device.

## Compose tests (Robolectric, on the JVM)

Compose tests live in `app/src/test/` and run under Robolectric as part of
`testDebugUnitTest` — no device, no emulator, no separate Gradle task (ADR-040). Two bits of
setup make it work: `testOptions.unitTests.isIncludeAndroidResources = true`, without which a
Compose test fails at startup rather than at its assertion, and `@RunWith(RobolectricTestRunner::class)`
on the class.

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")   // pin a realistic phone; see below
class WeekContentTest {
    @get:Rule val compose = createComposeRule()   // the v2 factory
}
```

**Pin the screen size.** Robolectric's default screen is much shorter than a real phone, and
layout-sensitive assertions silently change meaning with it — moving these tests off the
emulator turned one red because `WeekContent`'s seven non-scrolling rows no longer fit
(BACKLOG F25, a genuine defect the emulator's tall screen had hidden).

**Prefer `assertExists` over `assertIsDisplayed`** when the question is "is this labelled
correctly", and keep `assertIsDisplayed` for when visibility is genuinely the thing under test.
Conflating them is what makes a semantics test quietly depend on screen height.

**Verify every test can fail — UI tests especially, but SQL tests too.** A semantics assertion passes for the wrong reasons very
easily: `onNodeWithContentDescription` searches the *merged* tree, and `clickable` merges
descendants by itself, so a row can look labelled when nothing labelled it. Delete the modifier
under test and confirm the test goes red before trusting it. Two real catches from doing this:
once the mutation silently didn't apply at all (the target line ended in `},` not `}`) and three
tests "passed" against unchanged code; and once a DAO test asserting "searching `%` finds
nothing" passed with the `ESCAPE` clause *deleted*, because a mangled pattern also finds
nothing. An assertion has to distinguish the fix from its absence, not merely hold while the fix
is present.

**Robolectric has no real font metrics**, and this bites harder than it sounds. Text measures
degenerately — a "walk" capsule's label comes out ~5px wide against its 32px ✕ — so *any*
coordinate-based assertion is testing a fictional layout. Concretely, `performClick()` on a
capsule row lands on the remove button, because the ✕ occupies most of the row at those
measurements. Where a test must hit a specific region, use `performTouchInput { click(offset) }`
with an offset that stays correct regardless of text width (a leading edge, say), and don't
write assertions that depend on where text ends.

**Geometry, unlike text, is real.** Anything laid out by weights, aspect ratios or fixed dp
measures correctly — so a bounds assertion is fair game as long as no text width feeds into it.
`MonthContentTest.theWeekdayHeaderSitsOverTheColumnItLabels` uses this: it asserts the "W"
header's centre falls inside 1 July's cell, which is the one way to catch a weekday header
that disagrees with the grid under it. Only *text* metrics are fictional here.

**`AlertDialog` doesn't work — and fails destructively.** A Compose test that composes an
`AlertDialog` (or anything wrapping one) does not fail with an assertion: it spins, pins a CPU
core, and the Gradle test JVM eventually dies with `Process 'Gradle Test Executor N' finished
with non-zero exit value 1` after ~75s, reporting the test as *skipped*. Both compose-rule
factories behave the same way, and it happens at `setContent` — before any assertion. This
rules out `RenameTagDialog`, `ColorPickerDialog`, `TagsContent`'s delete confirmation and
`PeriodNavigationRow`'s date picker, and it's why the ViewModel-side coverage of those flows
(`TagsViewModelTest.renameTag_*`, `requestDelete`/`confirmDelete`) matters more than usual.
`ModalBottomSheet` is fine — it renders in the same window rather than its own — which is why
the instance sheet *is* covered. If you try a dialog anyway, kill the test JVM rather than
waiting it out.

The other direct casualty is **ADR-026/027**, whose entire subject is touch-target geometry: the ✕'s
48dp minimum-target inflation reaching back over the tag name so that tapping the name deleted
the tag. That cannot be verified here and stays a device check.

**What still needs a real device**, and stays in `UI_UX.md`'s manual list: rendering and
contrast, capsule touch-target geometry (ADR-026/027), gesture feel and timing, the drag-reorder
arbitration (ADR-022), and actual TalkBack.
`app/src/androidTest/` no longer exists; ADR-039 records how to run an emulator here if it's
ever wanted again, and the `androidTestImplementation` dependencies are kept for that.

## Coverage

No automated measurement: Kover 0.9.1 produces an empty report against AGP 9.3.1 and registers
no variant tasks, which looks like a straight incompatibility. Worth retrying when Kover
supports AGP 9. Until then, coverage is judged structurally — see § What gets a unit test above and
`BACKLOG.md` F20 for the known gaps. 197 tests across 21 classes as of 2026-08-08.

Every zoom level now has a test: Day (`DayContentTest`), Week (`WeekContentTest`), Month and
Year (`MonthContentTest`, `YearContentTest`), plus the ViewModel's per-zoom query ranges. So
does every screen except Settings, which is an empty placeholder. What remains untested is
what can't run here rather than what hasn't been got to: the device list above, and everything
behind an `AlertDialog`.

## Running them

`./gradlew testDebugUnitTest` (what CI runs, alongside `assembleDebug` — see
`BUILD_RELEASE.md`). HTML report at `app/build/reports/tests/testDebugUnitTest/index.html`.

The Android Studio template's `ExampleUnitTest`/`ExampleInstrumentedTest` boilerplate has
been deleted — it asserted `2 + 2 == 4` and the app's package name, contributing nothing
but a misleading test count.
