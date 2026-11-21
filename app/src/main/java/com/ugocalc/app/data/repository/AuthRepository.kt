package com.ugocalc.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ugocalc.app.data.auth.AuthResult
import com.ugocalc.app.data.auth.AuthUser
import com.ugocalc.app.data.auth.CloudflareAuthClient
import com.ugocalc.app.data.local.db.dao.CloudSyncQueueDao
import com.ugocalc.app.sync.CloudSyncManager
import com.ugocalc.app.workers.InferencePullWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "auth_preferences")

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cloudSyncQueueDao: CloudSyncQueueDao,
    private val workManager: WorkManager,
    private val authClient: CloudflareAuthClient,
) {
    private companion object {
        const val REFRESH_WINDOW_SECONDS = 7L * 24 * 60 * 60
    }

    private object PreferencesKeys {
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val LOGGED_IN = stringPreferencesKey("logged_in")
        val LOCAL_MODE = booleanPreferencesKey("local_mode")
    }

    val isLoggedInFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LOGGED_IN]?.toBooleanStrictOrNull() ?: false
        }

    val isLocalModeFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LOCAL_MODE] ?: false
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
        val result = authClient.register(email.trim(), password)
        if (result is AuthResult.Success) saveUser(result.user)
        return result
    }

    /** Con gli account Cloudflare non serve conferma via codice: l'account è subito attivo. */
    suspend fun confirmSignUp(email: String, code: String): AuthResult =
        AuthResult.Error("La conferma via codice non è più richiesta: accedi con email e password.")

    suspend fun signIn(email: String, password: String): AuthResult {
        val result = authClient.login(email.trim(), password)
        if (result is AuthResult.Success) saveUser(result.user)
        return result
    }

    suspend fun updateEmail(newEmail: String, password: String): AuthResult {
        val token = currentToken() ?: return AuthResult.Error("Non sei connesso")
        val result = authClient.changeEmail(token, password, newEmail.trim())
        if (result is AuthResult.Success) {
            context.dataStore.edit { it[PreferencesKeys.USER_EMAIL] = newEmail.trim().lowercase() }
        }
        return result
    }

    suspend fun updatePassword(oldPassword: String, newPassword: String): AuthResult {
        val token = currentToken() ?: return AuthResult.Error("Non sei connesso")
        return authClient.changePassword(token, oldPassword, newPassword)
    }

    suspend fun deleteUser(password: String): AuthResult {
        val token = currentToken() ?: return AuthResult.Error("Non sei connesso")
        val result = authClient.deleteAccount(token, password)
        if (result is AuthResult.Success) clearUser()
        return result
    }

    suspend fun signOut(): AuthResult {
        clearUser()
        return AuthResult.Success(AuthUser("", "", ""))
    }
    suspend fun enableLocalMode() {
        runCatching { cloudSyncQueueDao.deleteByUserId(CloudSyncManager.DEFAULT_USER_ID) }
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOCAL_MODE] = true
        }
    }

    /**
     * Restituisce l'utente salvato; se il token scade entro 7 giorni lo rinnova in silenzio.
     * Se il server rifiuta il token (account eliminato / segreto ruotato) l'utente resta loggato
     * in locale: la sync fallirà finché non si rifà il login.
     */
    suspend fun getCurrentUser(): AuthUser? {
        val user = currentUserFlow.first() ?: return null
        val expiresAt = tokenExpiryEpochSeconds(user.accessToken)
        val now = System.currentTimeMillis() / 1000
        if (expiresAt != null && expiresAt - now < REFRESH_WINDOW_SECONDS) {
            val refreshed = authClient.refresh(user.accessToken)
            if (refreshed is AuthResult.Success) {
                saveUser(refreshed.user)
                return refreshed.user
            }
        }
        return user
    }

    private suspend fun currentToken(): String? = currentUserFlow.first()?.accessToken?.takeIf { it.isNotEmpty() }

    private fun tokenExpiryEpochSeconds(token: String): Long? = runCatching {
        val payload = token.split('.')[1]
        val json = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
        org.json.JSONObject(json).optLong("exp").takeIf { it > 0 }
    }.getOrNull()
    private suspend fun saveUser(user: AuthUser) {
        cloudSyncQueueDao.deleteByUserId(CloudSyncManager.DEFAULT_USER_ID)
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_ID] = user.userId
            preferences[PreferencesKeys.USER_EMAIL] = user.email
            preferences[PreferencesKeys.ACCESS_TOKEN] = user.accessToken
            preferences[PreferencesKeys.LOGGED_IN] = "true"
            preferences[PreferencesKeys.LOCAL_MODE] = false
        }
        scheduleInferencePull()
    }

    private fun scheduleInferencePull() {
        val request = OneTimeWorkRequestBuilder<InferencePullWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork("inference_pull_on_login", ExistingWorkPolicy.REPLACE, request)
    }

    private suspend fun clearUser() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
