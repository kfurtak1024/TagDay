package dev.krfu.tagged.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "global_tags")
data class GlobalTagEntity(
    @PrimaryKey
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "color_argb")
    val colorArgb: Int,
    @ColumnInfo(name = "hidden")
    val hidden: Boolean,
    @ColumnInfo(name = "user_selected_color")
    val userSelectedColor: Boolean
)

@Entity(
    tableName = "tag_entries",
    indices = [Index(value = ["date_epoch_day"]), Index(value = ["name"])]
)
data class TagEntryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "date_epoch_day")
    val dateEpochDay: Long,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "value")
    val value: String?,
    @ColumnInfo(name = "rating")
    val rating: Int?
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = SETTINGS_ROW_ID,
    @ColumnInfo(name = "show_hidden_tags")
    val showHiddenTags: Boolean
)

const val SETTINGS_ROW_ID = 1
