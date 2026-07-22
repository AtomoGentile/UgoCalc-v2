package com.ugocalc.app.data.auth

import android.util.Log
import com.amplifyframework.core.Amplify
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AuthUser(
    val userId: String,
    val email: String,
    val accessToken: String,
)

sealed class AuthResult {
    data class Success(val user: AuthUser) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

object CognitoAuthManager {
    private const val TAG = "CognitoAuthManager"

    suspend fun signUp(email: String, password: String): AuthResult = suspendCancellableCoroutine { continuation ->
        try {
            Amplify.Auth.signUp(
                username = email,
                password = password,
                options = com.amplifyframework.auth.AuthSignUpOptions.builder()
                    .userAttribute("email", email)
                    .build()
            ).onSuccess { result ->
                if (result.isSignUpComplete) {
                    continuation.resume(AuthResult.Success(
                        AuthUser(
                            userId = email,
                            email = email,
                            accessToken = ""
                        )
                    ))
                } else {
                    continuation.resume(AuthResult.Error("Confirmation required: ${result.nextStep}"))
                }
            }.onError { error ->
                continuation.resume(AuthResult.Error("Sign up failed: ${error.message}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "signUp exception", e)
            continuation.resumeWithException(e)
        }
    }

    suspend fun signIn(email: String, password: String): AuthResult = suspendCancellableCoroutine { continuation ->
        try {
            Amplify.Auth.signIn(
                username = email,
                password = password
            ).onSuccess {
                fetchCurrentUserWithToken { user, token ->
                    continuation.resume(AuthResult.Success(
                        AuthUser(
                            userId = user,
                            email = email,
                            accessToken = token
                        )
                    ))
                }
            }.onError { error ->
                continuation.resume(AuthResult.Error("Sign in failed: ${error.message}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "signIn exception", e)
            continuation.resumeWithException(e)
        }
    }

    suspend fun getCurrentUser(): AuthUser? = suspendCancellableCoroutine { continuation ->
        try {
            Amplify.Auth.getCurrentUser { user ->
                Amplify.Auth.fetchAuthSession { session ->
                    val accessToken = session.userPoolTokens?.accessToken ?: ""
                    continuation.resume(AuthUser(
                        userId = user.userId,
                        email = user.username,
                        accessToken = accessToken
                    ))
                }.onError {
                    continuation.resume(null)
                }
            }.onError {
                continuation.resume(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentUser exception", e)
            continuation.resumeWithException(e)
        }
    }

    suspend fun signOut(): AuthResult = suspendCancellableCoroutine { continuation ->
        try {
            Amplify.Auth.signOut().onSuccess {
                continuation.resume(AuthResult.Success(
                    AuthUser(userId = "", email = "", accessToken = "")
                ))
            }.onError { error ->
                continuation.resume(AuthResult.Error("Sign out failed: ${error.message}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "signOut exception", e)
            continuation.resumeWithException(e)
        }
    }

    private fun fetchCurrentUserWithToken(callback: (String, String) -> Unit) {
        try {
            Amplify.Auth.getCurrentUser { user ->
                Amplify.Auth.fetchAuthSession { session ->
                    val accessToken = session.userPoolTokens?.accessToken ?: ""
                    callback(user.userId, accessToken)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchCurrentUserWithToken error", e)
        }
    }
}
