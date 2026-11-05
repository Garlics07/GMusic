package com.gmusic.app.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmusic.app.data.repository.MusicRepository
import com.gmusic.app.domain.model.AlbumSummary
import com.gmusic.app.domain.model.MoodGenreChip
import com.gmusic.app.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExploreUiState(
    val isLoading: Boolean = true,
    val selectedMoodTitle: String? = null,
    val chartsItaly: List<Track> = emptyList(),
    val newReleasesItaly: List<AlbumSummary> = emptyList(),
    val moodChips: List<MoodGenreChip> = emptyList()
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    init {
        loadExplore()
    }

    fun loadExplore() {
        _uiState.update { it.copy(isLoading = true, selectedMoodTitle = null) }
        viewModelScope.launch {
            val (charts, albums, moods) = musicRepository.loadExploreItalyData()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    chartsItaly = charts,
                    newReleasesItaly = albums,
                    moodChips = moods
                )
            }
        }
    }

    fun selectMoodGenre(chip: MoodGenreChip) {
        _uiState.update { it.copy(isLoading = true, selectedMoodTitle = chip.title) }
        viewModelScope.launch {
            val moodTracks = musicRepository.searchSongs(chip.query)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    chartsItaly = moodTracks
                )
            }
        }
    }
}
