package raven.compose.ui.text.input

/**
 * How a `TextField` shows its text: as it is ([None]) or hidden ([PasswordVisualTransformation]).
 * Compose's is an interface any app implements, filtering an `AnnotatedString`; Raven's field can
 * only show its text or hide it, so these two are the only ones.
 */
public sealed interface VisualTransformation {
    public companion object {
        /** The text as it is. */
        public val None: VisualTransformation = NoTransformation
    }
}

private data object NoTransformation : VisualTransformation

/**
 * Hides the text, one [mask] per character, as a password field does (`Rtext_input_set_password`).
 * Raven also refuses to copy or cut it, and a double click selects all of it rather than a word.
 * Raven draws a bullet, U+2022: another [mask] is drawn as a bullet too, with a warning.
 */
public data class PasswordVisualTransformation(val mask: Char = '•') : VisualTransformation
