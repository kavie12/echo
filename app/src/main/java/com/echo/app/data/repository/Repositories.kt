package com.echo.app.data.repository

import com.echo.app.core.AppEvent
import com.echo.app.core.EventBus
import com.echo.app.domain.model.ClipItem
import com.echo.app.domain.model.SmsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** In-memory message store. Fed purely by bus events, observed by the UI. */
class MessageRepository(bus: EventBus, scope: CoroutineScope) {
    private val _items = MutableStateFlow<List<SmsMessage>>(emptyList())
    val items: StateFlow<List<SmsMessage>> = _items.asStateFlow()

    init {
        scope.launch { bus.on<AppEvent.MessagesReceived>().collect { merge(it.items) } }
    }

    private fun merge(incoming: List<SmsMessage>) = _items.update { current ->
        (current + incoming).associateBy { it.id }.values.sortedByDescending { it.timestamp }.take(MAX_ITEMS)
    }

    fun clear() {
        _items.value = emptyList()
    }

    private companion object { const val MAX_ITEMS = 200 }
}

/** Clipboard history. On a host it records captured clips, on a client the received ones. */
class ClipboardRepository(bus: EventBus, scope: CoroutineScope) {
    private val _items = MutableStateFlow<List<ClipItem>>(emptyList())
    val items: StateFlow<List<ClipItem>> = _items.asStateFlow()

    init {
        scope.launch { bus.on<AppEvent.ClipboardCaptured>().collect { merge(listOf(it.item)) } }
        scope.launch { bus.on<AppEvent.ClipsReceived>().collect { merge(it.items) } }
    }

    private fun merge(incoming: List<ClipItem>) = _items.update { current ->
        (current + incoming).associateBy { it.id }.values.sortedByDescending { it.timestamp }.take(MAX_ITEMS)
    }

    fun clear() {
        _items.value = emptyList()
    }

    private companion object { const val MAX_ITEMS = 50 }
}
