package com.ugocalc.app.data.auth

import android.app.Activity
import android.util.Log
import com.amplifyframework.auth.AuthUserAttribute
import com.amplifyframework.auth.AuthUserAttributeKey
import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.amplifyframework.auth.options.AuthSignUpOptions
import com.amplifyframework.auth.options.AuthWebUISignInOptions
import com.amplifyframework.core.Amplify
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
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
    private const val COGNITO_SESSION_TIMEOUT_MS = 2_000L
    private const val UNAVAILABLE_MESSAGE = "Autenticazione cloud non disponibile. L'app resta utilizzabile in locale."

    private fun <T> CancellableContinuation<T>.safeResume(value: T) {
        if (isActive) resume(value)
    }

    private inline fun runAmplify(
        onFailure: (Exception) -> Unit,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure(e)
        }
    }

    suspend fun signUp(email: String, password: String): AuthResult =
        suspendCancellableCoroutine { cont ->
            runAmplify(
                onFailure = { e ->
                    Log.w(TAG, "signUp unavailable: ${e.message}")
                    cont.safeResume(AuthResult.Error(UNAVAILABLE_MESSAGE))
                },
            ) {
                Amplify.Auth.signUp(
                    email,
                    password,
                    AuthSignUpOptions.builder()
                        .userAttribute(AuthUserAttributeKey.email(), email)
                        .build(),
                    { result ->
                        if (result.isSignUpComplete) {
                            cont.safeResume(AuthResult.Success(AuthUser(email, email, "")))
                        } else {
                            cont.safeResume(AuthResult.NeedsConfirmation(email))
                        }
                    },
                    { error ->
                        Log.e(TAG, "signUp error", error)
                        cont.safeResume(AuthResult.Error(error.message ?: "Registrazione fallita"))
                    },
                )
            }
        }

    suspend fun confirmSignUp(email: String, code: String): AuthResult =
        suspendCancellableCoroutine { cont ->
            runAmplify(
                onFailure = { e ->
                    Log.w(TAG, "confirmSignUp unavailable: ${e.message}")
                    cont.safeResume(AuthResult.Error(UNAVAILABLE_MESSAGE))
                },
            ) {
                Amplify.Auth.confirmSignUp(
                    email,
                    code,
                    { result ->
                        if (result.isSignUpComplete) {
                            cont.safeResume(AuthResult.Success(AuthUser(email, email, "")))
                        } else {
                            cont.safeResume(AuthResult.Error("Conferma non completata"))
                        }
                    },
                    { error ->
                        Log.e(TAG, "confirmSignUp error", error)
                        cont.safeResume(AuthResult.Error(error.message ?: "Codice non valido"))
                    },
                )
            }
        }

    suspend fun signIn(email: String, password: String): AuthResult =
        suspendCancellableCoroutine { cont ->
            runAmplify(
                onFailure = { e ->
                    Log.w(TAG, "signIn unavailable: ${e.message}")
                    cont.safeResume(AuthResult.Error(UNAVAILABLE_MESSAGE))
                },
            ) {
                Amplify.Auth.signIn(
                    email,
                    password,
                    { _ ->
                        runAmplify(
                            onFailure = { cont.safeResume(AuthResult.Success(AuthUser(email, email, ""))) },
                        ) {
                            Amplify.Auth.getCurrentUser(
                                { amplifyUser ->
                                    runAmplify(
                                        onFailure = {
                                            cont.safeResume(AuthResult.Success(AuthUser(amplifyUser.userId, amplifyUser.username, "")))
                                        },
                                    ) {
                                        Amplify.Auth.fetchAuthSession(
                                            { session ->
                                                val token = extractToken(session)
                                                cont.safeResume(
                                                    AuthResult.Success(
                                                        AuthUser(amplifyUser.userId, amplifyUser.username, token)
                                                    )
                                                )
                                            },
                                            { _ ->
                                                cont.safeResume(AuthResult.Success(AuthUser(email, email, "")))
                                            },
                                        )
                                    }
                                },
                                { _ -> cont.safeResume(AuthResult.Success(AuthUser(email, email, ""))) },
                            )
                        }
                    },
                    { error ->
                        Log.e(TAG, "signIn error", error)
                        cont.safeResume(AuthResult.Error(error.message ?: "Accesso fallito"))
                    },
                )
            }
        }

    suspend fun getCurrentUser(): AuthUser? = withTimeoutOrNull(COGNITO_SESSION_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            runAmplify(
                onFailure = { e ->
                    Log.w(TAG, "getCurrentUser unavailable: ${e.message}")
                    cont.safeResume(null)
                },
            ) {
                Amplify.Auth.getCurrentUser(
                    { amplifyUser ->
                        runAmplify(
                            onFailure = {
                                cont.safeResume(AuthUser(amplifyUser.userId, amplifyUser.username, ""))
                            },
                        ) {
                            Amplify.Auth.fetchAuthSession(
                                { session ->
                                    val token = extractToken(session)
                                    cont.safeResume(AuthUser(amplifyUser.userId, amplifyUser.username, token))
                                },
                                { _ ->
                                    cont.safeResume(AuthUser(amplifyUser.userId, amplifyUser.username, ""))
                                },
                            )
                        }
                    },
                    { _ ->
                        // Prova a recuperare la sessione anche se l'oggetto utente non è caricato
                        runAmplify(
                            onFailure = { cont.safeResume(null) },
                        ) {
                            Amplify.Auth.fetchAuthSession(
                                { session ->
                                    val token = extractToken(session)
                                    if (token.isNotEmpty()) {
                                        cont.safeResume(AuthUser("unknown", "unknown", token))
                                    } else {
                                        cont.safeResume(null)
                                    }
                                },
                                { _ -> cont.safeResume(null) },
                            )
                        }
                    },
                )
            }
        }
    }

    suspend fun signInWithGoogle(activity: Activity): AuthResult =
        suspendCancellableCoroutine { cont ->
            runAmplify(
                onFailure = { e ->
                    Log.w(TAG, "signInWithGoogle unavailable: ${e.message}")
                    cont.safeResume(AuthResult.Error(UNAVAILABLE_MESSAGE))
                },
            ) {
                Amplify.Auth.signInWithSocialWebUI(
                    com.amplifyframework.auth.AuthProvider.google(),
                    activity,
                    AuthWebUISignInOptions.builder().build(),
                    { result ->
                        if (result.isSignedIn) {
                            runAmplify(
                                onFailure = { cont.safeResume(AuthResult.Success(AuthUser("", "", ""))) },
                            ) {
                                Amplify.Auth.getCurrentUser(
                                    { amplifyUser ->
                                        runAmplify(
                                            onFailure = {
                                                cont.safeResume(AuthResult.Success(AuthUser(amplifyUser.userId, amplifyUser.username, "")))
                                            },
                                        ) {
                                            Amplify.Auth.fetchAuthSession(
                                                { session ->
                                                    val token = extractToken(session)
                                                    cont.safeResume(
                                                        AuthResult.Success(
                                                            AuthUser(amplifyUser.userId, amplifyUser.username, token)
                                                        )
                                                    )
                                                },
                                                { _ ->
                                                    cont.safeResume(AuthResult.Success(AuthUser(amplifyUser.userId, amplifyUser.username, "")))
                                                },
                                            )
                                        }
                                    },
                                    { _ -> cont.safeResume(AuthResult.Success(AuthUser("", "", ""))) },
                                )
                            }
                        } else {
                            cont.safeResume(AuthResult.Error("Login Google non completato"))
                        }
                    },
                    { error ->
                        Log.e(TAG, "signInWithGoogle error", error)
                        cont.safeResume(AuthResult.Error(error.message ?: "Login Google fallito"))
                    },
                )
            }
        }

    suspend fun updateEmail(newEmail: String): AuthResult =
        suspendCancellableCoroutine { cont ->
            runAmplify(
                onFailure = { e ->
                    Log.w(TAG, "updateEmail unavailable: ${e.message}")
                    cont.safeResume(AuthResult.Error(UNAVAILABLE_MESSAGE))
                },
            ) {
                Amplify.Auth.updateUserAttribute(
                    AuthUserAttribute(AuthUserAttributeKey.email(), newEmail),
                    { cont.safeResume(AuthResult.Success(AuthUser(newEmail, newEmail, ""))) },
                    { error ->
                        Log.e(TAG, "updateEmail error", error)
                        cont.safeResume(AuthResult.Error(error.message ?: "Aggiornamento email fallito"))
                    },
                )
            }
        }

    suspend fun updatePassword(oldPassword: String, newPassword: String): AuthResult =
        suspendCancellableCoroutine { cont ->
            runAmplify(
                onFailure = { e ->
                    Log.w(TAG, "updatePassword unavailable: ${e.message}")
                    cont.safeResume(AuthResult.Error(UNAVAILABLE_MESSAGE))
                },
            ) {
                Amplify.Auth.updatePassword(
                    oldPassword,
                    newPassword,
                    { cont.safeResume(AuthResult.Success(AuthUser("", "", ""))) },
                    { error ->
                        Log.e(TAG, "updatePassword error", error)
                        cont.safeResume(AuthResult.Error(error.message ?: "Aggiornamento password fallito"))
                    },
                )
            }
        }

    suspend fun deleteUser(): AuthResult =
        suspendCancellableCoroutine { cont ->
            runAmplify(
                onFailure = { e ->
                    Log.w(TAG, "deleteUser unavailable: ${e.message}")
                    cont.safeResume(AuthResult.Error(UNAVAILABLE_MESSAGE))
                },
            ) {
                Amplify.Auth.deleteUser(
                    { cont.safeResume(AuthResult.Success(AuthUser("", "", ""))) },
                    { error ->
                        Log.e(TAG, "deleteUser error", error)
                        cont.safeResume(AuthResult.Error(error.message ?: "Eliminazione account fallita"))
                    },
                )
            }
        }

    suspend fun signOut(): AuthResult = withTimeoutOrNull(COGNITO_SESSION_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            runAmplify(
                onFailure = { e ->
                    Log.w(TAG, "signOut unavailable (local logout proceeds): ${e.message}")
                    cont.safeResume(AuthResult.Success(AuthUser("", "", "")))
                },
            ) {
                Amplify.Auth.signOut {
                    cont.safeResume(AuthResult.Success(AuthUser("", "", "")))
                }
            }
        }
    } ?: AuthResult.Success(AuthUser("", "", ""))

    private fun extractToken(session: com.amplifyframework.auth.AuthSession): String = try {
        val cognitoSession = session as AWSCognitoAuthSession
        val tokensResult = cognitoSession.userPoolTokensResult
        if (tokensResult.error != null) {
            Log.e(TAG, "extractToken: userPoolTokensResult has error: ${tokensResult.error}")
        }
        val tokens = tokensResult.value
        // Il backend (verifyToken.js) è configurato per tokenUse: 'access'.
        // Quindi preferiamo l'Access Token per evitare errori di validazione JWT.
        val token = tokens?.accessToken ?: tokens?.idToken ?: ""
        if (token.isEmpty()) {
            Log.w(TAG, "extractToken: tokens are null or empty. isSignedIn=${session.isSignedIn}")
        } else {
            Log.d(TAG, "extractToken: Token successfully extracted (length=${token.length})")
        }
        token
    } catch (e: Exception) {
        Log.e(TAG, "extractToken: cast or access error", e)
        ""
    }
}
