package raven.compose.ui.res

import platform.Foundation.NSBundle

/** The directory of the main bundle's executable: for an executable outside a bundle, its own. */
internal actual fun executableDirectory(): String? = NSBundle.mainBundle.executablePath?.substringBeforeLast('/', missingDelimiterValue = ".")
