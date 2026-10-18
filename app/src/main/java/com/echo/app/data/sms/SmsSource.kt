package com.echo.app.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.echo.app.core.AppEvent
import com.echo.app.core.EventBus
import com.echo.app.domain.model.SmsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reads the SMS provider and publishes [AppEvent.MessageCaptured] for every new
 * inbox/sent message. A [ContentObserver] covers received and sent messages with one mechanism.
 */
class SmsSource(
    private val context: Context,
    private val bus: EventBus,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var observer: ContentObserver? = null
    private var lastSeenId = 0L

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** Idempotent. Safe to call again after the permission has been granted. */
    fun start() {
        if (observer != null || !hasPermission()) return
        lastSeenId = query(null, null, 1).maxOfOrNull { it.id } ?: 0L
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = publishNew()
        }
        context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, obs)
        observer = obs
    }

    fun stop() {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
    }

    /** Newest messages first; used for the snapshot sent to a client that just connected. */
    fun recent(limit: Int = SNAPSHOT_SIZE): List<SmsMessage> =
        if (hasPermission()) query(null, null, limit) else emptyList()

    private fun publishNew() {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                val fresh = query("${Telephony.Sms._ID} > ?", arrayOf(lastSeenId.toString()), 20).sortedBy { it.id }
                if (fresh.isNotEmpty()) {
                    lastSeenId = fresh.last().id
                    fresh.forEach { bus.publish(AppEvent.MessageCaptured(it)) }
                }
            }
        }
    }

    private fun query(extraSelection: String?, args: Array<String>?, limit: Int): List<SmsMessage> {
        val selection = listOfNotNull(
            "${Telephony.Sms.TYPE} IN (${Telephony.Sms.MESSAGE_TYPE_INBOX}, ${Telephony.Sms.MESSAGE_TYPE_SENT})",
            extraSelection,
        ).joinToString(" AND ")
        val projection = arrayOf(
            Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE,
        )
        val result = mutableListOf<SmsMessage>()
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI, projection, selection, args, "${Telephony.Sms.DATE} DESC LIMIT $limit",
            )?.use { c ->
                while (c.moveToNext()) {
                    result += SmsMessage(
                        id = c.getLong(0),
                        address = c.getString(1) ?: "Unknown",
                        body = c.getString(2) ?: "",
                        timestamp = c.getLong(3),
                        incoming = c.getInt(4) == Telephony.Sms.MESSAGE_TYPE_INBOX,
                    )
                }
            }
        }
        return result
    }

    private companion object {
        const val SNAPSHOT_SIZE = 50
    }
}
