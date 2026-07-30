package dev.krfu.tagday.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tag_instances",
    foreignKeys = [
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["tagId"]),
        Index(value = ["date"]),
        Index(value = ["tagId", "date"]),
    ],
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
