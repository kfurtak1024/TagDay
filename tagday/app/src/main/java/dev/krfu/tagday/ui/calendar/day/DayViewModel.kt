package dev.krfu.tagday.ui.calendar.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.krfu.tagday.data.local.entity.TagInstance
import dev.krfu.tagday.data.local.entity.TagType
import dev.krfu.tagday.data.repository.TagInstanceRepository
import dev.krfu.tagday.data.repository.TagRepository
import dev.krfu.tagday.ui.theme.TagPalette
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

    fun createTagAndAdd(name: String, type: TagType, rating: Int? = null, value: String? = null) {
        viewModelScope.launch {
            val color = TagPalette.colors[uiState.value.allTags.size % TagPalette.colors.size]
            val tagId = tagRepository.createTag(name, color, type)
            tagInstanceRepository.addInstance(tagId, epochDay, rating, value)
        }
    }

    fun updateInstance(instance: TagInstance) {
        viewModelScope.launch {
            tagInstanceRepository.updateInstance(instance)
        }
    }

    fun removeInstance(instance: TagInstance) {
        viewModelScope.launch {
            tagInstanceRepository.removeInstance(instance)
        }
    }
}
