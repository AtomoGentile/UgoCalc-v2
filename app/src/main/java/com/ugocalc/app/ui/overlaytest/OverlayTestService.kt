package com.ugocalc.app.ui.overlaytest

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

private const val TAG = "OverlayTest"

/**
 * Service minimo per il test: aggiunge una singola View statica (rettangolo rosso semi-trasparente)
 * al [WindowManager] di sistema, in un angolo dello schermo (non al centro, per non coprire lo
 * shutter button della fotocamera di sistema sotto). Isolato dal resto della livella (§13 punto 7):
 * nessuna logica sensori qui, serve solo a verificare se il produttore del device blocca la
 * tecnica dell'overlay sopra un'altra app.
 *
 * FLAG_NOT_FOCUSABLE + FLAG_NOT_TOUCHABLE: la view non deve MAI intercettare i tap destinati alla
 * fotocamera sotto, altrimenti l'utente non potrebbe più scattare.
 */
class OverlayTestService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        addOverlayIfNeeded()
        return START_NOT_STICKY
    }

    private fun addOverlayIfNeeded() {
        if (overlayView != null) {
            Log.d(TAG, "addOverlayIfNeeded: già presente, no-op")
            return
        }
        val manager = getSystemService(WINDOW_SERVICE) as? WindowManager
        if (manager == null) {
            Log.d(TAG, "addOverlayIfNeeded: WindowManager non disponibile")
            return
        }
        windowManager = manager

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            240,
            240,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 150
        }

        val view = View(this).apply {
            setBackgroundColor(Color.argb(150, 255, 0, 0))
        }

        try {
            manager.addView(view, params)
            overlayView = view
            Log.d(TAG, "addOverlayIfNeeded: view aggiunta al WindowManager (type=$overlayType)")
        } catch (e: Exception) {
            Log.d(TAG, "addOverlayIfNeeded: eccezione nell'aggiungere la view — probabile blocco del produttore", e)
        }
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        try {
            windowManager?.removeView(view)
            Log.d(TAG, "removeOverlay: view rimossa dal WindowManager")
        } catch (e: Exception) {
            Log.d(TAG, "removeOverlay: eccezione nel rimuovere la view", e)
        } finally {
            overlayView = null
            windowManager = null
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: rimozione overlay di sicurezza (l'utente potrebbe essere uscito con back)")
        removeOverlay()
        super.onDestroy()
    }
}
