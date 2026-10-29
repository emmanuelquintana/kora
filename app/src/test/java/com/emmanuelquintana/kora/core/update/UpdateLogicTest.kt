package com.emmanuelquintana.kora.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {

    private fun v(raw: String) = SemVer.parse(raw)!!

    @Test
    fun parsesVersionsWithOptionalPrefixAndBuildMetadata() {
        assertEquals(SemVer(1, 2, 3), SemVer.parse("1.2.3"))
        assertEquals(SemVer(1, 2, 3), SemVer.parse("v1.2.3"))
        assertEquals(SemVer(1, 2, 3, listOf("dev", "57")), SemVer.parse("1.2.3-dev.57+abc123"))
        assertNull(SemVer.parse("1.2"))
        assertNull(SemVer.parse("01.2.3"))
        assertNull(SemVer.parse("1.2.3-01"))
        assertNull(SemVer.parse("versión 1"))
        assertNull(SemVer.parse(null))
    }

    @Test
    fun comparesBySemVerPrecedence() {
        assertTrue(v("1.0.0") < v("2.0.0"))
        assertTrue(v("1.9.0") < v("1.10.0"))
        assertTrue(v("1.0.9") < v("1.0.10"))
        assertTrue(v("1.0.0-dev.9") < v("1.0.0-dev.10"))
        assertTrue(v("1.0.0-alpha") < v("1.0.0-alpha.1"))
        assertTrue(v("1.0.0-alpha.1") < v("1.0.0-beta"))
        assertTrue(v("1.0.0-1") < v("1.0.0-alpha"))
        assertTrue(v("1.0.0-rc.1") < v("1.0.0"))
        assertTrue(v("0.1.0-dev.5") < v("0.1.0"))
        assertEquals(0, v("1.2.3").compareTo(v("v1.2.3+build")))
    }

    private val releaseJson = """
        {
          "tag_name": "v0.2.0",
          "name": "Kora v0.2.0",
          "body": "Mejoras en la gráfica de presión.",
          "html_url": "https://github.com/emmanuelquintana/kora/releases/tag/v0.2.0",
          "draft": false,
          "prerelease": false,
          "assets": [
            {"name": "Kora-v0.2.0.apk", "browser_download_url": "https://github.com/x/Kora-v0.2.0.apk", "size": 5242880, "content_type": "application/vnd.android.package-archive"},
            {"name": "Kora-v0.2.0.apk.sha256", "browser_download_url": "https://github.com/x/Kora-v0.2.0.apk.sha256", "size": 81, "content_type": "text/plain"}
          ]
        }
    """.trimIndent()

    @Test
    fun selectsTheOfficialApkAndItsChecksum() {
        val release = GitHubReleaseParser.parse(releaseJson)!!
        val selection = ReleaseSelector.select(release, v("0.1.0"))
        assertTrue(selection is ReleaseSelection.Available)
        val info = (selection as ReleaseSelection.Available).info
        assertEquals(v("0.2.0"), info.version)
        assertEquals("Kora-v0.2.0.apk", info.apk.name)
        assertEquals("Kora-v0.2.0.apk.sha256", info.checksum?.name)
        assertEquals("Mejoras en la gráfica de presión.", info.notes)
    }

    @Test
    fun sameOrOlderReleaseIsUpToDate() {
        val release = GitHubReleaseParser.parse(releaseJson)!!
        assertTrue(ReleaseSelector.select(release, v("0.2.0")) is ReleaseSelection.UpToDate)
        assertTrue(ReleaseSelector.select(release, v("0.3.0-dev.1")) is ReleaseSelection.UpToDate)
        // Un build de prueba de la misma versión sí recibe la versión estable.
        assertTrue(ReleaseSelector.select(release, v("0.2.0-dev.40")) is ReleaseSelection.Available)
    }

    @Test
    fun releaseWithoutApkOrWithInvalidTagIsRejected() {
        val noApk = GitHubReleaseParser.parse("""{"tag_name":"v0.2.0","assets":[]}""")!!
        assertEquals(ReleaseSelection.NoApk, ReleaseSelector.select(noApk, v("0.1.0")))
        val badTag = GitHubReleaseParser.parse("""{"tag_name":"latest","assets":[]}""")!!
        assertEquals(ReleaseSelection.InvalidVersion, ReleaseSelector.select(badTag, v("0.1.0")))
        assertNull(GitHubReleaseParser.parse("no es json"))
    }

    @Test
    fun ignoresTestBuildsAndRefusesAmbiguousApks() {
        val json = """
            {"tag_name":"v0.2.0","assets":[
              {"name":"Kora-v0.2.0-PRUEBA-firma-temporal.apk","browser_download_url":"https://x/a.apk","size":1,"content_type":"application/vnd.android.package-archive"},
              {"name":"otra.apk","browser_download_url":"https://x/b.apk","size":1,"content_type":"application/vnd.android.package-archive"},
              {"name":"tercera.apk","browser_download_url":"https://x/c.apk","size":1,"content_type":"application/vnd.android.package-archive"}
            ]}
        """.trimIndent()
        assertEquals(ReleaseSelection.NoApk, ReleaseSelector.select(GitHubReleaseParser.parse(json)!!, v("0.1.0")))
    }

    @Test
    fun parsesSha256SumFiles() {
        val hash = "a".repeat(64)
        assertEquals(hash, ReleaseSelector.parseChecksum("$hash  Kora-v0.2.0.apk\n"))
        assertEquals(hash, ReleaseSelector.parseChecksum(hash.uppercase()))
        assertNull(ReleaseSelector.parseChecksum("xyz  Kora.apk"))
    }
}
