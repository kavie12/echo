package com.echo.app.core

import com.echo.app.domain.model.ClipItem
import com.echo.app.domain.model.SmsMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance

/** Everything interesting that can happen in the app. Publishers never know who listens. */
sealed interface AppEvent {
    /** Host: a new SMS appeared on this phone. */
    data class MessageCaptured(val message: SmsMessage) : AppEvent

    /** Host: the clipboard of this phone changed. */
    data class ClipboardCaptured(val item: ClipItem) : AppEvent

    /** Host: a client (re)sent its preferences, so it may need a snapshot. */
    data class DevicePreferencesChanged(val deviceId: String) : AppEvent

    /** Client: messages arrived from the host. */
    data class MessagesReceived(val items: List<SmsMessage>) : AppEvent

    /** Client: clipboard entries arrived from the host. [live] is false for history snapshots. */
    data class ClipsReceived(val items: List<ClipItem>, val live: Boolean) : AppEvent
}

/** In-process publish/subscribe bus backed by a [SharedFlow]. */
class EventBus {
    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    fun publish(event: AppEvent) {
        _events.tryEmit(event)
    }

    inline fun <reified T : AppEvent> on(): Flow<T> = events.filterIsInstance<T>()
}
