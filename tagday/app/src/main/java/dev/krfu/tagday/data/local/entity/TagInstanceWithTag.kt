package dev.krfu.tagday.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class TagInstanceWithTag(
    @Embedded val instance: TagInstance,
    @Relation(parentColumn = "tagId", entityColumn = "id") val tag: Tag,
)
