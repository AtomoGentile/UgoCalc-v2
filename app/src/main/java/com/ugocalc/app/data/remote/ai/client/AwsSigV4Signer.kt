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
    private const val SERVICE = "bedrock-runtime"

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
        val canonicalUri = url.encodedPath.ifEmpty { "/" }
        val canonicalQuerystring = url.encodedQuery.orEmpty()

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

    private fun formatDate(date: Date, pattern: String): String {
        val sdf = SimpleDateFormat(pattern, Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }
}
