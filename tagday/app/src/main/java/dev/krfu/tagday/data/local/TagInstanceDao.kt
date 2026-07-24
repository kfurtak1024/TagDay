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
    @Transaction
    @Query("SELECT * FROM tag_instances WHERE date = :date")
    fun observeForDay(date: Int): Flow<List<TagInstanceWithTag>>

    // Week chips.
    @Transaction
    @Query("SELECT * FROM tag_instances WHERE date BETWEEN :start AND :end")
    fun observeForRange(start: Int, end: Int): Flow<List<TagInstanceWithTag>>

    // Month/Year heatmap.
    @Query("SELECT * FROM tag_instances WHERE tagId = :tagId AND date BETWEEN :start AND :end")
    fun observeForTagInRange(tagId: Long, start: Int, end: Int): Flow<List<TagInstance>>

    @Insert
    suspend fun insert(instance: TagInstance): Long

    @Update
    suspend fun update(instance: TagInstance)

    @Delete
    suspend fun delete(instance: TagInstance)
}
