package dev.krfu.tagged.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaggedDao {
    @Query("SELECT * FROM global_tags")
    fun observeGlobalTags(): Flow<List<GlobalTagEntity>>

    @Query("SELECT * FROM tag_entries")
    fun observeTagEntries(): Flow<List<TagEntryEntity>>

    @Query("SELECT * FROM app_settings WHERE id = :id LIMIT 1")
    fun observeSettings(id: Int = SETTINGS_ROW_ID): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM global_tags WHERE name = :name LIMIT 1")
    suspend fun findGlobalTagByName(name: String): GlobalTagEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGlobalTag(tag: GlobalTagEntity)

    @Update
    suspend fun updateGlobalTag(tag: GlobalTagEntity)

    @Query("DELETE FROM global_tags WHERE name = :name")
    suspend fun deleteGlobalTag(name: String)

    @Query("UPDATE tag_entries SET name = :newName WHERE name = :currentName")
    suspend fun renameTagEntries(currentName: String, newName: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTagEntry(entry: TagEntryEntity)

    @Query("DELETE FROM tag_entries WHERE id = :entryId")
    suspend fun deleteTagEntry(entryId: Long)

    @Query("DELETE FROM tag_entries WHERE date_epoch_day = :dateEpochDay AND name = :name")
    suspend fun deleteTagEntriesByDateAndName(dateEpochDay: Long, name: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: AppSettingsEntity)
}
