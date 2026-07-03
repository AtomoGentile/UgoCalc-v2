package com.ugocalc.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class UgoCalcApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            ANALYSIS_NOTIFICATION_CHANNEL_ID,
            "Analisi completate",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Notifica quando un'analisi termina mentre l'app è in background"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ANALYSIS_NOTIFICATION_CHANNEL_ID = "analysis_completed"
    }
}
