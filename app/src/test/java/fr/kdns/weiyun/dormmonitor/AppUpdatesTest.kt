package fr.kdns.weiyun.dormmonitor

import org.junit.Assert.*
import org.junit.Test

class AppUpdatesTest {
    @Test fun comparesStableVersionsNumerically() {
        assertTrue(compareAppVersions("v2.10.0", "2.2.1")!! > 0)
        assertTrue(compareAppVersions("2.2.0", "2.2.1")!! < 0)
        assertEquals(0, compareAppVersions("v2.2.1+build.5", "2.2.1.0"))
        assertTrue(compareAppVersions("3.0", "2.99.99")!! > 0)
    }

    @Test fun rejectsPrereleaseAndInvalidVersionTags() {
        listOf("latest", "v2.3.0-beta.1", "2.2.1-rc1", "2.2.x", "99999999999999.0").forEach {
            assertNull(compareAppVersions(it, "2.2.1"))
        }
    }

    @Test fun restrictsUpdateLinksToThisRepository() {
        val root = "https://github.com/$UPDATE_REPOSITORY/releases/"
        assertTrue(releaseUrlAllowed(root + "download/v2.3.0/dian-2.3.0.apk", true))
        assertTrue(releaseUrlAllowed(root + "tag/v2.3.0", false))
        listOf(
            "http://github.com/$UPDATE_REPOSITORY/releases/download/v3/app.apk",
            "https://github.com.evil.test/$UPDATE_REPOSITORY/releases/download/v3/app.apk",
            "https://github.com/other/repository/releases/download/v3/app.apk",
            "https://github.com/Hanyu-lianjiang/dianshi-android/releases/download/v3/app.apk",
            root + "download/v3/file.exe", root + "download/../outside.apk",
            root + "download/v3/%2e%2e/outside.apk", "https://github.com@evil.test/$UPDATE_REPOSITORY/releases/download/v3/app.apk"
        ).forEach { assertFalse(it, releaseUrlAllowed(it, true)) }
    }
}
