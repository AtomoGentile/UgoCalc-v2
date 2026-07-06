package com.ugocalc.app.ui.overlaytest

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

private const val TAG = "OverlayTest"

/**
 * Test isolato: SYSTEM_ALERT_WINDOW è un permesso "speciale" (non runtime standard) su Android
 * 6+ — dichiararlo nel manifest non basta, va concesso dall'utente nella schermata di sistema
 * aperta con ACTION_MANAGE_OVERLAY_PERMISSION, e va verificato con Settings.canDrawOverlays()
 * perché non passa dal normale flusso ActivityResultContracts.RequestPermission().
 */
object OverlayPermissionHelper {

    fun hasOverlayPermission(context: Context): Boolean {
        val granted = Settings.canDrawOverlays(context)
        Log.d(TAG, "hasOverlayPermission: $granted")
        return granted
    }

    /** Apre la schermata di sistema "Consenti sovrapposizione ad altre app" per questo package. */
    fun buildRequestOverlayPermissionIntent(context: Context): Intent {
        Log.d(TAG, "Costruzione intent ACTION_MANAGE_OVERLAY_PERMISSION")
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
    }
}
