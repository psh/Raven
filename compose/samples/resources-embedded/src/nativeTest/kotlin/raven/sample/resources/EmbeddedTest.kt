package raven.sample.resources

import raven.cinterop.R_TRUE
import raven.cinterop.Rfile_is_memory
import raven.compose.ui.test.runRavenTest
import compose_raven.samples.resources.generated.resources.Res
import kotlin.test.Test
import kotlin.test.assertEquals

/** With embed = true, every file is in the executable, registered once Res is used. */
class EmbeddedTest {
    @Test
    fun everyFileIsEmbedded() = runRavenTest {
        setContent {}
        Res.drawable.badge
        for (path in listOf("drawable/badge.svg", "drawable/ic-arrow back.svg", "font/Roboto-Regular-subset.ttf", "files/notes.txt", "files/LICENSE-Roboto.txt")) {
            assertEquals(R_TRUE, Rfile_is_memory(path), path)
        }
    }
}
