package com.echo.app.data.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.echo.app.core.AppEvent
import com.echo.app.core.EventBus
import com.echo.app.domain.model.ClipItem
import java.util.UUID

/**
 * Reads and writes the system clipboard.
 *
 * Android 10+ only lets an app READ the clipboard while it is focused (or is the keyboard),
 * so the listener fires while Echo is open. For background sharing the host uses the notification
 * action "Send clipboard", which opens a transparent activity that calls [captureNow].
 */
class ClipboardAccess(private val context: Context, private val bus: EventBus) {
    private val manager = context.getSystemService(ClipboardManager::class.java)
    private var lastText: String? = null
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { captureNow() }

    fun start() = manager.addPrimaryClipChangedListener(listener)

    fun stop() = manager.removePrimaryClipChangedListener(listener)

    /** Reads the current clip and publishes it. [force] re-sends identical text. */
    fun captureNow(force: Boolean = false) {
        val clip = runCatching { manager.primaryClip }.getOrNull() ?: return
        if (clip.description?.extras?.getBoolean(EXTRA_IS_SENSITIVE) == true) return // password managers
        val text = runCatching { clip.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
        if (text.isNullOrBlank() || (!force && text == lastText)) return
        lastText = text
        bus.publish(AppEvent.ClipboardCaptured(ClipItem(UUID.randomUUID().toString(), text, System.currentTimeMillis())))
    }

    /** Used by clients to put received text on the local clipboard. */
    fun write(text: String) {
        lastText = text
        manager.setPrimaryClip(ClipData.newPlainText("Echo", text))
    }

    private companion object {
        const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    }
}
