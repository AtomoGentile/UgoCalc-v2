package com.ugocalc.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.ugocalc.app.data.local.prefs.SecureApiKeyStore
import com.ugocalc.app.domain.model.AiModelCatalog
import dagger.hilt.android.HiltAndroidApp
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import javax.inject.Inject
import com.amplifyframework.AmplifyException
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.core.Amplify

@HiltAndroidApp
class UgoCalcApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var apiKeyStore: SecureApiKeyStore

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        val mantleModels = apiKeyStore.getBedrockAvailableModels()
        if (mantleModels.isNotEmpty()) AiModelCatalog.setBedrockModels(mantleModels)
        val iamModels = apiKeyStore.getBedrockIamAvailableModels()
        if (iamModels.isNotEmpty()) AiModelCatalog.setBedrockIamModels(iamModels)
        createNotificationChannel()
        scheduleBenchmarkWorker()
        initializeAmplify()
    }

    private fun initializeAmplify() {
        try {
            Amplify.addPlugin(AWSCognitoAuthPlugin())
            Amplify.configure(applicationContext)
            Log.i("UgoCalcApplication", "Amplify initialized successfully")
        } catch (error: AmplifyException) {
            Log.e("UgoCalcApplication", "Failed to initialize Amplify", error)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun scheduleBenchmarkWorker() {
        try {
            // Schedule a daily periodic work to evaluate benchmark promotion
            val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.ugocalc.app.workers.BenchmarkReportWorker>(1, java.util.concurrent.TimeUnit.DAYS)
                .setInitialDelay(1, java.util.concurrent.TimeUnit.HOURS)
                .build()
            androidx.work.WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                    "benchmark_report_worker",
                    androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                    workRequest,
                )
        } catch (t: Throwable) {
            // Non fatal: fallisce solo se WorkManager non è disponibile in questo ambiente (es. unit tests)
            t.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
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
