package com.ugocalc.app.data.repository

import android.app.Activity
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
import com.ugocalc.app.data.auth.CognitoAuthManager
import com.ugocalc.app.data.local.db.dao.CloudSyncQueueDao
import com.ugocalc.app.sync.CloudSyncManager
import com.ugocalc.app.workers.InferencePullWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
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
) {
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
        val result = CognitoAuthManager.signUp(email, password)
        if (result is AuthResult.Success) {
            saveUser(result.user)
        }
        return result
    }

    suspend fun confirmSignUp(email: String, code: String): AuthResult {
        return CognitoAuthManager.confirmSignUp(email, code)
    }

    suspend fun signIn(email: String, password: String): AuthResult {
        val result = CognitoAuthManager.signIn(email, password)
        if (result is AuthResult.Success) {
            saveUser(result.user)
        }
        return result
    }

    suspend fun signInWithGoogle(activity: Activity): AuthResult {
        val result = CognitoAuthManager.signInWithGoogle(activity)
        if (result is AuthResult.Success) saveUser(result.user)
        return result
    }

    suspend fun updateEmail(newEmail: String): AuthResult {
        return CognitoAuthManager.updateEmail(newEmail)
    }

    suspend fun updatePassword(oldPassword: String, newPassword: String): AuthResult {
        return CognitoAuthManager.updatePassword(oldPassword, newPassword)
    }

    suspend fun deleteUser(): AuthResult {
        val result = CognitoAuthManager.deleteUser()
        if (result is AuthResult.Success) clearUser()
        return result
    }

    suspend fun signOut(): AuthResult {
        val result = CognitoAuthManager.signOut()
        clearUser()
        return result
    }

    suspend fun enableLocalMode() {
        runCatching { cloudSyncQueueDao.deleteByUserId(CloudSyncManager.DEFAULT_USER_ID) }
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOCAL_MODE] = true
        }
    }

    suspend fun getCurrentUser(): AuthUser? {
        val user = runCatching { CognitoAuthManager.getCurrentUser() }.getOrNull()
        if (user != null && user.accessToken.isNotEmpty()) {
            // Aggiorna la cache locale se abbiamo un token fresco
            saveUser(user)
        }
        return user
    }

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
