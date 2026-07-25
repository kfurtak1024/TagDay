package dev.krfu.tagday.data.model

import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagType

data class TagDisplayGroup(
    val tagId: Long,
    val tagName: String,
    val color: Int,
    val type: TagType,
    val instances: List<TagInstance>,
    val summary: String,
)
