package dev.krfu.tagday.ui.calendar.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.data.repository.TagInstanceRepository
import dev.krfu.tagday.data.repository.TagRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class DayViewModel @Inject constructor(
    private val tagRepository: TagRepository,
    private val tagInstanceRepository: TagInstanceRepository,
) : ViewModel() {
    private val today: LocalDate = LocalDate.now()
    private val epochDay: Int = today.toEpochDay().toInt()

    val uiState: StateFlow<DayUiState> = combine(
        tagInstanceRepository.observeDayGroups(epochDay),
        tagRepository.observeAll(),
    ) { groups, allTags ->
        DayUiState(isLoading = false, date = today, groups = groups, allTags = allTags)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUiState(date = today))

    fun addExistingTag(tagId: Long) {
        viewModelScope.launch {
            tagInstanceRepository.addInstance(tagId, epochDay)
        }
    }

    fun createTagAndAdd(name: String, color: Int) {
        viewModelScope.launch {
            // M1's add-tag sheet only ever creates Simple tags — the type picker for
            // Rated/Valued is M2 scope (UI_UX.md § Add-tag flow).
            val tagId = tagRepository.createTag(name, color, TagType.SIMPLE)
            tagInstanceRepository.addInstance(tagId, epochDay)
        }
    }

    fun removeInstance(instance: TagInstance) {
        viewModelScope.launch {
            tagInstanceRepository.removeInstance(instance)
        }
    }
}
