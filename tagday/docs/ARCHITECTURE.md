# TagDay — Architecture

Follows Google's official app architecture guidance directly: unidirectional data flow,
single source of truth, clear UI/Data separation. See `CLAUDE.md` for the two hard rules
this file expands on (no domain layer until earned, single module until earned).

## Layers (v1)

```
┌────────────────────────────────────────────┐
│  UI layer                                  │
│  Compose screens + ViewModels              │
│  StateFlow<UiState> out, event lambdas in  │
└─────────────────────┬──────────────────────┘
                      │
┌─────────────────────▼──────────────────────┐
│  Data layer                                │
│  Repositories: CRUD + mapping raw Room     │
│  rows into display-ready models            │
│  (e.g. grouping/aggregation for Day view)  │
└─────────────────────┬──────────────────────┘
                      │
┌─────────────────────▼──────────────────────┐
│  Local layer                               │
│  Room: TagDao, TagInstanceDao, entities    │
└────────────────────────────────────────────┘
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
dev.krfu.tagday/
├── TagDayApplication.kt          # @HiltAndroidApp
├── MainActivity.kt               # single activity: enableEdgeToEdge + setContent
├── di/
│   ├── DatabaseModule.kt         # provides Room DB + DAOs
│   └── RepositoryModule.kt       # binds Repository interfaces to impls
├── data/
│   ├── local/
│   │   ├── TagDayDatabase.kt
│   │   ├── TagDao.kt
│   │   ├── TagInstanceDao.kt
│   │   └── entity/
│   │       ├── Tag.kt, TagInstance.kt, TagType.kt
│   │       └── TagInstanceWithTag.kt      # @Embedded + @Relation join row
│   ├── repository/
│   │   ├── TagRepository.kt              # interface
│   │   ├── TagRepositoryImpl.kt
│   │   ├── TagInstanceRepository.kt      # interface
│   │   └── TagInstanceRepositoryImpl.kt
│   └── model/
│       ├── TagDisplayGroup.kt            # UI-facing display model (see above)
│       └── TagDisplayGroups.kt           # summarize() + excludingInstances(), shared
│                                         # by the repository and CalendarViewModel
└── ui/
    ├── calendar/                 # one screen for all four zoom levels
    │   ├── CalendarScreen.kt, CalendarViewModel.kt, CalendarContent.kt
    │   ├── CalendarUiState.kt, CalendarPeriodData.kt, PendingRemoval.kt
    │   ├── CalendarDateRanges.kt         # pure date math, unit-tested
    │   ├── ZoomLevel.kt, ZoomLevelLabel.kt, ZoomLevelPicker.kt
    │   ├── HeatmapDayCell.kt, TagPickerDropdown.kt   # shared Month/Year pieces
    │   ├── day/    # DayContent, InstanceListSheet, TagQuickEntryBar,
    │   │           # ParsedTagInput, StarInput, TagTypeLabel
    │   ├── week/   # WeekContent
    │   ├── month/  # MonthContent (+ MonthGrid, reused by year/)
    │   └── year/   # YearContent
    ├── tags/       # TagsScreen, TagsViewModel, TagsUiState, TagsContent,
    │               # RenameTagDialog, ColorPickerDialog
    ├── settings/   # SettingsScreen, SettingsContent — empty placeholder, see UI_UX.md
    ├── navigation/ # TagDayApp, TagDayNavHost, TagDayDestination
    └── theme/      # Theme.kt, Color.kt, Type.kt, TagPalette.kt, TemporalColors.kt
```

**Not built yet**, but planned homes rather than invented ones: a `backup/` package for
Drive backup/restore services and its UI entry point (M5, `BACKUP_SYNC.md`) — the UI half
may end up under `settings/` instead of its own `ui/backup/` package, still undecided.

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
  never DAOs directly, keeping ViewModels decoupled from Room.

## Testability

Unit tests cover the **Repository** layer, the **ViewModels**, and pure utility logic,
all off-device with fakes rather than mocks:

- Each `XyzRepositoryImpl` is tested against a small hand-written fake of its DAO
  interface (an anonymous `object : XyzDao` implementing only the methods exercised,
  throwing `NotImplementedError()` for the rest), exercising real aggregation/mapping
  logic with no Room or Android framework dependency.
- Each ViewModel is tested against fakes of the repository *interfaces* it's constructed
  with (`FakeTagRepository`, `FakeTagInstanceRepository`), plus a `MainDispatcherRule` so
  `viewModelScope` works in a JVM test. This reverses an earlier decision to leave
  ViewModels untested, which stopped holding once ADR-019/ADR-021/ADR-022 put real state
  machines in `CalendarViewModel` — see ADR-024.
- Pure, non-Compose logic (parsing, date-range math, heatmap buckets) gets plain
  function-level tests.

Composables are the deliberate gap — instrumented tests need a device. The
`XyzScreen`/`XyzContent` split (ADR-005) keeps that door open. Full conventions live in
`TESTING.md`.

## Open notes for later docs

- `BACKUP_SYNC.md` should clarify whether the not-yet-built
  `DriveBackupService`/`DriveRestoreService` talk to Repositories or to the Room database
  directly — leaning Repositories, to keep Room fully encapsulated behind the data layer.
  (The earlier note asking `CONVENTIONS.md` to pin down `UiState`/callback naming is
  resolved: it has a naming table and a state-holder section covering both.)
