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
