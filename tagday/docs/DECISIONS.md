# TagDay — Decisions (ADR log)

Short, dated entries: what was decided, what the alternative was, and why. Not a design
doc — if the reasoning needs more than a few paragraphs, it belongs in the relevant
`docs/` file instead, with a link back here.

---

## ADR-001: Tag type lives on the instance, not the tag

> **Superseded by ADR-007.** Kept here for history — do not follow this decision.

**Decision:** `Tag` has no `type` column. Type (Simple/Rated/Valued) is chosen per
`TagInstance`, so the same tag name can be used as all three types simultaneously.

**Alternative considered:** Type as a fixed property of `Tag`, set once at creation.

**Why:** The core feature requirement is that a tag like `movie` can be used as a plain
presence marker, a star rating, and a free-text value — sometimes all on the same day.
Fixing type on the tag would make that impossible without creating duplicate tags per
type (`movie`, `movie-rated`, `movie-valued`), which defeats the point of a shared
repository. See `FEATURES.md` § Tag types.

---

## ADR-002: No domain layer in v1

**Decision:** UI layer talks directly to Repositories. No UseCase/Interactor layer.

**Alternative considered:** Full UI → Domain → Data layering from the start, per some
versions of Google's architecture guidance.

**Why:** Current business logic (CRUD + grouping/aggregation) is straightforward enough
to live in the Repository as data-layer mapping, not domain rules. Introducing a domain
layer now would mean UseCase classes that just delegate to a single Repository method —
ceremony without benefit. Revisit per-feature, the first time a ViewModel genuinely
coordinates multiple Repositories or holds non-trivial rules worth isolating (candidate:
Drive restore conflict handling). See `ARCHITECTURE.md` § Layers.

---

## ADR-003: Single Gradle module

**Decision:** Everything lives in `:app`, package-by-feature internally.

**Alternative considered:** Multi-module split (e.g. `:data`, `:ui`, per-feature modules).

**Why:** Single-developer project; modularization solves build-time and team-ownership
problems TagDay doesn't have yet. Revisit only if build times become painful or a second
contributor needs isolated module ownership. See `ARCHITECTURE.md` § Module strategy.

---

## ADR-004: Grouping/aggregation logic in Kotlin, not SQL

**Decision:** DAOs return raw joined rows (`TagInstanceWithTag`); the Repository groups
by type and computes count/average/value-list in Kotlin.

**Alternative considered:** Equivalent `GROUP BY`/aggregate SQL queries per zoom level.

**Why:** The instance count per day is always small, so the performance case for SQL
aggregation doesn't apply. Kotlin aggregation is easier to read, unit test, and reuse
across Day/Week/Month/Year views than several near-duplicate SQL queries. See
`DATA_MODEL.md` § Grouping & aggregation.

---

## ADR-005: Screen/Content composable split

**Decision:** Every screen is two composables — `XyzScreen` (collects ViewModel state,
no logic) and `XyzContent` (stateless, takes plain data + lambdas, is what gets
previewed/tested).

**Alternative considered:** One composable per screen, injecting the ViewModel directly.

**Why:** `XyzContent` can be previewed and tested with fixed sample data, with no Hilt
graph or real ViewModel required. The cost is one extra small composable per screen.
See `CONVENTIONS.md` § Compose conventions.

---

## ADR-006: Date stored as epoch day (Int), not a date string

**Decision:** `TagInstance.date` is an `Int` epoch day, converted to/from `LocalDate` at
the repository boundary via a Room `TypeConverter`.

**Alternative considered:** ISO date string (`"2026-07-24"`).

**Why:** Epoch day is a plain integer — cheap to index and compare, and `BETWEEN` range
queries (used by every zoom level) are simple integer comparisons rather than string
comparisons that happen to sort correctly. See `DATA_MODEL.md` § `TagInstance`.

---

## ADR-007: Tag type moved back onto `Tag`; supersedes ADR-001

**Decision:** `Tag` gets a `type` column (`SIMPLE` / `RATED` / `VALUED`), fixed at
creation and immutable. `TagInstance` no longer has a `type` column. A tag name can no
longer be reused across multiple types — one tag, one type, permanently.

**Alternative considered:** Keep type on the instance (ADR-001's approach), allowing
`movie`, `movie: ★★★`, and `movie: dune` to coexist as the same tag used three ways.

**Why:** This reverses ADR-001 by direct request — the earlier "same name, mixed types"
flexibility wasn't wanted after all. Type-on-tag is also the simpler model: no per-day
type-grouping logic, no question of what a type change does to historical instances,
and each `Tag` unambiguously has one meaning. The tradeoff: reusing a tag concept
differently now requires a new tag with a different name (e.g. `movie` and
`movie-rating` as separate tags) rather than layering types under one name.

**Migration note:** This landed after M1 was already implemented against ADR-001's
schema. Since there was no real user data at stake yet, the schema bump used
`fallbackToDestructiveMigration()` rather than a written `Migration` — see
`DATA_MODEL.md` § Open notes. Once the app has real installs, schema changes of this
kind need an actual migration path instead.

---

## ADR-008: An all-unrated Rated group summarizes like Simple

**Decision:** When a Rated tag's group for a day has zero rated instances (every
`TagInstance.rating` is `null`), its display summary falls back to the same bare
`name`/`name (count)` format Simple uses, instead of attempting a star average.

**Alternative considered:** Show a placeholder star state (e.g. `☆☆☆☆☆` or "not rated
yet") for an all-unrated group.

**Why:** `DATA_MODEL.md` documents that a Rated instance can exist unrated and be rated
later (`FEATURES.md` § Resolved decisions: rating can be set at any time, not just at
creation) — so an all-unrated group is a normal, expected state, not an edge case to
special-case away. `mapNotNull { it.rating }.average()` is `NaN` for an empty list, and
`NaN.roundToInt()` throws, so this needed a real fallback rather than being ignored. A
placeholder star state would need new UI just for a transient state; reusing Simple's
existing bare-name format needed no new code and reads naturally ("freediving" — no
rating yet — rather than "freediving: ☆☆☆☆☆"). See `TagInstanceRepositoryImpl.summarize()`.

---

## ADR-009: Quick-entry bar replaces the modal add-tag sheet

**Decision:** The FAB + modal `AddTagSheet` bottom sheet is replaced with a persistent
text field pinned to the bottom of the Day screen (`TagQuickEntryBar`), always visible,
with a "+" submit button inside it. Existing-tag suggestions appear above the field as
the user types. Tag type is inferred from input syntax instead of always requiring an
explicit picker: `name:***` auto-creates a Rated tag seeded with that star rating
(clamped to 5); `name:text` auto-creates a Valued tag seeded with that value; a plain
`name` with no existing match falls back to an explicit Simple/Rated/Valued chip row.
Color is auto-assigned by cycling through `TagPalette`, removing manual color choice
from tag creation entirely (deferred to M3's Tags view, alongside renaming).

**Alternative considered:** Keep `AddTagSheet` as-is and layer the colon-syntax parsing
onto its existing search field as an optional shortcut, leaving the modal interaction
model and manual color palette in place.

**Why:** Direct user request for a lighter-weight, always-available interaction — a
persistent field removes a modal hop for the single most frequent action in the app,
and syntax inference removes an extra tap in the two cases where the intended type is
already unambiguous from what was typed. Manual color choice was cut rather than
squeezed into the inline flow because there's no natural place for a palette next to a
one-line field; recoloring already belongs to M3's Tags view, so tags created via
quick-entry are simply auto-colored until then. See `UI_UX.md` § Quick-entry tag bar.

---

## ADR-010: Custom vector drawable for icons missing from `material-icons-core`

**Decision:** The Day screen's "open Tags" button needed a tag/label-shaped icon, which
doesn't exist in `material-icons-core` (the only icon dependency this project has — a
~48-icon subset covering basics like Edit, Settings, List, Star). Rather than adding
`material-icons-extended`, a single hand-added vector drawable (`res/drawable/ic_label.xml`, the standard Material "label" glyph) was added and loaded via
`ImageVector.vectorResource(...)`.

**Alternative considered:** Add `androidx.compose.material:material-icons-extended` and
use `Icons.Filled.Sell` directly — less code, one dependency line.

**Why:** `material-icons-extended` bundles ~1000+ icon composables; `app/build.gradle.kts` currently disables release-build optimization
(`buildTypes.release.optimization.enable = false`), so R8 wouldn't tree-shake the
unused ~999 icons out of a release build, inflating APK size for a single icon's worth
of value. A one-off vector drawable costs one small XML file and zero new dependencies.
This is now the house rule for any future icon missing from core — see
`CONVENTIONS.md` § Resources.

---

## ADR-011: In-house HSV color picker, no third-party library

**Decision:** M3's custom color picker (`ColorPickerDialog.kt`) is hand-built: a
rainbow-gradient hue `Slider` plus a draggable saturation/value square (`Box` with two
stacked gradients + a manual pointer-gesture handler), converting to/from the stored
ARGB `Int` via `android.graphics.Color.colorToHSV`/`HSVToColor` rather than hand-written
color math.

**Alternative considered:** Add a third-party Compose color-picker library.

**Why:** Same reasoning as ADR-010 — this is one self-contained piece of UI, not
worth a new dependency for. `android.graphics.Color`'s HSV conversion utilities avoid
the risk of hand-rolled RGB↔HSV math being subtly wrong, without needing an external
library. One implementation gotcha worth recording: the SV square's drag handler uses a
manual `awaitEachGesture { awaitFirstDown(); ...; drag(...) }` rather than
`detectDragGestures` — the latter only fires after touch-slop movement, so a plain tap
(no drag) would silently do nothing, breaking "tap to jump to a color."

---

## ADR-012: One `CalendarViewModel` for all four zoom levels; plain drag detection, not `Pager`

**Decision:** M4 introduces `CalendarViewModel`/`CalendarScreen`/`CalendarContent`
(replacing `DayViewModel`/`DayScreen`) as the single owner of `zoomLevel`, `focusedDate`,
and `selectedTagId` (the heatmap tag picker) for all four zoom levels — Week/Month/Year
get plain stateless `XyzContent` composables, not their own ViewModels. Swipe navigation
is one `Modifier.pointerInput { detectDragGestures(...) }` per gesture, accumulating
total drag delta and picking the dominant axis on release, rather than a vertical
`Pager(pageCount = 4)` (zoom) nesting horizontal per-zoom `Pager`s (time).

**Alternatives considered:** (1) A ViewModel per zoom level, coordinated somehow so
switching zoom/date stays consistent across them. (2) Nested `Pager`s for the swipe
gestures, leaning on their built-in fling/snap physics.

**Why:** `FEATURES.md` frames Day/Week/Month/Year as "one continuous calendar... not
four separate screens" — one ViewModel matches that directly, and avoids the real
complexity of keeping several Hilt-scoped ViewModels' state in sync, which "tap a day →
jump to Day zoom" would otherwise require (it must change zoom level *and* focused date
together; `ARCHITECTURE.md` already licensed "selected tag for heatmap" as legitimate
ViewModel-held state before this milestone existed). Nested `Pager`s were rejected for
the same reason: each `PagerState` would need two-way sync with the shared
`focusedDate`/`zoomLevel`, which is exactly the coordination problem jump-to-day makes
painful. `detectDragGestures`'s touch-slop-before-firing behavior is *correct* here
(the opposite of ADR-011's color-picker problem) — requiring real movement before
triggering is exactly what distinguishes a swipe from a tap. One layout gotcha this
produced: the vertical (zoom) gesture lives only on a dedicated, always-present,
non-scrolling swipe-handle strip, never on the scrollable body (Day's tag list, Year's
stacked month grids) — Compose resolves a child scrollable's consumption before an
ancestor drag detector sees the delta, so a vertical detector on the body would silently
lose to the scroll. See `UI_UX.md` § Calendar screen.

**Amendment 1**: the first version of that strip was swipe-only — a thin bar with a
subtle drag-handle graphic and no other affordance. In practice this was too easy to
miss entirely (small hit target, nothing telling you it was interactive or what zoom
level you were in), confirmed once real usage was possible. The strip was changed to
show the current zoom level's name with real tappable up/down chevron buttons (48dp,
meeting the standard minimum touch target) — the swipe still worked as a shortcut, it
was just no longer the only way to reach Week/Month/Year.

**Amendment 2**: by explicit request, the dedicated strip (chevrons, label, and the
reserved space it took) was removed entirely in favor of a plain swipe-only interaction
again — but merged into the *same* whole-body drag detector as the horizontal
time-navigation gesture, rather than reviving the old isolated-strip approach. This
knowingly reintroduced the scroll-conflict tradeoff the original strip was built to
avoid (a vertical swipe starting on Day's tag list or Year's stacked month grids scrolled
instead of changing zoom, since Compose gives the scrollable child priority) — accepted
at the time as a reasonable cost for a simpler, uncluttered screen.

**Amendment 3**: that tradeoff turned out not to be necessary — by explicit request, the
vertical gesture was rebuilt on `Modifier.scrollable`/`rememberScrollableState` instead
of a raw `pointerInput`/`detectDragGestures` accumulator. `Modifier.scrollable`
automatically installs nested-scroll participation (confirmed by reading
`ScrollableNestedScrollConnection` in Compose Foundation's source: it only implements
`onPostScroll`/`onPostFling`, never `onPreScroll`), so a `scrollable` ancestor only ever
receives the delta a descendant `scrollable` (Day's `verticalScroll`, Year's stacked
grids) didn't need — the descendant still scrolls normally first. This gives the
vertical zoom-swipe the *same activation zone* as the horizontal time-swipe on every
zoom level, including Day and Year, without the earlier tradeoff. Horizontal stays a
plain `detectDragGestures` (now `orientationLock`ed to `Horizontal` so it no longer
competes with the vertical `scrollable` for a given drag) since nothing scrolls
horizontally and there's no equivalent descendant to negotiate with. See `UI_UX.md`
§ Calendar screen.

---

## ADR-013: Trunk-based branching + build/test-only CI for now

**Decision:** Single `main` branch with short-lived PR feature branches and SemVer git
tags for releases — no GitFlow. CI (`.github/workflows/ci.yml`, GitHub Actions) runs
unit tests + a debug build on every push/PR. Signing, release builds, and any Play
Store upload stay fully manual for now.

**Alternatives considered:** (1) GitFlow-style `develop`/`release/*`/`hotfix/*`
branches. (2) CI that also builds and signs a release `.aab` on tag push, as a
downloadable artifact. (3) CI that fully automates upload to Play Console via the Play
Developer API.

**Why:** This is a solo-developer project working toward its first Play Store release
— GitFlow's parallel-branch coordination solves a problem (multiple contributors,
overlapping release trains) that doesn't exist here; trunk-based is simpler and
sufficient, revisit only if a second regular contributor joins. Automating signing or
Play Store upload before doing a single release by hand would mean debugging
keystore/Play Console issues through CI logs instead of a local terminal — the manual
path is where mistakes are cheap and visible the first time through. Automation is
worth adding once that process itself is trusted and repeatable, starting with signing
(see `BUILD_RELEASE.md` § Open notes) before ever considering automating the Play Store
upload itself.

---

## ADR-014: Top-bar zoom-level picker, alongside the swipe gesture

**Decision:** `ZoomLevelPicker` (`ui/calendar/ZoomLevelPicker.kt`) sits in the Calendar
screen's `TopAppBar` `title` slot (previously empty) — a `TextButton` showing the
current zoom level's name plus a dropdown chevron, opening a `DropdownMenu` with
Day/Week/Month/Year (leading checkmark on the current entry). Picking an entry calls a
new `CalendarViewModel.setZoom(ZoomLevel)`, an absolute setter added alongside the
existing relative `stepZoom(direction: Int)` that the swipe gesture uses — direct-jump
selection shouldn't be synthesized as N relative steps. Like the swipe gesture, it only
changes `zoomLevel`; `focusedDate` is untouched.

**Alternatives considered:** (1) `SingleChoiceSegmentedButtonRow` with all four labels
visible. (2) A `TabRow`. (3) An icon-only trigger (no visible current-zoom label).

**Why:** Swipe stays the primary way to change zoom (per `FEATURES.md`'s "one
continuous calendar" framing), but has no visible affordance telling a first-time user
it exists, nor a way to jump straight from Day to Year without three intermediate
steps. A corner control fixes both without adding a new gesture zone. Segmented buttons
and tabs were rejected on width: four text segments ("Day/Week/Month/Year") consume
nearly the full top-bar width on a phone, crowding the existing trailing Tags
`IconButton` below the 48dp minimum touch target — and icon-only segments have no
intuitive per-zoom-level icon to fall back on. Tabs additionally imply peer,
independently-swipeable destinations, which fights the "one continuous calendar, viewed
at different granularities" model this app documents elsewhere. An icon-only trigger
was rejected because the current zoom level is exactly the kind of state worth
surfacing at a glance, for free, without opening anything.

This is *not* a reversal of ADR-012's Amendment 2, which removed a dedicated
zoom-*strip* (label + chevrons reserving space in the swipe/content body, competing
with Day's tag list and Year's stacked grids for the same gesture zone). This control
lives in the top bar's chrome instead, in a slot that was sitting empty, and is
additive to the swipe rather than a rebuild of what Amendment 2 removed.

---

## ADR-015: M5 backup — Drive REST API, hidden `appdata` scope, periodic + manual trigger

**Decision:** M5 backs up to a hidden Google Drive app-data folder via the Drive REST
API (`drive.appdata` scope, Sign in with Google through Credential Manager), not a
user-visible file. Backup runs on a manual "Back up now" action or automatically when
the last backup is more than 24h stale (checked on app launch) — periodic with a
manual override, not a background sync service. Restore is auto-offered on a fresh
install (backup detected for the signed-in account, local data still empty) rather than
picked via a file browser, and fully replaces local data rather than merging. See
`BACKUP_SYNC.md` for the full shape.

**Alternatives considered:** (1) Android Auto Backup (`android:allowBackup` +
`BackupAgent`/XML rules) — the OS's own built-in mechanism, essentially free in code
but fully OS-triggered and invisible, with no way to expose a manual backup/restore
action or let the user choose which backup to restore. (2) Drive REST API with
`drive.file` scope, storing a visible file in "My Drive", triggered only manually
(the original M5 wording). (3) True live/real-time multi-device sync (e.g. backed by
Firestore instead of Drive files, with incremental change tracking and conflict
resolution).

**Why:** (1) was rejected outright — it cannot fulfill "manual restore, user picks
when," which is the actual feature being built, regardless of how little code it would
take. (3) was rejected as out of scope: `FEATURES.md` explicitly lists multi-device
live sync as a v1 non-goal, and building a real sync engine (incremental diffing,
conflict resolution) is a fundamentally different and much larger system than a backup
mechanism — worth revisiting only if v1.1 scope ever calls for actual multi-device use.
That leaves a choice between (2) and the periodic-hidden approach actually chosen,
which mirrors how WhatsApp does Drive backup on Android: hidden storage (so the backup
file never clutters "My Drive" or needs the user to manage it directly) and a
periodic-with-manual-override trigger (so backups happen without the user having to
remember, while still offering an explicit "back up now" for peace of mind before doing
something risky). `drive.appdata` over `drive.file` trades away user-visible file
management (the user can no longer see/move/share the backup file directly) for a
cleaner "it just works" model — an acceptable trade for a dataset this small (a JSON
dump of two tables, not media), where the value of a visible file is low and the
annoyance of Drive clutter is the more likely user-facing outcome. Note: WhatsApp's Drive
backups additionally don't count against the user's Drive storage quota — that's a
specific Google↔Meta business arrangement, not something `drive.appdata` grants by
itself; irrelevant here regardless, since TagDay's backup size is negligible. The
periodic trigger is a deliberate, scoped exception to `FEATURES.md`'s "no live sync"
non-goal — staleness-triggered snapshotting is not real-time multi-device sync, and the
line is worth keeping explicit so this decision isn't later read as reopening that
non-goal.
