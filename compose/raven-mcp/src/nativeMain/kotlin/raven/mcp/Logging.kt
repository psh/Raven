package raven.mcp

import io.github.oshai.kotlinlogging.Appender
import io.github.oshai.kotlinlogging.DirectLoggerFactory
import io.github.oshai.kotlinlogging.KLoggingEvent
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import io.ktor.util.logging.LogLevel
import io.ktor.util.logging.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.fflush
import platform.posix.fprintf
import platform.posix.stderr
import kotlin.concurrent.atomics.AtomicReference

/** How much a message matters, as the log callback receives it. */
public enum class McpLogLevel { Info, Warning, Error }

/**
 * Where everything Raven MCP and the libraries under it say goes: the app's log callback, or, with
 * none, nowhere but errors on stderr. Nothing is ever written to stdout.
 */
internal object McpLog {
    private val sink = AtomicReference<((McpLogLevel, String) -> Unit)?>(null)

    fun setSink(log: ((McpLogLevel, String) -> Unit)?) {
        sink.store(log)
    }

    fun info(message: String) = emit(McpLogLevel.Info, message)
    fun warn(message: String) = emit(McpLogLevel.Warning, message)
    fun error(message: String) = emit(McpLogLevel.Error, message)

    @OptIn(ExperimentalForeignApi::class)
    fun emit(level: McpLogLevel, message: String) {
        val log = sink.load()
        if (log != null) {
            log(level, message)
        } else if (level == McpLogLevel.Error) {
            fprintf(stderr, "raven_mcp: %s\n", message)
            fflush(stderr)
        }
    }

    /** Turns the SDK's kotlin-logging into McpLog, before any of its loggers exist. */
    fun routeKotlinLogging() {
        KotlinLoggingConfiguration.logStartupMessage = false
        KotlinLoggingConfiguration.loggerFactory = DirectLoggerFactory
        KotlinLoggingConfiguration.direct.logLevel = Level.INFO
        KotlinLoggingConfiguration.direct.appender = object : Appender {
            override fun log(loggingEvent: KLoggingEvent) {
                val level = when (loggingEvent.level) {
                    Level.ERROR -> McpLogLevel.Error
                    Level.WARN -> McpLogLevel.Warning
                    Level.INFO -> McpLogLevel.Info
                    else -> return
                }
                val cause = loggingEvent.cause?.let { ": $it" } ?: ""
                emit(level, "${loggingEvent.message ?: ""}$cause")
            }
        }
    }

    /** Ktor's server logger, as McpLog. */
    val ktor: Logger = object : Logger {
        override val level: LogLevel = LogLevel.INFO
        override fun error(message: String) = emit(McpLogLevel.Error, message)
        override fun error(message: String, cause: Throwable) = emit(McpLogLevel.Error, "$message: $cause")
        override fun warn(message: String) = emit(McpLogLevel.Warning, message)
        override fun warn(message: String, cause: Throwable) = emit(McpLogLevel.Warning, "$message: $cause")
        override fun info(message: String) = emit(McpLogLevel.Info, message)
        override fun info(message: String, cause: Throwable) = emit(McpLogLevel.Info, "$message: $cause")
        override fun debug(message: String) {}
        override fun debug(message: String, cause: Throwable) {}
        override fun trace(message: String) {}
        override fun trace(message: String, cause: Throwable) {}
    }
}
