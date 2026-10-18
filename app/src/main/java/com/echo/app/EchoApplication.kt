package com.echo.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.echo.app.di.AppContainer
import com.echo.app.service.SyncService

class EchoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(SyncService.CHANNEL_ID, "Sync status", NotificationManager.IMPORTANCE_LOW),
        )
    }
}
