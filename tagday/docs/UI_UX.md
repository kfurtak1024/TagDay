# TagDay — UI / UX

Scoped to what **M0/M1/M2** actually need (see `MILESTONES.md`): the navigation shell and
the Day screen, all three tag types. Week/Month/Year zoom, the Tags management screen, and
Drive backup UI are specced when their milestone arrives — don't design ahead of them.

## Navigation shell

No bottom navigation bar — Calendar (`DayScreen`) is the sole start destination and the
app's primary surface. Tags is reached via a small icon in the top-right corner of the
Day screen (see § Day screen below) and returned from via its own back arrow, i.e. it's
a screen you navigate *into* and back out of, not a peer tab.

| Destination | Route | Content |
|---|---|---|
| Calendar | `calendar` | `DayScreen` — the only zoom level that exists yet, start destination |
| Tags | `tags` | Placeholder screen ("Tags view — coming soon") until M3, pushed on top of Calendar |

There's no nested zoom-level navigation within Calendar yet — that's introduced in M4
alongside the swipe gestures; for now `calendar` routes straight to `DayScreen` showing
today.

```kotlin
NavHost(navController, startDestination = "calendar") {
    composable("calendar") {
        DayScreen(onNavigateToTags = { navController.navigate("tags") })
    }
    composable("tags") {
        TagsPlaceholderScreen(onNavigateBack = { navController.popBackStack() })
    }
}
```

## Day screen

Per `CONVENTIONS.md` § Compose conventions: `DayScreen` (collects `DayViewModel` state)
delegates to stateless `DayContent`.

### Layout

```
┌─────────────────────────────┐
│                         [🏷]  │  ← top-right icon, opens Tags (see § Navigation shell)
│░░░░░░░░ JULY 2026 ░░░░░░░░░░│  ← header card: colored month/year band
│                              │
│             25               │  ← huge day-of-month number
│           Saturday            │  ← weekday name
├─────────────────────────────┤
│  walk (2)                    │  ← Simple group row
│  reading                     │  ← Simple group row (single instance, no count)
│  freediving: ★★★★ (2)        │  ← Rated group row (average + count)
│  movie: [dune, terminator]   │  ← Valued group row (values listed)
│                               │
├─────────────────────────────┤
│  Add a tag…              [+] │  ← quick-entry bar, always visible — see below
└─────────────────────────────┘
```

- Each row is one `TagDisplayGroup` (from `ARCHITECTURE.md`), rendered per the
  `FEATURES.md` grouping rules — Simple (`name` or `name (count)`), Rated (star average
  + count), Valued (per-value list with per-value counts). A Rated group with no rated
  instances yet (freshly added, unrated) displays like a Simple group until an instance
  is rated — see ADR-008 in `DECISIONS.md`.
- **Tap a row** → opens a bottom sheet listing the individual instances behind that
  group, each independently editable and removable (per the resolved "manage individual
  instances" decision in `FEATURES.md`). Simple instances show a plain timestamp with a
  delete icon; Rated instances show an editable 1–5 `StarInput` (tap a star to set/change
  that instance's rating, at any time — no requirement to rate at creation); Valued
  instances show an editable text field for that instance's value. All three keep the
  delete icon.
- **Header**: a `Card` styled like a tear-off desk-calendar page — a top band in
  `colorScheme.primary` with the month and year in small caps, then the day-of-month
  in `displayLarge`/bold (the dominant element) and the weekday name in `titleLarge`
  below it. The four fields are exposed to screen readers as one merged node ("Saturday,
  25 July 2026") rather than four separate fragments.
- **Top bar**: a minimal `TopAppBar` with no title text, holding a single trailing
  `IconButton` (tag/label-shaped icon, `R.drawable.ic_label` — a custom vector, since
  `material-icons-core`'s bundled set has no tag icon and pulling in
  `material-icons-extended` for one icon isn't worth the APK-size tradeoff while release
  builds have minification disabled, "Edit tags") that navigates to the Tags screen —
  this is the only way into Tags, per § Navigation shell.
- **No date navigation in M1.** The header shows today's date but isn't tappable and
  there are no prev/next controls — that's deliberately deferred to M4's swipe
  gestures, per the milestone scope. Don't add a stopgap arrow button; it'd just be
  thrown away.

### Quick-entry tag bar

Replaces the earlier modal "add a tag" bottom sheet (see ADR-009 in `DECISIONS.md`).
Pinned to the bottom of the Day screen (`Scaffold`'s `bottomBar`), always visible —
no FAB, no sheet to open.

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
  - `name` alone (no `:`) → ambiguous. The suggestions area is replaced with a row of
    three filter chips (Simple / Rated / Valued, via `TagType.label()`); tapping one
    both picks the type and finishes creating the tag in one step (blank first
    instance — rating/value, for Rated/Valued, stay settable later via the instance-list
    sheet, same as today).
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
  Recoloring a tag is deferred to M3's Tags view, same as renaming.
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

## Theming

- Material 3 default color scheme for v1; a full theming/dynamic-color pass is M6
  (`MILESTONES.md`), not now.
- Tag colors are **not** part of the Material theme — each tag row/chip renders using
  the tag's own stored ARGB `Int` (`DATA_MODEL.md` § `Tag`), independent of light/dark
  mode. Ensure sufficient contrast against both theme backgrounds is a noted M6 item,
  not a v1-launch blocker.

## Open notes for later docs

- **M3** replaces `TagsPlaceholderScreen` with the real Tags management screen (list,
  filter, rename, recolor, delete) — full spec deferred until then.
- **M4** replaces the static header with swipeable zoom levels and adds Week/Month/Year
  layouts (chip overview, single-tag heatmap) — full spec deferred until then.
- **M5** adds a Drive backup/restore entry point — likely a simple settings-style
  screen reachable from somewhere in the nav shell (exact placement TBD when reached).
