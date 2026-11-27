@file:OptIn(InternalComposeRavenApi::class)

package raven.sample.resources

import kotlinx.coroutines.runBlocking
import raven.compose.ui.InternalComposeRavenApi
import raven.compose.ui.res.DrawableResource
import raven.compose.ui.res.FontResource
import raven.compose.ui.test.runRavenTest
import raven.compose.ui.test.widgetRect
import compose_raven.samples.resources.generated.resources.Res
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** composeResources/ through the generated Res, read from the folder copied beside the test executable. */
class ResourcesTest {
    @Test
    fun resIsGeneratedByComposesRules() {
        assertEquals(DrawableResource("drawable/ic-arrow back.svg"), Res.drawable.ic_arrow_back, "a hyphen and a space become _")
        assertEquals(DrawableResource("drawable/badge.svg"), Res.drawable.badge)
        assertEquals(FontResource("font/Roboto-Regular-subset.ttf"), Res.font.Roboto_Regular_subset)
    }

    @Test
    fun eachResourceLoadsFromTheCopiedFolder() = runRavenTest {
        setContent { ResourcesScreen() }
        waitForIdle()
        onNodeWithTag("notes").assertTextEquals("Read from composeResources/files/ through Res.readBytes.")
        // The images are ready, sized by their SVGs: the icon at Icon's 24 dp, the badge at its 32.
        assertEquals(24f, widgetRect(onNodeWithTag("icon").fetchWidget()).width)
        assertEquals(32f, widgetRect(onNodeWithTag("badge").fetchWidget()).width)
    }

    @Test
    fun theFilesAreThere() = runBlocking {
        assertTrue(Res.readBytes("font/Roboto-Regular-subset.ttf").size > 10_000, "the font file")
        assertTrue(Res.readBytes("files/LICENSE-Roboto.txt").decodeToString().contains("Apache License"))
        assertFailsWith<IllegalStateException> { Res.readBytes("files/no-such-file.txt") }
        Unit
    }
}
