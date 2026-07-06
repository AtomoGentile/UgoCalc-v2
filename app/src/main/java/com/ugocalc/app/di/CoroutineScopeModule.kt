package com.ugocalc.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Scope per la pipeline V2 in ombra (piano V2 §13 punto 1, §10 Fase 2): il chiamante
 * ([com.ugocalc.app.ui.camera.CameraCaptureViewModel]) naviga via non appena V1 finisce, cancellando
 * il proprio `viewModelScope` — il task ombra deve sopravvivere a quella navigazione, quindi gira su
 * uno scope applicativo indipendente invece che su quello del ViewModel.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ShadowAnalysisScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {

    @Provides
    @Singleton
    @ShadowAnalysisScope
    fun provideShadowAnalysisScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
