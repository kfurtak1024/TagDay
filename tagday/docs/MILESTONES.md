# TagDay — Milestones

Vertical slices, not horizontal layers — each milestone produces something runnable and
demoable, not "all the ViewModels" then "all the UI." Order roughly follows dependency:
you can't build Week/Month/Year zoom sensibly before Day view works, can't do rich tag
types before Simple ones prove the plumbing out.

Each milestone below should pull in only as much of `UI_UX.md` as it needs — don't spec
screens you haven't reached yet.

## M0 — Project scaffolding

- Empty Compose app, Hilt set up (`TagDayApplication`, `DatabaseModule` stub).
- Room DB with `Tag` and `TagInstance` entities + DAOs from `DATA_MODEL.md`, `version = 1`.
- Bottom navigation shell: Calendar / Tags, both showing placeholder content.
- **Done when**: app launches, empty Room DB is created, nav switches between two blank screens.

## M1 — Day view, Simple tags only

- Day zoom screen only (no Week/Month/Year yet, no swipe gestures).
- Add a Simple-type tag to today: pick existing tag from repository or create one inline
  (name + color from fixed palette — no custom color picker yet).
- Remove a tag instance from the day.
- Grouping/count display for repeated Simple instances (`walk (2)`), per `FEATURES.md`.
- **Done when**: a user can open the app, land on today, add/remove Simple tags, and see
  correct counts — this is the smallest end-to-end slice through every layer (Compose →
  ViewModel → Repository → Room).

## M2 — Rated & Valued types, full grouping

- Type picker when adding a tag instance (Simple / Rated / Valued).
- Rated: 1–5 star input, editable at any time, average+count aggregation.
- Valued: free-text input, per-value count aggregation, values listed.
- Same tag name usable across all three types on the same day (per resolved decision).
- Instance type mutability: change an existing instance's type in place.
- Individual instance editing/removal within a type group (tap the group, get a list).
- **Done when**: the full `movie (2), movie: ★ (3), movie: [dune (2), terminator]` example
  from `FEATURES.md` is reproducible and each instance is independently editable.

## M3 — Tags view

- List all tags, filter by name.
- Rename (name-only; instance associations unaffected).
- Edit color: fixed palette + custom color picker (32-bit ARGB storage).
- Delete: confirmation dialog showing instance count, cascade delete on confirm.
- **Done when**: the tag repository is fully manageable without needing the Day view as
  a workaround for any tag operation.

## M4 — Calendar zoom levels

- Swipe up/down to change zoom (Day ↔ Week ↔ Month ↔ Year); swipe left/right to move
  through time at the current zoom level.
- Week: multi-tag chip/dot overview per day.
- Month/Year: single-tag heatmap with a tag picker/dropdown at the top.
- Tap a day above Day zoom → jump to that day at Day zoom.
- **Done when**: all four zoom levels are navigable by gesture and show the data shapes
  defined in `FEATURES.md`.

## M5 — Google Drive backup/restore

- Manual export of local data to Drive.
- Manual restore/import from a prior export.
- No live sync — this milestone is explicitly scoped to manual, one-shot operations.
- **Done when**: a fresh install can restore a previously exported backup and end up
  with an identical `Tag`/`TagInstance` dataset.

## M6 — Polish

- Material 3 theming pass, empty states (no tags yet, no instances today), error/loading
  states surfaced properly in each `UiState`.
- Accessibility pass (content descriptions, touch targets, contrast).
- Fill in test coverage gaps per `TESTING.md`.
- **Done when**: the app feels finished, not just functional.

## Explicitly deferred (not a v1 milestone)

Per `FEATURES.md` § Non-goals: stats/insights screen, filtering days by tag content,
multi-device sync, tag categories/hierarchies, notifications, sharing, widgets. Revisit
as a v1.1 milestone list once M0–M6 ship.
