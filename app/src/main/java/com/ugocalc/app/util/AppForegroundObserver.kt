package com.ugocalc.app.util

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Traccia se l'app è in foreground, per decidere se mostrare la notifica "Analisi terminata"
 * (§2.2 Permessi: "quando l'app è in background o lo schermo è spento"). Lo schermo spento è
 * coperto indirettamente: se lo schermo si spegne, ON_STOP scatta comunque sull'activity in primo piano.
 */
@Singleton
class AppForegroundObserver @Inject constructor(@ApplicationContext context: Context) : DefaultLifecycleObserver {

    var isInForeground: Boolean = false
        private set

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        isInForeground = true
    }

    override fun onStop(owner: LifecycleOwner) {
        isInForeground = false
    }
}
