package com.ugocalc.app.data.auth

import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

private class FakeChain(private val initialRequest: Request) : Interceptor.Chain {
    var proceededRequest: Request? = null

    override fun request(): Request = initialRequest

    override fun proceed(request: Request): Response {
        proceededRequest = request
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{}".toResponseBody())
            .build()
    }

    override fun connection(): Connection? = null
    override fun call(): Call = throw UnsupportedOperationException()
    override fun connectTimeoutMillis(): Int = 1000
    override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    override fun readTimeoutMillis(): Int = 1000
    override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    override fun writeTimeoutMillis(): Int = 1000
    override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
}

class AuthInterceptorTest {

    private val awsBaseUrl = "https://ceii722srf.execute-api.us-east-1.amazonaws.com/prod"

    @Test
    fun `non-AWS hosts never trigger token lookup or receive Cognito token`() {
        var resolverCalls = 0
        val interceptor = AuthInterceptor(awsBaseUrl) {
            resolverCalls++
            AuthUser("u1", "u1@test.com", "secret-cognito-token")
        }

        val urls = listOf(
            "https://api.github.com/repos/foo/bar/releases/latest",
            "https://world.openfoodfacts.org/api/v2/product/123.json",
            "https://oauth2.googleapis.com/token",
        )

        for (url in urls) {
            val chain = FakeChain(Request.Builder().url(url).build())
            interceptor.intercept(chain)
            assertNull("Expected no Authorization header for $url", chain.proceededRequest?.header("Authorization"))
        }
        assertEquals(0, resolverCalls)
    }

    @Test
    fun `public version endpoint skips token lookup`() {
        var resolverCalls = 0
        val interceptor = AuthInterceptor(awsBaseUrl) {
            resolverCalls++
            AuthUser("u1", "u1@test.com", "secret-cognito-token")
        }

        val chain = FakeChain(Request.Builder().url("$awsBaseUrl/version").build())
        interceptor.intercept(chain)

        assertNull(chain.proceededRequest?.header("Authorization"))
        assertEquals(0, resolverCalls)
    }

    @Test
    fun `blank awsBaseUrl skips token lookup cleanly`() {
        var resolverCalls = 0
        val interceptor = AuthInterceptor("") {
            resolverCalls++
            AuthUser("u1", "u1@test.com", "secret-cognito-token")
        }

        val chain = FakeChain(Request.Builder().url("$awsBaseUrl/inference").build())
        interceptor.intercept(chain)

        assertNull(chain.proceededRequest?.header("Authorization"))
        assertEquals(0, resolverCalls)
    }

    @Test
    fun `authenticated AWS backend request receives Bearer token`() {
        var resolverCalls = 0
        val interceptor = AuthInterceptor(awsBaseUrl) {
            resolverCalls++
            AuthUser("u1", "u1@test.com", "cognito-access-jwt")
        }

        val chain = FakeChain(Request.Builder().url("$awsBaseUrl/inference").build())
        interceptor.intercept(chain)

        assertEquals("Bearer cognito-access-jwt", chain.proceededRequest?.header("Authorization"))
        assertEquals(1, resolverCalls)
    }

    @Test
    fun `resolver failure degrades gracefully without throwing`() {
        val interceptor = AuthInterceptor(awsBaseUrl) {
            throw IllegalStateException("Amplify crashed")
        }

        val chain = FakeChain(Request.Builder().url("$awsBaseUrl/inference").build())
        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertNull(chain.proceededRequest?.header("Authorization"))
    }
}
