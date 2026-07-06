package com.ugocalc.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Vero se il permesso Fotocamera è già stato concesso (usato sia in Impostazioni > Permessi sia prima di scattare, §2.2/§2.4). */
fun isCameraPermissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
