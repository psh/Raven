package raven.compose.ui.layout

/**
 * How an image is fitted in its widget when their sizes differ, as Compose's. Compose's `Inside`,
 * `FillWidth` and `FillHeight` have no Raven stretch and are left out.
 */
public enum class ContentScale {
    /** The whole image, as large as fits, centred: `R_STRETCH_FIT`. */
    Fit,

    /** The widget filled, keeping the image's ratio, the overflow cut evenly: `R_STRETCH_COVER`. */
    Crop,

    /** The widget filled, the image stretched to its ratio: `R_STRETCH_FILL`. */
    FillBounds,

    /** The image at its own size, centred, cut at the widget's bounds: `R_STRETCH_NONE`. */
    None,
}
