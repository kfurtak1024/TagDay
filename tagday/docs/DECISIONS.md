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
