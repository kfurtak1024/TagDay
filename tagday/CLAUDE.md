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
- **Tag type lives on the instance, not the tag.** The same tag name can be used as
  Simple, Rated, and Valued simultaneously — don't reintroduce a `type` field on the
  `Tag` entity itself. See `docs/FEATURES.md` § Tag types.

## Docs map

| File | Contents |
|---|---|
| [`docs/FEATURES.md`](docs/FEATURES.md) | Feature spec: tag types, views, resolved decisions |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Layering, module strategy, DI setup |
| [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md) | Room entities, relations, DAOs/queries |
| [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md) | Package layout, naming, Compose style rules |
| [`docs/UI_UX.md`](docs/UI_UX.md) | Screens, nav graph, theming |
| [`docs/BACKUP_SYNC.md`](docs/BACKUP_SYNC.md) | Drive backup format, trigger, restore, conflicts |
| [`docs/TESTING.md`](docs/TESTING.md) | Test strategy per layer |
| [`docs/MILESTONES.md`](docs/MILESTONES.md) | Vertical-slice build plan |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | ADR log — why X over Y |

## Current status

Documentation phase. `FEATURES.md` is complete. Everything else is not yet written.

**Not started yet.**
