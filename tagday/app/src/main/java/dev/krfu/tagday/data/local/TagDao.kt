package dev.krfu.tagday.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.krfu.tagday.data.local.entity.Tag
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE name LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE")
    fun observeFiltered(query: String): Flow<List<Tag>>

    @Query("SELECT EXISTS(SELECT 1 FROM tags WHERE name = :name COLLATE NOCASE AND id != :excludingId)")
    suspend fun nameExists(name: String, excludingId: Long = 0): Boolean

    @Insert
    suspend fun insert(tag: Tag): Long

    @Update
    suspend fun update(tag: Tag)

    // Cascades to tag_instances via FK, see DATA_MODEL.md § TagInstance.
    @Delete
    suspend fun delete(tag: Tag)

    // For the deletion confirmation dialog.
    @Query("SELECT COUNT(*) FROM tag_instances WHERE tagId = :tagId")
    suspend fun instanceCount(tagId: Long): Int
}
