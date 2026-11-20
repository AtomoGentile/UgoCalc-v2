package com.ugocalc.app.data.remote.ai.client

import okhttp3.Request
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Firma le richieste OkHttp con AWS Signature Version 4.
 * Implementazione minimale senza dipendenze AWS SDK — sufficiente per Bedrock Runtime.
 */
object AwsSigV4Signer {

    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    private const val SERVICE = "bedrock"

    fun sign(
        request: Request,
        accessKeyId: String,
        secretAccessKey: String,
        region: String,
        body: ByteArray,
    ): Request {
        val now = Date()
        val dateStamp = formatDate(now, "yyyyMMdd")
        val amzDate = formatDate(now, "yyyyMMdd'T'HHmmss'Z'")

        val url = request.url
        val host = url.host
        // SigV4 canonical URI: take the already-percent-encoded path from OkHttp, then
        // URI-encode each character that is not unreserved AND not a forward slash.
        // The '%' in any existing %XX escape must itself be encoded to %25, producing %25XX.
        // Example: modelId "foo:bar" → OkHttp encodes ':' → "%3A" in encodedPath
        //          → canonical URI encodes '%' of "%3A" → "%253A"  (AWS expected behavior).
        val canonicalUri = uriEncodePath(url.encodedPath.ifEmpty { "/" })
        val canonicalQuerystring = url.encodedQuery
            ?.split("&")
            ?.sorted()
            ?.joinToString("&")
            .orEmpty()

        val payloadHash = sha256Hex(body)

        val headers = sortedMapOf(
            "host" to host,
            "x-amz-date" to amzDate,
            "x-amz-content-sha256" to payloadHash,
        )
        request.header("Content-Type")?.let { headers["content-type"] = it }

        val signedHeaders = headers.keys.joinToString(";")
        val canonicalHeaders = headers.entries.joinToString("") { "${it.key}:${it.value}\n" }

        val canonicalRequest = listOf(
            request.method,
            canonicalUri,
            canonicalQuerystring,
            canonicalHeaders,
            signedHeaders,
            payloadHash,
        ).joinToString("\n")

        val credentialScope = "$dateStamp/$region/$SERVICE/aws4_request"
        val stringToSign = listOf(
            ALGORITHM,
            amzDate,
            credentialScope,
            sha256Hex(canonicalRequest.toByteArray()),
        ).joinToString("\n")

        val signingKey = deriveSigningKey(secretAccessKey, dateStamp, region)
        val signature = hmacSha256(signingKey, stringToSign).toHex()

        val authorization = "$ALGORITHM Credential=$accessKeyId/$credentialScope, " +
            "SignedHeaders=$signedHeaders, Signature=$signature"

        return request.newBuilder()
            .header("x-amz-date", amzDate)
            .header("x-amz-content-sha256", payloadHash)
            .header("Authorization", authorization)
            .build()
    }

    private fun deriveSigningKey(secretKey: String, dateStamp: String, region: String): ByteArray {
        val kDate = hmacSha256("AWS4$secretKey".toByteArray(), dateStamp)
        val kRegion = hmacSha256(kDate, region)
        val kService = hmacSha256(kRegion, SERVICE)
        return hmacSha256(kService, "aws4_request")
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray())
    }

    private fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    // SigV4: encode every byte except unreserved chars (A-Z a-z 0-9 - _ . ~) and '/'.
    // Applied to the already-encoded path from OkHttp, so '%' → '%25', turning %3A → %253A.
    private fun uriEncodePath(encodedPath: String): String = buildString {
        for (ch in encodedPath) {
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' ||
                ch == '-' || ch == '_' || ch == '.' || ch == '~' || ch == '/') {
                append(ch)
            } else {
                ch.toString().toByteArray(Charsets.UTF_8).forEach { byte ->
                    append("%${"%02X".format(byte)}")
                }
            }
        }
    }


    private fun formatDate(date: Date, pattern: String): String {
        val sdf = SimpleDateFormat(pattern, Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }
}
