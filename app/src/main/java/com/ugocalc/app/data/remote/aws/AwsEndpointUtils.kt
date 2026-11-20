package com.ugocalc.app.data.remote.aws

/**
 * Normalizza l'URL base del backend opzionale (Cloudflare Workers Free o AWS API Gateway).
 * Restituisce `null` se l'URL è vuoto/disabilitato o non valido (non http/https),
 * permettendo ai client di degradare immediatamente in modalità Local-First.
 */
internal fun normalizeAwsBaseUrl(baseUrl: String): String? {
    val trimmed = baseUrl.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    val hasValidScheme = trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("http://", ignoreCase = true)
    return if (hasValidScheme) trimmed else null
}

/**
 * Risolve l'endpoint finale per risorse compatibili con hosting statico gratuito
 * (es. GitHub Raw `version.json` / `config.json`) o backend serverless (Cloudflare Worker / AWS).
 * Se [staticOverrideUrl] è valido, ha priorità e funziona anche con [baseUrl] vuoto.
 */
internal fun resolveEndpointUrl(
    baseUrl: String,
    path: String,
    staticOverrideUrl: String = "",
): String? {
    val override = normalizeAwsBaseUrl(staticOverrideUrl)
    if (override != null) return override
    val base = normalizeAwsBaseUrl(baseUrl) ?: return null
    val cleanPath = path.trimStart('/')
    return if (cleanPath.isEmpty()) base else "$base/$cleanPath"
}
