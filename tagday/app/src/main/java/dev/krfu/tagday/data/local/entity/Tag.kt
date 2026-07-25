package dev.krfu.tagday.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name", collate = ColumnInfo.NOCASE) val name: String,
    val type: TagType,
    val color: Int,
    val createdAt: Long,
)
