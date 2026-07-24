# TagDay — UI / UX

Scoped to what **M0/M1** actually need (see `MILESTONES.md`): the navigation shell and
the Day screen, Simple tags only. Week/Month/Year zoom, the Tags management screen, and
Drive backup UI are specced when their milestone arrives — don't design ahead of them.

## Navigation shell

Bottom navigation, two destinations:

| Destination | Route | M0/M1 content |
|---|---|---|
| Calendar | `calendar` | `DayScreen` — the only zoom level that exists yet |
| Tags | `tags` | Placeholder screen ("Tags view — coming soon") until M3 |

`Calendar` is the default/start destination. There's no nested zoom-level navigation
yet — that's introduced in M4 alongside the swipe gestures; for now `calendar` routes
straight to `DayScreen` showing today.

```kotlin
NavHost(navController, startDestination = "calendar") {
    composable("calendar") { DayScreen() }
    composable("tags") { TagsPlaceholderScreen() }
}
```

## Day screen

Per `CONVENTIONS.md` § Compose conventions: `DayScreen` (collects `DayViewModel` state)
delegates to stateless `DayContent`.

### Layout

```
┌─────────────────────────────┐
│  Today, 24 July 2026         │  ← header, static in M1 (no prev/next — that's M4)
├─────────────────────────────┤
│  walk (2)                    │  ← tag group row
│  reading                     │  ← tag group row (single instance, no count)
│  meditation                  │
│                               │
│                               │
├─────────────────────────────┤
│                          [+]  │  ← FAB, opens add-tag bottom sheet
└─────────────────────────────┘
```

- Each row is one `TagDisplayGroup` (from `ARCHITECTURE.md`), rendered per the
  `FEATURES.md` grouping rules — in M1 this only ever produces Simple-style rows
  (`name` or `name (count)`), since Rated/Valued don't exist until M2.
- **Tap a row** → opens a bottom sheet listing the individual instances behind that
  group, each with its own remove action (per the resolved "manage individual
  instances" decision in `FEATURES.md`). For a Simple tag this is just a plain list of
  timestamps with a delete icon each — unglamorous, but keeps M1's interaction model
  identical to what M2 will reuse for Rated/Valued groups.
- **No date navigation in M1.** The header shows today's date but isn't tappable and
  there are no prev/next controls — that's deliberately deferred to M4's swipe
  gestures, per the milestone scope. Don't add a stopgap arrow button; it'd just be
  thrown away.

### Add-tag flow (bottom sheet)

Triggered by the FAB.

```
┌─────────────────────────────┐
│  Add a tag                   │
│  🔍 Search or create...      │  ← text field, filters the list below as you type
├─────────────────────────────┤
│  ● reading                   │  ← existing tag, tap to add an instance immediately
│  ● meditation                │
│  ● walk                      │
├─────────────────────────────┤
│  + Create "yoga"              │  ← appears only when typed text matches no existing tag
└─────────────────────────────┘
```

- Typing filters the existing-tag list (same filter behavior `TagsView` will later
  expose in M3 — this bottom sheet is a lightweight preview of that, not a separate
  implementation).
- Tapping an existing tag adds a new Simple instance for today immediately and closes
  the sheet (M1 has only one type, so there's nothing further to configure — M2 adds a
  type-picker step here).
- "Create" only appears once the typed name doesn't match an existing tag
  (case-insensitive). Creating asks for a color from the fixed palette (`ARCHITECTURE.md`
  § package layout implies a `theme`-adjacent palette source — no custom color picker
  until M3) and then adds the new tag's first instance to today in one step.

### Empty states

- **No tags on today**: centered message + icon ("Nothing tagged yet — tap + to add
  one"), not just a blank screen.
- **No tags in the repository yet** (fresh install, add-tag sheet opened): the sheet
  skips straight to "Create your first tag" — no empty existing-tag list shown above it.

## Theming

- Material 3 default color scheme for v1; a full theming/dynamic-color pass is M6
  (`MILESTONES.md`), not now.
- Tag colors are **not** part of the Material theme — each tag row/chip renders using
  the tag's own stored ARGB `Int` (`DATA_MODEL.md` § `Tag`), independent of light/dark
  mode. Ensure sufficient contrast against both theme backgrounds is a noted M6 item,
  not a v1-launch blocker.

## Open notes for later docs

- **M2** extends the add-tag flow with a type picker (Simple / Rated / Valued) and
  extends the row format per `FEATURES.md` grouping rules (star average, value lists).
- **M3** replaces `TagsPlaceholderScreen` with the real Tags management screen (list,
  filter, rename, recolor, delete) — full spec deferred until then.
- **M4** replaces the static header with swipeable zoom levels and adds Week/Month/Year
  layouts (chip overview, single-tag heatmap) — full spec deferred until then.
- **M5** adds a Drive backup/restore entry point — likely a simple settings-style
  screen reachable from somewhere in the nav shell (exact placement TBD when reached).
