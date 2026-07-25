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
| [`docs/BACKUP_SYNC.md`](docs/BACKUP_SYNC.md) | Drive backup format, trigger, restore — M5, not yet implemented |
| [`docs/TESTING.md`](docs/TESTING.md) | What's tested, how, and deliberately not |
| [`docs/BUILD_RELEASE.md`](docs/BUILD_RELEASE.md) | Branching, CI, signing, Play Store release process |
| [`docs/MILESTONES.md`](docs/MILESTONES.md) | Vertical-slice build plan |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | ADR log — why X over Y |

## Current status

M0-M4 are implemented. `CalendarScreen`/`CalendarViewModel` (`ui/calendar/`) drive all
four zoom levels — Day, Week, Month, Year — as one continuous calendar (not four
screens), with swipe gestures (vertical = zoom level, horizontal = move through time),
tap-a-day-to-jump-to-Day-zoom, and a top-bar `ZoomLevelPicker` dropdown for jumping
directly to a zoom level, per ADR-012 and ADR-014. Day zoom has Simple, Rated, and Valued
tags with full grouping/aggregation and per-instance editing; adding a tag uses an
always-visible quick-entry bar (`TagQuickEntryBar`) with syntax-based type inference and
auto-assigned color (ADR-009). Week is a multi-tag dot overview; Month/Year are a
single-tag heatmap (instance count only, `TagPickerDropdown`). `TagsScreen`
(list/filter/rename/recolor/delete, reached via the Calendar screen's top-right icon) is
management-only, with an in-house HSV color picker (ADR-011). Next up is M5 (Google
Drive backup/restore) — see `docs/MILESTONES.md`.
