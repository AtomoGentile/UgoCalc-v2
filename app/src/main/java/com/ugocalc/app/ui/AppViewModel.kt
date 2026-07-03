package com.ugocalc.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.BuildConfig
import com.ugocalc.app.data.local.prefs.UserSettings
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.domain.model.Changelog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    /**
     * Popup "Novità" (§ richiesta utente): confronta `versionCode` (incrementato ad ogni commit),
     * non `versionName` (fisso a "1.0.0", vedi [[ugocalc-workflow]]). `0` significa "mai avviata
     * prima": in quel caso non mostriamo nulla qui, se ne occupa il disclaimer di primo avvio.
     * Richiede anche [Changelog.ENTRIES] non vuoto: finché non esce il primo aggiornamento dopo
     * la 1.0.0 non c'è nulla da mostrare (evita anche un crash su `ENTRIES.first()`).
     */
    val showWhatsNew: StateFlow<Boolean> = settings
        .map { it.lastSeenVersionCode != 0 && it.lastSeenVersionCode < BuildConfig.VERSION_CODE && Changelog.ENTRIES.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Bug fix: [settings] parte con i valori di default di [UserSettings] finché DataStore non
     * emette il valore vero (asincrono), quindi per un istante `hasSeenDataDisclaimer = false` per
     * qualsiasi utente — anche chi l'aveva già chiuso — causando un lampeggio del disclaimer/popup
     * Novità ad ogni avvio. [isReady] resta `false` finché non arriva la prima emissione reale, così
     * il chiamante può evitare di mostrare questi dialog prima d'allora.
     */
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    init {
        viewModelScope.launch {
            val first = settingsRepository.settings.first()
            if (first.lastSeenVersionCode == 0) {
                settingsRepository.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
            }
            _isReady.value = true
        }
    }

    fun dismissDataDisclaimer() {
        viewModelScope.launch { settingsRepository.setHasSeenDataDisclaimer(true) }
    }

    fun dismissWhatsNew() {
        viewModelScope.launch { settingsRepository.setLastSeenVersionCode(BuildConfig.VERSION_CODE) }
    }
}
