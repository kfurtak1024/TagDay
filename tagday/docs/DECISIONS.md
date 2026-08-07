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
`material-icons-extended`, a single hand-added vector drawable
(`res/drawable/ic_label.xml`, the standard Material "label" glyph) was added and loaded
via `ImageVector.vectorResource(...)`.

**Alternative considered:** Add `androidx.compose.material:material-icons-extended` and
use `Icons.Filled.Sell` directly — less code, one dependency line.

**Why:** `material-icons-extended` bundles ~1000+ icon composables;
`app/build.gradle.kts` currently disables release-build optimization
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

---

## ADR-016: Year zoom becomes a fixed, no-scroll 12-month grid; tap jumps to Month, not Day

**Decision:** `YearContent` renders all 12 months at once as a fixed 4-row × 3-column
grid, sized entirely with nested `weight()` modifiers rather than `verticalScroll` +
`aspectRatio`, replacing the previous stacked-and-scrollable list of full-size
`MonthGrid`s. Each month tile is a compact 6-week × 7-day grid of plain shaded cells —
no day-of-month numbers, no per-day tap target. The whole tile is clickable instead,
calling a new `CalendarViewModel.jumpToMonth(date)` (mirroring `jumpToDay`) that jumps
to Month zoom for that month rather than straight to Day zoom.

**Alternatives considered:** (1) Keep the stacked-and-scrollable list of full-size
month grids (status quo). (2) Fixed one-screen grid, but keep per-day tap-to-Day-zoom
by shrinking `HeatmapDayCell` (day numbers and all) down to fit.

**Why:** The goal was an actual heat-map — see everything at a glance, GitHub-
contributions style — which requires all 12 months visible without scrolling. That
alone rules out (1). Sizing via nested `weight()` rather than `aspectRatio` was chosen
because `aspectRatio` derives height from width and has no way to guarantee the result
fits the remaining vertical space on an arbitrary device — it would need a `verticalScroll`
fallback for the cases where it doesn't, defeating the point. Weight-based sizing lets
Compose's constraint system divide the exact available space top-down, guaranteeing a
fit by construction on any screen size, with no measurement math of our own.

(2) was rejected on touch-target grounds: fitting 12 months on one screen puts each day
cell at roughly 15–18dp square on a typical phone — well under the 48dp minimum touch
target, and day-of-month numbers are illegible at that size regardless. Rather than
ship a heat-map with barely-tappable, barely-readable day cells, the day-level detail
was dropped entirely from Year zoom (pure color, no numbers) and the tap target was
moved up a level: the whole month tile (~100dp+, comfortably above the minimum) jumps
to Month zoom, where `HeatmapDayCell` is already full-size, numbered, and individually
tappable. This makes Year→Day a two-step drill-down instead of one, a deliberate
trade-off of Week/Month's established "tap a day, land on Day zoom" convenience for a
control that's actually usable at Year's density. `UI_UX.md` was updated accordingly —
Week and Month keep the original one-step rule; Year is now the documented exception.

---

## ADR-017: Jump-to-today scoped to Day zoom; today-indicators tuned per zoom density

**Decision:** Two related additions, both about locating "today" while browsing:

1. A jump-to-today `IconButton` in the shared top bar's `title` slot, next to
   `ZoomLevelPicker` rather than in the trailing `actions` group — rendered only when
   `zoomLevel == DAY && focusedDate != LocalDate.now()`. Uses a bullseye icon
   (`R.drawable.ic_target`, a custom vector — two concentric rings around a solid
   center dot, no crosshair ticks). It calls a new `CalendarViewModel.jumpToToday()` (sets
   `focusedDate = LocalDate.now()`, leaves `zoomLevel` untouched — same
   minimal-mutation contract as `jumpToDay`/`stepTime`). Week/Month/Year get no
   equivalent button.
2. A visual "today" indicator at every zoom level instead, each styled to fit what that
   density already has room for: Day zoom gets a small `TemporalLabel` pill in the
   header card (see below); Week zoom fills the day-of-month number's background with
   `colorScheme.primary` (Google Calendar-style); Month zoom draws a `colorScheme.primary`
   border **ring** around the day cell (a stroke, not a fill, since `HeatmapDayCell`'s
   background already carries the heat-shading signal — the two need to read as
   independent layers); Year zoom draws that same border around the **whole month
   tile**, not an individual day cell.

Separately, Day zoom also gets a **temporal label**: a small rounded pill below the
weekday name reading "Past"/"Today"/"Future" (`date` vs. `LocalDate.now()`), colored
gold/`TemporalColors.PAST`, green/`TemporalColors.TODAY`, violet/`TemporalColors.FUTURE`
respectively — fixed ARGB ints kept outside the Material color scheme, same reasoning as
`TagPalette` (ADR-011's neighborhood): dynamic color varies per device wallpaper, which
would make a semantic past/today/future signal unreliable depending on the user's system
theme. The label's text is folded into the header `Card`'s existing merged
`contentDescription` rather than left as an unannounced fourth visual-only fragment.

**Alternatives considered:** (1) A jump-to-today button visible at every zoom level,
consistent with Tags/Settings. (2) A per-day-cell today marker at Year zoom, matching
Month's ring treatment exactly. (3) `Icons.Default.DateRange` (already available in
`material-icons-core`) instead of a new custom vector for the jump-to-today icon. (4)
Placing the jump-to-today button in the trailing `actions` group alongside Tags/Settings
(where it was first built) rather than in the `title` slot next to `ZoomLevelPicker`.
(5) A calendar-with-highlighted-date glyph (the standard Material "today" icon) instead
of a bullseye. (6) A crosshair-style bullseye (the standard Material "gps_fixed" glyph,
with tick marks radiating past the ring) instead of a plain two-ring-plus-dot bullseye.

**Why:** (1) was rejected per direct product direction: "today" is fundamentally a
Day-zoom concept in this app's mental model — Week/Month/Year are overview/browsing
modes where the *destination* of "jump to today" (a specific day, viewed in full detail)
doesn't exist yet. Those three zooms get a highlight instead, so today's position is
always visible while browsing, and the existing tap-to-drill-down convention (tap a
day/month → jump toward Day zoom, per `FEATURES.md`/ADR-016) is what actually gets you
there — jump-to-today only needs to exist once you're already at Day zoom looking at
some other date. This also keeps the top bar's chrome from growing on the three zooms
that already carry the most content (multi-day rows, grids).

(2) was rejected for the same touch-target reasoning ADR-016 already established for
Year: cells there are ~15–18dp, well under the 48dp minimum, so a per-cell ring would be
both hard to see and reinforce a false affordance (Year has no per-day tap target at
all — see ADR-016). A whole-tile border matches the existing whole-tile tap target and
scales with it.

(3) was rejected for consistency with ADR-010's precedent: `DateRange` reads as
"calendar/date-range" rather than "today" specifically, and the project's established
pattern for a missing-but-needed glyph is a one-off hand-added vector
(`res/drawable/ic_target.xml`, mirroring `ic_label.xml`'s format) rather than reaching
for a Material icon that's merely adjacent in meaning, or pulling in
`material-icons-extended` for it.

(4) was reconsidered and reversed after initial implementation, per direct product
feedback: `actions` groups navigation destinations (Tags, Settings — screens you go
*to*), while jump-to-today doesn't navigate anywhere, it changes which slice of time the
*current* screen shows — the same category of control as `ZoomLevelPicker` right next to
it, not a peer of Tags/Settings. Grouping it with the zoom picker also means its
conditional appearance never shifts Tags/Settings' position, which placing it at the end
of `actions` had already achieved incidentally but for the wrong conceptual reason.

(5) was reconsidered and reversed alongside (4): a calendar glyph restates "this is a
date-related control," which is already obvious from context (it sits right next to the
zoom picker). A bullseye (`ic_target.xml`) more directly signals the actual action —
"snap back to a specific point" — and reads as distinct from the calendar/label
iconography already used elsewhere in the top bar (`ic_label`) and Day zoom's own header.

(6) went through one round of iteration: the crosshair/`gps_fixed` variant shipped
first, then was swapped for a plainer two-ring-plus-center-dot bullseye (no ticks
radiating past the outer ring) per direct product feedback. Both read as "target," but
the crosshair variant carries GPS/location-pin baggage from its original Material
meaning; the plain-rings version is a more generic bullseye with no borrowed
association to shed.

---

## ADR-018: Day-zoom instance-edit sheet restricted to Rated/Valued tags

> **Superseded by ADR-031.** Simple capsules are tappable again, opening a panel that edits
> the day's *count* rather than listing timestamped rows. The reasoning below still explains
> why the *original* Simple sheet was worth removing — ADR-031 doesn't bring that back.

**Decision:** `TagGroupCapsule`'s tap target (`onCapsuleClick`, opening
`InstanceListSheet`) is now `enabled = type != TagType.SIMPLE`. Tapping a Simple
capsule does nothing; the sheet only ever opens for Rated or Valued groups. Simple's
only removal path remains the capsule's inline "x" (whole-group, all instances for that
day, no confirmation — unchanged from before).

**Alternatives considered:** (1) Leave the sheet reachable for Simple groups too
(today's behavior — tapping `walk (2)` opens a sheet listing two timestamped rows, each
with a delete icon, but nothing to edit). (2) Keep Simple tappable but make the sheet a
no-op/empty state for it. (3) Add per-instance removal for Simple some other way (e.g. a
long-press) instead of removing the capability outright.

**Why:** (1) is what M3 originally shipped, but it doesn't hold up against Simple's own
definition in `FEATURES.md` § Tag types — "name only (presence/absence)." A Simple
instance carries no rating, no value, nothing to set; the sheet's only content for it was
ever a timestamp and a delete button, i.e. a fancier, slower path to the same outcome the
capsule "x" already provides in one tap. Keeping it around cost a tap (open sheet, find
the row, tap delete, dismiss sheet) for zero benefit over "tap x." (2) was rejected as
pure overhead — an empty/degenerate sheet is a UI state to build and reason about for a
capability nobody needs. (3) was rejected as unrequested scope: per-instance removal for
Simple (peeling off one of two `walk` instances rather than clearing both) has never come
up as a need, and the app already has a fast, obvious "remove all of today's `walk`" path;
if per-instance Simple removal is ever actually wanted, it can be added deliberately
later rather than preserved by default now. Net effect: Simple stays a pure
presence/absence marker end to end — logged via the quick-entry bar, cleared via the
capsule "x," never opening an editor that has nothing to show it.

---

## ADR-019: Keep-style delay-delete undo for Day-zoom removals

**Decision:** Both Day-zoom removal paths — the capsule "x" (whole-group,
`CalendarViewModel.removeGroup`) and the instance-list sheet's per-instance delete
(`removeInstance`) — now go through a **delay-delete** flow instead of deleting
immediately:

1. The tapped instance(s) are held in a single `PendingRemoval(instances, tagName)`
   (`ui/calendar/PendingRemoval.kt`) in a new `CalendarViewModel` field, and *not* yet
   sent to the repository.
2. `CalendarViewModel.uiState`'s Day `periodData` is derived by filtering the repository's
   groups against whatever's in `PendingRemoval` (`TagDisplayGroup.excludingInstances`,
   `data/model/TagDisplayGroup.kt`) — so the removed instance(s) disappear from the Day
   list immediately, optimistically, even though the row is still in Room untouched.
   A group that becomes empty after filtering is dropped from the list entirely, same as
   today's real-delete behavior (and the existing "close the sheet once its group is
   gone" `LaunchedEffect` in `CalendarScreen` keeps working unmodified, since it already
   reacts to the filtered list).
3. `CalendarContent` shows a `Snackbar` ("'\<tagName\>' removed", action "Undo") for
   `SnackbarDuration.Short`, driven by a `LaunchedEffect(uiState.pendingRemoval)` — no
   separate `Channel`/one-shot-event plumbing (see alternatives). Tapping **Undo**
   (`SnackbarResult.ActionPerformed`) calls `CalendarViewModel.undoRemoval()`, which just
   clears the pending state — nothing was ever deleted, so "undo" is a no-op against the
   repository. Any other resolution (timeout, swipe-to-dismiss) calls
   `commitPendingRemoval()`, which performs the real `tagInstanceRepository.removeInstances(...)`
   call the delete would have done immediately before.
4. **Single pending slot, not a queue.** Starting a *new* removal while one is already
   pending commits the old one immediately (synchronously, before the new pending state
   is set) rather than stacking a second snackbar. At most one removal is ever "in
   flight" awaiting undo.
5. The now-redundant singular `TagInstanceRepository.removeInstance`/
   `TagInstanceDao.delete(instance)` were deleted — every removal, single-instance or
   whole-group, now funnels through `removeInstances`/`deleteAll`.

This resolves the "Considered: Undo snackbar for the capsule 'x'" open note that's been
sitting in `UI_UX.md` since ADR-009-era work, extending it to the instance-list sheet's
per-instance delete too, per direct product direction.

**Alternatives considered:** (1) Delete immediately, cache the removed rows client-side
for restore-on-undo (the "immediate delete + re-insert" variant `UI_UX.md`'s open note
raised as one option). (2) A queue of independent pending removals, each with its own
snackbar and timer, so unrelated deletes don't stomp on each other. (3) A dedicated
`Channel<UndoEvent>` one-shot-event stream (the pattern `ARCHITECTURE.md`/`CONVENTIONS.md`
already carve out an exception for) instead of keying a `LaunchedEffect` off
`uiState.pendingRemoval` directly. (4) Extend the same delay-delete treatment to the Tags
screen's tag deletion. (5) A custom fixed delay (e.g. a raw `delay(4000)` + `Job`) instead
of `SnackbarHostState.showSnackbar`'s built-in duration/result handling.

**Why:** (1) was rejected because it re-introduces exactly the race the delay-delete
model avoids for free: a real delete followed by a real re-insert means the "undone"
instance gets a new row id, new `createdAt` if reconstructed carelessly, and a window
where the data is genuinely gone (crash between delete and undo loses it for good).
Deferring the real delete until the snackbar actually resolves means undo is always
exact and free, and the worst-case failure mode (process death mid-timer) is "the
delete silently doesn't happen," which is the safe direction to fail in. (2) was
rejected as unneeded complexity for a single-developer, mostly-single-action-at-a-time
app — Gmail/Keep's own undo bars behave the same way (a new delete flushes the old
snackbar), and users don't generally fire off multiple unrelated deletes within the same
4-second window; if that changes, a queue is a contained follow-up, not a foundation
that needs to be right on day one. (3) was rejected because the `PendingRemoval` state
already has to live in `uiState` for the optimistic-filtering step (point 2 above) — a
parallel `Channel` would duplicate that same information in a second place with its own
timing, risking the two disagreeing (e.g. the channel event fires but the filtered list
hasn't recomposed yet, or vice versa). Keying a plain `LaunchedEffect` off the existing
state field gets the same "fires once per new value, cancels/replaces on the next" behavior
`Channel` would give here, with one source of truth instead of two. (4) was rejected per
`UI_UX.md`'s existing framing: Tags-screen deletion is a whole-tag, all-days, cascading
removal gated by a confirmation dialog specifically *because* it's higher-stakes and
harder to walk back informally than a single day's instances — a confirmation dialog and
a delay-delete snackbar are two different answers to "how do we prevent an accidental
destructive action," and the Tags screen already committed to the dialog answer; stacking
both would be redundant. (5) was rejected because `SnackbarHostState.showSnackbar` already
gives duration handling (including automatic extension for accessibility services, e.g.
TalkBack) and a typed result (`ActionPerformed` vs. `Dismissed`) for free — a hand-rolled
`Job` + `delay()` would have to reimplement both without the a11y awareness.

---

## ADR-020: Add-new-value control in the Valued instance sheet, Rated stays edit-only

**Decision:** `InstanceListSheet` gains a new fixed row below the instance list, shown
only when `group.type == TagType.VALUED`: a text field + "add" icon button
(`AddValueRow`, `ui/calendar/day/InstanceListSheet.kt`). Submitting calls a new
`CalendarViewModel.addValue(tagId, value)`, a thin pass-through to the existing
`TagInstanceRepository.addInstance(tagId, date, value = value)` — no repository/DAO
changes needed, since that method already supported a `value` argument for
creation-time seeding. Blank/whitespace-only input is ignored, mirroring
`TagQuickEntryBar.submit()`'s guard. **Rated** groups get no equivalent "add a new
rating" control — the sheet stays edit-existing/remove-only there, unchanged from
ADR-018/ADR-019.

**Alternatives considered:** (1) A reveal-on-tap `+`/FAB that only shows the text field
once tapped, instead of an always-visible row. (2) A symmetric "add a new instance"
control for Rated too, for consistency between the two editable types. (3) Instead of
adding an in-sheet control, fix `TagQuickEntryBar.submit()` so that typing `name:value`
for a tag that already exists actually seeds that value rather than silently adding a
blank instance (today, any exact name match short-circuits straight to
`onAddExistingTag`, discarding whatever was typed after the `:`).

**Why:** (1) was rejected because adding another value is expected to be a routine
action for a Valued tag (that's the whole point of the type), not an edge case worth
hiding behind an extra tap — the sheet already has vertical room, and every other
editable row in it is already always-visible. (2) was rejected as unrequested scope:
this feature was specifically asked for on Valued tags only ("allowing... adding new
ones"), while Rated was only asked to support changing an existing instance's rating;
building the extra control anyway would be scope nobody asked for, same reasoning
ADR-018 already used to keep Simple out of the sheet entirely — if a symmetric Rated
control is ever actually wanted, it's a contained follow-up, not something to bundle in
by default. (3) is a real, separate bug (it also affects adding to existing Rated and
Simple tags via quick-entry, not just Valued), but fixing it doesn't address the actual
ask here — even a fixed quick-entry bar would still require leaving the sheet, retyping
the tag name, and reopening the sheet to see the result, versus adding inline while
already looking at the list. Left as a known pre-existing quirk, not fixed as a side
effect of this change.

---

## ADR-021: Valued instance sheet — scroll+scrollbar, manual reordering, fixed height,
no timestamp; quick-entry chip-row visibility bug fixed

> **Point 3 (move-up/move-down reordering) is superseded by ADR-022**, which got a real
> drag handle working, and **point 6's fixed panel height by ADR-028**, which made both
> panels size to their content. The rest of this ADR still stands. The "Why move-up/move-down
> buttons, not a drag handle" section below is kept for history — it's the record of what
> was tried and why it failed, which ADR-022 builds directly on.

**Decision:** Several related adjustments to `InstanceListSheet`/`ValuedInstanceList`
(`ui/calendar/day/InstanceListSheet.kt`), scoped to **Valued** groups unless noted:

1. Valued rows no longer show the `createdAt` timestamp (`supportingContent`) below the
   value field — Rated (and, if it were ever reachable, Simple) keep it, since Valued is
   now manually ordered rather than time-ordered.
2. Both Rated's and Valued's instance lists render inside a scrollable region with a
   scrollbar, instead of overflowing the sheet. The scrollbar is a hand-drawn thumb
   (`ScrollbarTrack`, a `Modifier.drawBehind` overlay reading
   `ScrollableState.scrollIndicatorState` — a `scrollOffset`/`contentSize`/`viewportSize`
   API this version of `androidx.compose.foundation`, 1.11.4, ships specifically for
   scroll indicators) — no library, same "self-contained UI piece" precedent as
   ADR-011's color picker. It's drawn as a **separate sibling `Box`**, not a modifier
   chained onto the scrollable `Column` itself: chaining it directly (an earlier attempt)
   puts the thumb's draw call inside the part of the tree that `verticalScroll` offsets
   to implement scrolling, so it scrolls away with the content and gets increasingly
   clipped against the viewport's top edge — reading as "shrinks, barely moves." A
   sibling's coordinate space is never touched by the Column's internal scroll offset.
3. Each Valued row gets move-up/move-down `IconButton`s (`Icons.Filled.KeyboardArrowUp`/
   `KeyboardArrowDown`, both already part of the `material-icons-core` dependency this
   project uses) instead of a drag handle. On tap, the tapped row swaps with its
   neighbor and the whole group's `TagInstance.sortOrder` is rewritten sequentially via
   a new `CalendarViewModel.reorderValues(instances)` → new
   `TagInstanceRepository.updateInstances(instances)` → new
   `TagInstanceDao.updateAll(instances)` (a plain `@Update` on a `List`, which Room
   supports natively). See "Why" below for why this replaced an actual drag handle.
4. `TagQuickEntryBar`'s suggestions list and its Simple/Rated/Valued type-chip
   row/hint were previously rendered by a single mutually-exclusive `when` block, so
   typing a name that partially matched an existing tag (showing suggestions) hid the
   chip row needed to create a *new* tag with that exact name — "+" appeared to do
   nothing. Fixed by making the two independent: suggestions show whenever there are
   any, and the chip row/hint shows whenever there's no *exact* match, regardless of
   whether suggestions are also present.
5. The Valued chip in that same type-picker row no longer calls `onCreateTag` (which
   immediately adds an instance with `value = null`) — `TagDisplayGroups.summarize`'s
   Valued branch drops null values entirely (`mapNotNull { it.value }`), so that
   instance had no meaningful display (`"name: []"`) and no working way to edit it
   afterwards either. The chip now calls a new `onCreateValuedTag(name)` →
   `CalendarViewModel.createValuedTagForEditing(name)`, which creates the tag with
   **no** instance and opens `InstanceListSheet` directly for it via a new single-shot
   `pendingValuedTagEdit: StateFlow<Long?>` that `CalendarScreen` observes; the sheet
   falls back to a synthetic, instance-less `TagDisplayGroup` (built from the `Tag` row
   in `allTags`, since `dayGroups` has nothing for a tag with zero instances yet) so it
   opens showing only the `AddValueRow`, ready for the tag's first real value. This
   fallback is gated by a `selectedGroupIsFreshValuedTag` flag set only by this flow —
   tapping an existing capsule leaves it `false`, so removing an existing group's last
   instance still auto-dismisses the sheet as before, rather than reopening it empty.
6. `InstanceListSheet` is a **fixed** height (50% of screen height,
   `LocalConfiguration.current.screenHeightDp.dp * 0.5f` on its content `Column`) instead
   of sizing to content up to a cap, and can no longer be dismissed by dragging it, back
   press, or scrim tap. `rememberModalBottomSheetState(skipPartiallyExpanded = true,
   confirmValueChange = { it != SheetValue.Hidden })` rejects every attempt to *settle*
   on `Hidden` (covers back press/scrim tap, which both dismiss via `sheetState.hide()`);
   `ModalBottomSheet`'s `sheetGesturesEnabled = false` separately disables the sheet's
   own `.draggable()` modifier outright, since `confirmValueChange` alone only vetoes
   the final settle target — the drag itself still visibly followed the finger before
   snapping back without it. `dragHandle = null` drops the visual affordance suggesting
   it's draggable. The sheet now closes only via an explicit close (`Icons.Filled.Close`)
   `IconButton` next to the tag name, calling `onDismiss` directly.
7. It was possible to edit a Valued instance's value down to an empty string —
   `InstanceEditor`'s `OutlinedTextField` called `onUpdateInstance` on every keystroke
   with no guard, unlike `AddValueRow`, which already trims-and-rejects blank input
   before calling `onAdd`. Fixed by giving the field a local `remember(instance.id)`
   text buffer: it always shows whatever's being typed (so clearing the field to retype
   a value still feels normal), but only calls `onUpdateInstance` when the trimmed text
   is non-empty, so the persisted value can never actually become blank.

Persisting a manual order requires a new `TagInstance.sortOrder: Long = 0` column
(`TagDayDatabase` bumped to version 3, relying on the existing
`fallbackToDestructiveMigration(dropAllTables = true)` in `di/DatabaseModule.kt` rather
than a real `Migration`, consistent with how version 1→2 was handled pre-release).
`addInstance` seeds `sortOrder = createdAt` at creation time (same
`System.currentTimeMillis()` value) — this costs no extra query and keeps
newly-added values sorting after any manually-reordered ones for free, since a
manual reorder always rewrites the whole group's `sortOrder` to small sequential
indices (`0, 1, 2, ...`), which are always less than a fresh epoch-millis timestamp.
`InstanceListSheet` now sorts Valued rows by `sortOrder` instead of `createdAt`.

**Why move-up/move-down buttons, not a drag handle:** the user originally asked for a
drag handle, and that's where this feature started — but it went through five
iterations trying to make a per-row drag handle coexist with the list's own
`verticalScroll`, and never worked reliably, so it was replaced with buttons as a
deliberate scope-down once the pattern of failure became clear. Worth recording in
detail, since it's exactly the kind of thing someone might reasonably try to re-add
later:

1. A plain `detectDragGestures` on the handle raced the ancestor `Column`'s
   `verticalScroll` and the sheet's own drag-to-dismiss, since all three independently
   watched the same touch-slop threshold with no coordination — reordering sometimes
   scrolled the list or resized the sheet instead of moving the row.
2. Switching to `detectDragGesturesAfterLongPress` looked like a fix but wasn't: that
   helper only ties disabling the ancestor scroll to *after* the long press resolves,
   leaving the entire hold duration exposed to the same race.
3. Hand-rolling the gesture (`awaitEachGesture`/`awaitFirstDown`/`drag`, claiming a
   `draggingId` flag — which gated `verticalScroll`'s `enabled` param — on the very
   first touch, before any long-press timer even started) fixed *that* race, but
   uncovered a separate, unrelated bug: `ValuedInstanceList`'s row loop had no
   `key(instance.id)` wrapper, so Compose tracked rows by *position*, and the first
   in-gesture swap reassigned which instance rendered at the dragged row's position —
   tearing down and recreating the composable (and its `pointerInput` coroutine) there,
   killing every drag after exactly one swap.
4. Adding `key(instance.id)` fixed *that*, but reordering still didn't work: the
   long-press wait had become redundant for its original race-avoidance purpose (once
   `draggingId` was claimed on first touch) but was still required before any drag
   started, so an immediate natural drag attempt (the expected gesture for a handle)
   silently did nothing until roughly 500ms of holding still had passed, with no visual
   affordance hinting a hold was needed.
5. Removing the wait (`drag()` tracking movement from the first touch) still didn't
   work, and this time the actual root cause was found by pulling and reading the real
   `androidx.compose.foundation` 1.11.4 sources (`Draggable.kt`/`Scrollable.kt`) rather
   than reasoning from general Compose knowledge: `enabled = draggingId == null` only
   takes effect on the *next recomposition*, but `DragGestureNode.isInterested()` (what
   `verticalScroll`'s scrollable is built on) reads `enabled` at the moment of the
   *down* event, which is necessarily still `true` then — we can only react to a down
   after it's already happened. The ancestor scrollable therefore always registers
   itself as a candidate for the gesture, and if it then wins the very first move
   (plausible, since our recomposition may not have applied by the time that move is
   dispatched), `drag()` treats losing even one move to another consumer as permanent,
   immediate failure — not "skip this one and keep trying" — so the gesture died
   silently on the first pixel of movement, every time. The fix at this point (removing
   `verticalScroll`'s own gesture entirely, replacing it with a second hand-rolled
   scroll loop) did work for scrolling in isolation, but broke normal list scrolling as
   an unintended side effect and still needed to be verified for the handle itself —
   at which point continuing to spend fixes on this exact interaction stopped being
   worth it.

Given five iterations of increasingly deep, individually-reasoned fixes each either
failed or introduced a new regression, and given this project's own testing policy
(`TESTING.md`) already documents that gesture "feel/timing/threshold correctness can
only really be judged on a device" — unavailable in this working environment — it made
sense to stop trying to make a drag handle coexist with a scrollable list at all, and
use move-up/move-down buttons instead. A button's `onClick` has no gesture-arbitration
race to lose: it fires once, unconditionally, on tap. This trades a fluid drag for a
guaranteed-to-work discrete move — an explicit, deliberate scope-down, not a
regression, given the track record above.

**Alternatives considered:** (1) A `LazyColumn` with an external reorder library, for
the (abandoned) drag-handle approach. (2) A dedicated "max sortOrder for this
tagId+date" query to seed new instances at the end explicitly, instead of reusing
`createdAt`. (3) Keep the sheet dismissible by back press/scrim tap while blocking only
the drag gesture. (4) Leave the sheet's height dynamic (content-sized up to a cap, the
pre-existing behavior) rather than fixed.

**Why:** (1) was rejected on the same grounds as ADR-010/ADR-011 — no reorder library
exists in this project's dependencies today; moot now that the drag handle itself was
abandoned. (2) was rejected because reusing `createdAt` as the initial `sortOrder` gets
the same "new values append to the end" behavior for free, with zero additional DAO
queries or repository logic. (3) isn't achievable with `ModalBottomSheet`'s public API:
back press and scrim tap both dismiss by calling `sheetState.hide()` and only invoke
`onDismissRequest` once that completes, so a `confirmValueChange` that blocks the
`Hidden` state blocks all three paths uniformly — there's no hook that distinguishes
*why* a transition to `Hidden` was requested. Given that, disabling all implicit
dismissal and adding one explicit, always-visible close button is more predictable than
a partial block that still leaves two other implicit-dismiss paths in place. (4) was
rejected because the user explicitly asked to remove the "sometimes resizes" behavior —
a dynamic height was part of what made the panel feel inconsistent (short content
leaves it small, overflow content snapped it to the cap); a fixed height is simpler to
reason about and matches what was asked for directly, at the cost of visible empty
space below a short instance list.

**Amendment 1** (BACKLOG F15): point 6's *reasoning* about back press is now out of date, and
so is alternative (3)'s "isn't achievable with `ModalBottomSheet`'s public API". Both were
accurate against the Material3 of the time. Two things have changed in the version now on the
BOM (material3 1.4.0):

- **Back press no longer goes through `sheetState.hide()` alone.** It's handled by a
  `PredictiveBackOnBackPressedCallback` that drives the *dialog's* `onDismissRequest`
  (`ModalBottomSheet.android.kt`, gated only by `properties.shouldDismissOnBackPress`), which
  with `skipPartiallyExpanded = true` lands on
  `scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }` — and unlike
  the scrim's `animateToDismiss`, that completion handler carries **no `if
  (!sheetState.isVisible)` guard**. So `onDismiss` fires whether or not `confirmValueChange`
  vetoed the hide: **back has been closing this sheet all along**, contrary to what point 6
  intended and to what this ADR claims.
- **`ModalBottomSheetProperties` gained `shouldDismissOnClickOutside`**, which gates the
  `Scrim`'s own dismissal (`ModalBottomSheet.kt`: `dismissEnabled = ...`). Scrim and back are
  therefore separable now, which is exactly what alternative (3) wanted and couldn't have.

**The intended end state is unchanged in spirit but corrected in one respect**: back *should*
close the sheet. Point 6 lumped it in with drag and scrim as "implicit dismissal", but a back
press is a deliberate act, and on Android 14+ blocking it would mean a predictive-back peek
followed by nothing — which reads as a broken screen, not a protected one. Accidental
dismissal was the real concern, and drag and scrim remain blocked.

What changes in code is only that this stops being accidental: block the scrim with
`shouldDismissOnClickOutside = false` rather than with a `confirmValueChange` veto that no
longer does the job it was added for, keep `sheetGesturesEnabled = false` for the drag, and
let back take the library's normal path — which also restores the predictive-back animation
instead of an instant close.

**Not verified on a device** — one step of the above (whether a vetoed `hide()` completes
promptly enough for the unguarded handler to fire) was read from the library's sources, not
observed. See `UI_UX.md`'s manual-check list.

---

## ADR-022: Valued reordering is a real drag handle after all, via `Modifier.draggable`

**Decision:** Supersedes ADR-021's point 3. Each Valued row's leading control is a drag
handle again (`DragHandle` in `ui/calendar/day/InstanceListSheet.kt`): press it and drag
vertically to move the row, which trades places with a neighbour once it has travelled
past half of that neighbour's height, and commits the whole group's `sortOrder` once on
drop (same `CalendarViewModel.reorderValues` → `updateInstances` → `updateAll` path
ADR-021 already added). While a drag is in flight the rendered order is local state, so
rows shuffle immediately instead of waiting for each swap to round-trip through Room; it
resyncs from the persisted order whenever the *set* of instances changes (a value added
or removed), but deliberately not on a pure order change, since this UI is the only
writer of `sortOrder` and adopting a late-arriving emission of our own commit can revert
a fresher local order. Move-up/move-down survive as `CustomAccessibilityAction`s on the
handle — touch-drag is unreachable for screen readers, so the discrete path still has to
exist, just not as visible buttons. The list's own `verticalScroll` is untouched, so
dragging anywhere other than the handle still scrolls normally; because the handle
consumes the events the scroll would otherwise use, holding a row near either edge of the
viewport drives an explicit edge auto-scroll instead (a `withFrameNanos` loop that feeds
the scroll it actually consumed back into the drag offset, so the row stays under the
finger and keeps swapping as the list moves past it). That loop is armed by the drag's net
*finger* travel — in magnitude and direction, and excluding its own feedback — so it only
ever continues a drag the user is really making: since `startDragImmediately` means drags
begin at touch-down, a press on the handle that never moves must not scroll, and a row
being dragged *down* out of the top edge zone must not get yanked back up on its way out.
(The residual drag offset is no use for that test — it flips sign at every swap.) The
handle's glyph is
`Icons.Filled.Menu` — the same stack of horizontal bars — because `DragHandle` is an
extended-set icon and ADR-010/ADR-011's no-new-dependencies line still applies.

**Why this worked where ADR-021's five attempts didn't:** every earlier attempt drove the
gesture from `pointerInput` plus `detectDragGestures` / `awaitEachGesture` + `drag()`.
`Modifier.draggable` instead installs a `DragGestureNode` — the *same class*
`verticalScroll`'s scrollable is built on — so the handle competes with the list on equal
terms instead of fighting it from outside. Reading `androidx.compose.foundation` 1.11.4's
`Draggable.kt` and `androidx.compose.ui`'s `HitPathTracker.kt` (both pulled from the
Gradle cache, as ADR-021's last attempts already started doing) confirms four separate
mechanics that make this hold together, each of which was a failure mode before:

1. **The handle wins arbitration by position in the tree.** `HitPathTracker`'s
   `dispatchMainEventPass` recurses into children *before* invoking a node's own Main
   handler, so the handle's node processes each move first, consumes it, and the ancestor
   scrollable only ever sees an already-consumed move — at which point it parks itself in
   `AwaitGesturePickup` for the rest of the gesture. This replaces ADR-021 attempt #5's
   `enabled = draggingId == null` trick, which couldn't work: `enabled` is read at *down*
   time, before there's anything to react to.
2. **`startDragImmediately = true`** skips touch-slop detection entirely and consumes the
   down event in the Initial pass, so the claim happens at touch-down rather than
   depending on who crosses slop first — and with no long press (attempts #2/#4), which
   left a race window open and then a silent hold requirement with no affordance.
3. **Losing an event isn't fatal.** `DragGestureNode` has an explicit
   `AwaitGesturePickup` state that re-enters slop detection if the gesture frees up again;
   the low-level `drag()` helper the earlier attempts used treats a single move consumed
   by someone else as immediate, permanent failure, which is precisely why attempt #5 died
   on the first pixel of movement.
4. **Offsetting the dragged row can't feed back into the deltas.** `HitPathTracker`
   converts a change's `position` *and* `previousPosition` with the node's current
   coordinates, so `positionChange()` is translation-invariant: a row that moves to follow
   the finger doesn't thereby report smaller deltas and stall. Relatedly,
   `DraggableNode.update` only resets pointer-input handling when `state`/`orientation`/
   `reverseDirection`/`enabled` change — new lambda identities per recomposition don't —
   so the recomposition every swap triggers doesn't kill the in-flight gesture, as long as
   `key(instance.id)` keeps the node itself alive across the reorder (ADR-021 attempt #3's
   bug, still load-bearing here).

**Alternatives considered:** (1) Keep ADR-021's move-up/move-down buttons. (2)
`LazyColumn` plus a third-party reorderable library. (3) Long-press-then-drag on the
handle. (4) Drag anywhere on the row instead of a dedicated handle. (5) Animate the
dragged row settling into its final slot on drop, instead of snapping.

**Why:** (1) was rejected because a drag handle was the original ask, and ADR-021's
scope-down was explicitly a "stop spending fixes on this" call, not a judgment that
buttons are the better UI — the reasoning behind it (five failed attempts) doesn't survive
having found the actual mechanism, and the buttons' one real advantage, being reachable
without a drag gesture, is preserved as accessibility actions. (2) still means a new
dependency for something now working in ~60 lines of foundation-only code (ADR-010/ADR-011).
(3) is unnecessary once the handle claims the gesture at down-time, and a hold requirement
with nothing hinting at it was one of the things that made attempt #4 feel broken. (4) was
rejected because a whole-row drag has to be distinguishable from a scroll of the list —
which is exactly the problem long press normally solves, reintroducing (3) — and the row
also holds a text field with its own taps to worry about; a dedicated handle keeps "drag
here = reorder, anywhere else = scroll" unambiguous. (5) was left out as polish, not
mechanism: on drop the row snaps up to half a row's height into place, which is standard
settle behavior, and adding an animation would mean holding a second, animated offset
alongside the real one purely for looks.

**Not verified on a device.** The mechanism above is read out of the actual foundation/ui
sources rather than recalled, and the app builds and its unit tests pass, but no
device/emulator exists in this working environment (`TESTING.md`) — gesture feel,
thresholds, and the auto-scroll rate can only really be judged by hand. See `UI_UX.md`'s
known-issues list for what to look at first.

---

## ADR-023: Display order of instances is owned by the DAO query, not by each consumer

**Decision:** `TagInstanceDao.observeForDay`/`observeForRange` gained `ORDER BY sortOrder`,
and that order is now a documented contract: `TagInstanceRepository.observeDayGroups`
returns each group's `instances` in **display order**, and consumers render them as given
rather than sorting again. `TagDisplayGroups.summarize` already preserved the order it was
handed, so `movie: [terminator, dune]` in the day capsule now follows a manual reorder; the
client-side `sortedBy { it.sortOrder }` in `ValuedInstanceList` and
`sortedBy { it.createdAt }` in the (renamed) `TimeOrderedInstanceList` are both gone. A
unit test (`valued_summaryFollowsRowOrder_soAManualReorderShowsInTheCapsule`) pins the
summarizer half of the contract.

**Why:** ADR-021/ADR-022's reordering only ever affected the instance-list sheet, because
the sheet sorted its own rows while the capsule summary rendered whatever order SQLite
happened to return — the queries had no `ORDER BY` at all, so in practice insertion
(rowid) order. Reordering values therefore appeared to do nothing once the sheet closed,
which is the wrong half of the feature to have working. Two independent notions of "the
order" were the root cause, so the fix is one owner rather than a second sort bolted onto
the capsule path: the DAO decides, everything downstream inherits.

**Alternatives considered:** (1) Sort inside `summarize` (or in `toDisplayGroups`) instead
of in SQL. (2) Leave the queries unordered and sort in every consumer. (3) Order by
`sortOrder, createdAt` as a tiebreak.

**Why:** (1) would keep the ordering rule in Kotlin next to the aggregation it feeds,
which fits ADR-004's "aggregate in Kotlin, not SQL" — but ordering isn't aggregation, and
`summarize` is also called on already-filtered lists from `CalendarViewModel`
(`excludingInstances`, ADR-019), so it would re-sort on paths that don't need it while
still leaving the raw `instances` list unordered for anything else that reads it. (2) is
what was there and is exactly what broke. (3) is unnecessary: `sortOrder` is seeded from
`createdAt` (a millisecond timestamp) and rewritten to sequential indices only within one
tag+day group, so collisions would need two inserts in the same millisecond for the same
tag — and if that ever happened, either order is equally correct.

---

## ADR-024: ViewModels get unit tests; `kotlinx-coroutines-test` joins the test classpath

**Decision:** Reverses `TESTING.md`/`ARCHITECTURE.md`'s earlier "ViewModels are
deliberately not tested" position. `CalendarViewModel` and `TagsViewModel` now have unit
tests, which means one new test dependency (`kotlinx-coroutines-test`, pinned to the
coroutines version already on the runtime classpath transitively) and two small pieces of
test scaffolding: `MainDispatcherRule` (swaps `Dispatchers.Main` for an
`UnconfinedTestDispatcher`, without which `viewModelScope` doesn't work off-device) and
`TestScope.keepSubscribed`, since every `uiState` is `SharingStarted.WhileSubscribed` and
would otherwise never leave its initial value. Collaborators are faked at the *repository
interface* boundary (`FakeTagRepository`, `FakeTagInstanceRepository`, backed by
`MutableStateFlow`), extending the existing fake-the-DAO pattern one layer up rather than
introducing a mocking library. The Android Studio template's `ExampleUnitTest` /
`ExampleInstrumentedTest` boilerplate is deleted at the same time.

**Why:** the original reasoning was specific and, at the time, correct — the ViewModels
were pass-throughs, so a test would only have re-asserted repository behaviour already
covered a layer down. That stopped being true: ADR-019 put a delay-delete state machine in
`CalendarViewModel` (optimistic filtering of not-yet-deleted instances, plus a
flush-the-previous-pending-removal rule that's easy to break into "hidden forever but
never actually deleted"), ADR-021 added a single-shot `pendingValuedTagEdit` signal, and
ADR-022 added reorder write-through. None of that logic exists in the repositories, so
none of it was covered anywhere. The decision was conditional from the start — its own
wording was "if a ViewModel ever grows real logic of its own, that's the point to
reconsider" — so this is the trigger firing, not a change of principle.

**Alternatives considered:** (1) Keep ViewModels untested and push the logic down into the
data layer (or a domain layer, per ADR-002) where the existing test setup already reaches.
(2) Test through the UI with Compose UI tests instead. (3) Add a mocking library (MockK)
rather than hand-writing repository fakes.

**Why:** (1) is the more architecturally pure answer for the pending-removal logic and
worth revisiting if it grows further, but it isn't free — `PendingRemoval` is UI state (it
exists to make an *undo affordance* work, and dies with the ViewModel by design), so
pushing it into the data layer would mean a repository holding "deleted, probably" rows,
which is a worse model than a tested ViewModel. (2) needs a device/emulator, which this
working environment doesn't have (`TESTING.md`), and would test the snackbar wiring rather
than the state machine. (3) buys little here: the fakes are ~60 lines each, they double as
readable in-memory reference implementations, and a `MutableStateFlow`-backed fake models
"a write shows up in the next emission" far more naturally than stubbed return values —
which is exactly the behaviour these tests need to assert.

---

## ADR-025: The Rated instance sheet is a bare row of stars; hollow star is a hand-added drawable

> **Points 1-3 are superseded by ADR-028**: the Rated panel got its per-instance delete back,
> gained reordering and an add-rating row, and its content-sized height was generalised to
> both panels. Point 4 (the hollow-star drawable) stands, and the reasoning in points 1-3 is
> kept because ADR-028 is a deliberate re-decision of the same tradeoffs, not a correction.

**Decision:** Four related changes to the **Rated** side of `InstanceListSheet`, which had
been carrying the Valued side's layout by default:

1. **Compact, content-sized panel.** ADR-021's fixed half-screen height now applies to
   Valued only. Rated sizes to its content with `heightIn(max = panelHeight)`, so a
   one-instance group opens a sheet barely taller than the stars themselves; the ceiling
   only comes into play once enough instances stack up, at which point the list scrolls as
   before. `ScrollableInstanceList` wraps its content vertically to allow this, and the
   Rated list deliberately doesn't take `weight(1f)` the way the Valued one does.
2. **No per-instance delete button.** Removing a Rated instance goes through the capsule's
   own "x" (whole group, delay-delete via ADR-019). See the tradeoff below.
3. **No timestamp.** Valued rows dropped theirs in ADR-021; Rated's is gone too, which
   leaves nothing in the sheet that formats a time — `formatTime`/`timeFormatter` are
   deleted with it. A Rated row is now just a `StarInput`, not a `ListItem`, since
   `ListItem`'s one-line minimum height was itself part of the vertical bulk.
4. **Stars now reflect the actual rating.** They previously rendered filled at every
   position regardless of the value, which made the whole panel useless for reading a
   rating back. Cause: `StarInput` used `Icons.Outlined.Star` for the empty state, and in
   `material-icons-core` that icon's path data is *identical* to `Icons.Filled.Star` — a
   solid star (verified against the artifact's own sources in the Gradle cache, not
   assumed). The core artifact ships an `outlined` package covering the same ~48 icons as
   `filled`, but for Star the two entries differ only by a redundant `lineTo` back to the
   start point. The genuine hollow glyph (`star_border`) only exists in
   `material-icons-extended`. Fixed by hand-adding `res/drawable/ic_star_border.xml` and
   loading it with `ImageVector.vectorResource`, exactly the escape hatch ADR-010 set up
   for `ic_label.xml`.

**Alternatives considered:** (1) Keep the per-instance delete button but shrink it, or move
it into an overflow. (2) Add `material-icons-extended` for `Icons.Outlined.StarBorder`.
(3) Draw the empty star with `Canvas`/a stroked path instead of a drawable. (4) Render the
empty state as a filled star tinted at low alpha, keeping `Icons.Filled.Star` for both.
(5) Collapse a multi-instance Rated group to a single shared rating control, since the ask
was "we just want to set one rating".

**Why:** (1) is a real loss, and worth stating plainly: a Rated group with two instances
can no longer have *one* of them removed — it's the whole group or nothing. That was the
explicit instruction ("tag can be deleted by clicking 'x' on tag itself"), and it's
consistent with Simple already being whole-group-only (ADR-018); Valued keeps its
per-instance delete because it's the type where individual rows carry distinct content
worth removing one at a time. (2) is the same ~1000-icon dependency ADR-010 turned down for
one glyph, and release builds still don't run R8 to shake it out. (3) means hand-rolling a
ten-vertex star polygon in code where a 12-line drawable does it declaratively, and loses
the ability to eyeball the asset. (4) reads as "dimmer", not "empty", and low-alpha fills
are exactly the kind of thing that disappears at low contrast. (5) was rejected as
overreach: the underlying model genuinely allows several rated instances per day
(`freediving: ★★★ (2)`), and silently editing them as one would either drop data or need a
rule for which instance wins — the compact panel already achieves the stated goal for the
common single-instance case without changing what the data means.

**Verification note:** the drawable's `pathData` was checked by parsing it — two subpaths,
outer wound clockwise and inner counter-clockwise (so the default nonZero fill leaves a
hole), inner strictly inside outer, and the outer ring a regular 5-pointed star (10
vertices, radii alternating 10.51/4.78 within 0.02). Malformed `pathData` throws at
inflation time rather than build time, so "it compiles" says nothing here. It still hasn't
been *looked at* on a device — see `UI_UX.md`'s known-issues list.

---

## ADR-026: The capsule "x" gets a real 48dp touch target, fixing Simple capsules that
removed themselves on tap

**Decision:** `TagGroupCapsule`'s inline remove control (`ui/calendar/day/DayContent.kt`)
grows from a 20dp box to a 48dp square. The ✕ glyph stays 14dp, so the control looks the
same; what changes is that the capsule is now as tall as that target (its vertical padding
goes away, since the 48dp child sets the height) rather than ~32dp.

**Why:** tapping a **Simple** capsule's text removed the tag. Cause, read out of
`androidx.compose.ui` 1.11.4's `NodeCoordinator`/`ViewConfiguration` rather than guessed:
Compose hit-testing expands any pointer-input node smaller than
`ViewConfiguration.minimumTouchTargetSize` — a flat `DpSize(48.dp, 48.dp)`, defaulted on the
interface and *not* overridden by `AndroidViewConfiguration`, so there's no device variance —
evenly around the node. The 20dp ✕ therefore had a 48dp hit area reaching ~14dp back over
the capsule's own text. `NodeCoordinator.outOfBoundsHit`'s documented priority is that a
*direct* hit beats another node's inflated hit, which is exactly why this only affected
Simple: on Rated/Valued the capsule body's `clickable` is enabled and won the overlap, but
ADR-018 disables it for Simple, so nothing competed and the ✕ took the tap. At >= 48dp the
inflation is zero (`calculateMinimumTouchTargetPadding` only pads a positive difference), so
the touch bounds equal the visual bounds and can't overlap the text at all.

**Alternatives considered:** (1) Keep the 20dp control and give Simple capsules a no-op
`clickable` (with `indication = null`) purely to win the direct-hit priority. (2) Keep the
20dp control and suppress the inflation for that subtree by providing a
`LocalViewConfiguration` whose `minimumTouchTargetSize` is `DpSize.Zero`. (3) Move the ✕
outside the capsule entirely. (4) Drop the inline ✕ and require the instance sheet (or a long
press) for removal.

**Why:** (1) works, but only by relying on hit-test precedence between two overlapping
targets — a load-bearing no-op click handler that reads like dead code and that anyone
tidying up would delete, re-introducing the bug; it also leaves a 20dp target, well under
the accessibility minimum. (2) fixes the overlap by making a too-small target *officially*
too small, trading a real accessibility property for visual density, and does it via a
CompositionLocal override that would silently apply to anything later added to that subtree.
(3) and (4) are redesigns of a control that works, for a bug that's a sizing mistake.

**Tradeoff accepted:** Day-zoom capsules are taller (~32dp → 48dp), so the tag list is less
dense. That also makes the *capsule* tap (which opens the instance sheet) meet the 48dp
minimum, which it previously didn't either. If the density matters more than the guideline,
the fallback is (1) — but it should then be commented as load-bearing, not left to look
incidental. Same 48dp question is still open for `StarInput`'s 32dp stars, noted in
`UI_UX.md`'s known-issues list.

**Not verified on a device.** Hit-test geometry isn't something the unit tests reach (this
is precisely the Composable gap `TESTING.md` documents), so the mechanism here is
source-derived, not observed. `UI_UX.md`'s known-issues list has the taps to try.

---

## ADR-027: Capsules go back to 32dp; the ✕ keeps exact touch bounds instead of a 48dp size

**Decision:** Supersedes ADR-026's *sizing* (its diagnosis stands). The capsule's ✕ tap area
is 32dp square again — so capsules are ~32dp tall rather than 48dp — and the overlap bug is
held shut a different way: a `CompositionLocalProvider` scoped to that one Box provides a
`LocalViewConfiguration` whose `minimumTouchTargetSize` is `DpSize.Zero`, which switches off
the inflation that caused the bug. Hit-testing reads `LayoutNode.viewConfiguration`, and
`LayoutNode.compositionLocalMap`'s setter assigns it from exactly this CompositionLocal, so
this is a supported path rather than a workaround. Net effect: the ✕'s touch bounds equal its
visual bounds — the full 32dp right end of the capsule — and can no longer reach over the tag
text, so a tap on a Simple capsule's body hits the (disabled, per ADR-018) body clickable and
does nothing, which is the specified behaviour.

The provider deliberately wraps **only** the ✕. The capsule body's own `clickable` keeps its
default inflation, which is harmless there (it's an ancestor of the ✕, not a sibling
competing with it, and the innermost node consumes first) and makes the sheet-opening tap
slightly more forgiving vertically.

**Why:** ADR-026 fixed the bug by making the ✕ a full 48dp, and listed this
`LocalViewConfiguration` override as a rejected alternative — on the grounds that it trades a
real accessibility property for visual density and would "silently apply to anything later
added to that subtree". Both objections were raised against a version that wrapped the whole
capsule; scoping it to the single Box answers the second one outright. The first is a genuine
tradeoff, and it was called the wrong way: 48dp targets made every Day-zoom capsule 50%
taller, and density on the app's primary surface was worth more than the guideline here — a
32dp target that behaves exactly as it looks is also a real improvement on what shipped
before ADR-026 (a 20dp glyph whose true target was 48dp and overlapped its neighbour).

**Alternatives considered:** (1) Keep ADR-026's 48dp ✕ and accept the taller capsules.
(2) Keep the small ✕ but give Simple capsules a no-op `clickable` so a direct hit wins the
overlap. (3) Shrink the ✕ target below 32dp for even denser capsules. (4) Drop the inline ✕
and remove groups only from the instance sheet.

**Why:** (1) is what this supersedes — correct by the guideline, rejected on density.
(2) still relies on hit-test precedence between two overlapping targets and reads as dead
code (ADR-026 covers this). (3) is available by changing one constant
(`CAPSULE_REMOVE_TARGET_SIZE`), but it now costs real tappability rather than just apparent
size, since there's no inflation left to fall back on — 32dp is the floor worth defending
without a device to test on. (4) removes a working fast path.

**Consequence to keep in mind:** because inflation is off for the ✕, its size *is* its touch
target, and that includes the bounds reported to accessibility services. Anything added to
that Box's subtree later inherits the same rule.

---

## ADR-028: Content-sized instance panels, shared reorderable list, comma-seeded values, and
tag-name rules

**Decision:** Four changes, three of them re-decisions of earlier calls in this same corner
of the app.

1. **Both instance panels size to their content**, with half the screen height as a *ceiling*
   rather than a fixed height (`heightIn(max = panelHeight)` on the sheet's content `Column`).
   The instance list takes `Modifier.weight(1f, fill = false)`, which is what makes it work:
   the add-row below is measured first, then the list gets whatever is left *as a maximum* and
   wraps to its content if that's shorter. `fill = true` — the default, and what Valued used
   before — stretches the list to fill, which is exactly what pinned the sheet open at full
   height. Supersedes ADR-021's point 6 (fixed height) and generalises ADR-025's ceiling from
   Rated to both types.
2. **The Rated panel gets a per-instance delete button, drag-to-reorder, and an add-rating
   row** ("+" next to a `StarInput`, where the Valued panel's add-value field sits). Pressing
   "+" with no stars picked adds an *unrated* instance, which is a legitimate state for this
   type (ADR-008) and matches what quick-entry's Rated chip already creates — so unlike
   `AddValueRow`'s blank guard there's nothing to reject here. Reverses ADR-025's points 1-2.
3. **`ValuedInstanceList` became `ReorderableInstanceList`**, shared by both types: it owns the
   ordering state, drag gesture, swap thresholds and edge auto-scroll (all ADR-022 mechanics,
   unchanged), and takes the row content as a slot that receives the drag handle to place.
   `InstanceRow` is the shared shell (handle, editor, delete) each type fills with its own
   editor. `CalendarViewModel.reorderValues` is renamed `reorderInstances` to match.
4. **Comma-separated values seed several instances**: `film:dune,tenet` creates the tag with
   two values rather than one literal `"dune,tenet"`. `ParsedTagInput.Valued` now carries a
   non-empty `values: List<String>`, and a new `TagInstanceRepository.addValues` inserts one
   instance per value with `sortOrder = now + index` — a plain `addInstance` loop would stamp
   them all with the same millisecond and leave `ORDER BY sortOrder` (ADR-023) to break the
   tie arbitrarily. A suffix of nothing but commas is treated as ambiguous, like a bare name.
5. **Tag names are constrained**: lowercase letters, single `-` separators, starting and
   ending with a letter (`^[a-z]+(-[a-z]+)*$`). `data/model/TagName` states the rule once for
   both entry points that can name a tag, and splits it in two halves on purpose:
   `sanitize` runs on every keystroke (lowercases; rewrites anything separator-shaped —
   whitespace, `_`, `-` — to a single `-`; drops everything else; never leaves a leading or
   doubled separator) while `isValid` gates the save. Whitespace becoming a separator rather
   than vanishing is deliberate: `-` is *the* separator for these names (`fast-food`,
   `playing-game`), so two typed words mean one separated name, not `fastfood`. They
   differ over a trailing `-`, which sanitize must keep — you can't type `fast-food` without
   passing through `fast-` — and isValid must reject. In the quick-entry bar only the text
   *before* the first `:` is sanitized, since values are free text (`film:blade runner`,
   `set:1,2,3`). The rename dialog sanitizes the whole field and disables Save while invalid,
   with a supporting line explaining the rule instead of an inert button.

**Why:** (1) and (2) are the user's call after living with the panels: a fixed half-screen
sheet to edit one rating was mostly empty space, and ADR-025's stripped-down Rated panel went
too far — losing per-instance delete meant a two-instance Rated group could only be removed
wholesale. Worth noting that ADR-021's fixed height was *itself* a response to a complaint
about the height moving around; what makes "responsive" work this time is that it's bounded,
so the sheet can't grow past half the screen no matter how many instances there are. (3) is
what makes (2) cheap — the alternative was a second copy of the drag machinery, which
`TESTING.md` already flags as the most intricate untested logic in the app. (5) makes tag
names predictable enough to type from memory and to read back in a summary
(`movie: [dune, tenet]`), and the sanitize/validate split is what lets it be enforced without
fighting the user mid-word.

**Alternatives considered:** (a) Keep Valued's fixed height and make only Rated responsive.
(b) Reject the trailing-`-` state as it's typed, rather than at save. (c) Enforce the name
rules in the repository too. (d) Show an error for disallowed characters instead of silently
dropping them. (e) Split comma-separated values in the sheet's add-value row as well, not just
at creation. (f) Require at least one star before the add-rating "+" does anything.

**Why:** (a) leaves two different height rules for two panels that are otherwise converging —
harder to explain than one rule. (b) makes hyphenated names untypeable, which is what forced
the two-halves design. (c) is the right instinct for a real invariant, but the repository is
also what *reads* legacy rows: names created before this rule still exist, must keep working,
and are still matchable by typing them (only *creation* is gated), so a hard repository-level
check would either reject renaming a legacy tag to another legacy-shaped name or need an
exemption list. The two UI entry points are the only writers today; if a third appears (an
import path, say), that's the point to add the check in the data layer. (d) fights the user
mid-keystroke for characters that can never be valid — dropping them is quieter and the rule
is spelled out where it matters (the rename dialog's supporting text). Note that dropping
applies to characters with no separator meaning (digits, punctuation); whitespace and `_` are
*translated* instead, per point 5 — `walk 2` therefore lands on the incomplete `walk-` rather
than a silently-different `walk`, which is visible and self-explaining. (e) wasn't asked for and
would make a literal comma impossible to store from the one place built for careful, single
value entry. (f) would make the sheet unable to add an unrated instance, something quick-entry
can already do (ADR-008).

**Known consequence:** a tag whose name predates these rules (spaces, capitals, digits) can
still be displayed, matched by typing its exact name, and reordered/edited — but the rename
dialog will insist on a conforming name before it saves, which is the intended migration path.
Nothing rewrites existing names automatically.

**Not verified on a device**, as ever for this screen — see `UI_UX.md`'s known-issues list for
what to try, which now includes both panels' heights and the new Rated controls.

---

## ADR-029: Tag type is an explicit single-select segmented button, defaulting to Simple

**Decision:** Creating a tag from the quick-entry bar now always shows a type picker with
**Simple** preselected, and `+` is what creates the tag using whatever is selected. The picker
is a Material **single-select segmented button row**
(`SingleChoiceSegmentedButtonRow` + `SegmentedButton`, stable API in material3 1.4.0),
replacing the three `FilterChip`s that previously appeared only for an ambiguous name and
where *tapping a chip both chose the type and created the tag in one go*.

Consequences of the type becoming a selection rather than an action:

1. **The picker is always visible while creating** — whenever the typed name is valid and
   isn't an exact match for an existing tag. It's hidden when the field is empty or resolves
   to an existing tag, since there's no type to choose in either case.
2. **The `name:***` / `name:value` shorthands (ADR-009) now move the selection** instead of
   bypassing it, via a `LaunchedEffect` keyed on the type the syntax implies. What's selected
   is therefore always what `+` will create, and the shorthand stays a shortcut rather than a
   second, hidden mechanism. A manual pick afterwards wins (the effect only re-runs when the
   *implied type itself* changes), so choosing Simple after typing `film:dune` gives a Simple
   `film` and the typed value is dropped — visible in the selector rather than silent.
3. **The "Will create a new Rated tag" hint line is gone**, along with its string: the picker
   states the type directly, so a sentence restating it is redundant.
4. **`+` is disabled** when there's nothing to add (empty or non-conforming name), matching the
   rename dialog's disabled Save (ADR-028) instead of an inert button.
5. Tapping **Valued** with no value typed still creates the tag with no instance and opens the
   instance sheet, preserving ADR-021's reasoning — a value-less Valued instance has nothing
   to display — just reached through `+` now rather than through the chip tap.
6. The selection resets to Simple after each successful add, so "preselected Simple" holds for
   every new tag rather than only the first.

**Why a segmented button:** it's Material's designated component for a small set of mutually
exclusive options that should all stay visible, and on mobile it's the conventional answer
where a desktop UI would reach for radio buttons — Material reserves radio buttons for lists
and dialogs, where each option needs its own labelled line. It also brings `selectableGroup()`
semantics, so a screen reader announces one "2 of 3"-style choice instead of three unrelated
buttons, which is exactly the accessibility difference between "select one of these" and
"three things you can tap".

**Alternatives considered:** (a) Keep `FilterChip`s but drive them as a single-select group
(`selected = type == selectedType`). (b) Actual `RadioButton`s in a column. (c) A dropdown /
`ExposedDropdownMenuBox` like the heatmap's tag picker. (d) Keep type inference as the only
mechanism and show the picker only when the input is ambiguous. (e) No default — require a
pick before `+` enables.

**Why:** (a) is the closest runner-up and would have been a smaller diff, but chips carry
filter/input semantics rather than "one of these", and getting the same `selectableGroup()`
announcement means hand-rolling what the segmented row already does. (b) costs three vertical
rows in a bar that sits above the keyboard, for the same information. (c) hides two of three
options behind a tap and adds a menu to dismiss — worse for a choice this small, though it's
the right call for the tag picker, where the list is unbounded. (d) is the behaviour being
replaced: it made the type invisible whenever the syntax implied one, which is precisely what
made "which type am I about to create?" unanswerable without knowing the shorthand. (e) would
add a required step to the most common action (a Simple tag) for no gain — the instruction was
explicitly "preselected simple".

**Not verified on a device** — see `UI_UX.md`'s known-issues list.

---

## ADR-030: The scrollbar becomes a shared `ui/components` widget, typed to `ScrollableState`

**Decision:** The instance-list sheet's private `ScrollbarTrack` moves to
`ui/components/VerticalScrollbar.kt` as a public composable, and its parameter widens from
`ScrollState` to **`ScrollableState`**. The Tags screen's `LazyColumn` now gets one too. This
also establishes `ui/components/` as the home for widgets shared *across features* —
previously there was nowhere for such a thing to live, since `CONVENTIONS.md` packages `ui/`
by feature.

**Why `ScrollableState`:** `scrollIndicatorState` is declared on the `ScrollableState`
interface, not on `ScrollState`, so widening costs nothing and both `ScrollState` and
`LazyListState` satisfy it — the drawing logic is identical for a `Column(verticalScroll)`
and a `LazyColumn`. Worth knowing that `LazyListState` implements the trio as *estimates*
(`visibleItemsAverageSize() * firstVisibleItemIndex + firstVisibleItemScrollOffset`, and a
content size extrapolated the same way), which the interface explicitly allows. For the Tags
list that estimate is exact, since every row is a `ListItem` of the same shape; for a list of
wildly varying row heights the thumb would drift slightly, which is acceptable for an
indicator and worth remembering before reusing it somewhere heterogeneous.

The "only when needed" behaviour was already in the original: the draw is skipped when
`contentSize <= viewportSize`, and equally when any of the three values is still
`Int.MAX_VALUE` ("not yet known"). One robustness fix came along with the move — the thumb's
position ratio is now `coerceIn(0f, 1f)`, since a lazy layout's estimated `scrollOffset` can
briefly exceed `contentSize - viewportSize` while the estimate settles, which would otherwise
push the thumb past the end of the track.

**Alternatives considered:** (1) Copy the ~25 lines into `TagsContent` and keep both private.
(2) Keep it in `ui/calendar/day/` and import it from `ui/tags/` (Kotlin permits it — the
package boundary isn't enforced). (3) Put it in `ui/theme/`. (4) Make it a `Modifier`
extension (`Modifier.verticalScrollbar(state)`) instead of a composable. (5) Fade the thumb in
while scrolling and out when idle, instead of showing it whenever content overflows.

**Why:** (1) is two copies of geometry that already went wrong once (ADR-021's first attempt
chained it onto the scrollable node and the thumb scrolled away with the content) — exactly
the kind of thing that should have one home. (2) would leave the Tags screen depending on the
calendar's Day package for a scrollbar, which is the sort of quiet coupling the
package-by-feature rule exists to prevent. (3) `theme/` is for colors/typography, not
widgets. (4) reads nicer at the call site but is what ADR-021 tried and abandoned: a modifier
on the scrollable node draws inside the scrolled coordinate space; a modifier on a *wrapper*
would work but hides the fact that this needs to be an overlay sibling, and the sizing
subtlety (`matchParentSize`, not `fillMaxHeight`) is easier to state as a call-site argument
than to bury. (5) is nicer on a phone and is what platform scrollbars do, but it needs an
animation + idle-timeout and an `InteractionSource`/`isScrollInProgress` hookup; deferred as
polish rather than bundled into a move — the always-visible-when-overflowing version is what
the sheet has shipped with and it's legible.

**Not verified on a device.** Same Composable gap as ever (`TESTING.md`) — `UI_UX.md`'s
known-issues list has the checks.

---

## ADR-031: Rated creation defers to the sheet like Valued; Simple gets a count-only panel

**Decision:** Two changes to what happens when a tag is created or tapped.

1. **Creating a Rated tag without a `:***` seed now creates the tag with no instance and
   opens the instance sheet**, exactly as Valued already did. `createValuedTagForEditing`
   generalises to `createTagForEditing(name, type)` and `pendingValuedTagEdit` to
   `pendingTagEdit`; the sheet's synthetic empty group was already type-driven, so the Rated
   panel opens with an empty list and its add-rating row ready (ADR-028). Typing `mood:***`
   still creates the tag *with* that rating in one step — only the unseeded case defers.
2. **Simple capsules are tappable again** (superseding ADR-018), opening a panel whose only
   control is a stepper over how many times the tag applies to that day: `−  3  +`. "+" adds
   an instance; "−" deletes the newest one **immediately**, with no undo snackbar. The count is
   floored at 1 — removing the tag from the day entirely stays the capsule's "x".
   `CalendarViewModel.addExistingTag` is renamed `addInstance` since the count editor uses the
   same path, and a new `removeInstanceImmediately` provides the non-undoable delete. The minus
   glyph is a hand-added `ic_minus.xml`, since `material-icons-core` has no minus (ADR-010).

**Why (1):** ADR-021 gave Valued the defer-to-sheet treatment because a value-less Valued
instance displays as nothing useful, and explicitly kept Rated creating a blank instance
*because* Rated has a sensible empty display — ADR-008's rule that an all-unrated group
summarises like Simple. That reasoning was about what's displayable, not about what someone
choosing "Rated" is trying to do: picking Rated and pressing "+" means "I want to rate this",
and landing on a bare `freediving` with no way to rate it without a second tap into the sheet
is the wrong default. ADR-008's fallback is untouched and still needed — an instance can
become unrated by other routes (the sheet's "+" with no stars picked, ADR-028) — it just isn't
the shape *creation* aims for any more.

**Why (2):** ADR-018 removed the Simple sheet because its content was a timestamp and a delete
button, i.e. a slower path to what the capsule "x" already did in one tap. That was correct,
and this doesn't undo it: the new panel shows neither timestamps nor per-instance rows. Count
*is* the one piece of state a Simple tag has on a day (`walk (2)` is a count, and it's already
in the summary), and until now the only way to reach 3 was to type the name into quick-entry
three times, with no way down from 3 except deleting the whole group and starting over. A
stepper is the smallest control that closes that gap.

**Why "−" skips delay-delete:** a Simple instance holds nothing but its own existence, so "+"
restores an equivalent one exactly — there's nothing an undo could recover that the adjacent
button doesn't. Worse, ADR-019's snackbar says "'walk' removed", which is plainly wrong for a
decrement from 3 to 2. Undo still guards the paths where something is actually lost: the
capsule "x" (the whole group) and the Rated/Valued per-instance deletes.

**Alternatives considered:** (a) Let the Simple count go to 0, closing the sheet and removing
the group. (b) Route "−" through delay-delete for consistency with ADR-019. (c) A text field
for the count instead of a stepper. (d) Remove the oldest instance rather than the newest.
(e) Keep Rated's creation as-is and let the user tap into the sheet themselves.

**Why:** (a) gives two removal paths that behave differently — the "x" is undoable, a stepper
to 0 wouldn't be — and makes the sheet dismiss itself as a side effect of a decrement, which
reads as a glitch. (b) is covered above. (c) invites typing 400, needs validation and a
keyboard for a number that's realistically 1–5, and can't beat two taps. (d) is
indistinguishable to the user (Simple instances are interchangeable and show no timestamps
since ADR-025) but makes repeated −/+ churn `sortOrder` unnecessarily; taking the newest makes
the pair a true no-op. (e) is the behaviour being changed, and leaves "Rated" as the one type
whose creation doesn't get you to a rating.

**Note on ADR-026/ADR-027:** Simple's body `clickable` being *disabled* was what let the ✕'s
inflated touch target steal body taps. That case no longer exists, but ADR-027's exact-bounds
trick stays — it's what keeps the ✕'s target where it looks, and without it the ✕ would inflate
into the FlowRow's gaps and compete with capsules in adjacent rows.

**Not verified on a device** — see `UI_UX.md`'s known-issues list.

---

## ADR-032: M5 splits — local JSON export/import first, the Drive transport deferred behind it

**Decision:** M5 becomes two milestones. **M5a** defines the backup *document* and ships it as
local export/import from the Settings screen — no account, no network, no new permissions
(Storage Access Framework `CREATE_DOCUMENT`/`OPEN_DOCUMENT`). **M5b** is the Drive transport
exactly as ADR-015 describes it, uploading the same document M5a already produces. ADR-015 is
**not** superseded: hidden `appDataFolder`, one rolling backup, replace-not-merge, and
periodic-with-manual-override all still stand for M5b. What changes is the order, and that M5b
is now explicitly conditional — whether it's worth its cost gets decided after living with
M5a, not now.

Decisions M5a settles, because they're expensive to revisit once a file format is in the wild:

1. **`kotlinx.serialization`** for the payload, not `org.json` or hand-rolled strings.
2. **`schemaVersion`, with rules**: an older version migrates on read; a **newer** version is
   *refused outright* rather than parsed leniently. Silently dropping fields it doesn't
   understand would turn "restore an old app build" into quiet data loss.
3. **Import fully replaces** local `Tag`/`TagInstance` data, matching M5's original
   done-criteria — and writes a pre-import export of the current state first, so a bad import
   is recoverable. The app makes deleting one instance undoable (ADR-019); replacing the entire
   database shouldn't be the one destructive act with no way back.
4. Two corrections to `BACKUP_SYNC.md` that carry into M5b: **never auto-backup when local data
   is empty and a remote backup exists** (otherwise a declined restore prompt followed by the
   24h staleness trigger overwrites the good backup with nothing), and use **WorkManager** for
   the automatic path — "not a background sync service" rules out continuous sync, not a
   one-shot constrained job, and it answers the retry/backoff question the doc left open.

**Why:** the live problem isn't that backups are missing, it's that
`fallbackToDestructiveMigration(dropAllTables = true)` means the next schema change wipes real
data (`DATA_MODEL.md` § Schema history). A local export defuses that *now*, with none of
Drive's cost. It's also the only part of M5 that's fully unit-testable in this project's setup
— a round-trip over the format needs no device, whereas the transport can't be tested here at
all. And M5b's real cost is mostly non-code: a Cloud project, consent-screen configuration,
and OAuth client IDs bound to package name + signing-certificate SHA-1 — which means it's
blocked on release signing, which doesn't exist yet (`BUILD_RELEASE.md`). Sequencing the
format first takes that blocker off the critical path instead of parking the whole milestone
behind it.

**Alternatives considered:** (1) Build M5 as specced, Drive first. (2) Replace Drive with local
export permanently — cancel M5b outright. (3) Lean on Android's auto-backup instead of building
either. (4) `org.json` instead of `kotlinx.serialization`, to avoid a dependency. (5) Copy the
Room `.db` file instead of serializing to JSON.

**Why:** (1) puts the untestable, externally-blocked half first and leaves the data-loss risk
open in the meantime. (2) is tempting and may well be where this lands — a single-device
personal app may get 90% of the value from a file you can copy off the device — but it's a
guess until the export exists; deferring keeps the option, cancelling spends it. (3) was
already rejected by ADR-015 for a reason that still holds (it can't be triggered or restored on
demand), and note it's *partly on right now* — see the open item below. (4) would save a
dependency and cost the thing that matters most here: `org.json` is stubbed on the JVM
unit-test classpath, so every test touching it fails with "not mocked" unless the whole module
opts into returning default values. Choosing it would make the one genuinely testable piece of
M5 untestable, which is backwards. `kotlinx.serialization` also makes the schema explicit in
the type system, which point 2 depends on. (5) is less code, but the `.db` file is the
*schema-coupled* artifact — restoring a v3 file into a v4 app lands straight back in the
destructive-migration path this is meant to escape. A versioned JSON document is
schema-tolerant by construction, which is the entire point of doing it this way.

**Open, and required before M5b:** the manifest still has `allowBackup="true"` with the
template's empty rules, so Android's own auto-backup may already be copying the Room database —
including restoring an older-schema file into the destructive path. That overlap needs a
deliberate answer (exclude the database and let the export/Drive own it, or keep both
knowingly). It's left open here rather than decided in passing because it's a privacy call —
whether a diary belongs in the OS backup stream — not a technical one.

---

## ADR-033: A period-navigation row at Week/Month/Year; Day deliberately left out for now

**Decision:** Week, Month and Year gain a row above their content: `‹  July 2026  ›`
(`PeriodNavigationRow`). The arrows step the focused period by one unit — the same
`onStepTime` the horizontal swipe already calls — and the label is tappable, opening a
`DatePickerDialog` for jumps no amount of swiping is worth. Picking a date calls a new
`CalendarViewModel.setFocusedDate`, which moves the date *without* changing zoom level, unlike
`jumpToDay`. The label itself comes from `CalendarPeriodLabels`, a pure locale-parameterised
formatter so the awkward cases have unit tests: a week inside one month says the month once
(`20 – 26 July 2026`), a week straddling months or years names both
(`27 Jul – 2 Aug 2026`, `28 Dec 2026 – 3 Jan 2027`).

**Day zoom is deliberately excluded**, and so is any change to the jump-to-today button.

**Why here and not on Day:** Week, Month and Year currently name their period *nowhere*. A
Month grid is bare day numbers with no "July 2026" anywhere on screen; a Year grid shows month
abbreviations with the year present only inside a per-tile content description, invisible
unless you're using a screen reader. At Month zoom you genuinely cannot tell which month you're
looking at except by spotting the today-ring. That's a defect, and the row fixes it. Day has
the opposite problem: its header card already shows the month/year band, a large day number,
the weekday *and* a past/today/future pill, so the same row there would state the date twice
while costing roughly 1.3 rows of tag capsules (the capsule area is about 420dp ≈ 7 rows on a
360×800 screen). Day's version is worth doing as a redesign of that card — arrows on the card
itself, no new height — which is a separate change.

**On not repeating ADR-012:** a dedicated strip with a label and chevrons was built once before
and removed by explicit request, for exactly the space it reserved (ADR-012 Amendment 2); the
discoverability it provided was later recovered by moving the control into the app bar
(ADR-014's `ZoomLevelPicker`). This row is knowingly the same *shape* as the thing that was
removed, and the difference that justifies it is narrow: the old strip displayed the zoom
level, which the app bar could carry just as well, whereas this one displays the period, which
at these zoom levels exists nowhere else. Where that isn't true — Day — the row isn't used.
Fitting the arrows alongside a gesture, rather than replacing it, follows ADR-014 directly: the
swipe stays the fast path, and the buttons make it discoverable while giving the time axis a
non-gesture route for anyone who can't comfortably swipe.

**Alternatives considered:** (1) Put the period label in the top bar instead of a row. (2) Do
Day first, as originally proposed. (3) Tap the date to jump to Month zoom rather than opening a
picker. (4) Label only, no arrows — leave stepping to the swipe. (5) Remove the jump-to-today
button now, as originally proposed.

**Why:** (1) is where ADR-014 put the zoom picker and it's the cheaper option for space, but
the title slot already holds the zoom picker plus a conditional today button, and adding
`‹ date ›` beside them crowds it past the point of being tappable — revisit if the row proves
unwelcome. (2) inverts the value: Day is where the row adds least and costs most. (3) doesn't
work — Month zoom is a *single-tag heatmap*, not a general calendar grid, and shows "pick a
tag" until one is selected, so tapping a date to navigate would frequently land on an empty
state. (4) leaves the discoverability and accessibility gaps that motivated the row's ancestor
in ADR-012 Amendment 1, where a swipe-only affordance proved too easy to miss. (5) was rejected
for now: "back to today" is the app's most common navigation and the button makes it one tap,
where the picker makes it three (open, navigate, confirm). The button is Day-only, so it isn't
even in this change's way; if the row later comes to Day, the right move is to relocate the
button into it rather than delete it, keeping ADR-017's conditional visibility.

**Implementation note:** `DatePicker` deals in UTC-midnight millis regardless of device zone,
so both conversions pin to `ZoneOffset.UTC`. Going through the local zone shifts the selected
date by a day for anyone west of Greenwich.

**Not verified on a device** — see `UI_UX.md`'s known-issues list.

---

## ADR-034: Quick-entry's `:` seed applies to tags that already exist; a bare name opens the sheet

**Decision:** Fixes BACKLOG F4. `TagQuickEntryBar.submit()` currently checks
`exactMatch != null` *before* the type branches and short-circuits to
`onAddExistingTag(id)` → `addInstance(tagId, date)` with `rating = null, value = null`, so
everything after the `:` is silently thrown away for any tag that already exists. Instead, an
existing tag is dispatched on **its own type**:

| Existing tag | Typed | Result |
|---|---|---|
| Simple | `walk` | one more instance (unchanged) |
| Rated | `mood:***` | instance with `rating = 3` |
| Rated | `mood` | tag's sheet opens, ready for the first rating |
| Valued | `film:dune` | instance with `value = "dune"` |
| Valued | `film:dune,tenet` | one instance per value (as at creation, ADR-028) |
| Valued | `film` | tag's sheet opens, ready for the first value |

This makes ADR-009's shorthand mean the same thing whatever the tag's history, and makes the
bare-name case behave exactly like creating a fresh Rated/Valued tag already does under
ADR-021/ADR-031 — the sheet opens rather than an empty instance being guessed at.

**A seed that can't apply to the tag's type is ignored**, and the tag's type always wins:
typing `walk:***` where `walk` is Simple adds a plain instance. Tag type is immutable
(`CLAUDE.md` § Hard rules), so there's nothing else the app *could* do, and the type picker
isn't even displayed once the name matches an existing tag, so no UI is contradicted.

**Why:** the current behaviour's worst case isn't the dropped text, it's that the app appears
to do nothing. A value-less instance on a Valued tag is invisible in the capsule —
`TagDisplayGroups.summarize`'s Valued branch drops nulls via `mapNotNull { it.value }` — so
pressing "+" produces no visible change while quietly adding a row that only shows up inside
the sheet; on a day with no other instances the capsule reads `film: []`. That's the same
defect ADR-021 point 5 already fixed for tag *creation*, left in place on the path that gets
used far more often: adding today's value to a Valued tag you've had for months is the single
most common Valued action there is. Fixing creation and not this was an oversight, not a
distinction.

**Alternatives considered:** (1) Apply the seed, but let a bare name keep adding a value-less
instance, fixing only the `film: []` display. (2) Always open the sheet for an existing
Rated/Valued tag, ignoring the seed entirely. (3) Refuse the submit (or warn) when a seed
can't apply to the existing tag's type, rather than ignoring it.

**Why:** (1) is the smaller diff and leaves the rough edge that motivated this — an instance
you can create but can't see. (2) has one rule and no special cases, which is genuinely
attractive, but it makes `:value` a creation-only shorthand and puts a tap between the user
and the most common action; the seed is unambiguous when it's there, so honouring it costs
nothing. (3) sounds safer than it is: the mismatch is only reachable by typing `:` on a name
that already exists as another type, the type picker is hidden in that state so nothing has
promised otherwise, and a refusal would block a submit the user has no way to satisfy short of
renaming the tag.

**Implementation note:** opening the sheet for an *existing* tag needs the single-shot
`pendingTagEdit` signal that `createTagForEditing` currently sets — so the ViewModel needs a
way to request it without creating anything (`requestTagEdit(tagId)`), and
`CalendarScreen`'s `selectedGroupIsFreshTag` flag stops meaning "just created" and starts
meaning "may legitimately have no instances on this day". The synthetic empty
`TagDisplayGroup` fallback it guards is needed in exactly the same way here: a tag that exists
but has nothing on the focused date has no entry in `dayGroups`. Worth renaming with the
change, since the old name will actively mislead.

**Not verified on a device** — see `UI_UX.md`'s manual-check list.

---

## ADR-035: Calendar state moves into `SavedStateHandle`; the heatmap tag is remembered per session

**Decision:** Fixes BACKLOG F10 and F12. Three related changes to how calendar state survives:

1. **`CalendarViewModel.query` is backed by `SavedStateHandle`** rather than a plain
   `MutableStateFlow`, so zoom level, focused date and selected heatmap tag survive process
   death. `LocalDate` is stored as an epoch day `Int`, the same representation the DAO layer
   already uses (`DATA_MODEL.md` § `TagInstance`), so nothing new has to be made parcelable.
2. **Month/Year therefore stop landing on "Pick a tag" every time.** The empty state remains
   the *initial* state — with nothing remembered there's genuinely nothing to show — but once
   a tag has been picked, zooming back out returns to it instead of resetting.
3. **UI-layer dialog/sheet state becomes `rememberSaveable`**: `selectedGroupKey` in
   `CalendarScreen`, `renamingTag`/`recoloringTag` in `TagsScreen`. Currently plain `remember`,
   so rotating the device with a sheet or dialog open closes it. Tags are keyed by id, which is
   already `Saveable`.

**Explicitly deferred: memory across a cold start.** `SavedStateHandle` survives process death
while the task lives, not a fresh launch after the task is swept. Carrying the heatmap tag
across that needs a real preferences store, and this project has twice declined a dependency
for a single feature (ADR-010, ADR-011) — the honest sequencing is to live with session-scoped
memory first and see whether the cold-start case is even felt.

**Why:** these are the same bug wearing three hats — state the app treats as ephemeral that
the user experiences as durable. F12 reads as a UX complaint ("the heatmap forgets") and F10
as a lifecycle one ("rotation closes my sheet"), but both are "nothing here is saved". Doing
them together is also cheaper than doing them apart: `SavedStateHandle` is one constructor
parameter and one accessor style, and splitting it across two changes means touching
`CalendarViewModel`'s state plumbing twice.

Worth noting what this does *not* fix: `CalendarUiState.focusedDate` defaults to
`LocalDate.now()` evaluated at construction, and nothing re-reads it as the day rolls over
(BACKLOG F6). Restoring a saved focused date makes that staleness *more* visible, not less —
reopen the app the next morning and it restores yesterday. F6 should land with or before this.

**Alternatives considered:** (1) Auto-select the first tag for the heatmap instead of
remembering one. (2) Keep the "Pick a tag" empty state as-is and close F12 as intended
behaviour. (3) Add DataStore now and persist properly across cold starts. (4) Lock the app to
portrait, which removes the rotation half of F10 outright.

**Why:** (1) always shows something, but what it shows is arbitrary (alphabetically first) and
a heatmap of a tag you didn't ask about is noise that looks like signal. (2) is defensible —
choosing the tag *is* using the feature — but the cost falls on the most ordinary gesture in
the app: swiping down from Week hits a dead end every single time. (3) is where this may well
end up, and M5a will bring file I/O into the project anyway; it's deferred rather than
rejected. (4) is a real option for a single-user portrait-first app and would also defuse part
of BACKLOG F23 — kept on the table, but it answers the rotation case by removing rotation,
which is a bigger product decision than this ADR should make in passing.

**Not verified on a device** — see `UI_UX.md`'s manual-check list.

---

## ADR-036: The calendar's state stops lying — paired emissions, a live "today", a ViewModel-owned undo window

**Decision:** Three fixes to `CalendarViewModel` that share one root cause: state the UI
presented as fact that wasn't. Fixes BACKLOG F5, F6 and F7. (F10/F12's `SavedStateHandle` lands
alongside them under ADR-035.)

1. **The query travels with its data.** `uiState` no longer collects `query` as a `combine`
   source of its own. It was both a source *and* the input to the `flatMapLatest` producing
   `periodData`, so `combine` fired the moment the query changed and paired the new query with
   the **previous** query's data — a real emission carrying tomorrow's date under today's
   capsules, or `zoomLevel = WEEK` while `periodData` was still `Day`. `CalendarContent`'s
   `as? CalendarPeriodData.Week` casts are what turned the latter into a blank screen instead
   of a crash. Now `query.flatMapLatest { q -> periodDataFlow(q).map { q to it } }` emits the
   pair, so a `CalendarUiState` is consistent by construction.
2. **`today` is observed, not read.** A `Flow<LocalDate>` that emits the current date and
   re-emits at the next local midnight, exposed as `CalendarUiState.today`. Every
   today-highlight — the Day header's past/today/future pill, Week's row, Month's ring, Year's
   tile border, the jump-to-today button's visibility — took its own `LocalDate.now()` during
   composition, so none of them ever changed: left open past midnight, the app kept calling
   yesterday "Today".
3. **The undo window moved into the ViewModel.** `beginPendingRemoval` now launches its own
   `viewModelScope` timer. The commit previously lived in the snackbar's `LaunchedEffect`, so
   navigating to Tags or Settings inside the window cancelled it with *neither* branch taken —
   the instances stayed hidden from Day zoom but were never deleted, and the snackbar
   reappeared on the way back. The snackbar is now display-and-undo only.

**Why:** these look like three unrelated bugs and are really one habit — deriving state at the
point of *use* rather than owning it. Each was invisible in the settled state, which is why
they survived: `uiState.value` was always right afterwards, `LocalDate.now()` is right at the
moment you call it, and the pending removal did commit as long as nobody navigated. They only
show up in the transitions, which is where a user actually lives.

Point 3 is also the one that could lose data, in the sense that matters for a diary: it
couldn't delete anything the user hadn't asked to delete, but it could leave the app claiming
something was removed when it wasn't. Failing toward "still there" is the right direction, and
that's still what happens if the process dies mid-window — `viewModelScope` is cancelled, the
timer dies, the instances survive. Worth stating plainly rather than pretending the window is
transactional.

**Alternatives considered:** (1) For point 1, keep the immediate query emission and blank the
period data until the matching data lands. (2) For point 2, a `BroadcastReceiver` on
`ACTION_DATE_CHANGED`/`ACTION_TIMEZONE_CHANGED`. (3) For point 2, recompute today only when the
screen resumes, with no timer at all. (4) For point 3, commit the pending removal from
`onCleared()`, or from a `DisposableEffect` when the screen leaves composition.

**Why:** (1) trades a wrong-data flash for an empty-data flash, and "Nothing tagged yet" on
every swipe reads worse than a frame of lag; the DB round-trip here is a local query over a
handful of rows. (2) is the thorough answer and would also catch a manual clock change, but it
needs a registered receiver and a lifecycle to unregister it, for a case a personal diary meets
approximately never — the timer plus `WhileSubscribed`'s restart-on-resubscribe already covers
rollover-while-open and any change that happened while backgrounded. Worth revisiting only if
the gap is ever actually felt. (3) misses the case the finding was about: the app sitting open
across midnight. (4) doesn't work for the navigation case, which is the common one:
`onCleared` isn't called when you merely navigate to another destination, and it isn't
guaranteed on process death either, while a `DisposableEffect` would also fire on rotation and
so cut the undo window short. Owning the timer where the state lives makes the lifecycle
question moot.

**Implementation note:** `Clock` is now injected (`di/TimeModule.kt`) rather than reached for
statically, so the midnight rollover is testable — `MutableClock` in the test sources moves the
date while `runTest`'s scheduler moves the delay, and the two together cross midnight in a
unit test. Without that, point 2 could only be verified by waiting until midnight.

**Verification:** point 1's test records *every* emission rather than asserting on `.value`,
and was confirmed to fail against the previous implementation before the fix was kept. Points 2
and 3 have tests that drive virtual time. **Not verified on a device** — see `UI_UX.md`'s
manual-check list.

---

## ADR-037: A taken tag name resolves instead of throwing; value edits are debounced

**Decision:** Two changes to the write paths behind quick-entry and the instance sheet.
Addresses BACKLOG F11 and F18 in full, and the *reachable crash* half of F3.

1. **`TagDao.insert` uses `OnConflictStrategy.IGNORE`**, and `TagRepositoryImpl.createTag`
   resolves a rejected insert to the existing tag via a new `findByName`, returning `Long?`
   (null only if the name is neither insertable nor findable — deleted in between). `tags.name`
   carries a unique index, quick-entry's duplicate guard compares against `allTags` as of the
   last flow emission, and two fast "+" presses both pass it — so the second insert threw
   `SQLiteConstraintException` straight out of `viewModelScope` and killed the process.
2. **`ValueField` debounces its write** (400ms) and persists the *trimmed* text. It previously
   wrote on every keystroke — one Room `UPDATE` per character, each round-tripping back through
   the day's flow to recompose the sheet — while persisting untrimmed text it had only
   validated trimmed, so `"dune "` is what reached the database.

`ImeAction.Done` on quick-entry and the add-value field is folded in as the same kind of
change: both are `singleLine` fields whose only submit was a button tap.

**Why:** for (1), a constraint violation here isn't an error condition worth surfacing — it
means the tag the user was trying to make already exists, which is precisely the state the
caller wanted to reach. Resolving is what they meant. Crashing was never a defensible response
to pressing a button twice.

For (2), the debounce is not primarily about write volume — a handful of `UPDATE`s is nothing
for SQLite. It's that each write came back through the observing flow and recomposed the sheet
mid-typing, which is the kind of thing that produces cursor and IME oddities that are miserable
to diagnose later. Trimming on write closes a real (if small) data defect: the guard and the
value disagreed about what was being saved.

**Alternatives considered:** (1a) `OnConflictStrategy.REPLACE` on insert. (1b) Wrap the write
in `try`/`catch` and surface an error to the UI. (1c) Make the duplicate check atomic with a
single "get or create" transaction. (2a) Write on focus loss instead of debouncing. (2b) Keep
writing per keystroke and accept it.

**Why:** (1a) is actively dangerous — `REPLACE` on a conflicting unique index *deletes* the
existing row and inserts a new one, which with `ON DELETE CASCADE` on `tag_instances` would
take every instance of that tag with it. A tag's entire history, silently, because someone
double-tapped. (1b) is the right shape for genuine failures and remains open as the rest of F3,
but it's the wrong answer for this particular case, where there's nothing to report. (1c) is
the textbook fix and would also close the race properly rather than resolving after the fact;
it's more machinery than this earns, and the resolve path reaches the same end state. (2a)
misses the case where the sheet is closed with the keyboard still up. (2b) is the status quo,
and the recomposition-during-typing risk is what argues against it.

**Still open in F3:** there is no general error path. Every repository call remains a bare
`viewModelScope.launch { … }` with no `catch` and no error field on `CalendarUiState`, so a
genuine failure (disk full, corrupted database) still crashes. That needs a UX decision about
what the user should see, which is why it isn't bundled here.

**Not verified on a device** — see `UI_UX.md`'s manual-check list.

---

## ADR-038: Week and the heatmaps get semantics; counts become plurals

**Decision:** An accessibility pass over the parts of the calendar that communicated purely by
colour. Fixes BACKLOG F13, F14, F16 and F9.

1. **A Week row announces itself.** `WeekDayRow` merges its descendants and carries one
   description — "Monday 20 July: walk, reading", or "…: nothing tagged". Its coloured dots had
   no semantics whatsoever, so the entire Week zoom level was silent to a screen reader, and
   the dots are unlabelled by eye too once two tags land on similar colours (which ADR-009's
   `size % palette` assignment makes likely — BACKLOG F19).
2. **A heatmap cell announces its date and count.** `HeatmapDayCell` now takes the `LocalDate`
   it represents and describes itself as "Wednesday 15 July, 3 times". The count previously
   existed *only* as background alpha, reaching neither a screen reader nor anyone who can't
   separate the shades. Year's month tiles get the same treatment with the month total, since
   ADR-016 already established that their per-day squares are too small to be individual
   targets.
3. **Star ratings say what the rating is.** The `StarInput` row carries "Rated 3 stars" (or
   "Not rated"); each star says what tapping it *does* — "Rate 4 stars". Every star was
   previously labelled with its own index, so TalkBack read "1 stars, 2 stars, 3 stars, 4
   stars, 5 stars" and never stated the current value.
4. **Counted strings become `<plurals>`.** The rating labels and the delete dialog's message,
   both of which lint had flagged. And the delete dialog now counts the right thing:
   `TagDao.instanceCount` was `COUNT(*)` over instances while the message said "tagged day(s)",
   so a tag applied twice in one day overstated its reach. It's `COUNT(DISTINCT date)`, renamed
   `taggedDayCount` through the repository, ViewModel and UI state so the name can't drift from
   the meaning again.

**Why:** points 1 and 2 are the same defect — a view whose entire content is colour. That's
invisible to a screen reader by construction, and it's the textbook case for not encoding
information in colour alone. It matters more here than the usual argument suggests, because
Week and Month/Year are the *overview* levels: they're where you look to see a pattern, and
without them the app is Day-zoom-only for anyone using TalkBack.

Point 4's counting bug is small but it's the kind that erodes trust in a confirmation dialog —
the one place the app asks "are you sure?" should not overstate what's about to happen.

**Alternatives considered:** (1a) Label each Week dot individually rather than merging the row.
(2a) Draw the count as text in each heatmap cell. (3a) Leave the star labels and let the sheet's
title carry the rating.

**Why:** (1a) means seven-plus focus stops per row and a screen reader user swiping through
dozens of unlabelled nodes to assemble one day; merging is what makes the row a single useful
utterance. (2a) is a genuine option and would help sighted users too, but a Month cell is
already tight at this density and Year's are a few pixels — the whole reason ADR-016 dropped
day numbers from Year. (3a) doesn't work: the row is the control being operated, so it's the
thing that has to report its state.

**Still open:** `alphaForCount` saturates at 3+, so 3 and 30 render identically (BACKLOG F14's
second half). That's a visual-design question about bucket boundaries rather than an
accessibility one, and the content description now states the true count regardless.

**Not verified on a device, and this is the change where that matters most** — semantics
merging and announcement order really need TalkBack to judge. See `UI_UX.md`'s manual-check
list.

---

## ADR-039: Instrumented tests are possible here after all, and Week's semantics get the first one

**Decision:** `app/src/androidTest/` exists, starting with `WeekContentTest`. This reverses
`TESTING.md`'s standing claim that instrumented tests need "a connected device/emulator,
unavailable in this project's usual working environment" — the same shape of reversal ADR-024
made for ViewModel tests, and for the same reason: the stated obstacle turned out not to be
real.

**Why the claim was wrong:** the machine has the emulator package, x86_64 system images for API
36/36.1, and a usable `/dev/kvm` (an ACL grants the user read/write, so the missing `kvm` group
membership doesn't matter). Only an AVD was absent, and none had ever been created. What's
genuinely missing is `cmdline-tools`, hence no `avdmanager` — but an AVD is two `.ini` files,
so that's a detour rather than a blocker. `TESTING.md` § Instrumented tests records the setup.

**Why Week first:** BACKLOG F13. `WeekContent` renders one coloured dot per tag and no text
whatsoever, so before ADR-038 a screen reader got nothing from an entire zoom level. That fix
lives *only* in the composed semantics tree — there is no pure function to unit-test, and
`QuickEntryAction`-style extraction doesn't apply. It was the clearest example of a fix that
could be reasoned about but not verified, which makes it the right thing to point the new
capability at.

**Alternatives considered:** (1) Keep relying on `@Preview` plus the manual check list.
(2) Add Robolectric and run Compose tests on the JVM instead. (3) Install `cmdline-tools` and
use `avdmanager` properly.

**Why:** (1) is what the false premise forced, and the cost is visible in `UI_UX.md` — nineteen
accumulated manual checks, none of them run. (2) is still wanted and is the next step: it gives
a fast layer that runs on every build with no emulator, which suits semantics and state-driven
behaviour. It doesn't replace this one, since it can't judge real rendering, gesture feel, or
actual TalkBack. Running both was the explicit choice. (3) is the tidy answer and worth doing if
AVDs are ever needed routinely; hand-writing one config file was cheaper than a 150MB download
for a one-off.

**A caution, learned the hard way:** a Compose semantics assertion passes for the wrong reasons
very easily. `onNodeWithContentDescription` searches the **merged** tree, and `clickable` sets
`mergeDescendants` by itself, so a row can appear labelled when nothing labelled it. Every UI
test here should be shown to fail with its fix removed. The first attempt at exactly that check
silently mutated nothing — the target line ended `},` not `}` — and three tests "passed"
against untouched code, which is precisely the failure mode this note exists to prevent.

**Verified:** all three tests fail with `WeekContent`'s `semantics(mergeDescendants = true)`
removed, and pass with it restored.

---

## ADR-040: Compose tests run on the JVM under Robolectric; the emulator is a local tool, not a build step

**Decision:** Supersedes ADR-039's placement, not its reasoning. The Compose tests move from
`app/src/androidTest/` to `app/src/test/` and run under Robolectric as part of
`testDebugUnitTest`. `app/src/androidTest/` is deleted. Instrumented testing stays *possible* —
ADR-039's finding that this machine can run an emulator stands, and `TESTING.md` keeps the
setup — but it's a local tool for manual checks, not something the build or CI depends on.

**Why:** the three tests ADR-039 added run in ~3 seconds on the JVM with no emulator, no AVD
and no `ANDROID_SERIAL` juggling, and they run on *every* build rather than when someone
remembers a separate Gradle task. Nothing was lost in the move: they assert on the semantics
tree, which Robolectric composes for real.

The dependency question deserves a straight answer, since ADR-010 and ADR-011 both refused one.
Those were **runtime** dependencies — `material-icons-extended` for a single glyph, a
colour-picker library — where the cost is APK size shipped to users, with R8 disabled. Robolectric
is `testImplementation`: it never reaches the APK. The costs are a one-time jar download and unit
tests that go from microseconds to ~1s each. Different trade, different answer.

Robolectric also unlocks something the emulator was going to be needed for anyway: it ships a
real SQLite, so `Room.inMemoryDatabaseBuilder` works on the JVM. The DAO layer — the largest
untested surface in the app, and the groundwork for migration testing (F1, F2) — becomes
testable without a device at all.

**Alternatives considered:** (1) Keep instrumented tests and add Gradle Managed Devices so CI
can boot an emulator. (2) Keep instrumented tests, run them locally only, leave CI alone.
(3) Robolectric, but keep `androidTest/` too for a handful of things. (4) Neither — go on
extracting pure logic and accept that semantics are untestable.

**Why:** (1) triples the CI runtime to run three tests, and needs a KVM setup step on the
runner; worth revisiting if the DAO/migration suite grows into something worth gating on.
(2) is what ADR-039 left in place, and its flaw is that a test nothing runs automatically is a
test that rots. (3) is the honest long-term shape — real gesture arbitration (ADR-022) can't be
trusted to a simulation — but there's nothing in `androidTest/` worth the second source set
today, and it can come back the moment there is. (4) is where the project was, and the cost was
ADR-038's whole accessibility pass shipping unverified.

**What Robolectric does not cover**, and where the emulator or a device is still the authority:
real rendering and contrast, gesture feel and timing, the drag-reorder arbitration ADR-022 took
five attempts to get right on hardware, and actual TalkBack. `UI_UX.md`'s manual list stays.

**A concrete instance of that limit, found immediately:** moving the tests turned one red.
Robolectric's default screen is far shorter than the emulator's, and `WeekContent` is a
*non-scrolling* `Column` of seven rows — so the last days were clipped with no way to reach
them. The emulator's 2400px screen had hidden it. That's a real defect (BACKLOG F25), found by
the environment change rather than by the assertion, and the tests now pin a realistic screen
size (`@Config(qualifiers = "w411dp-h891dp")`) so they test semantics rather than whatever the
runner defaults to.

**Coverage measurement was attempted and abandoned:** Kover 0.9.1 registers no report tasks
against AGP 9.3.1 — "No sources", and no per-variant tasks appear. It looks like a plain
version incompatibility. Worth retrying when Kover supports AGP 9; until then coverage is
judged structurally in `TESTING.md`.
