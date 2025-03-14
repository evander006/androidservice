package com.example.serviceapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build


const val CHANNEL_ID="channel_id"
const val CHANNEL_NAME="channel_name"
class BaseApp: Application() {
    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}