# TagDay — Findings backlog

Output of a full read-through of the codebase on **2026-08-03**, against the tree at
commit `9f6c9c8` plus the ADR-032/ADR-033 working changes. Everything here is a gap in the
app as built — not a feature wishlist. Feature scope lives in `FEATURES.md`, the build plan
in `MILESTONES.md`, and the reasoning behind existing choices in `DECISIONS.md`.

**How to work this file.** Findings are ordered by importance and numbered stably — F1 stays
F1 even after it's fixed, so commits and ADRs can cite it. Tick the box when done and add the
commit or ADR that closed it. When a fix turns out to need a real decision (not just code),
write the ADR and link it here rather than burying the reasoning in this file. Don't
renumber; append new findings at the end of their tier with the next free number.

Two findings were verified beyond reading the source:
- **F5** was reproduced with a throwaway unit test (output inline below).
- **F3**'s `grep` (no `try`/`catch`/`runCatching` anywhere in `app/src/main/java`) and the
  lint warnings behind F21, F16 and F9 were run, not assumed.

Nothing here has been verified on a device — same standing caveat as `UI_UX.md`'s
manual-check list, which covers behaviour this audit could only read.

A finding that turns out to be **wrong** gets corrected in place with a ⚠️ note rather than
deleted, so the correction is visible to anyone who read the original (F15 is one). A finding
closed by *deciding not to act* is ticked with the reasoning inline (F8), so it doesn't get
re-raised as though it had been missed.

---

## Tier 1 — data loss & crashes

- [x] **F1 — `exportSchema = false` is what makes the destructive-migration risk hard to fix.**
  *Done: `exportSchema = true` via the Room Gradle plugin, `app/schemas/…/3.json` committed.
  No migration written and none needed yet — see `DATA_MODEL.md` § Schema export for what
  the recording does and doesn't commit to.*
  `TagDayDatabase.kt:10-11` is at `version = 3` with `exportSchema = false`, and
  `DatabaseModule.kt:22` uses `fallbackToDestructiveMigration(dropAllTables = true)`.
  `CLAUDE.md` already flags the wipe; what it doesn't say is that there's **no exported schema
  JSON for v3**, and writing or testing a real `Migration` needs the old schema
  (`MigrationTestHelper` requires exported ones). Every version bump makes the eventual fix
  more expensive. Turning `exportSchema` on and committing the v3 schema costs one line and is
  a prerequisite for the migration work, not part of it. Do this before touching the schema
  again. See `DATA_MODEL.md` § Schema history.

- [ ] **F2 — Android's own auto-backup is probably already copying the database into the
  destructive path.** The manifest's `<application>` sets `allowBackup="true"` while
  `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml` are both still the
  **untouched project template**, all content commented out. So cloud backup and
  device-to-device transfer include `tagday.db`, and a restored older-schema file meets
  `fallbackToDestructiveMigration` on first open. ADR-032 lists this as open and frames it as a
  privacy call (does a diary belong in the OS backup stream?); that framing stands, but the
  data-loss half closes with a single `<exclude>` and shouldn't wait on the privacy half.

- [ ] **F3 — No error handling on any database write, anywhere.** ⚠️ *Partially fixed —
  ADR-037 point 1. The one **reachable** crash (a double-tapped "+" violating the unique index
  on `tags.name`) is closed: the insert uses `IGNORE` and resolves to the existing tag.
  **The general half is still open** — every repository call is still a bare
  `viewModelScope.launch { … }` with no `catch`, and `CalendarUiState` still has no error
  field, so a genuine failure (disk full, corrupt database) still crashes. Left open because it
  needs a UX decision about what the user should see.*

  Original finding: `grep` for
  `try {`/`catch`/`runCatching` across `app/src/main/java` returns nothing, and
  `CalendarUiState` has no error field. Every mutation is a bare
  `viewModelScope.launch { repository… }`, so anything thrown crashes the app. Concrete
  reachable path: `tags.name` carries a unique index (`Tag.kt:10`), `createTag` uses the
  default `OnConflictStrategy.ABORT`, and quick-entry's duplicate guard
  (`TagQuickEntryBar.kt:61`) only compares against `allTags` as of the last flow emission — a
  fast double-press of "+" inserts the same name twice and the `SQLiteConstraintException`
  takes the process down. Needs both a local fix (conflict strategy / guard) and a general one
  (an error path in the UI state).

## Tier 2 — functional bugs

- [x] **F4 — Quick-entry silently discards the typed value or rating when the tag already
  exists.** *Fixed — ADR-034. The dispatch lives in `QuickEntryAction.forExistingTag`, a pure
  function extracted precisely so ADR-034's table could be unit-tested rather than buried in a
  Composable this project can't test. Tapping a suggestion now routes through the same rule.
  `selectedGroupIsFreshTag` renamed to `selectedGroupMayBeEmpty`, as the ADR asked.*

  Original finding: `TagQuickEntryBar.kt:93` checks `exactMatch != null` *before* the type branches, so
  it always calls `onAddExistingTag(id)` → `addInstance(tagId, date)` with
  `rating = null, value = null`. Typing `film:dune` on an existing `film` throws "dune" away.
  For a Valued tag this leaves a phantom value-less instance: `summarize` drops it via
  `mapNotNull { it.value }` (`TagDisplayGroups.kt:31`), so **pressing "+" looks like it did
  nothing** while an empty row appears in the sheet — and on a day whose only instances are
  value-less, the capsule reads `film: []`. Adding a value to a Valued tag you already own is
  the most common Valued action there is, and ADR-009's shorthand currently only works at
  creation time. Fixing this properly is an ADR: what *should* `film:dune` do on an existing
  Valued `film`, and what should a bare `film` do?

- [x] **F5 — Every navigation emits one frame of mismatched state.** *Fixed — ADR-036 point 1.
  The query now travels with its data. Guarded by a test that records every emission and was
  confirmed to fail against the old implementation.*

  Original finding: `CalendarViewModel.kt:55`
  combines `query` with `query.flatMapLatest { periodDataFlow(it) }`, so `combine` fires on the
  query's own emission before the data flow has switched. Reproduced with a throwaway test
  against the in-memory fakes:

  ```
  --- stepTime(+1) ---
  date=2026-08-04 … groups=[walk]   ← new date, yesterday's tags
  date=2026-08-04 … groups=[]
  --- setZoom(WEEK) ---
  zoom=WEEK data=Day                ← Week renders empty
  zoom=WEEK data=Week
  ```

  That's with the *synchronous* fake; real Room widens the window. The
  `as? CalendarPeriodData.Week` casts in `CalendarContent.kt` are what turn the mismatch into a
  blank flash rather than a crash. Fix by carrying the query through the data flow so the pair
  can't desync, which also lets those casts become exhaustive.

- [x] **F6 — Nothing updates at midnight.** *Fixed — ADR-036 point 2. `CalendarUiState.today`
  is observed and re-emitted at midnight; `Clock` is injected so the rollover is unit-tested.
  A manual clock or timezone change while the app is open is still not caught — see the ADR.*

  Original finding: `LocalDate.now()` was read ad-hoc during composition
  (`DayContent.kt:190`, `WeekContent.kt:51`, `MonthContent.kt:74`, `YearContent.kt:87`,
  `CalendarContent.kt:138`) and once at ViewModel construction (`CalendarViewModel.kt:43`).
  There's no ticker and no `ACTION_DATE_CHANGED`/`ACTION_TIMEZONE_CHANGED` receiver. Leave the
  app open overnight and the header still calls yesterday "Today", the today-ring sits on the
  wrong cell, and the jump-to-today button (ADR-017) hides itself on the wrong day.

- [x] **F7 — A pending removal can be stranded indefinitely.** *Fixed — ADR-036 point 3. The
  undo window is a `viewModelScope` timer, so it survives navigation and rotation. Process
  death still cancels it, which leaves the instances undeleted — the safe direction.*

  Original finding: `pendingRemoval` lives in the
  ViewModel but is only ever resolved by the snackbar's `LaunchedEffect`
  (`CalendarContent.kt:113`). Navigate to Tags or Settings inside the undo window and that
  coroutine is cancelled with neither `undoRemoval()` nor `commitPendingRemoval()` — the
  instances stay hidden but undeleted, and returning re-shows the "removed" snackbar.
  `onCleared()` isn't overridden, so process death drops the deletion entirely and the tag
  reappears. See ADR-019 for the intended shape.

- [x] **F8 — A rating can never be cleared, and a value can never be emptied.**
  *Closed by decision (2026-08-03), no code change: rating is set-once-then-adjust. Unrated
  exists until you first rate an instance; undoing means deleting the row and adding a fresh
  unrated one from the sheet's add-row, which ADR-008 and ADR-028 already support. A
  tap-the-selected-star-to-clear gesture was considered and turned down as an invisible
  affordance that's as easy to trigger by accident as on purpose; an explicit clear button was
  turned down for width, in a row already holding a drag handle, five stars and a delete. The
  value half stays as ADR-021 point 7 decided it. Re-raise only with real usage behind it.*
  Original finding, kept for the record:
  `StarInput.kt:33` maps taps 1..5 only, so once an instance is rated there's no route back to
  unrated — even though unrated is a first-class state (ADR-008) that the sheet's own add-row
  can create. `ValueField` (`InstanceListSheet.kt:628`) only persists when the trimmed text is
  non-empty, so clearing a value silently reverts on reopen and deleting the row is the only
  way out. The `ValueField` half is deliberate (it's in `UI_UX.md`'s check list); the *absence
  of any other way to clear* is the gap.

- [ ] **F9 — The delete-tag dialog counts instances but says days.** `TagDao.kt:38` is
  `COUNT(*) FROM tag_instances`, while `strings.xml:35` reads "removes it from %1$d tagged
  day(s)". Tag something twice in one day and the number overstates. Wants
  `COUNT(DISTINCT date)` — and a `<plurals>` while it's being touched (see F16).

## Tier 3 — state & lifecycle

- [x] **F10 — No saved state anywhere in the app.** *Fixed — ADR-035. `CalendarViewModel`'s
  query is backed by `SavedStateHandle`; `selectedGroupKey` and the Tags dialogs are
  `rememberSaveable` (the latter now keyed by tag id rather than a `Tag` snapshot). F6 landed
  with it, so a restored focused date doesn't restore a stale idea of today.*

  Original finding: `CalendarViewModel.query` (zoom, focused
  date, selected heatmap tag) is a plain `MutableStateFlow` with no `SavedStateHandle`; and
  `selectedGroupKey` (`CalendarScreen.kt:25`) plus `renamingTag`/`recoloringTag`
  (`TagsScreen.kt:21`) are `remember`, not `rememberSaveable`. Rotating with a sheet or dialog
  open closes it; process death resets to Day/today with the heatmap tag cleared. Note
  `PeriodNavigationRow` and `TagQuickEntryBar` *do* use `rememberSaveable` — so this is an
  inconsistency to settle, not the house style.

- [x] **F11 — `ValueField` writes to Room on every keystroke** *— fixed, ADR-037 point 2:
  debounced 400ms and the persisted text is now trimmed.* Original finding: (`InstanceListSheet.kt:629`):
  one `UPDATE` per character, no debounce, each round-tripping through the day's flow and
  recomposing the sheet. It also persists `newValue` untrimmed while the guard tests
  `newValue.trim()`, so `"dune "` is what lands in the database — inconsistent with
  `AddValueRow`, which trims.

## Tier 4 — UI/UX & accessibility

- [x] **F12 — Month/Year zoom lands on a dead end every time.** *Fixed — ADR-035, via F10's
  `SavedStateHandle`: the picked tag survives process death. Still empty on a genuinely first
  use and after a cold start, which is what ADR-035 decided; cross-restart memory remains
  deferred rather than taking a preferences dependency.*

  Original finding: `selectedTagId` starts `null`,
  is never defaulted and never persisted, so the ordinary zoom-out gesture from Week hits "Pick
  a tag above to see its heatmap" — on every launch, and again after process death. Worth
  deciding whether it defaults to the first tag, the last used, or stays deliberately empty.

- [ ] **F13 — Week zoom is unlabelled colored dots.** `WeekContent.kt:104` renders a `Box` per
  group with no name, no count and **no `contentDescription`**, so the whole zoom level is
  invisible to TalkBack and ambiguous by eye once two tags have similar colors (which F19 makes
  likely). Nothing caps the dot count for a heavily tagged day either.

- [ ] **F14 — Heatmap cells convey their value by alpha alone.** `HeatmapDayCell.kt` exposes
  only the day number, with no content description carrying the count, so the data reaches
  neither a screen reader nor a color-vision-impaired user. `alphaForCount`
  (`HeatmapDayCell.kt:20`) also saturates at 3+, making 3 and 30 identical.

- [ ] **F15 — The instance sheet's back-press handling is accidental, not designed.**
  ⚠️ *Originally filed as "back does nothing" — **that was wrong**, written from ADR-021's
  stated intent rather than from the library now on the BOM. Corrected 2026-08-03 by reading
  material3 1.4.0's sources: back takes a different path from the scrim
  (`ModalBottomSheet.kt:174`) whose `invokeOnCompletion { onDismissRequest() }` carries no
  `if (!sheetState.isVisible)` guard, so **back has been closing the sheet all along**.*

  What remains is that the desired behaviour depends on that unguarded completion handler —
  a library inconsistency that could be tightened in any release — while the
  `confirmValueChange` veto is doing a job (blocking the scrim) that
  `ModalBottomSheetProperties.shouldDismissOnClickOutside` now does directly and legibly.
  Decided — see ADR-021 Amendment 1: make it explicit, which also restores the predictive-back
  animation. Also still open and untested: no `imePadding()` on the sheet content, so the
  keyboard may cover the add-value row.

- [ ] **F16 — Star content descriptions announce the wrong thing.** Every star reads
  "%1$d stars" — its own index (`StarInput.kt:36`) — so TalkBack enumerates "1 stars … 5 stars"
  and never states the current rating. Lint flags this and `tags_delete_dialog_message` as
  `<plurals>` candidates ("1 stars").

- [ ] **F17 — Dates use hardcoded field order despite the locale plumbing.**
  `CalendarPeriodLabels.kt` (ADR-033) and `DayContent.kt:53-57` thread a `Locale` into
  `DateTimeFormatter.ofPattern("d MMMM yyyy")`, which localizes the month *name* but never the
  order — a US locale still gets "25 July 2026". `ofLocalizedDate` /
  `getLocalizedDateTimePattern` is what would make the parameterisation pay off. Every
  formatter is also a top-level `val`, binding the locale at class-init. Note
  `CalendarPeriodLabelsTest` asserts the current en-GB-shaped output, so it changes with this.

- [x] **F18 — No keyboard submit path.** *Fixed — `ImeAction.Done` plus `KeyboardActions` on
  both quick-entry and the sheet's add-value field (ADR-037).* Original finding: Quick-entry (`TagQuickEntryBar.kt:154`) and the
  add-value field are `singleLine` with no `imeAction`/`KeyboardActions`, so "+" must be tapped
  on every single entry — in an app whose entire loop is type-a-tag-and-add.

- [ ] **F19 — Palette colors collide by construction.**
  `TagPalette.colors[allTags.size % size]` (`CalendarViewModel.kt:175` and `:196`) — delete one
  tag and the next duplicates an existing color, with no check of what's in use. Week zoom,
  being dots-only (F13), is exactly where that's unreadable.

## Tier 5 — code quality & process

- [ ] **F20 — No instrumented tests exist at all.** `app/src/androidTest` isn't present, though
  the dependencies are configured in `app/build.gradle.kts`. `TESTING.md` documents this as a
  no-device gap, which is honest — but it means the drag-reorder (ADR-022), the swipe
  arbitration (ADR-012), the capsule hit-target trick (ADR-026/027) and the sheet are verified
  only by `UI_UX.md`'s manual checklist.

- [ ] **F21 — Deprecated API**: `LocalConfiguration.current.screenHeightDp`
  (`InstanceListSheet.kt:106`) — lint's `ConfigurationScreenWidthHeight` wants
  `LocalWindowInfo.current.containerSize`. The deprecated value has target-SDK-dependent inset
  behaviour, which directly affects the sheet's 50% ceiling (ADR-028).

- [ ] **F22 — `renameTag` is a `suspend fun` on the ViewModel** (`TagsViewModel.kt:63`) called
  from the dialog's `rememberCoroutineScope` (`RenameTagDialog.kt:64`), so the write is tied to
  the composition's lifetime and dismissing or rotating mid-rename can cancel it. Every other
  mutation goes through `viewModelScope`. It's also a check-then-write against a unique index,
  so the `@Update` can throw the same way F3 describes.

- [ ] **F23 — Nothing enforces lint, and there's no landscape/tablet story.** `lintDebug`
  passes with 5 warnings but no baseline and no gate. Separately: the sheet is 50% of screen
  height, the capsule `FlowRow` and the Year 4×3 grid are portrait-shaped, and nothing uses
  window size classes. R8 being off in release is deliberate and stays deferred to M6.

- [ ] **F24 — `hiltViewModel()` is deprecated where it's used.** Both `CalendarScreen` and
  `TagsScreen` call `androidx.hilt.navigation.compose.hiltViewModel`, which has moved to
  `androidx.hilt.lifecycle.viewmodel.compose`. Compiler warning on every build, and the two
  call sites are the only ones. *(Noticed while working F5–F10, 2026-08-04 — not part of the
  original audit.)*

---

## Deliberately not listed

Things that look like gaps but are settled decisions — don't re-raise without reading the ADR:

- Simple capsules opening a count-only stepper rather than being inert (ADR-031).
- The sheet resisting drag and scrim dismissal (ADR-021 point 6 — *not* ADR-025, which this
  file cited in error). Back press is the exception, per ADR-021 Amendment 1 and F15.
- No domain layer, single Gradle module (`CLAUDE.md` § Hard rules).
- Tag type being immutable and living on the tag (`CLAUDE.md` § Hard rules).
- R8, app icon and signing deferred to M6 (`MILESTONES.md`).
