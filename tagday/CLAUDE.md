# CLAUDE.md

Entry point for Claude CLI working on **TagDay**. Read this first; it links out to the
rest of `docs/` for anything beyond the essentials below.

## What this is

TagDay is a calendar-based diary for Android where days are annotated with **tags**
instead of free-text notes. Full feature spec: [`docs/FEATURES.md`](docs/FEATURES.md).

## Stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Architecture | MVVM, unidirectional data flow |
| Local persistence | Room |
| Async | Coroutines + Flow |
| DI | Hilt |
| Cloud backup | Google Drive, manual export/import only (no live sync in v1) |

## Hard rules

These are deliberate constraints, not gaps — don't "fix" them without discussing first:

- **No domain layer (UseCases) until it's actually earned.** Start with UI layer +
  Data layer only. Add a domain layer per-feature, only when a ViewModel is genuinely
  coordinating multiple repositories or holding non-trivial business logic worth isolating.
- **Single Gradle module until there's a real reason not to be.** Don't modularize
  preemptively. Revisit only if build times become painful or a second contributor needs
  isolated ownership of a module.
- **Tag type lives on the tag, not the instance.** Every tag has exactly one type
  (Simple/Rated/Valued), fixed at creation and immutable — don't reintroduce a `type`
  column on `TagInstance`, and don't build any "change this tag's type" UI or DAO
  method. To use a tag concept differently, the user creates a new tag. See
  `docs/FEATURES.md` § Tag types.

## Docs map

| File | Contents |
|---|---|
| [`docs/FEATURES.md`](docs/FEATURES.md) | Feature spec: tag types, views, resolved decisions |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Layering, module strategy, DI setup |
| [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md) | Room entities, relations, DAOs/queries |
| [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md) | Package layout, naming, Compose style rules |
| [`docs/UI_UX.md`](docs/UI_UX.md) | Screens, nav graph, theming |
| [`docs/BACKUP_SYNC.md`](docs/BACKUP_SYNC.md) | Drive backup format, trigger, restore — M5b, not yet implemented |
| [`docs/TESTING.md`](docs/TESTING.md) | What's tested, how, and deliberately not |
| [`docs/BUILD_RELEASE.md`](docs/BUILD_RELEASE.md) | Branching, CI, signing, Play Store release process |
| [`docs/MILESTONES.md`](docs/MILESTONES.md) | Vertical-slice build plan |
| [`docs/BACKLOG.md`](docs/BACKLOG.md) | Audited gaps (F1–F23), ordered by severity — open work |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | ADR log — why X over Y |

## Current status

M0-M4 are implemented. `CalendarScreen`/`CalendarViewModel` (`ui/calendar/`) drive all
four zoom levels — Day, Week, Month, Year — as one continuous calendar (not four
screens), with swipe gestures (vertical = zoom level, horizontal = move through time),
tap-a-day-to-jump-to-Day-zoom (Week/Month) or tap-a-month-to-jump-to-Month-zoom (Year,
per ADR-016), and a top-bar `ZoomLevelPicker` dropdown for jumping directly to a zoom
level, per ADR-012 and ADR-014. A conditional top-bar icon (`jumpToToday`, ADR-017)
returns to today from Day zoom only; Week/Month/Year instead highlight today's
row/cell/tile in place, sized to what each density has room for. Those three also carry a
`PeriodNavigationRow` above their content — the period's name plus `‹`/`›` and a tappable
label opening a date picker — since they otherwise never say which week/month/year is on
screen; Day is excluded, its header card already showing the date (ADR-033). Day zoom has
Simple, Rated, and Valued tags with full grouping/aggregation; tapping any group opens a bottom
sheet to edit it — per-instance rows for Rated/Valued, and for Simple a count-only stepper,
since how many times it applies is all a Simple tag has (ADR-031, superseding ADR-018's
"Simple isn't tappable"). Creating a Rated or Valued tag without a `:***`/`:value` seed
creates it with no instance and opens that sheet, rather than guessing an empty one
(ADR-021, ADR-031). Valued sheets also have an in-sheet "add value" row for
adding a new instance without leaving the sheet; Rated has no equivalent, staying
edit/remove-only (ADR-020). The sheet is a fixed 50% of screen height and only closes via
an explicit close button (not drag/back/scrim), and shows no timestamps (ADR-021,
ADR-025). Both types' panels now have the same shape and share one `ReorderableInstanceList`:
a row per instance (drag handle, editor, delete) plus an add-row below, sized to content up to
half the screen, past which the list scrolls (ADR-028). Rated's editor is five stars — whose
empty glyph is a hand-added drawable, because `material-icons-core`'s `Icons.Outlined.Star` is
a *solid* star and made every rating look like five (ADR-025) — and its add-row adds an
unrated instance when "+" is pressed with nothing picked (ADR-008).
Valued rows reorder by dragging a leading handle, with edge auto-scroll while dragging and
move-up/move-down kept as accessibility actions on that handle — `Modifier.draggable` with
`startDragImmediately`, which is what finally made a drag handle coexist with the list's
own scrolling after five earlier attempts failed (ADR-022 supersedes ADR-021's
move-buttons; read both before touching that gesture). Both
removal paths (that sheet's per-instance delete,
and each capsule's inline "x" for whole-group removal) are delay-delete: a snackbar with
an "Undo" action holds the actual deletion for a few seconds (`PendingRemoval`, ADR-019).
A small Past/Today/Future label sits on the header card (`TemporalColors`, ADR-017);
adding a tag uses an always-visible quick-entry bar (`TagQuickEntryBar`) with a
Simple/Rated/Valued segmented-button type picker that defaults to Simple, syntax
shorthands that move that selection rather than bypass it, and auto-assigned color
(ADR-009, ADR-029). Week is a multi-tag dot
overview; Month/Year are a single-tag heatmap (instance count only, `TagPickerDropdown`).
`TagsScreen` (list/filter/rename/recolor/delete, reached via the Calendar screen's
top-right icon) is management-only, with an in-house HSV color picker (ADR-011) and a
scrollbar on its list — the shared `ui/components/VerticalScrollbar`, also used by the
instance sheet, which draws only while the content overflows (ADR-030). A
`SettingsScreen` shell also exists (reached via a second top-right icon, next to Tags) —
empty placeholder for now, scaffolded ahead of any specific content.

Instance display order is owned by `TagInstanceDao`'s `ORDER BY sortOrder`, not by each
consumer sorting for itself (ADR-023) — that's what makes a manual reorder show up in the
day capsule as well as in the sheet. Day capsules are ~32dp tall and their ✕ opts out of
Compose's minimum-touch-target inflation, which otherwise overlapped the capsule text
and removed Simple tags on a body tap (ADR-026, ADR-027). Unit tests cover the
repositories, both ViewModels (ADR-024) and the pure date/parsing utilities; Composables
and gesture code are the
deliberate gap, since instrumented tests need a device this environment doesn't have. See
`docs/TESTING.md`.

**Tag names are constrained** to lowercase letters with single `-` separators, starting and
ending with a letter (`data/model/TagName`, ADR-028). Both naming entry points — the
quick-entry bar and the rename dialog — normalize as the user types and refuse to save a
non-conforming name; only the part before a `:` is normalized, so values stay free text.
Names predating the rule still work and are only brought into line on rename.

**Before changing the Room schema, read `docs/DATA_MODEL.md` § Schema history &
migrations.** The database still uses `fallbackToDestructiveMigration(dropAllTables =
true)`, so the next `version` bump wipes real user data unless a real `Migration` is
written first. Schemas are now exported to `app/schemas/` and committed (BACKLOG F1), which
is what makes writing that `Migration` possible — it doesn't remove the need for one, and it
doesn't mean the schema is settled.

Beyond M0-M4, the project is now in an open-ended **"feature complete"** phase (not
labeled v1 — that term is reserved for an eventual public Play Store release, which
isn't currently planned) rather than working straight through the original M5/M6
milestone list. Polish items (app icon, signing, R8) are deliberately deferred to once
feature-complete is reached. In-progress/planned for this phase, beyond what's landed: a
local JSON export — now scoped as **M5a** and sequenced ahead of the Drive transport,
which becomes the conditional M5b (ADR-032) — and a UX rework of the quick-entry add-tag
control. See `docs/MILESTONES.md` for the M5a/M5b and M6 (polish) milestone content,
still relevant but no longer strictly next-in-line.

Running alongside that is `docs/BACKLOG.md` — gaps found by auditing the code as built
(2026-08-03), numbered F1–F23 and ordered by severity, from the destructive-migration/
auto-backup data-loss pair down to lint and landscape. It's the open-defect list; work it
in tiers and tick items off in place.
