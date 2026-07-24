package dev.krfu.tagday.ui.calendar.day

import dev.krfu.tagday.data.local.entity.Tag
import dev.krfu.tagday.data.model.TagDisplayGroup
import java.time.LocalDate

data class DayUiState(
    val isLoading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val groups: List<TagDisplayGroup> = emptyList(),
    val allTags: List<Tag> = emptyList(),
)
