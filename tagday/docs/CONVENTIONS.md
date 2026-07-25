# TagDay — Conventions

Naming, structure, and style rules. The goal is consistency a single developer (or
Claude CLI) can apply mechanically without re-deciding it per file.

## Kotlin style baseline

Standard [Kotlin official style guide](https://kotlinlang.org/docs/coding-conventions.html)
(4-space indent, no wildcard imports, trailing commas in multi-line constructs). No
custom linter config beyond Android Studio defaults for v1 — revisit if inconsistency
becomes a real problem, not preemptively.

## Package layout

See `ARCHITECTURE.md` § Package layout for the full tree. Rule of thumb: **package by
feature** under `ui/` (`ui/calendar/day`, `ui/tags`, …), **package by layer** under
`data/` (`data/local`, `data/repository`, `data/model`). Don't mix the two strategies
within the same subtree.

## Naming conventions

| Kind | Pattern | Example |
|---|---|---|
| Room entity | Singular noun, no suffix | `Tag`, `TagInstance` |
| Room DAO | `XyzDao` | `TagDao`, `TagInstanceDao` |
| Repository interface | `XyzRepository` | `TagRepository` |
| Repository impl | `XyzRepositoryImpl` | `TagRepositoryImpl` |
| Hilt module | `XyzModule` | `DatabaseModule`, `RepositoryModule` |
| ViewModel | `XyzViewModel` | `CalendarViewModel`, `TagsViewModel` |
| UI state | `XyzUiState` | `CalendarUiState`, `TagsUiState` |
| Screen composable | `XyzScreen` | `CalendarScreen`, `TagsScreen` |
| Stateless content composable | `XyzContent` | `DayContent` (see below) |
| UI-facing display model | `XyzDisplay*` / descriptive noun | `TagDisplayGroup` |

One top-level public declaration per file; filename matches it exactly
(`TagRepository.kt` contains only `TagRepository`).

## State holder pattern

- One `XyzUiState` immutable data class per screen, with sensible defaults so a
  `UiState()` is always constructible (e.g. `isLoading: Boolean = true`, empty lists,
  `null` selections) — no `lateinit`, no nullable `UiState?` at the collection site.
- ViewModel exposes exactly one:
  ```kotlin
  val uiState: StateFlow<CalendarUiState> = /* combine/map repository flows */
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())
  ```
- No sealed-class `Intent`/`Effect` dispatch system (see `ARCHITECTURE.md` § State
  management for the reasoning). One-shot events (snackbar after Drive export, nav
  trigger) use a small `Channel`/`SharedFlow` only where genuinely needed — not as a
  default pattern for every screen.

## Compose conventions

- **Screen composables** (`CalendarScreen`) take a `ViewModel` via `hiltViewModel()`,
  collect `uiState`, and delegate rendering to a **stateless content composable**
  (`DayContent`) that takes plain data + lambdas — this is what gets `@Preview`'d and
  unit-tested, without needing a real ViewModel or Hilt graph.
- **State hoisting**: stateless composables never hold their own business state;
  transient UI-only state (e.g. a text field's current draft value before submit) may
  use local `remember`/`mutableStateOf`.
- **Callback naming**: `onVerbNoun`, matching the event not the implementation —
  `onTagAdded: (tagId: Long) -> Unit`, not `onButtonClick`.
- **Modifier parameter**: every public composable takes `modifier: Modifier = Modifier`
  as the first optional parameter, and passes it to its root layout node.
- **Previews**: `XyzScreenPreview` / `XyzContentPreview`, using fixed sample `UiState`
  values — no live data, no ViewModel, in a preview.

## Coroutines & Flow

- Repositories expose `Flow` for reads, `suspend fun` for writes — no callbacks, no
  `LiveData`.
- Flows are combined/mapped in the ViewModel, converted to `StateFlow` via `stateIn`
  as shown above — never collected directly in a Composable body (`collectAsStateWithLifecycle()`
  is the one exception, at the Screen-level collection point only).
- No `GlobalScope`; all coroutines launched in `viewModelScope` (ViewModel) or a scope
  provided by Hilt (`@ApplicationScope` for anything outliving a ViewModel, e.g. Drive
  backup work).

## Resources

- Tag colors are **app data** (32-bit ARGB ints in Room), not Android color resources —
  don't add them to `colors.xml`.
- `strings.xml` keys are prefixed by screen: `day_add_tag_button`, `tags_rename_dialog_title`.
- Material 3 theme colors (`colors.xml`/`Theme.kt`) are the only colors that belong in
  resources — those are app chrome, not tag data.
- **Icons**: prefer `material-icons-core` (already a dependency) over adding
  `material-icons-extended` for the sake of one missing icon — the extended artifact
  bundles ~1000+ icons, and release builds currently have minification disabled
  (`app/build.gradle.kts`), so unused icons wouldn't be tree-shaken out. When the icon
  you need isn't in core, add a single hand-picked vector drawable to `res/drawable/`
  (e.g. `ic_label.xml`) and load it via `ImageVector.vectorResource(R.drawable.xyz)`
  instead.

## Comments & documentation

- Prefer self-documenting names over comments explaining *what* code does.
- KDoc only where behavior is non-obvious from the signature (e.g. "why cascade delete
  is safe here" belongs in code near the delete call, referencing `DATA_MODEL.md`).
- Don't restate what's already captured in `docs/` — link to it instead of duplicating.

## Open notes for later docs

- `TESTING.md` should define test class/method naming (e.g. `given...when...then` vs.
  backtick-named `fun \`adds tag to day\`()`), building on the fake-Repository approach
  from `ARCHITECTURE.md` § Testability.
