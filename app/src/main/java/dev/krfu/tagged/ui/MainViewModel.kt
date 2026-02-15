package dev.krfu.tagged.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.krfu.tagged.data.TaggedRepository
import dev.krfu.tagged.data.room.RoomTaggedRepository
import dev.krfu.tagged.data.room.TaggedDatabase
import dev.krfu.tagged.domain.TagSummary
import dev.krfu.tagged.domain.aggregateDayTags
import dev.krfu.tagged.model.TagEntry
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class TabScreen {
    Day,
    GlobalTags
}

data class DayEntryUi(
    val id: Long,
    val displayText: String
)

data class TagSummaryUi(
    val name: String,
    val label: String,
    val colorArgb: Int,
    val rating: Int? = null,
    val ratingCount: Int = 0
)

data class GlobalTagUi(
    val name: String,
    val colorArgb: Int,
    val hidden: Boolean
)

data class MainUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val tagInput: String = "",
    val inputError: String? = null,
    val showHiddenTags: Boolean = false,
    val daySummary: List<TagSummaryUi> = emptyList(),
    val dayEntries: List<DayEntryUi> = emptyList(),
    val globalTags: List<GlobalTagUi> = emptyList(),
    val selectedTab: TabScreen = TabScreen.Day,
    val colorPalette: List<Int> = emptyList()
)

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val repository: TaggedRepository = RoomTaggedRepository(
        TaggedDatabase.getInstance(application)
    )

    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val tagInput = MutableStateFlow("")
    private val inputError = MutableStateFlow<String?>(null)
    private val selectedTab = MutableStateFlow(TabScreen.Day)

    val uiState: StateFlow<MainUiState> = combine(
        repository.state,
        selectedDate,
        tagInput,
        inputError,
        selectedTab
    ) { repoState, date, input, error, tab ->
        val dayEntries = repoState.entriesByDate[date].orEmpty()
        MainUiState(
            selectedDate = date,
            tagInput = input,
            inputError = error,
            showHiddenTags = repoState.settings.showHiddenTags,
            daySummary = aggregateDayTags(
                entries = dayEntries,
                globalTags = repoState.globalTags,
                showHiddenTags = repoState.settings.showHiddenTags
            ).map(TagSummary::toUi),
            dayEntries = dayEntries
                .filter { repoState.settings.showHiddenTags || repoState.globalTags[it.name]?.hidden != true }
                .map(TagEntry::toUi),
            globalTags = repoState.globalTags.values
                .sortedBy { it.name }
                .map { tag ->
                    GlobalTagUi(
                        name = tag.name,
                        colorArgb = tag.colorArgb,
                        hidden = tag.hidden
                    )
                },
            selectedTab = tab,
            colorPalette = repository.palette()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(colorPalette = repository.palette())
    )

    fun updateTagInput(value: String) {
        tagInput.value = value
        if (inputError.value != null) {
            inputError.value = null
        }
    }

    fun addTagForSelectedDay() {
        viewModelScope.launch {
            repository.addTag(selectedDate.value, tagInput.value)
                .onSuccess {
                    tagInput.value = ""
                    inputError.value = null
                }
                .onFailure { error ->
                    inputError.value = error.message
                }
        }
    }

    fun removeEntry(entryId: Long) {
        viewModelScope.launch {
            repository.removeEntry(entryId)
        }
    }

    fun removeTagFromSelectedDay(tagName: String) {
        viewModelScope.launch {
            repository.removeTagForDate(selectedDate.value, tagName)
        }
    }

    fun selectPreviousDay() {
        selectedDate.update { it.minusDays(1) }
    }

    fun selectNextDay() {
        selectedDate.update { it.plusDays(1) }
    }

    fun selectToday() {
        selectedDate.value = LocalDate.now()
    }

    fun setShowHiddenTags(show: Boolean) {
        viewModelScope.launch {
            repository.setShowHiddenTags(show)
        }
    }

    fun selectTab(tab: TabScreen) {
        selectedTab.value = tab
    }

    fun updateGlobalTag(currentName: String, newName: String, colorArgb: Int, hidden: Boolean) {
        viewModelScope.launch {
            repository.renameGlobalTag(currentName, newName)
                .onFailure { error ->
                    inputError.value = error.message
                    return@launch
                }

            repository.updateGlobalTagColor(newName, colorArgb)
            repository.setGlobalTagHidden(newName, hidden)
        }
    }

    fun deleteGlobalTag(name: String) {
        viewModelScope.launch {
            repository.deleteGlobalTag(name)
        }
    }
}

private fun TagSummary.toUi(): TagSummaryUi = TagSummaryUi(
    name = name,
    label = label,
    colorArgb = colorArgb,
    rating = rating,
    ratingCount = ratingCount
)

private fun TagEntry.toUi(): DayEntryUi {
    val payload = when {
        rating != null -> ":" + "*".repeat(rating)
        !value.isNullOrBlank() -> ":$value"
        else -> ""
    }

    val dateFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd")
    return DayEntryUi(
        id = id,
        displayText = "${date.format(dateFormatter)}  $name$payload"
    )
}
