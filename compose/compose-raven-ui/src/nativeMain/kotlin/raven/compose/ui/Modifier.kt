package raven.compose.ui

/**
 * An ordered, immutable chain of elements that decorate a composable's widget, as in Compose:
 * `Modifier.padding(8.dp).fillMaxWidth()`. Chains compare equal when their elements do, so an
 * unchanged chain costs nothing on recomposition.
 */
public interface Modifier {
    /** Folds the elements from the first in the chain to the last. */
    public fun <R> foldIn(initial: R, operation: (R, Element) -> R): R

    /** This chain followed by [other]. */
    public infix fun then(other: Modifier): Modifier = if (other === Modifier) this else CombinedModifier(this, other)

    /** One element of a chain. */
    public interface Element : Modifier {
        override fun <R> foldIn(initial: R, operation: (R, Element) -> R): R = operation(initial, this)
    }

    /** The empty chain, and the start of every chain. */
    public companion object : Modifier {
        override fun <R> foldIn(initial: R, operation: (R, Element) -> R): R = initial
        override infix fun then(other: Modifier): Modifier = other
        override fun toString(): String = "Modifier"
    }
}

private class CombinedModifier(private val outer: Modifier, private val inner: Modifier) : Modifier {
    override fun <R> foldIn(initial: R, operation: (R, Modifier.Element) -> R): R =
        inner.foldIn(outer.foldIn(initial, operation), operation)

    override fun equals(other: Any?): Boolean =
        other is CombinedModifier && outer == other.outer && inner == other.inner

    override fun hashCode(): Int = outer.hashCode() + 31 * inner.hashCode()

    override fun toString(): String =
        "[" + foldIn("") { text, element -> if (text.isEmpty()) element.toString() else "$text, $element" } + "]"
}
