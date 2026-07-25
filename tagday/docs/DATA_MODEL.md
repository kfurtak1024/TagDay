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
| `name` | `String` | Unique, case-insensitive (`NOCASE` collation + unique index). Renamable. |
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
| `createdAt` | `Long` (epoch millis) | Tie-breaker for ordering multiple instances added the same day. |

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
    val createdAt: Long
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

## DAOs

### `TagDao`

```kotlin
@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE name LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE")
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
    @Query("SELECT * FROM tag_instances WHERE date = :date")
    fun observeForDay(date: Int): Flow<List<TagInstanceWithTag>>

    @Transaction
    @Query("SELECT * FROM tag_instances WHERE date BETWEEN :start AND :end")
    fun observeForRange(start: Int, end: Int): Flow<List<TagInstanceWithTag>> // Week chips

    @Query("SELECT * FROM tag_instances WHERE tagId = :tagId AND date BETWEEN :start AND :end")
    fun observeForTagInRange(tagId: Long, start: Int, end: Int): Flow<List<TagInstance>> // Month/Year heatmap

    @Insert suspend fun insert(instance: TagInstance): Long
    @Update suspend fun update(instance: TagInstance)
    @Delete suspend fun delete(instance: TagInstance)
    @Delete suspend fun deleteAll(instances: List<TagInstance>) // capsule "x" — whole group at once
}
```

## Open notes for `ARCHITECTURE.md`

- Whether `Tag` / `TagInstance` are used directly as UI-facing models, or mapped to
  lightweight UI models (e.g. a `TagDisplayGroup` for the aggregated Day-view rows) —
  likely the latter just for the grouped display, without a full domain layer.
- Schema history: `version = 1` was the original (pre-M1-completion) shape with `type`
  on `TagInstance`. Moving `type` to `Tag` bumps this to `version = 2`. Since this
  happened during local M1 development with no real user data to preserve, the move
  used `fallbackToDestructiveMigration()` rather than a written `Migration` — a real
  migration path should be introduced once the app has real installs to protect.
