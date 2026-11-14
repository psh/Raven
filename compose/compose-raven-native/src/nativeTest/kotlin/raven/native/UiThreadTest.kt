@file:OptIn(ObsoleteWorkersApi::class)

package raven.native

import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UiThreadTest {
    @Test
    fun passesOnTheUiThread() = withHeadlessRaven {
        requireUiThread()
    }

    @Test
    fun failsOffTheUiThread() = withHeadlessRaven {
        val worker = Worker.start()
        try {
            val failed = worker.execute(TransferMode.SAFE, {}) {
                try {
                    requireUiThread()
                    false
                } catch (_: IllegalStateException) {
                    true
                }
            }.result
            assertTrue(failed, "requireUiThread passed on a worker thread")
        } finally {
            worker.requestTermination().result
        }
    }

    @Test
    fun failsWithRavenStopped() {
        assertFailsWith<IllegalStateException> { requireUiThread() }
    }
}
