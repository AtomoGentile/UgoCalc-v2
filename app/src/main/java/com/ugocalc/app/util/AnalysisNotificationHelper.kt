package com.ugocalc.app.util

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import com.ugocalc.app.MainActivity
import com.ugocalc.app.UgoCalcApplication
import com.ugocalc.app.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notifica "Analisi terminata" (§2.2 Permessi), mostrata solo se l'app non è in primo piano e
 * l'utente ha mantenuto attivo il permesso/preferenza notifiche.
 */
@Singleton
class AnalysisNotificationHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val foregroundObserver: AppForegroundObserver,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun notifyAnalysisCompletedIfBackgrounded() {
        if (foregroundObserver.isInForeground) return
        val settings = settingsRepository.settings.first()
        if (!settings.notificationsEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(0, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        }

        val notification = NotificationCompat.Builder(context, UgoCalcApplication.ANALYSIS_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Analisi terminata")
            .setContentText("Tocca per vedere i risultati")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
            ANALYSIS_NOTIFICATION_ID,
            notification,
        )
    }

    companion object {
        private const val ANALYSIS_NOTIFICATION_ID = 1001
    }
}
