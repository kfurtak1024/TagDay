package dev.krfu.tagday.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagInstanceWithTag
import kotlinx.coroutines.flow.Flow

@Dao
interface TagInstanceDao {
    // ORDER BY sortOrder is what makes a manual reorder (ADR-021/ADR-022) show up
    // everywhere an instance list does, not just in the sheet the dragging happens in:
    // TagDisplayGroups.summarize preserves the order it's handed, so the capsule's
    // `movie: [dune, terminator]` follows it too. Unordered, these returned rowid order,
    // i.e. insertion order. sortOrder is seeded from createdAt (see
    // TagInstanceRepositoryImpl.addInstance), so this stays chronological until something
    // is actually reordered by hand.
    @Transaction
    @Query("SELECT * FROM tag_instances WHERE date = :date ORDER BY sortOrder")
    fun observeForDay(date: Int): Flow<List<TagInstanceWithTag>>

    // Week chips.
    @Transaction
    @Query("SELECT * FROM tag_instances WHERE date BETWEEN :start AND :end ORDER BY sortOrder")
    fun observeForRange(start: Int, end: Int): Flow<List<TagInstanceWithTag>>

    // Month/Year heatmap.
    @Query("SELECT * FROM tag_instances WHERE tagId = :tagId AND date BETWEEN :start AND :end")
    fun observeForTagInRange(tagId: Long, start: Int, end: Int): Flow<List<TagInstance>>

    @Insert
    suspend fun insert(instance: TagInstance): Long

    @Update
    suspend fun update(instance: TagInstance)

    @Update
    suspend fun updateAll(instances: List<TagInstance>)

    @Delete
    suspend fun deleteAll(instances: List<TagInstance>)
}
