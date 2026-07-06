package com.ugocalc.app.data.repository

import com.ugocalc.app.data.remote.github.GitHubReleaseClient
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
