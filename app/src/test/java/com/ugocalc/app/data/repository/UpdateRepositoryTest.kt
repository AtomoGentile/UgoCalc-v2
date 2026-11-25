package com.ugocalc.app.data.repository

import com.ugocalc.app.data.remote.github.ApkUpdateDownloader
import com.ugocalc.app.data.remote.github.GitHubReleaseClient
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateRepositoryTest {

    // isNewerVersion non tocca mai il client: istanze reali ma inerti, sufficienti a costruire il repository.
    private val repository = UpdateRepository(GitHubReleaseClient(OkHttpClient(), Json))

    @Test
    fun `a higher minor version is newer`() {
        assertTrue(repository.isNewerVersion("1.3.0", "1.2.0"))
    }

    @Test
    fun `numeric comparison beats lexicographic ordering past the tenth minor release`() {
        // Confronto testuale metterebbe "1.10.0" prima di "1.2.0": qui deve vincere quello numerico.
        assertTrue(repository.isNewerVersion("1.10.0", "1.2.0"))
        assertFalse(repository.isNewerVersion("1.2.0", "1.10.0"))
    }

    @Test
    fun `an identical version is not newer`() {
        assertFalse(repository.isNewerVersion("1.2.0", "1.2.0"))
    }

    @Test
    fun `an older version is not newer`() {
        assertFalse(repository.isNewerVersion("1.0.0", "1.2.0"))
    }

    @Test
    fun `missing trailing segments count as zero`() {
        assertFalse(repository.isNewerVersion("1.3", "1.3.0"))
        assertTrue(repository.isNewerVersion("1.3.1", "1.3"))
    }

    @Test
    fun `AppUpdateStatus detects direct apk download urls vs release pages`() {
        val directApk = AppUpdateStatus(
            latestVersion = "4.6.2",
            changelog = "Fix",
            apkUrl = "https://github.com/AtomoGentile/UgoCalc-v2/releases/download/v4.6.2/UgoCalc-4.6.2-debug.apk",
            mandatory = false,
        )
        assertTrue(directApk.hasDirectApkDownload)

        val releasePage = AppUpdateStatus(
            latestVersion = "4.6.2",
            changelog = "Fix",
            apkUrl = "https://github.com/AtomoGentile/UgoCalc-v2/releases/tag/v4.6.2",
            mandatory = false,
        )
        assertFalse(releasePage.hasDirectApkDownload)
    }

    @Test
    fun `ApkUpdateDownloader validates apk zip header and formats sizes`() {
        val validApk = File.createTempFile("valid_test_", ".apk")
        val invalidHtml = File.createTempFile("invalid_test_", ".apk")
        try {
            val zipBytes = ByteArray(2048)
            zipBytes[0] = 0x50
            zipBytes[1] = 0x4B
            zipBytes[2] = 0x03
            zipBytes[3] = 0x04
            validApk.writeBytes(zipBytes)
            assertTrue(ApkUpdateDownloader.isValidApkFile(validApk))

            invalidHtml.writeText("<html><body>404 Not Found</body></html>".repeat(40))
            assertFalse(ApkUpdateDownloader.isValidApkFile(invalidHtml))
        } finally {
            validApk.delete()
            invalidHtml.delete()
        }

        assertEquals("0 MB", ApkUpdateDownloader.formatMegabytes(0L))
        assertTrue(ApkUpdateDownloader.formatMegabytes(10L * 1024L * 1024L).startsWith("10"))
    }
}
