@file:OptIn(InternalComposeRavenApi::class)

package raven.compose.foundation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import raven.compose.ui.InternalComposeRavenApi
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RavenHostTest {
    @Test
    fun aStateWriteRecomposesOnTheNextFrame() = runRavenTest {
        var count by mutableIntStateOf(0)
        var composed = -1
        setContent { composed = count }
        assertEquals(0, composed)

        count = 1
        frame()
        assertEquals(1, composed)
    }

    @OptIn(ObsoleteWorkersApi::class)
    @Test
    fun aWriteFromABackgroundThreadWakesTheLoop() = runRavenTest {
        var count by mutableIntStateOf(0)
        var composed = -1
        setContent { composed = count }

        val worker = Worker.start()
        try {
            worker.execute(TransferMode.SAFE, { { count = 1 } }) { it() }.result
        } finally {
            worker.requestTermination().result
        }
        // Raven sleeps until something wakes it: the write must have posted the apply.
        assertTrue(host.waitFrame())
        waitForIdle()
        assertEquals(1, composed)
    }

    @Test
    fun anEndlessFrameLoopDoesNotStallWaitForIdle() = runRavenTest {
        var frames = 0
        setContent {
            LaunchedEffect(Unit) {
                while (true) withFrameNanos { frames++ }
            }
        }
        repeat(3) { frame() }
        waitForIdle()
        assertTrue(frames > 0, "the frame loop ran")
    }
}
