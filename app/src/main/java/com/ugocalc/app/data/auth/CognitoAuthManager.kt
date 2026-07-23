package com.ugocalc.app.data.auth

import android.util.Log
import com.amplifyframework.auth.AuthUserAttribute
import com.amplifyframework.auth.AuthUserAttributeKey
import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.amplifyframework.auth.options.AuthSignUpOptions
import com.amplifyframework.core.Amplify
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class AuthUser(
    val userId: String,
    val email: String,
    val accessToken: String,
)

sealed class AuthResult {
    data class Success(val user: AuthUser) : AuthResult()
    data class NeedsConfirmation(val email: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

object CognitoAuthManager {
    private const val TAG = "CognitoAuthManager"

    suspend fun signUp(email: String, password: String): AuthResult =
        suspendCancellableCoroutine { cont ->
            Amplify.Auth.signUp(
                email,
                password,
                AuthSignUpOptions.builder()
                    .userAttribute(AuthUserAttributeKey.email(), email)
                    .build(),
                { result ->
                    if (result.isSignUpComplete) {
                        cont.resume(AuthResult.Success(AuthUser(email, email, "")))
                    } else {
                        cont.resume(AuthResult.NeedsConfirmation(email))
                    }
                },
                { error ->
                    Log.e(TAG, "signUp error", error)
                    cont.resume(AuthResult.Error(error.message ?: "Registrazione fallita"))
                }
            )
        }

    suspend fun confirmSignUp(email: String, code: String): AuthResult =
        suspendCancellableCoroutine { cont ->
            Amplify.Auth.confirmSignUp(
                email, code,
                { result ->
                    if (result.isSignUpComplete) {
                        cont.resume(AuthResult.Success(AuthUser(email, email, "")))
                    } else {
                        cont.resume(AuthResult.Error("Conferma non completata"))
                    }
                },
                { error ->
                    Log.e(TAG, "confirmSignUp error", error)
                    cont.resume(AuthResult.Error(error.message ?: "Codice non valido"))
                }
            )
        }

    suspend fun signIn(email: String, password: String): AuthResult =
        suspendCancellableCoroutine { cont ->
            Amplify.Auth.signIn(
                email,
                password,
                { _ ->
                    Amplify.Auth.getCurrentUser(
                        { amplifyUser ->
                            Amplify.Auth.fetchAuthSession(
                                { session ->
                                    val token = extractToken(session)
                                    cont.resume(AuthResult.Success(
                                        AuthUser(amplifyUser.userId, amplifyUser.username, token)
                                    ))
                                },
                                { _ ->
                                    cont.resume(AuthResult.Success(AuthUser(email, email, "")))
                                }
                            )
                        },
                        { _ -> cont.resume(AuthResult.Success(AuthUser(email, email, ""))) }
                    )
                },
                { error ->
                    Log.e(TAG, "signIn error", error)
                    cont.resume(AuthResult.Error(error.message ?: "Accesso fallito"))
                }
            )
        }

    suspend fun getCurrentUser(): AuthUser? =
        suspendCancellableCoroutine { cont ->
            Amplify.Auth.getCurrentUser(
                { amplifyUser ->
                    Amplify.Auth.fetchAuthSession(
                        { session ->
                            val token = extractToken(session)
                            cont.resume(AuthUser(amplifyUser.userId, amplifyUser.username, token))
                        },
                        { _ ->
                            cont.resume(AuthUser(amplifyUser.userId, amplifyUser.username, ""))
                        }
                    )
                },
                { _ -> cont.resume(null) }
            )
        }

    suspend fun updateEmail(newEmail: String): AuthResult =
        suspendCancellableCoroutine { cont ->
            Amplify.Auth.updateUserAttribute(
                AuthUserAttribute(AuthUserAttributeKey.email(), newEmail),
                { cont.resume(AuthResult.Success(AuthUser(newEmail, newEmail, ""))) },
                { error ->
                    Log.e(TAG, "updateEmail error", error)
                    cont.resume(AuthResult.Error(error.message ?: "Aggiornamento email fallito"))
                }
            )
        }

    suspend fun updatePassword(oldPassword: String, newPassword: String): AuthResult =
        suspendCancellableCoroutine { cont ->
            Amplify.Auth.updatePassword(
                oldPassword, newPassword,
                { cont.resume(AuthResult.Success(AuthUser("", "", ""))) },
                { error ->
                    Log.e(TAG, "updatePassword error", error)
                    cont.resume(AuthResult.Error(error.message ?: "Aggiornamento password fallito"))
                }
            )
        }

    suspend fun deleteUser(): AuthResult =
        suspendCancellableCoroutine { cont ->
            Amplify.Auth.deleteUser(
                { cont.resume(AuthResult.Success(AuthUser("", "", ""))) },
                { error ->
                    Log.e(TAG, "deleteUser error", error)
                    cont.resume(AuthResult.Error(error.message ?: "Eliminazione account fallita"))
                }
            )
        }

    suspend fun signOut(): AuthResult =
        suspendCancellableCoroutine { cont ->
            Amplify.Auth.signOut {
                cont.resume(AuthResult.Success(AuthUser("", "", "")))
            }
        }

    private fun extractToken(session: com.amplifyframework.auth.AuthSession): String = try {
        val cognitoSession = session as AWSCognitoAuthSession
        cognitoSession.userPoolTokensResult.value?.accessToken ?: ""
    } catch (_: Exception) {
        ""
    }
}
