package raven.compose.ui

/**
 * Marks the parts of compose-raven that its own modules and test harness use across module
 * boundaries. They are not for apps and may change in any release.
 */
@RequiresOptIn(
    message = "This is compose-raven's internal API, for its own modules and test harness.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class InternalComposeRavenApi
