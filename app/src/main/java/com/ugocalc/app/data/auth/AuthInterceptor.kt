package com.ugocalc.app.data.auth

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider

internal fun interface AwsAuthUserResolver {
    suspend fun resolveUser(): AuthUser?
}

class AuthInterceptor private constructor(
    private val awsBaseUrl: String,
    private val userResolver: AwsAuthUserResolver,
) : Interceptor {

    @Inject
    constructor(
        authRepositoryProvider: Provider<com.ugocalc.app.data.repository.AuthRepository>,
        @Named("aws_base_url") awsBaseUrl: String,
    ) : this(
        awsBaseUrl = awsBaseUrl,
        userResolver = AwsAuthUserResolver {
            try {
                withTimeoutOrNull(3_000L) { authRepositoryProvider.get().getCurrentUser() }
            } catch (_: Exception) {
                null
            }
        },
    )
    internal constructor(
        awsBaseUrl: String,
        resolveUser: suspend () -> AuthUser?,
    ) : this(
        awsBaseUrl = awsBaseUrl,
        userResolver = AwsAuthUserResolver(resolveUser),
    )

    @Volatile
    private var cachedUser: AuthUser? = null

    @Volatile
    private var lastFetchTime: Long = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        if (originalRequest.header("Authorization") != null ||
            !shouldAttachAwsAuth(originalRequest.url, awsBaseUrl)
        ) {
            return chain.proceed(originalRequest)
        }

        val finalUser = runBlocking {
            val now = System.currentTimeMillis()
            val cached = cachedUser
            if (cached != null && now - lastFetchTime < CACHE_TTL_MS) {
                return@runBlocking cached
            }

            val resolved = try {
                userResolver.resolveUser()
            } catch (_: Exception) {
                null
            }

            if (resolved != null && resolved.accessToken.isNotEmpty()) {
                cachedUser = resolved
                lastFetchTime = now
            }
            resolved
        }

        return if (finalUser != null && finalUser.accessToken.isNotEmpty()) {
            val authenticatedRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer ${finalUser.accessToken}")
                .build()
            chain.proceed(authenticatedRequest)
        } else {
            chain.proceed(originalRequest)
        }
    }

    companion object {
        private const val CACHE_TTL_MS = 60_000L

        /**
         * Allega il token di sessione UgoCalc SOLO alle chiamate dirette all'host del backend AWS configurato
         * (escludendo `/version` che è pubblico). Evita blocchi `runBlocking` e leak del token
         * verso endpoint terzi (GitHub Releases, OpenFoodFacts, Vertex AI OAuth).
         */
        internal fun shouldAttachAwsAuth(requestUrl: HttpUrl, awsBaseUrl: String): Boolean {
            val trimmedBase = awsBaseUrl.trim().trimEnd('/')
            if (trimmedBase.isEmpty()) return false
            val baseHttpUrl = trimmedBase.toHttpUrlOrNull() ?: return false
            if (!requestUrl.host.equals(baseHttpUrl.host, ignoreCase = true)) return false
            if (requestUrl.encodedPath.endsWith("/version")) return false
            return true
        }
    }
}
