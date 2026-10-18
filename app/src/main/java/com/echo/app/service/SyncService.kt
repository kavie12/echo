package com.echo.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.echo.app.EchoApplication
import com.echo.app.MainActivity
import com.echo.app.R
import com.echo.app.domain.model.Role

/** Foreground service that keeps the host server / client connection alive in the background. */
class SyncService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val role = intent?.getStringExtra(EXTRA_ROLE)?.let { runCatching { Role.valueOf(it) }.getOrNull() }
        val notification = buildNotification(role ?: Role.CLIENT)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (role == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        (application as EchoApplication).container.startEngine(role)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        (application as EchoApplication).container.stopEngines()
        super.onDestroy()
    }

    private fun buildNotification(role: Role): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_echo)
            .setContentTitle("Echo")
            .setContentText(if (role == Role.HOST) "Sharing with connected phones" else "Receiving from the host")
            .setContentIntent(open)
            .setOngoing(true)
        if (role == Role.HOST) {
            val share = PendingIntent.getActivity(
                this, 1,
                Intent(this, ClipboardShareActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Send clipboard", share)
        }
        return builder.build()
    }

    companion object {
        const val CHANNEL_ID = "echo_sync"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_ROLE = "role"

        fun start(context: Context, role: Role) {
            ContextCompat.startForegroundService(
                context, Intent(context, SyncService::class.java).putExtra(EXTRA_ROLE, role.name),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SyncService::class.java))
        }
    }
}
