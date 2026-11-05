package com.gmusic.app.playback.service

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface PlaybackUiEvent {
    data class StreamErrorSkipped(val failedTrackTitle: String) : PlaybackUiEvent
}

@Singleton
class PlaybackEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<PlaybackUiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PlaybackUiEvent> = _events.asSharedFlow()

    fun tryEmit(event: PlaybackUiEvent) {
        _events.tryEmit(event)
    }
}
