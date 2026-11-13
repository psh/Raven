package raven.compose.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class RavenInfoTest {
    @Test
    fun theVersionIsRavensOwnEvenBeforeRavenStarts() {
        val version = RavenInfo.version
        assertTrue(Regex("""\d+\.\d+\.\d+.*""").matches(version), "a version: \"$version\"")
    }
}
