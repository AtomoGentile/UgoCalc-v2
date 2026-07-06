package com.ugocalc.app.util

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.ugocalc.app.data.repository.BiasCorrectionRepository
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.di.ShadowAnalysisScope
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Traccia se l'app è in foreground, per decidere se mostrare la notifica "Analisi terminata"
 * (§2.2 Permessi: "quando l'app è in background o lo schermo è spento"). Lo schermo spento è
 * coperto indirettamente: se lo schermo si spegne, ON_STOP scatta comunque sull'activity in primo piano.
 *
 * Fa anche da trigger periodico per il ricalcolo dei fattori di bias (§8, §13 punto 3): ad ogni
 * apertura dell'app in foreground, ricalcola tutti i segmenti con ground truth disponibile. Non è un
 * job periodico in senso stretto (nessun `WorkManager`), ma copre lo stesso scopo del "job periodico
 * o all'apertura dell'app" descritto in `BiasCorrectionRepository.recalculate` senza aggiungere una
 * dipendenza in più: l'app si apre comunque prima di ogni analisi.
 */
@Singleton
class AppForegroundObserver @Inject constructor(
    @ApplicationContext context: Context,
    private val biasCorrectionRepository: BiasCorrectionRepository,
    private val settingsRepository: SettingsRepository,
    @ShadowAnalysisScope private val applicationScope: CoroutineScope,
) : DefaultLifecycleObserver {

    var isInForeground: Boolean = false
        private set

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        isInForeground = true
        applicationScope.launch {
            val consentGiven = settingsRepository.settings.first().biasDataConsentGiven
            biasCorrectionRepository.recalculateAll(consentGiven)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        isInForeground = false
    }
}
