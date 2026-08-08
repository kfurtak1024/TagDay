# TagDay — Data Model

Room schema derived directly from `FEATURES.md`. Two entities only — no separate `Day`
entity, no domain-layer models yet (see `ARCHITECTURE.md` for why).

## Why only two entities

- **No `Day` entity.** A day is just a date; most days won't have tags at all, and the
  ones that do are fully represented by their `TagInstance` rows. Adding an empty `Day`
  row per calendar date would just be bookkeeping with no query or integrity benefit.
- **`type` lives on `Tag`, not `TagInstance`.** Per the resolved decision in `FEATURES.md`,
  a tag's type is fixed at creation and immutable. Storing it on `Tag` (rather than
  duplicating it on every instance) keeps it as a single source of truth and makes
  "same tag, different type" structurally impossible — exactly the constraint we want.

## Entities

### `Tag`

The tag repository. One row per distinct tag name.

| Column | Type | Notes |
|---|---|---|
| `id` | `Long` (PK, autogenerate) | Stable internal id; everything else references this, not the name. |
| `name` | `String` | Unique, case-insensitive (`NOCASE` collation + unique index). Renamable. Shape is constrained to `^[a-z]+(-[a-z]+)*$` at the UI entry points (`data/model/TagName`), not by the schema — rows predating the rule are still valid data. See ADR-028. |
| `type` | `String` enum (`SIMPLE` / `RATED` / `VALUED`) | Fixed at creation, immutable — no update path is exposed for this column. |
| `color` | `Int` | 32-bit ARGB, from fixed palette or custom picker. |
| `createdAt` | `Long` (epoch millis) | For sort order in the Tags view (e.g. "recently created"). |

```kotlin
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name", collate = ColumnInfo.NOCASE) val name: String,
    val type: TagType,
    val color: Int,
    val createdAt: Long
)

enum class TagType { SIMPLE, RATED, VALUED }
```

### `TagInstance`

One row per addition of a tag to a day. This is where rating/value data live; the type
that determines which of those columns is meaningful comes from the parent `Tag`, not
from this row.

| Column | Type | Notes |
|---|---|---|
| `id` | `Long` (PK, autogenerate) | Needed so individual instances can be edited/removed independently (resolved decision). |
| `tagId` | `Long` (FK → `Tag.id`, `CASCADE` on delete) | Cascade matches the resolved tag-deletion behavior — the confirmation dialog is a UI/repository concern, not a DB one. |
| `date` | `Int` (epoch day) | Stored as an epoch day (not a date string) so range queries (`BETWEEN`) are cheap and index-friendly. |
| `rating` | `Int?` | 1–5, only meaningful when the parent `Tag.type == RATED`. Nullable — a Rated instance can be seeded at creation (quick-entry shorthand `name:***`) or left "unrated" and set later. |
| `value` | `String?` | Free text, only meaningful when the parent `Tag.type == VALUED`. Nullable for the same reason — seedable at creation (`name:text`) or set later. |
| `createdAt` | `Long` (epoch millis) | When the instance was added. Displayed as a timestamp on Rated rows in the instance-list sheet. |
| `sortOrder` | `Long` | **Display order** within a tag+day group. Seeded to the same value as `createdAt` on insert, so instances start out in creation order; rewritten to small sequential indices (`0, 1, 2, …`) when a Valued group is reordered by hand, which keeps later inserts (epoch millis) sorting after reordered ones for free. Every read path orders by it — see § Display order. Added in ADR-021/ADR-022. |

```kotlin
@Entity(
    tableName = "tag_instances",
    foreignKeys = [
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tagId"]),
        Index(value = ["date"]),
        Index(value = ["tagId", "date"])
    ]
)
data class TagInstance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tagId: Long,
    val date: Int,
    val rating: Int? = null,
    val value: String? = null,
    val createdAt: Long,
    val sortOrder: Long = 0,
)
```

## Relationships

```
Tag (1) ──────< (many) TagInstance
        id            tagId
```

A single join query covers most read paths — the Day view, Week chips, and Month/Year
heatmap all need `TagInstance` rows joined with their `Tag`'s name/color:

```kotlin
data class TagInstanceWithTag(
    @Embedded val instance: TagInstance,
    @Relation(parentColumn = "tagId", entityColumn = "id") val tag: Tag
)
```

## Grouping & aggregation: DB vs. Kotlin

DAOs return raw `TagInstanceWithTag` rows for a given day/range. **Grouping instances by
tag and aggregating (count / average / value list) according to the tag's type happens
in the repository, in Kotlin — not in SQL.** For the handful of instances a single day
realistically has, this is simpler to read, test, and change than building the
equivalent in a SQL query, and it keeps the aggregation logic in one place shared by
Day/Week/Month/Year views alike.

## Display order

`sortOrder` is the single owner of the order instances appear in — the two
`TagInstanceWithTag` queries carry `ORDER BY sortOrder`, and everything downstream
(`TagInstanceRepositoryImpl.toDisplayGroups`, `TagDisplayGroups.summarize`, the Day
capsules, the instance-list sheet) renders that order as given rather than sorting for
itself. That's deliberate: while the sheet sorted its own rows and the capsule summary
didn't, a manual reorder showed up in one and not the other. See ADR-023.

## DAOs

### `TagDao`

```kotlin
@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Tag>>

    // `query` arrives with LIKE wildcards already escaped by TagRepositoryImpl.
    @Query("SELECT * FROM tags WHERE name LIKE '%' || :query || '%' ESCAPE '\\' ORDER BY name COLLATE NOCASE")
    fun observeFiltered(query: String): Flow<List<Tag>>

    @Query("SELECT EXISTS(SELECT 1 FROM tags WHERE name = :name COLLATE NOCASE AND id != :excludingId)")
    suspend fun nameExists(name: String, excludingId: Long = 0): Boolean

    @Insert suspend fun insert(tag: Tag): Long
    @Update suspend fun update(tag: Tag)
    @Delete suspend fun delete(tag: Tag) // cascades to tag_instances via FK

    @Query("SELECT COUNT(*) FROM tag_instances WHERE tagId = :tagId")
    suspend fun instanceCount(tagId: Long): Int // for the deletion confirmation dialog
}
```

### `TagInstanceDao`

```kotlin
@Dao
interface TagInstanceDao {
    @Transaction
    @Query("SELECT * FROM tag_instances WHERE date = :date ORDER BY sortOrder")
    fun observeForDay(date: Int): Flow<List<TagInstanceWithTag>>

    @Transaction
    @Query("SELECT * FROM tag_instances WHERE date BETWEEN :start AND :end ORDER BY sortOrder")
    fun observeForRange(start: Int, end: Int): Flow<List<TagInstanceWithTag>> // Week chips

    @Query("SELECT * FROM tag_instances WHERE tagId = :tagId AND date BETWEEN :start AND :end")
    fun observeForTagInRange(tagId: Long, start: Int, end: Int): Flow<List<TagInstance>> // Month/Year heatmap

    @Insert suspend fun insert(instance: TagInstance): Long
    @Update suspend fun update(instance: TagInstance)
    @Update suspend fun updateAll(instances: List<TagInstance>) // whole-group sortOrder rewrite — see ADR-022
    @Delete suspend fun deleteAll(instances: List<TagInstance>) // capsule "x" (whole group) or a single instance — see ADR-019
}
```

## Schema history & migrations

| Version | Change |
|---|---|
| 1 | Current shape: `type` on `Tag` (ADR-007), `TagInstance.sortOrder` (ADR-021). |

**The numbering was reset to 1 on 2026-08-08**, and this table starts over with it. It
previously ran to 3, recording two pre-release bumps — `type` moving from `TagInstance` to
`Tag`, then `sortOrder` being added — as if they were history. They aren't: nothing has
shipped, so no install has ever *been* on a version this app would need to migrate from, and
every bump was absorbed by `fallbackToDestructiveMigration(dropAllTables = true)` in
`di/DatabaseModule.kt` rather than by a written `Migration`. Those numbers described the
development of the schema, not the versions any user's database was on.

Version 1 is therefore the shape the app is intended to **launch** with, and the "before" side
the first real `Migration` will be written against. It's not frozen yet — destructive fallback
is still in place and the schema can still change freely while it is (see the caveat under
§ Schema export about bumping vs. not bumping). What changes is the meaning of a bump: past 1
means the schema moved after the point it was meant to settle, so treat it as a decision worth
recording here rather than routine.

The old versions 2 and 3 are gone along with the numbering; `app/schemas/…/3.json` was deleted
and `1.json` regenerated. Nothing is lost — the deleted file described exactly the same tables
`1.json` now does.

### Schema export

`exportSchema = true` (`TagDayDatabase`), with the location wired by the Room Gradle plugin
in `app/build.gradle.kts`:

```kotlin
room { schemaDirectory("$projectDir/schemas") }
```

Room writes one JSON file per `version` to `app/schemas/<database class>/`, and **those files
are committed**. They're the only record of what a given version's tables looked like, and a
real `Migration` can't be written or tested (`MigrationTestHelper` reads them) without the
"before" side. They can't be reconstructed after the fact, which is why the export is on now
rather than when migrations are actually needed.

Two things this deliberately does *not* mean:

- **It isn't a claim the schema is final.** While the database still uses destructive
  fallback, the export is a recording, not a contract — keep changing entities freely.
  (Changing entities *without* bumping `version` is a different matter: Room's identity-hash
  check fails at open time with "you've changed schema but forgot to update the version
  number", which destructive fallback does **not** catch.) Since the reset, the way out of
  that is **reinstall or clear the app's data**, not another bump: 1 is meant to stay 1 until
  release, and `1.json` is simply regenerated in place each time the entities change.
- **It doesn't oblige a migration yet.** Migrations become mandatory only when
  `fallbackToDestructiveMigration` comes out.

There is exactly one exported schema, `1.json`, and it matches the current entities. Migrations
written from here on start at 1 — see § Schema history above for why the earlier 2 and 3 were
dropped rather than kept.

> **The remaining data-loss risk.** Destructive fallback still means a schema change silently
> wipes every tag and instance on any device that has the app installed. Right now that costs
> nothing — the app isn't installed anywhere but development, which is what made the version
> reset above free — so the risk is entirely about *when that stops being true*. The moment
> there's an install whose data matters (the developer's own day-to-day one included), the
> next schema change destroys it. The export above makes the fix *possible*; it doesn't apply
> it. Writing real `Migration`s must still happen before the schema changes past 1, not after. Note also that Android's auto-backup is not a
> substitute: the manifest has `allowBackup="true"` with the template's empty rules, so a
> restore can bring back a database file from an older schema version and hit exactly the same
> destructive path — see ADR-032's open item, and F2 in `BACKLOG.md` for why closing that is
> sequenced with M5a's local export rather than done now.

Which resolves the earlier open note here: `Tag`/`TagInstance` are *not* used directly as
UI-facing models — `TagDisplayGroup` (`data/model/`) is, per `ARCHITECTURE.md`.
