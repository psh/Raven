@file:OptIn(ExperimentalForeignApi::class)

package raven.mcp

import kotlinx.cinterop.ExperimentalForeignApi
import raven.mcp.cinterop.R_FALSE
import raven.mcp.cinterop.R_TRUE
import raven.mcp.cinterop.Rget_time
import raven.mcp.cinterop.Ris_idle

/**
 * How long a tool's reply waits for the app to be idle, on Raven's clock: about 120 frames at 60 Hz
 * (compose/docs/mcp.md, "The UI tools"). Timed rather than counted, because headless frames take
 * no time, and an animation that settles in a dozen frames on a display takes hundreds there.
 */
internal const val IDLE_SECONDS: Double = 2.0

/**
 * Checks Ris_idle once a frame, through Rtask_post, and runs [then] on the UI thread once the app
 * is idle (true) or still busy after [IDLE_SECONDS] (false, logged). Never Rwait_idle: a tool runs
 * inside a frame.
 */
internal fun whenIdle(tool: String, then: (idle: Boolean) -> Unit) {
    val deadline = Rget_time() + IDLE_SECONDS
    fun check() {
        postToUiThread {
            when {
                Ris_idle(R_FALSE) == R_TRUE -> then(true)
                Rget_time() >= deadline -> {
                    McpLog.warn("$tool: the app was still busy after ${IDLE_SECONDS.toInt()} s; replied anyway")
                    then(false)
                }
                else -> check()
            }
        }
    }
    check()
}
