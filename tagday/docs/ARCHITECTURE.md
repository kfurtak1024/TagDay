# TagDay — Architecture

Follows Google's official app architecture guidance directly: unidirectional data flow,
single source of truth, clear UI/Data separation. See `CLAUDE.md` for the two hard rules
this file expands on (no domain layer until earned, single module until earned).

## Layers (v1)

```
┌─────────────────────────────────────────┐
│  UI layer                                │
│  Compose screens + ViewModels            │
│  StateFlow<UiState> out, event lambdas in│
└───────────────────┬───────────────────────┘
                    │
┌───────────────────▼───────────────────────┐
│  Data layer                              │
│  Repositories: CRUD + mapping raw Room    │
│  rows into display-ready models           │
│  (e.g. grouping/aggregation for Day view) │
└───────────────────┬───────────────────────┘
                    │
┌───────────────────▼───────────────────────┐
│  Local layer                             │
│  Room: TagDao, TagInstanceDao, entities   │
└─────────────────────────────────────────┘
```

**No domain layer yet.** This resolves the open item from `DATA_MODEL.md`: `Tag` /
`TagInstance` are *not* exposed to the UI directly. The Repository maps raw
`TagInstanceWithTag` rows into a small UI-facing model:

```kotlin
data class TagDisplayGroup(
    val tagId: Long,
    val tagName: String,
    val color: Int,
    val type: TagType,
    val instances: List<TagInstance>, // the raw instances backing this group
    val summary: String               // pre-formatted: "movie (2)", "movie: ★ (3)", etc.
)
```

This mapping is **data-layer plumbing, not domain logic** — it's a straightforward
transform with no branching business rules, so it lives in the Repository (per
`DATA_MODEL.md` § Grouping & aggregation) rather than justifying a UseCase. A domain
layer gets introduced later, per-feature, only once a ViewModel is genuinely
coordinating multiple repositories or holding non-trivial rules worth isolating and
unit-testing on their own — for example, if Drive backup/restore ends up needing
conflict resolution logic that spans both the local Repository and the Drive API,
that would be a reasonable first candidate for a `RestoreBackupUseCase`.

## Module strategy

Single Gradle module (`:app`), package-by-feature internally. Revisit only if build
times become painful or a second contributor needs isolated module ownership — not
before.

## Package layout

```
com.tagday/
├── TagDayApplication.kt
├── di/
│   ├── DatabaseModule.kt        # provides Room DB + DAOs
│   └── RepositoryModule.kt      # binds Repository interfaces to impls
├── data/
│   ├── local/
│   │   ├── TagDayDatabase.kt
│   │   ├── TagDao.kt
│   │   ├── TagInstanceDao.kt
│   │   └── entity/
│   │       ├── Tag.kt
│   │       └── TagInstance.kt
│   ├── repository/
│   │   ├── TagRepository.kt         # interface
│   │   ├── TagRepositoryImpl.kt
│   │   ├── TagInstanceRepository.kt # interface
│   │   └── TagInstanceRepositoryImpl.kt
│   └── model/
│       └── TagDisplayGroup.kt   # UI-facing display model (see above)
├── backup/
│   ├── DriveBackupService.kt
│   └── DriveRestoreService.kt
└── ui/
    ├── calendar/
    │   ├── day/       # DayScreen, DayViewModel
    │   ├── week/
    │   ├── month/
    │   └── year/
    ├── tags/          # TagsScreen, TagsViewModel
    ├── backup/        # settings-adjacent screen for manual export/import
    ├── navigation/     # NavHost, nav graph
    └── theme/         # Material 3 theme, color palette
```

## State management

Standard MVVM + unidirectional data flow, kept intentionally simple:

- Each screen has one `ViewModel` exposing a single `StateFlow<XyzUiState>` — no
  separate event/effect channel unless a screen genuinely needs one-shot events
  (e.g. "show snackbar after Drive export completes").
- ViewModels collect `Flow`s from Repositories (already mapped to display models) and
  map them into `UiState` via `stateIn` — Repositories are the single source of truth,
  ViewModels hold no independent state beyond UI concerns (loading flags, selected
  tag for heatmap, etc.).
- The UI sends intent back via plain lambda callbacks passed into Composables
  (`onTagAdded: (tagId) -> Unit`, `onTagCreated: (name, type, color) -> Unit`), not a
  formal event-bus/sealed-class dispatch system — that's more ceremony than a
  single-developer app like this needs.

## Dependency injection (Hilt)

- `DatabaseModule`: provides the `TagDayDatabase` singleton and its two DAOs.
- `RepositoryModule`: `@Binds` each Repository interface to its impl, scoped
  `@Singleton`.
- ViewModels use `@HiltViewModel` + constructor injection of Repository interfaces —
  never DAOs directly, so ViewModels stay decoupled from Room and remain testable with
  fake Repositories.

## Testability

Repository interfaces (not concrete Room-backed classes) are what ViewModels depend
on, so ViewModel unit tests use fake/in-memory Repository implementations with no
Room or Android framework dependency. Full test strategy lives in `TESTING.md`.

## Open notes for later docs

- `CONVENTIONS.md` should pin down naming for the `UiState` data classes and the
  callback-lambda naming pattern referenced above, so it's applied consistently
  across Day/Week/Month/Year/Tags screens.
- `BACKUP_SYNC.md` should clarify whether `DriveBackupService`/`DriveRestoreService`
  talk to Repositories or to the Room database directly — leaning Repositories, to
  keep Room fully encapsulated behind the data layer.
