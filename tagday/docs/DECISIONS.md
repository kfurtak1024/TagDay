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
