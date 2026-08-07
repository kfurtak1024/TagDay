package dev.krfu.tagday.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.krfu.tagday.data.local.entity.Tag
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Tag>>

    /**
     * [query] must already have LIKE's wildcards escaped — `TagRepositoryImpl` does it via
     * `escapeLikeWildcards`. Without the ESCAPE clause, a typed `%` matched everything and
     * a typed `_` matched any single character.
     */
    @Query("SELECT * FROM tags WHERE name LIKE '%' || :query || '%' ESCAPE '\\' ORDER BY name COLLATE NOCASE")
    fun observeFiltered(query: String): Flow<List<Tag>>

    @Query("SELECT EXISTS(SELECT 1 FROM tags WHERE name = :name COLLATE NOCASE AND id != :excludingId)")
    suspend fun nameExists(name: String, excludingId: Long = 0): Boolean

    /**
     * Returns the new row id, or **-1** if a tag with this name already exists — `name` carries
     * a unique index, and the default `ABORT` threw `SQLiteConstraintException` straight out of
     * the calling coroutine, crashing the app (BACKLOG F3). `IGNORE` turns the collision into a
     * value the caller can act on; `TagRepositoryImpl.createTag` resolves it to the existing
     * tag.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: Tag): Long

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Tag?

    @Update
    suspend fun update(tag: Tag)

    // Cascades to tag_instances via FK, see DATA_MODEL.md § TagInstance.
    @Delete
    suspend fun delete(tag: Tag)

    /**
     * How many *days* carry this tag, which is what the deletion dialog says it's counting.
     * It was `COUNT(*)` — the number of instances — so a tag applied twice in one day
     * overstated the days it would disappear from (BACKLOG F9).
     */
    @Query("SELECT COUNT(DISTINCT date) FROM tag_instances WHERE tagId = :tagId")
    suspend fun taggedDayCount(tagId: Long): Int
}
