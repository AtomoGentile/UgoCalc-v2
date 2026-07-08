package com.ugocalc.app.ui.bias

import androidx.lifecycle.ViewModel
import com.ugocalc.app.ui.AnalysisSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class V2ResultViewModel @Inject constructor(
    private val sessionStore: AnalysisSessionStore
) : ViewModel() {
    val session = sessionStore.session
}
