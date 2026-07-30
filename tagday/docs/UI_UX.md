# TagDay — UI / UX

Scoped to what **M0-M4** actually need (see `MILESTONES.md`): the navigation shell, the
Calendar screen (all four zoom levels, all three tag types), and the Tags management
screen. The Settings screen was scaffolded early (empty, ahead of any specific
milestone) so it exists as a destination for later work to land in — see § Settings
screen. Its actual content, including Drive backup UI, is specced when M5 arrives —
don't design ahead of that.

## Navigation shell

No bottom navigation bar — Calendar (`CalendarScreen`) is the sole start destination and
the app's primary surface. Tags and Settings are each reached via a small icon in the
top-right corner of the Calendar screen (shared across all four zoom levels, see
§ Calendar screen below) and returned from via their own back arrow, i.e. they're
screens you navigate *into* and back out of, not peer tabs.

| Destination | Route | Content |
|---|---|---|
| Calendar | `calendar` | `CalendarScreen` — Day/Week/Month/Year, one continuous calendar, start destination |
| Tags | `tags` | `TagsScreen` — list/filter/rename/recolor/delete, pushed on top of Calendar |
| Settings | `settings` | `SettingsScreen` — empty placeholder for now, pushed on top of Calendar |

```kotlin
NavHost(navController, startDestination = "calendar") {
    composable("calendar") {
        CalendarScreen(
            onNavigateToTags = { navController.navigate("tags") },
            onNavigateToSettings = { navController.navigate("settings") },
        )
    }
    composable("tags") {
        TagsScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable("settings") {
        SettingsScreen(onNavigateBack = { navController.popBackStack() })
    }
}
```

## Calendar screen

Per `CONVENTIONS.md` § Compose conventions: `CalendarScreen` (collects `CalendarViewModel`
state) delegates to stateless `CalendarContent`. One screen, four zoom levels (Day/Week/
Month/Year) — per `FEATURES.md`, "one continuous calendar, viewed at different
granularities, not four separate screens." `CalendarContent` owns the shared chrome
(top bar, swipe-handle strip, gesture detection) and dispatches to each zoom level's
stateless `XyzContent` (`DayContent`, `WeekContent`, `MonthContent`, `YearContent`) based
on `CalendarViewModel`'s `zoomLevel` state.

### Shared chrome

```
┌─────────────────────────────┐
│  Day ▾ [⌖]            [🏷] [⚙]│  ← top bar: zoom picker + jump-to-today (left), Tags + Settings (right)
├─────────────────────────────┤
│                               │
│      (zoom-level content)    │  ← swipe up/down = zoom, left/right = move through time
│                               │
├─────────────────────────────┤
│  Add a tag…              [+] │  ← quick-entry bar, Day zoom only
└─────────────────────────────┘
```

- **Top bar**: a `TopAppBar` whose `title` slot holds a `Row` with `ZoomLevelPicker`
  (a `TextButton` showing the current zoom level's name, e.g. "Day", plus a small
  dropdown chevron) followed by a conditional jump-to-today `IconButton` — a bullseye
  icon (`R.drawable.ic_target`, a custom vector: two concentric rings around a solid
  center dot, no crosshair ticks; no "today" or target glyph in `material-icons-core`,
  and pulling in `material-icons-extended` for one icon isn't worth the APK-size
  tradeoff while release builds have minification disabled), rendered only when
  `zoomLevel == DAY && focusedDate != today`, calling
  `CalendarViewModel.jumpToToday()`. It sits next to the zoom picker rather than in the
  trailing `actions` group — it's a peer of "which slice of time am I looking at,"
  not a navigation destination like Tags/Settings — see ADR-017 for why it's
  Day-zoom-only and § Day zoom for its own on-screen indicator.

  The trailing `actions` slot holds two `IconButton`s, always shown at every zoom level,
  the only way into their respective screens, per § Navigation shell:
  1. Tag/label-shaped icon (`R.drawable.ic_label` — a custom vector, same rationale as
     `ic_target` above) that navigates to the Tags screen.
  2. Gear icon (`Icons.Default.Settings` — already in `material-icons-core`, no custom
     vector needed) that navigates to the Settings screen.

  Settings sits after Tags — established, more-frequently-used destination stays in its
  existing position; new, currently-empty destination is appended rather than inserted.
- **Zoom picker**: `ZoomLevelPicker` (`ui/calendar/ZoomLevelPicker.kt`) — tapping it opens
  a `DropdownMenu` listing Day/Week/Month/Year, current entry marked with a leading
  checkmark; picking one calls `CalendarViewModel.setZoom` directly (an absolute setter,
  distinct from `stepZoom`'s relative ±1 used by the swipe gesture below) and only
  changes `zoomLevel` — `focusedDate` is left untouched, same contract as the swipe. This
  is a discoverability/direct-jump affordance *alongside* the swipe gesture, not a
  replacement for it — see ADR-014 in `DECISIONS.md`, and its relationship to the
  strip that ADR-012 tried and removed.
- **Quick-entry bar**: shown only at Day zoom — adding tags is a Day-only capability
  (`FEATURES.md`); Week/Month/Year are read-only overviews/navigation.
- **Gesture model**: no dedicated control for zoom *in the content body* — the zoom
  picker above lives in the top bar's chrome, not the swipe area, and reaching a zoom
  level by swiping never requires it. Swipe anywhere in the content body.
  Horizontal is a plain drag detector: accumulates delta over the gesture and, on
  release, steps the focused date by one unit of the current zoom level, forward or
  back (nothing in any zoom level scrolls horizontally, so no coordination is needed).
  Vertical steps the zoom level the same way conceptually (swipe up zooms out,
  Day→Week→Month→Year; swipe down zooms in; clamped at both ends, no wraparound) but is
  implemented as a real `Modifier.scrollable`, not a raw drag detector — this lets it
  properly negotiate with Day's tag list, which is itself `scrollable` (via
  `verticalScroll`). A nested `scrollable` only ever grabs the delta its descendant
  didn't consume (Compose dispatches to the *innermost* scrollable first), so the tag
  list still scrolls normally, and only once it's out of room — or on Week/Month/Year,
  which have nothing scrollable at all (see § Month / Year zoom for Year's fixed,
  no-scroll grid) — does a vertical swipe change the zoom level. Same activation zone as
  horizontal, same screen area, on every zoom level. Deliberately not `Pager`-based —
  see ADR-012 in `DECISIONS.md`, including its amendments for the history of this
  control (a dedicated tappable strip was tried and removed; the original
  vertical-only-off-to-the-side gesture zone was replaced by this nested-scroll-aware
  whole-body approach).
- **Tap a day** at Week/Month zoom jumps straight to that day at Day zoom; **tap a
  month** at Year zoom jumps to that month at Month zoom (Year has no per-day tap
  target — see § Month / Year zoom)
  (`CalendarViewModel.jumpToDay`), per `FEATURES.md`.

### Day zoom

```
┌─────────────────────────────┐
│░░░░░░░░ JULY 2026 ░░░░░░░░░░│  ← header card: colored month/year band
│                              │
│             25               │  ← huge day-of-month number
│           Saturday            │  ← weekday name
│            [Today]            │  ← temporal label pill (Past/Today/Future)
├─────────────────────────────┤
│  walk (2)                    │  ← Simple group row
│  reading                     │  ← Simple group row (single instance, no count)
│  freediving: ★★★★ (2)        │  ← Rated group row (average + count)
│  movie: [dune, terminator]   │  ← Valued group row (values listed)
└─────────────────────────────┘
```

- Each row is one `TagDisplayGroup` (from `ARCHITECTURE.md`), rendered per the
  `FEATURES.md` grouping rules — Simple (`name` or `name (count)`), Rated (star average
  + count), Valued (per-value list with per-value counts). A Rated group with no rated
  instances yet (freshly added, unrated) displays like a Simple group until an instance
  is rated — see ADR-008 in `DECISIONS.md`.
- **Tap a row** → for **Rated or Valued** groups only, opens a bottom sheet, fixed at
  50% of screen height and **not** dismissible by dragging, back press, or scrim tap — an
  explicit close (✕) button next to the tag name is the only way to close it (ADR-021).
  It lists the individual instances behind that group, each independently editable and
  removable (per the resolved "manage individual instances" decision in `FEATURES.md`),
  in a scrollable region (with a scrollbar once they overflow the fixed height) for both
  types. Rated instances show an editable 1–5 `StarInput` (tap a star to set/change that
  instance's rating, at any time — no requirement to rate at creation), plus a timestamp
  below each row; Valued instances show an editable text field for that instance's
  value, with no timestamp (dropped per ADR-021 — clutter for a type where the list is
  reordered by hand, not by time) — the field can be typed/cleared freely, but it never
  persists a blank value (ADR-021), same rule `AddValueRow` already applied to new
  values. Both keep a delete icon per instance. **Simple**
  capsules don't respond to a tap at all (`TagGroupCapsule`'s `clickable` is `enabled =
  type != SIMPLE`) — there's no per-instance state to edit, only presence, so the sheet
  would have nothing to offer beyond what the "x" below already does. See ADR-018.
  **Valued** sheets additionally have: a leading **drag handle** per row (`DragHandle` in
  `ValuedInstanceList`) for manually reordering values, persisted via
  `TagInstance.sortOrder`. Press the handle and drag vertically — the row lifts (raised
  container color, drawn above its neighbours) and follows the finger, trading places with
  a neighbour once it passes half of that neighbour's height, and the new order is written
  once on drop. Holding the row near the top or bottom edge auto-scrolls the list, since a
  drag in progress owns the touch and the list can't scroll itself then; dragging anywhere
  *other* than the handle scrolls the list as normal. Screen readers get the same reorder
  as move-up/move-down accessibility actions on the handle, since there's no way to
  perform a touch-drag through one. The order applies everywhere the values are listed,
  the capsule summary included — instances reach the UI already in display order and are
  rendered as given, rather than each screen sorting for itself (ADR-023). Reordering went
  through several failed attempts before this one — see ADR-021 for what didn't work and
  ADR-022 for the mechanism that does.
  Valued sheets also have a fixed row below the instance list — a text field + add button
  (`AddValueRow`) for adding another value without leaving the sheet. **Rated** has
  neither the handle nor the add-row, staying edit/remove-only. See ADR-020, ADR-021,
  ADR-022.
- **Capsule "x"**: each group's capsule also has its own inline "x" (`TagGroupCapsule` in
  `DayContent.kt`), separate from the row-tap above — tapping it removes *all* of that
  tag's instances for the day, regardless of count, with no confirmation dialog (a fast
  full-group removal, vs. the row-tap's per-instance editing/removal).
- **Undo**: neither the capsule "x" nor the row-tap sheet's per-instance delete deletes
  immediately — both are delay-delete (`CalendarViewModel`'s `PendingRemoval`, ADR-019).
  The item disappears from the Day list right away (an optimistic client-side filter,
  `TagDisplayGroup.excludingInstances`), and a `Snackbar` ("'\<tag\>' removed", "Undo")
  appears at the bottom of the screen. Tapping **Undo** restores it (nothing was ever
  deleted); leaving the snackbar to time out (`SnackbarDuration.Short`, ~4s) or
  dismissing it commits the deletion. Only one removal is ever pending at a time —
  removing something else while a snackbar is still showing commits the first one
  immediately rather than queuing a second snackbar.
- **Header**: a `Card` styled like a tear-off desk-calendar page — a top band in
  `colorScheme.primary` with the month and year in small caps, then the day-of-month
  in `displayLarge`/bold (the dominant element), the weekday name in `titleLarge` below
  it, and a small rounded **temporal label** pill beneath that: "Past"/"Today"/"Future"
  relative to the device clock, colored gold/green/violet respectively via
  `TemporalColors` (fixed ARGB ints, deliberately outside the Material color scheme —
  same rationale as `TagPalette`: dynamic color would make a semantic signal like this
  unreliable across devices/wallpapers). All five fields are exposed to screen readers
  as one merged node ("Saturday, 25 July 2026, Today") rather than five separate
  fragments. See ADR-017.

### Week zoom

7 day-rows for the current ISO week (Monday start). Each row: weekday + day-of-month,
then a small row of colored dots — **one per distinct tag present that day**, not one
per instance (a repeated Simple tag still shows a single dot, mirroring how Day zoom
collapses repeats into `walk (2)`). Tapping a row jumps to that day at Day zoom.
Today's row gets a filled `colorScheme.primary` circle behind its day-of-month number
(Google Calendar-style) — see ADR-017.

### Month / Year zoom

Both are a **single-tag heatmap** — a `TagPickerDropdown` at the top (empty-state prompt
"Pick a tag above to see its heatmap" until one is picked; no auto-selected default),
shaded by that tag's **instance count only**, same rule regardless of tag type (a Rated
tag's actual average rating isn't reflected — only how often it was logged that day).
Shading buckets: 0 instances = transparent, 1 = 30%, 2 = 60%, 3+ = 100% of the tag's own
color (`alphaForCount` in `HeatmapDayCell.kt`, shared by both zoom levels).

- **Month**: one weekday-aligned grid of `HeatmapDayCell`s, each showing its
  day-of-month number (leading blank cells align the 1st to its column). Tapping a day
  cell jumps to that day at Day zoom. Today's cell gets a `colorScheme.primary` border
  ring — a stroke rather than a fill, since the cell's background already carries the
  heat-shading signal; the two read as independent layers. See ADR-017.
- **Year** (`ui/calendar/year/YearContent.kt`): all 12 months at once, as a fixed
  4-row × 3-column grid sized with nested `weight()` modifiers — not `verticalScroll`,
  not `aspectRatio` — so it always fills the available area exactly, with no scrolling
  and no overflow risk regardless of screen size. Each month tile is a compact 6-week ×
  7-day grid of plain shaded cells: no day-of-month numbers (illegible at this density)
  and no per-day tap target (cells land well under the 48dp minimum touch target at
  12-months-on-one-screen density). The **whole month tile** is the tap target instead,
  jumping to Month zoom for that month rather than straight to Day zoom — a two-step
  drill-down (Year → tap month → Month → tap day) instead of Month/Week's one-step. See
  ADR-016 in `DECISIONS.md`. The tile containing the current month gets a
  `colorScheme.primary` border around the **whole tile** rather than a per-day marker —
  individual day cells are already too small for a tap target at this density (per
  ADR-016), so they're too small for a second visual signal too. See ADR-017.

### Quick-entry tag bar

Replaces the earlier modal "add a tag" bottom sheet (see ADR-009 in `DECISIONS.md`).
Pinned to the bottom of Day zoom only (`CalendarContent`'s `Scaffold` `bottomBar`,
conditional on `zoomLevel == DAY`), always visible while at Day zoom — no FAB, no sheet
to open.

```
┌─────────────────────────────┐
│  ● reading                   │  ← substring-matching existing tags, tap to add
│  ● meditation                │     an instance immediately
├─────────────────────────────┤
│  [ Add a tag…          ] [+] │  ← always-visible text field + submit button
└─────────────────────────────┘
```

- **Suggestions**: as the user types, existing tags whose name contains the typed text
  (case-insensitive substring, capped to a scrollable height) appear above the field.
  Tapping one adds a **blank** instance for today (same as before — no type picker,
  ever, since type is fixed on the tag) and clears the field.
- **Type inference from syntax**, applied only when the typed name has no existing-tag
  match (an exact case-insensitive name match always just adds a blank instance to that
  tag, per the rule above — any `:suffix` typed alongside an exact match is ignored):
  - `name` alone (no `:`) → ambiguous. A row of three filter chips (Simple / Rated /
    Valued, via `TagType.label()`) shows alongside any suggestions (not instead of them —
    ADR-021). Tapping **Simple** or **Rated** creates the tag with a blank first instance
    (rating stays settable later via the instance-list sheet, same as today — ADR-008).
    Tapping **Valued** instead creates the tag with *no* instance yet and opens the
    instance-list sheet directly for it, so the user types the first real value there —
    a Valued instance with no value has no meaningful display, unlike Rated's "unrated"
    fallback (ADR-021).
  - `name:***` (one or more literal `*` characters, nothing else, after the colon) →
    unambiguous. Creates a **Rated** tag and seeds the first instance's rating with the
    star count (clamped to 5 if more than five `*` are typed). No picker shown — a hint
    line above the field ("Will create a new Rated tag") confirms what's about to
    happen before the user taps `+`.
  - `name:text` (any other non-blank text after the colon) → unambiguous. Creates a
    **Valued** tag and seeds the first instance's value with that text, with the same
    hint-line confirmation.
- **Color** is auto-assigned for every new tag by cycling through `TagPalette`'s fixed
  colors (`allTags.size % colors.size`) — there is no manual color picker in this flow.
  Recoloring a tag happens in the Tags view (M3) instead, same as renaming.
- The type choice (explicit chip tap, or syntax inference) only ever happens at
  **creation**, never when adding to an already-existing tag, and is never editable
  afterward (`FEATURES.md` § Tag types) — this rule is unchanged from before.

### Empty states

- **No tags on today**: centered message + icon ("Nothing tagged yet — add one
  below"), not just a blank screen. The quick-entry bar itself is still visible below
  it, since it's a persistent part of the screen, not something that needs opening.
- **No tags in the repository yet** (fresh install): the quick-entry bar behaves the
  same as always — typing a name with no matches shows the Simple/Rated/Valued chip row
  (or the Rated/Valued hint line, if the shorthand syntax is used) exactly as it would
  for any other new tag name.
- **No tag picked yet** (Month/Year): centered prompt in place of the heatmap grid,
  until the user picks one from the dropdown.

## Tags screen

Per `CONVENTIONS.md` § Compose conventions: `TagsScreen` (collects `TagsViewModel`
state) delegates to stateless `TagsContent`. Management-only — no "create tag" entry
here; creation stays exclusively in the Calendar screen's Day-zoom quick-entry bar (ADR-009).

### Layout

```
┌─────────────────────────────┐
│  ←  Tags                     │  ← TopAppBar, back arrow returns to Calendar
├─────────────────────────────┤
│  🔍 Search tags…             │  ← filters the list below as you type
├─────────────────────────────┤
│  ● walk        Simple   ✎ 🗑 │  ← color dot / name+type / rename / delete
│  ● freediving  Rated    ✎ 🗑 │
│  ● movie       Valued   ✎ 🗑 │
└─────────────────────────────┘
```

- **Filter**: typing filters the list by name (case-insensitive substring), same
  `TagRepository.observeFiltered` query the Day-zoom quick-entry bar already uses.
- **Color dot** (leading, tappable): opens `ColorPickerDialog` — the fixed palette plus
  a custom hue-slider + saturation/value-square picker (see below). Saving calls
  `TagsViewModel.updateColor`.
- **Rename** (trailing pencil icon): opens `RenameTagDialog`, a text field pre-filled
  with the current name. Save is disabled while blank; a duplicate name (checked
  case-insensitively, excluding the tag being renamed) shows an inline error instead of
  saving. Renaming only changes the display name — existing day associations
  (keyed by tag id) are unaffected, per `FEATURES.md`.
- **Delete** (trailing trash icon): fetches the tag's instance count, then shows a
  confirmation dialog ("Delete '<name>'? This removes it from N tagged day(s). This
  can't be undone.") before cascading the delete — per `FEATURES.md`'s resolved
  deletion decision. Unlike Day zoom's capsule "x" (no confirmation, single day
  only), this is a whole-tag, all-days deletion, so it keeps the confirmation step.
- **Color picker**: fixed palette row (tap to select immediately) plus a custom
  section — a rainbow-gradient hue slider and a draggable saturation/value square,
  converted to/from the stored ARGB `Int` via `android.graphics.Color.colorToHSV`/
  `HSVToColor` (see ADR-011 in `DECISIONS.md` for why this is hand-built rather than a
  third-party color-picker dependency). A live preview swatch shows whichever of
  palette-tap or custom-adjust was chosen last; Save commits it.

### Empty states

- **No tags in the repository yet**: "No tags yet" centered message.
- **No tags match the filter**: "No tags match '<query>'" — distinct from the
  fully-empty case so it's clear the repository isn't actually empty.

## Settings screen

Empty placeholder, scaffolded ahead of any specific content — reached via the gear icon
in the Calendar screen's top bar (§ Calendar screen), returned from via its own back
arrow, same pattern as Tags. `SettingsScreen` (`ui/settings/SettingsScreen.kt`) has no
`ViewModel` yet (nothing to hold state for) and delegates straight to stateless
`SettingsContent`: a `TopAppBar` titled "Settings" with a back arrow, and a centered
"Nothing here yet" message in place of content. What eventually lives here — Drive
backup/restore controls, theming options, or something else — is undecided and explicitly
not scoped by this doc; don't design ahead of it.

## Theming

- Material 3 default color scheme for v1; a full theming/dynamic-color pass is M6
  (`MILESTONES.md`), not now.
- Tag colors are **not** part of the Material theme — each tag row/chip renders using
  the tag's own stored ARGB `Int` (`DATA_MODEL.md` § `Tag`), independent of light/dark
  mode. Ensure sufficient contrast against both theme backgrounds is a noted M6 item,
  not a v1-launch blocker.

## Open notes for later docs

- **M5** adds a Drive backup/restore entry point — likely a simple settings-style
  screen reachable from somewhere in the nav shell (exact placement TBD when reached).
- ~~**Considered: Undo snackbar for the capsule "x"**~~ — resolved, see § Day zoom
  above and ADR-019: both the capsule "x" and the instance-list sheet's per-instance
  delete now go through a delay-delete snackbar. The Scaffold's `snackbarHost` slot
  positions it above `bottomBar` automatically, so it wasn't expected to collide with
  the quick-entry bar, but this hasn't been confirmed on a running device/emulator (none
  available in this working environment — see `TESTING.md`); worth a quick look next
  time this screen is run on a device.
- **The Valued instance sheet (ADR-020/ADR-021/ADR-022) has never run on a device.** No
  device/emulator exists in this working environment (`TESTING.md`), so everything about
  it — including the drag-to-reorder, which took six attempts and whose final mechanism is
  read directly out of the `androidx.compose.foundation`/`androidx.compose.ui` 1.11.4
  sources in the Gradle cache rather than recalled (see ADR-022) — is unexercised beyond
  compiling. Gesture *feel* in particular can't be judged here at all. Worth a deliberate
  pass next time this screen is run on a device:
  - Drag a row up and down past several neighbours, and confirm plain scrolling (finger
    anywhere but the handle) still works, including immediately after a drag.
  - Drag to a position that starts off-screen, to exercise the edge auto-scroll — check
    the rate feels controllable rather than runaway, and that the row keeps swapping
    correctly as the list moves under it.
  - Drag at both ends of the list (nothing to swap with), and tap the handle without
    moving (should be a no-op, not a spurious reorder write).
  - Reorder twice in quick succession, to check the second order sticks rather than being
    reverted by the first reorder's Room emission landing late.
  - Reopen the sheet (and the day) afterwards and confirm the order actually persisted.
  - With TalkBack on, confirm the handle exposes its move-up/move-down actions and that
    they're absent at the respective ends of the list.
  - Many values with scrolling: confirm the scrollbar thumb tracks the visible position at
    various scroll depths, not just at the very top/bottom.
  - Try to clear a value to blank and confirm it snaps back rather than persisting empty;
    confirm the sheet resists drag/back/scrim dismissal and that the close button works;
    and add a brand-new Valued tag end to end.
