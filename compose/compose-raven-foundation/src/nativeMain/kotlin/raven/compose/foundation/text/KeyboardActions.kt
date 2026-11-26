package raven.compose.foundation.text

/**
 * What a `TextField` does when the user presses Enter: [onDone], from Raven's committed callback
 * (`Rtext_input_set_committed_callback`). Compose's also has `onGo`, `onNext`, `onSearch` and the
 * others, chosen by `KeyboardOptions.imeAction`; a desktop field has no IME action to choose, so
 * Enter is always done.
 */
public class KeyboardActions(public val onDone: (() -> Unit)? = null) {
    override fun equals(other: Any?): Boolean = other is KeyboardActions && other.onDone == onDone

    override fun hashCode(): Int = onDone.hashCode()

    public companion object {
        /** Enter does nothing. */
        public val Default: KeyboardActions = KeyboardActions()
    }
}
