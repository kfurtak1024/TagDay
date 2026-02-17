package dev.krfu.tagday.model

import java.time.LocalDate

data class TagEntry(
    val id: Long,
    val date: LocalDate,
    val name: String,
    val value: String? = null,
    val rating: Int? = null
)

data class GlobalTag(
    val name: String,
    val colorArgb: Int,
    val hidden: Boolean = false,
    val userSelectedColor: Boolean = false
)

data class AppSettings(
    val showHiddenTags: Boolean = false
)

data class RepositoryState(
    val globalTags: Map<String, GlobalTag> = emptyMap(),
    val entriesByDate: Map<LocalDate, List<TagEntry>> = emptyMap(),
    val settings: AppSettings = AppSettings(),
    val nextEntryId: Long = 1L
)

data class ParsedTagInput(
    val name: String,
    val value: String? = null,
    val rating: Int? = null
)
