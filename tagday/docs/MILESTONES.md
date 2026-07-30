# TagDay — Milestones

Vertical slices, not horizontal layers — each milestone produces something runnable and
demoable, not "all the ViewModels" then "all the UI." Order roughly follows dependency:
you can't build Week/Month/Year zoom sensibly before Day view works, can't do rich tag
types before Simple ones prove the plumbing out.

Each milestone below should pull in only as much of `UI_UX.md` as it needs — don't spec
screens you haven't reached yet.

> **Current position (read this before treating the list as a plan).** M0–M4 are done, and
> the project has since moved into the open-ended "feature complete" phase described in
> `CLAUDE.md` § Current status rather than working straight through M5 → M6. The
> milestones below are still an accurate record of what was built and a useful backlog for
> M5/M6, but they are no longer the running order, and the polish items in M6 have been
> partly picked off out of order (a11y content descriptions, empty states, and test
> coverage — see ADR-024 — are largely in place; app icon, signing and R8 are not).
> Note also that "v1" in this file predates the current usage: it's reserved now for an
> eventual public Play Store release, which isn't planned. Read it as "the first feature
> complete build".

## M0 — Project scaffolding

- Empty Compose app, Hilt set up (`TagDayApplication`, `DatabaseModule` stub).
- Room DB with `Tag` and `TagInstance` entities + DAOs from `DATA_MODEL.md`, `version = 1`.
- Navigation shell: Calendar / Tags, both showing placeholder content. (Shipped as
  top-bar icon navigation rather than a bottom bar — see `UI_UX.md`.)
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

- Type picker in the tag **creation** flow (Simple / Rated / Valued), chosen once and
  fixed thereafter — adding an existing tag never shows this picker.
- Rated: 1–5 star input, editable at any time, average+count aggregation.
- Valued: free-text input, per-value count aggregation, values listed.
- Individual instance editing/removal within a tag's group (tap the group, get a list).
- **Done when**: a Rated tag like `freediving: ★★★ (2)` and a Valued tag like
  `movie: [dune, terminator]` both work end-to-end, and each instance within a group is
  independently editable.

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

- App-managed backup to a hidden Drive app-data folder (`drive.appdata` scope), not a
  user-browsable file — see `BACKUP_SYNC.md` and ADR-015.
- Backup triggered manually ("Back up now") or automatically when the last backup is
  more than 24h stale, checked on app launch — periodic with a manual override, not a
  background sync service.
- Restore is auto-offered on a fresh install when a backup exists for the signed-in
  Google account (no file picker) and fully replaces local `Tag`/`TagInstance` data —
  no merge.
- Still not live sync — one-directional backup only, no multi-device sync.
- **Done when**: a fresh install, once signed in, is offered and can restore the most
  recent backup and end up with an identical `Tag`/`TagInstance` dataset to what the
  source device had at last backup.

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
