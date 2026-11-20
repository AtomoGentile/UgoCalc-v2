package com.ugocalc.app.data.remote.aws

import com.ugocalc.app.data.remote.github.GitHubReleaseClient
import com.ugocalc.app.data.repository.AppUpdateCheckResult
import com.ugocalc.app.data.repository.AppUpdateRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AwsClientsGracefulDegradationTest {

    private val okHttp = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `resolveEndpointUrl supports Cloudflare Worker base URLs and static overrides`() {
        assertEquals(
            "https://ugocalc-api.example.workers.dev/version",
            resolveEndpointUrl("https://ugocalc-api.example.workers.dev/", "version"),
        )
        assertEquals(
            "https://raw.githubusercontent.com/user/repo/main/version.json",
            resolveEndpointUrl(
                baseUrl = "",
                path = "version",
                staticOverrideUrl = "https://raw.githubusercontent.com/user/repo/main/version.json",
            ),
        )
        assertNull(resolveEndpointUrl("", "config"))
        assertNull(resolveEndpointUrl("ftp://invalid", "config"))
    }

    @Test
    fun `AppVersionClient degrades immediately when baseUrl is blank or invalid`() = runBlocking {
        assertEquals(VersionCheckResult.NetworkError, AppVersionClient(okHttp, json, "").fetchVersion())
        assertEquals(VersionCheckResult.NetworkError, AppVersionClient(okHttp, json, "   ").fetchVersion())
        assertEquals(VersionCheckResult.NetworkError, AppVersionClient(okHttp, json, "not-a-url").fetchVersion())
    }

    @Test
    fun `RemoteConfigClient degrades immediately when baseUrl is blank or invalid`() = runBlocking {
        assertEquals(RemoteConfigResult.NetworkError, RemoteConfigClient(okHttp, json, "").fetch())
        assertEquals(RemoteConfigResult.NetworkError, RemoteConfigClient(okHttp, json, "ftp://invalid").fetch())
    }

    @Test
    fun `RealCloudSyncClient degrades immediately when baseUrl is blank or payload is malformed`() = runBlocking {
        val disabledClient = RealCloudSyncClient(okHttp, json, "")
        assertTrue(disabledClient.fetchInferenceRecords().isEmpty())
        assertTrue(disabledClient.fetchCalibrationRecords().isEmpty())
        assertTrue(disabledClient.sendEvent("inference", "e1", "u1", "{}") is SyncResult.FatalError)

        val enabledClient = RealCloudSyncClient(okHttp, json, "https://example.invalid")
        val badJsonResult = enabledClient.sendEvent("inference", "e1", "u1", "{not-valid-json")
        assertTrue(badJsonResult is SyncResult.FatalError)
    }

    @Test
    fun `ShareClient degrades immediately when baseUrl is blank`() = runBlocking {
        val shareClient = ShareClient(okHttp, "")
        assertTrue(shareClient.generateInvite() is GenerateInviteResult.Error)
        assertTrue(shareClient.requestAccess("tok") is RequestAccessResult.Error)
        assertTrue(shareClient.pollStatus("tok") is InviteStatusResult.Error)
        assertTrue(shareClient.approve("tok", true) is ApproveResult.Error)
    }

    @Test
    fun `AppUpdateRepository uses GitHub Releases as primary source without calling AWS`() = runBlocking {
        val gitHubClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val body = """
                    {
                      "tag_name": "v9.9.0",
                      "html_url": "https://github.com/AtomoGentile/UgoCalc-v2/releases/tag/v9.9.0",
                      "body": "Release notes",
                      "assets": [
                        {
                          "name": "ugocalc.apk",
                          "browser_download_url": "https://github.com/AtomoGentile/UgoCalc-v2/releases/download/v9.9.0/ugocalc.apk"
                        }
                      ]
                    }
                """.trimIndent()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody())
                    .build()
            }
            .build()

        var fallbackCalled = false
        val fallbackHttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                fallbackCalled = true
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Error")
                    .body("".toResponseBody())
                    .build()
            }
            .build()

        val repo = AppUpdateRepository(
            versionClient = AppVersionClient(fallbackHttp, json, "https://example.invalid"),
            gitHubReleaseClient = GitHubReleaseClient(gitHubClient, json),
        )

        val result = repo.checkForUpdate()
        assertTrue(result is AppUpdateCheckResult.UpdateAvailable)
        val status = (result as AppUpdateCheckResult.UpdateAvailable).status
        assertEquals("9.9.0", status.latestVersion)
        assertEquals("https://github.com/AtomoGentile/UgoCalc-v2/releases/download/v9.9.0/ugocalc.apk", status.apkUrl)
        assertFalse(fallbackCalled)
        assertTrue(repo.isOlderVersion("3.0.4", "3.0.5"))
        assertFalse(repo.isOlderVersion("3.10.0", "3.2.0"))
    }

    @Test
    fun `AppUpdateRepository degrades to Unavailable when GitHub and fallback are offline`() = runBlocking {
        val offlineGitHub = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("{}".toResponseBody())
                    .build()
            }
            .build()

        val repo = AppUpdateRepository(
            versionClient = AppVersionClient(okHttp, json, ""),
            gitHubReleaseClient = GitHubReleaseClient(offlineGitHub, json),
        )

        assertEquals(AppUpdateCheckResult.Unavailable, repo.checkForUpdate())
    }
}
