package com.ugocalc.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ugocalc.app.data.auth.AuthResult
import com.ugocalc.app.data.auth.AuthUser
import com.ugocalc.app.data.auth.CognitoAuthManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "auth_preferences")

@Singleton
class AuthRepository @Inject constructor(
    private val context: Context
) {
    private object PreferencesKeys {
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val LOGGED_IN = stringPreferencesKey("logged_in")
    }

    val isLoggedInFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LOGGED_IN]?.toBooleanStrictOrNull() ?: false
        }

    val currentUserFlow: Flow<AuthUser?> = context.dataStore.data
        .map { preferences ->
            val userId = preferences[PreferencesKeys.USER_ID]
            val email = preferences[PreferencesKeys.USER_EMAIL]
            val token = preferences[PreferencesKeys.ACCESS_TOKEN]

            if (userId != null && email != null && token != null) {
                AuthUser(userId = userId, email = email, accessToken = token)
            } else {
                null
            }
        }

    suspend fun signUp(email: String, password: String): AuthResult {
        val result = CognitoAuthManager.signUp(email, password)
        if (result is AuthResult.Success) {
            saveUser(result.user)
        }
        return result
    }

    suspend fun signIn(email: String, password: String): AuthResult {
        val result = CognitoAuthManager.signIn(email, password)
        if (result is AuthResult.Success) {
            saveUser(result.user)
        }
        return result
    }

    suspend fun signOut(): AuthResult {
        val result = CognitoAuthManager.signOut()
        clearUser()
        return result
    }

    suspend fun getCurrentUser(): AuthUser? {
        return CognitoAuthManager.getCurrentUser()
    }

    private suspend fun saveUser(user: AuthUser) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_ID] = user.userId
            preferences[PreferencesKeys.USER_EMAIL] = user.email
            preferences[PreferencesKeys.ACCESS_TOKEN] = user.accessToken
            preferences[PreferencesKeys.LOGGED_IN] = "true"
        }
    }

    private suspend fun clearUser() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
