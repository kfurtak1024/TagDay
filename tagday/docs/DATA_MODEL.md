# TagDay — Data Model

Room schema derived directly from `FEATURES.md`. Two entities only — no separate `Day`
entity, no domain-layer models yet (see `ARCHITECTURE.md` for why).

## Why only two entities

- **No `Day` entity.** A day is just a date; most days won't have tags at all, and the
  ones that do are fully represented by their `TagInstance` rows. Adding an empty `Day`
  row per calendar date would just be bookkeeping with no query or integrity benefit.
- **No `type` column on `Tag`.** Per the resolved decision in `FEATURES.md`, type is a
  property of each instance, not the tag definition. Putting it on `Tag` would make it
  impossible for the same tag name to be used as Simple, Rated, and Valued at once.

## Entities

### `Tag`

The tag repository. One row per distinct tag name.

| Column | Type | Notes |
|---|---|---|
| `id` | `Long` (PK, autogenerate) | Stable internal id; everything else references this, not the name. |
| `name` | `String` | Unique, case-insensitive (`NOCASE` collation + unique index). Renamable. |
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
    val color: Int,
    val createdAt: Long
)
```

### `TagInstance`

One row per addition of a tag to a day. This is where type, rating, and value live.

| Column | Type | Notes |
|---|---|---|
| `id` | `Long` (PK, autogenerate) | Needed so individual instances can be edited/removed independently (resolved decision). |
| `tagId` | `Long` (FK → `Tag.id`, `CASCADE` on delete) | Cascade matches the resolved tag-deletion behavior — the confirmation dialog is a UI/repository concern, not a DB one. |
| `date` | `Int` (epoch day) | Stored as an epoch day (not a date string) so range queries (`BETWEEN`) are cheap and index-friendly. |
| `type` | `String` enum (`SIMPLE` / `RATED` / `VALUED`) | Set per instance; mutable (resolved decision) — changing it just updates this column and clears/repopulates `rating`/`value` as appropriate. |
| `rating` | `Int?` | 1–5, only meaningful when `type == RATED`. Nullable — a Rated instance can exist "unrated" and be set later. |
| `value` | `String?` | Free text, only meaningful when `type == VALUED`. |
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
    val type: TagInstanceType,
    val rating: Int? = null,
    val value: String? = null,
    val createdAt: Long
)

enum class TagInstanceType { SIMPLE, RATED, VALUED }
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

DAOs return raw `TagInstanceWithTag` rows for a given day/range. **Grouping by type and
aggregating (count / average / value list) happens in the repository, in Kotlin — not
in SQL.** For the handful of instances a single day realistically has, this is simpler
to read, test, and change than building the equivalent in a SQL query, and it keeps the
aggregation logic in one place shared by Day/Week/Month/Year views alike.

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
}
```

## Open notes for `ARCHITECTURE.md`

- Whether `Tag` / `TagInstance` are used directly as UI-facing models, or mapped to
  lightweight UI models (e.g. a `TagDisplayGroup` for the aggregated Day-view rows) —
  likely the latter just for the grouped display, without a full domain layer.
- Schema starts at Room `version = 1`; no migrations needed yet.
