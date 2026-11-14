package raven.compose.navigation3

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import raven.compose.ui.Dialog
import raven.compose.ui.DialogProperties

/**
 * Shows an entry whose metadata is [dialog]'s in a `Dialog` over the scenes under it, and declines
 * for any other: put it before the strategy of the other entries. Dialogs stack, and dismissing one
 * (Escape) goes back.
 */
public class DialogSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val entry = entries.last()
        val properties = entry.metadata[DIALOG_KEY] as? DialogProperties ?: return null
        return DialogScene(entry.contentKey, entry, entries.dropLast(1), entries.dropLast(1), properties, onBack)
    }

    public companion object {
        internal const val DIALOG_KEY: String = "raven.compose.navigation3.dialog"

        /** The metadata of an entry shown as a dialog, with [dialogProperties]. */
        public fun dialog(dialogProperties: DialogProperties = DialogProperties()): Map<String, Any> = mapOf(DIALOG_KEY to dialogProperties)
    }
}

/** An entry in a `Dialog`, over the scenes of [overlaidEntries]. */
public class DialogScene<T : Any>(
    override val key: Any,
    public val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val dialogProperties: DialogProperties,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)
    override val content: @Composable () -> Unit = {
        Dialog(onDismissRequest = onBack, properties = dialogProperties) { entry.Content() }
    }
}
