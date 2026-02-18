package dev.krfu.tagday.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.krfu.tagday.data.TagDayRepository
import dev.krfu.tagday.domain.TagSummary
import dev.krfu.tagday.domain.aggregateDayTags
import dev.krfu.tagday.model.TagEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

enum class TabScreen {
    Day,
    Week,
    Month,
    Year,
    GlobalTags,
    Settings
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

data class DateSummaryUi(
    val date: LocalDate,
    val topLabels: List<String>,
    val extraCount: Int
)

data class MonthSummaryUi(
    val month: Int,
    val topLabels: List<String>,
    val extraCount: Int
)

data class MainUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val tagInput: String = "",
    val inputError: String? = null,
    val globalTagError: String? = null,
    val showHiddenTags: Boolean = false,
    val daySummary: List<TagSummaryUi> = emptyList(),
    val dayEntries: List<DayEntryUi> = emptyList(),
    val weekSummary: List<DateSummaryUi> = emptyList(),
    val monthSummary: List<DateSummaryUi> = emptyList(),
    val yearSummary: List<MonthSummaryUi> = emptyList(),
    val globalTags: List<GlobalTagUi> = emptyList(),
    val selectedTab: TabScreen = TabScreen.Day,
    val colorPalette: List<Int> = emptyList()
)

class MainViewModel(
    private val repository: TagDayRepository
) : ViewModel() {

    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val tagInput = MutableStateFlow("")
    private val inputError = MutableStateFlow<String?>(null)
    private val globalTagError = MutableStateFlow<String?>(null)
    private val selectedTab = MutableStateFlow(TabScreen.Day)
    private val palette = repository.palette()

    private val derivedUiState: StateFlow<MainUiState> = combine(
        repository.state,
        selectedDate,
        selectedTab
    ) { repoState, date, tab ->
        val dayEntries = repoState.entriesByDate[date].orEmpty()
        val weekSummary = buildWeekSummary(
            selectedDate = date,
            entriesByDate = repoState.entriesByDate,
            globalTags = repoState.globalTags,
            showHiddenTags = repoState.settings.showHiddenTags
        )
        val monthSummary = buildMonthSummary(
            selectedDate = date,
            entriesByDate = repoState.entriesByDate,
            globalTags = repoState.globalTags,
            showHiddenTags = repoState.settings.showHiddenTags
        )
        val yearSummary = buildYearSummary(
            year = date.year,
            entriesByDate = repoState.entriesByDate,
            globalTags = repoState.globalTags,
            showHiddenTags = repoState.settings.showHiddenTags
        )
        MainUiState(
            selectedDate = date,
            showHiddenTags = repoState.settings.showHiddenTags,
            daySummary = aggregateDayTags(
                entries = dayEntries,
                globalTags = repoState.globalTags,
                showHiddenTags = repoState.settings.showHiddenTags
            ).map(TagSummary::toUi),
            dayEntries = dayEntries
                .filter { repoState.settings.showHiddenTags || repoState.globalTags[it.name]?.hidden != true }
                .map(TagEntry::toUi),
            weekSummary = weekSummary,
            monthSummary = monthSummary,
            yearSummary = yearSummary,
            globalTags = repoState.globalTags.values
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .map { tag ->
                    GlobalTagUi(
                        name = tag.name,
                        colorArgb = tag.colorArgb,
                        hidden = tag.hidden
                    )
                },
            selectedTab = tab,
            colorPalette = palette
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(colorPalette = palette)
    )

    val uiState: StateFlow<MainUiState> = combine(
        derivedUiState,
        tagInput,
        inputError,
        globalTagError
    ) { state, input, error, globalError ->
        state.copy(
            tagInput = input,
            inputError = error,
            globalTagError = globalError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(colorPalette = palette)
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
        selectedTab.value = TabScreen.Day
    }

    fun setShowHiddenTags(show: Boolean) {
        viewModelScope.launch {
            repository.setShowHiddenTags(show)
        }
    }

    fun selectTab(tab: TabScreen) {
        selectedTab.value = tab
    }

    fun showDay() {
        selectedTab.value = TabScreen.Day
    }

    fun showWeek() {
        selectedTab.value = TabScreen.Week
    }

    fun showMonth() {
        selectedTab.value = TabScreen.Month
    }

    fun showYear() {
        selectedTab.value = TabScreen.Year
    }

    fun selectPreviousWeek() {
        selectedDate.update { it.minusWeeks(1) }
    }

    fun selectNextWeek() {
        selectedDate.update { it.plusWeeks(1) }
    }

    fun selectPreviousMonth() {
        selectedDate.update { it.minusMonths(1) }
    }

    fun selectNextMonth() {
        selectedDate.update { it.plusMonths(1) }
    }

    fun selectPreviousYear() {
        selectedDate.update { it.minusYears(1) }
    }

    fun selectNextYear() {
        selectedDate.update { it.plusYears(1) }
    }

    fun openDay(date: LocalDate) {
        selectedDate.value = date
        selectedTab.value = TabScreen.Day
    }

    fun updateGlobalTag(currentName: String, newName: String, colorArgb: Int, hidden: Boolean) {
        viewModelScope.launch {
            globalTagError.value = null
            repository.renameGlobalTag(currentName, newName)
                .onFailure { error ->
                    globalTagError.value = error.message
                    return@launch
                }

            try {
                repository.updateGlobalTagColor(newName, colorArgb)
                repository.setGlobalTagHidden(newName, hidden)
            } catch (error: Exception) {
                globalTagError.value = error.message ?: "Failed to update global tag."
            }
        }
    }

    fun deleteGlobalTag(name: String) {
        viewModelScope.launch {
            repository.deleteGlobalTag(name)
        }
    }

    class Factory(
        private val repository: TagDayRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
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

private fun buildWeekSummary(
    selectedDate: LocalDate,
    entriesByDate: Map<LocalDate, List<TagEntry>>,
    globalTags: Map<String, dev.krfu.tagday.model.GlobalTag>,
    showHiddenTags: Boolean
): List<DateSummaryUi> {
    val monday = selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
    return (0..6).map { offset ->
        val date = monday.plusDays(offset.toLong())
        buildDateSummary(date, entriesByDate, globalTags, showHiddenTags)
    }
}

private fun buildMonthSummary(
    selectedDate: LocalDate,
    entriesByDate: Map<LocalDate, List<TagEntry>>,
    globalTags: Map<String, dev.krfu.tagday.model.GlobalTag>,
    showHiddenTags: Boolean
): List<DateSummaryUi> {
    val month = YearMonth.from(selectedDate)
    return (1..month.lengthOfMonth()).map { day ->
        buildDateSummary(month.atDay(day), entriesByDate, globalTags, showHiddenTags)
    }
}

private fun buildYearSummary(
    year: Int,
    entriesByDate: Map<LocalDate, List<TagEntry>>,
    globalTags: Map<String, dev.krfu.tagday.model.GlobalTag>,
    showHiddenTags: Boolean
): List<MonthSummaryUi> {
    return (1..12).map { month ->
        val monthEntries = entriesByDate
            .asSequence()
            .filter { (date, _) -> date.year == year && date.monthValue == month }
            .flatMap { (_, entries) -> entries.asSequence() }
            .filter { showHiddenTags || globalTags[it.name]?.hidden != true }
            .toList()
        val grouped = monthEntries.groupBy { it.name }
        val labels = grouped.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<TagEntry>>> { it.value.size }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.key }
            )
            .map { (name, entries) ->
                if (entries.size > 1) "$name (${entries.size})" else name
            }
        MonthSummaryUi(
            month = month,
            topLabels = labels.take(2),
            extraCount = (labels.size - 2).coerceAtLeast(0)
        )
    }
}

private fun buildDateSummary(
    date: LocalDate,
    entriesByDate: Map<LocalDate, List<TagEntry>>,
    globalTags: Map<String, dev.krfu.tagday.model.GlobalTag>,
    showHiddenTags: Boolean
): DateSummaryUi {
    val summaries = aggregateDayTags(
        entries = entriesByDate[date].orEmpty(),
        globalTags = globalTags,
        showHiddenTags = showHiddenTags
    )
    val labels = summaries.map { it.toCompactLabel() }
    return DateSummaryUi(
        date = date,
        topLabels = labels.take(2),
        extraCount = (labels.size - 2).coerceAtLeast(0)
    )
}

private fun TagSummary.toCompactLabel(): String {
    if (rating == null) {
        return label
    }
    val stars = "★".repeat(rating) + "☆".repeat(5 - rating)
    return if (ratingCount > 1) "$name $stars ($ratingCount)" else "$name $stars"
}
