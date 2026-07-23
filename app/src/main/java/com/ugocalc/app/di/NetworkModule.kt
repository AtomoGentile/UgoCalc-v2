package com.ugocalc.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // URL base del backend AWS. Sostituire con l'URL reale al momento del deploy.
    private const val AWS_BASE_URL = "https://ceii722srf.execute-api.us-east-1.amazonaws.com/prod"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // Client con AuthInterceptor Cognito — usato per le chiamate al backend AWS (inference, bias…).
    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: com.ugocalc.app.data.auth.AuthInterceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            // Le chiamate di Visione (immagini) e Normalizzazione (tabelle estese) possono richiedere
            // tempi più lunghi del default: timeout generosi per evitare falsi "Analisi non riuscita".
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    // Client senza AuthInterceptor Cognito — usato dai client AI che gestiscono l'autenticazione
    // autonomamente (header x-api-key, x-goog-api-key, Authorization: Bearer propri).
    // Se usassimo il client con AuthInterceptor, il token Cognito sovrascrive il Bearer delle API
    // esterne (es. Bedrock Mantle, OpenAI) che usano lo stesso header Authorization.
    @Provides
    @Singleton
    @Named("no_auth")
    fun provideOkHttpClientNoAuth(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Named("aws_base_url")
    fun provideAwsBaseUrl(): String = AWS_BASE_URL
}
