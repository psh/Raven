package raven.sample.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import raven.compose.foundation.Button
import raven.compose.foundation.Canvas
import raven.compose.foundation.Checkbox
import raven.compose.foundation.CheckboxItem
import raven.compose.foundation.ComboBox
import raven.compose.foundation.Expander
import raven.compose.foundation.HorizontalDivider
import raven.compose.foundation.Item
import raven.compose.foundation.ListView
import raven.compose.foundation.Menu
import raven.compose.foundation.MenuBar
import raven.compose.foundation.Separator
import raven.compose.foundation.Slider
import raven.compose.foundation.SpinBox
import raven.compose.foundation.Switch
import raven.compose.foundation.TabView
import raven.compose.foundation.Text
import raven.compose.foundation.TextButton
import raven.compose.foundation.TextField
import raven.compose.foundation.Toolbar
import raven.compose.foundation.TreeView
import raven.compose.foundation.Viewport
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.Grid
import raven.compose.foundation.layout.GridTrack
import raven.compose.foundation.layout.HorizontalSplitter
import raven.compose.foundation.layout.Row
import raven.compose.foundation.layout.Spacer
import raven.compose.foundation.layout.VerticalScroll
import raven.compose.foundation.layout.VerticalSplitter
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.foundation.layout.padding
import raven.compose.foundation.layout.widthIn
import raven.compose.foundation.rememberListViewState
import raven.compose.ui.Alignment
import raven.compose.ui.Modifier
import raven.compose.ui.RavenInfo
import raven.compose.ui.Window
import raven.compose.ui.alpha
import raven.compose.ui.application
import raven.compose.ui.geometry.Offset
import raven.compose.ui.geometry.Size
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.ImageBitmap
import raven.compose.ui.input.key.Key
import raven.compose.ui.input.key.KeyEventType
import raven.compose.ui.input.key.KeyShortcut
import raven.compose.ui.input.pointer.PointerButton
import raven.compose.ui.input.pointer.PointerEventType
import raven.compose.ui.input.pointer.PointerIcon
import raven.compose.ui.input.pointer.onPointerEvent
import raven.compose.ui.input.pointer.pointerHoverIcon
import raven.compose.ui.platform.LocalDensity
import raven.compose.ui.platform.LocalWindowInfo
import raven.compose.ui.rememberWindowState
import raven.compose.ui.testTag
import raven.compose.ui.theme.Look
import raven.compose.ui.theme.RavenTheme
import raven.compose.ui.theme.ThemeMetric
import raven.compose.ui.tooltip
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.IntSize
import raven.compose.ui.unit.dp
import raven.compose.ui.unit.sp

// A port of Raven's examples/editor: a small scene editor, with what an editor needs.
//
//   the window          composables where the C example has editor.xml, state where it finds
//                       widgets by name and drives them through actions
//   the scene           a Viewport showing an ImageBitmap the app renders in software, at the
//                       viewport's size in pixels, in bands on every core with coroutines (the C
//                       example's job system), from a withFrameNanos loop while it plays
//   the profiler        a Canvas drawing the time between frames
//   the log             a ListView, which asks for the rows it draws, kept at its last line
//   View > Scale        RavenTheme(metrics), where the C example sets the theme's metrics

fun main() = application {
    EditorApp(onQuit = ::exitApplication)
}

/** The View menu's themes: a look, dark or light. */
internal enum class EditorTheme(val text: String, val look: Look, val dark: Boolean) {
    Dark("Dark theme", Look.Raven, true),
    Light("Light theme", Look.Raven, false),
    GlassDark("Dark glass", Look.Glass, true),
    GlassLight("Light glass", Look.Glass, false),
}

/** View > Scale: the theme's metrics for a denser or a roomier window. */
internal val CompactMetrics = mapOf(ThemeMetric.FontSize to 12f, ThemeMetric.ControlHeight to 22f, ThemeMetric.RowHeight to 20f)
internal val ComfortableMetrics = mapOf(ThemeMetric.FontSize to 15f, ThemeMetric.ControlHeight to 32f, ThemeMetric.RowHeight to 28f)

/** The application: its theme and scale, which are app-wide, around the editor's window. */
@Composable
internal fun EditorApp(onQuit: () -> Unit, editor: Editor = remember { Editor() }) {
    var theme by remember { mutableStateOf(EditorTheme.Dark) }
    var metrics by remember { mutableStateOf(emptyMap<ThemeMetric, Float>()) }
    RavenTheme(look = theme.look, dark = theme.dark, metrics = metrics) {
        Window(
            onCloseRequest = onQuit,
            state = rememberWindowState(size = DpSize(1180.dp, 720.dp)),
            title = "Raven - editor example",
            minimumSize = DpSize(640.dp, 420.dp),
            // The C example's window shortcuts: F5 plays or pauses, Delete deletes the selection.
            onKeyEvent = { event ->
                when {
                    event.type != KeyEventType.KeyDown -> false
                    event.key == Key.F5 -> true.also { editor.togglePlaying() }
                    event.key == Key.Delete -> true.also { editor.deleteSelected() }
                    else -> false
                }
            },
        ) {
            EditorWindow(editor, theme, { theme = it }, { metrics = it }, onQuit)
        }
    }
}

@Composable
private fun EditorWindow(
    editor: Editor,
    theme: EditorTheme,
    onThemeChange: (EditorTheme) -> Unit,
    onMetricsChange: (Map<ThemeMetric, Float>) -> Unit,
    onQuit: () -> Unit,
) {
    LaunchedEffect(editor) {
        editor.log("Raven ${RavenInfo.version}")
        editor.add("Sun")
        editor.add("Planet")
        editor.add("Moon")
    }
    Column(Modifier.fillMaxSize()) {
        MenuBar(Modifier.fillMaxWidth()) {
            Menu("File") {
                Item("New scene", onClick = editor::newScene, shortcut = KeyShortcut(Key.N, ctrl = true))
                Item("Add object", onClick = { editor.add() }, shortcut = KeyShortcut(Key.D, ctrl = true))
                Separator()
                Item("Quit", onClick = onQuit, shortcut = KeyShortcut(Key.Q, ctrl = true))
            }
            Menu("View") {
                // The two glass themes make every control translucent (see samples/glass).
                for (choice in EditorTheme.entries) CheckboxItem(choice.text, choice == theme, { onThemeChange(choice) })
                Separator()
                Menu("Scale") {
                    Item("Compact", onClick = { onMetricsChange(CompactMetrics) })
                    Item("Comfortable", onClick = { onMetricsChange(ComfortableMetrics) })
                }
            }
        }
        val stats = remember { mutableStateOf("") }
        Toolbar(Modifier.fillMaxWidth()) {
            Button(onClick = editor::togglePlaying, Modifier.widthIn(min = 72.dp).tooltip("Start or stop the animation (F5)").testTag("play")) {
                Text(if (editor.playing) "Pause" else "Play")
            }
            TextButton(onClick = { editor.add() }, Modifier.tooltip("Add an object to the scene (Ctrl+D)").testTag("add")) { Text("Add object") }
            TextButton(onClick = editor::deleteSelected, Modifier.tooltip("Delete the selected object (Delete)").testTag("delete")) { Text("Delete") }
            Spacer(Modifier.weight(1f))
            Text(stats.value, Modifier.alpha(0.7f))
        }
        HorizontalDivider()
        VerticalSplitter(Modifier.weight(1f).fillMaxWidth()) {
            HorizontalSplitter(Modifier.weight(3f)) {
                Column(Modifier.weight(1f).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Outliner", fontSize = 15.sp)
                    TreeView(
                        editor.objects, { emptyList() }, { it.name }, { it.id },
                        selection = setOfNotNull(editor.selectedId),
                        onSelectionChange = { editor.selectedId = it.firstOrNull() as Int? },
                        modifier = Modifier.weight(1f).fillMaxWidth().testTag("outliner"),
                    )
                }
                var tab by remember { mutableIntStateOf(0) }
                TabView(tab, { tab = it }, Modifier.weight(3f)) {
                    Tab("Scene") { ScenePanel(editor, stats) }
                    Tab("Profiler") { Profiler(editor) }
                }
                VerticalScroll(Modifier.weight(1.3f)) { Properties(editor) }
            }
            val logState = rememberListViewState()
            LaunchedEffect(editor.log.size) { if (editor.log.isNotEmpty()) logState.revealItem(editor.log.lastIndex) }
            ListView(editor.log, { it }, -1, {}, Modifier.weight(1f).testTag("log"), state = logState)
        }
    }
}

/** How the scene is rendered: the Rendering expander's settings, and what the last frame took. */
internal class RenderSettings {
    var parallel by mutableStateOf(true)
    var divisor by mutableIntStateOf(1)
    var info by mutableStateOf("")
}

/** The profiler's times between frames, oldest first from [cursor]. */
internal class FrameTimes {
    val samples = FloatArray(SAMPLES)
    var cursor by mutableIntStateOf(0)
        private set

    fun record(ms: Float) {
        samples[cursor] = ms
        cursor = (cursor + 1) % SAMPLES
    }

    companion object {
        const val SAMPLES = 240
    }
}

@Composable
private fun ScenePanel(editor: Editor, stats: androidx.compose.runtime.MutableState<String>) {
    val image = remember { ImageBitmap(1, 1, ByteArray(4)) }
    val renderer = remember(image) { SceneRenderer(image) }
    var pixels by remember { mutableStateOf(IntSize(0, 0)) }
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current.density

    suspend fun render() {
        val divisor = editor.rendering.divisor
        editor.rendering.info = renderer.render(editor.frame(), pixels.width / divisor, pixels.height / divisor, editor.rendering.parallel)
    }

    LaunchedEffect(editor.playing) {
        if (editor.playing) {
            // While it plays, the scene moves: a frame each frame, and the profiler and stats with it.
            var last = withFrameNanos { it }
            var sinceStats = 0.0
            while (true) {
                val now = withFrameNanos { it }
                val seconds = (now - last) / 1e9
                last = now
                // A frame that brought no time (a test's held clock) has nothing new to show.
                if (seconds <= 0.0) continue
                editor.time += seconds
                editor.frameTimes.record((seconds * 1000).toFloat())
                sinceStats += seconds
                if (sinceStats > 0.25) {
                    sinceStats = 0.0
                    val s = windowInfo.frameStats()
                    stats.value = "${s.widgetCount} widgets   ${s.primitiveCount} primitives in ${s.drawCallCount} draw call" +
                        (if (s.drawCallCount > 1) "s" else "") + "   layout ${s.layoutMs.ms()} ms   paint ${s.paintMs.ms()} ms   gpu ${s.renderMs.ms()} ms"
                }
                render()
            }
        } else {
            // Paused, it is rendered again only when it was edited.
            snapshotFlow { Triple(editor.frame(), pixels, editor.rendering.divisor to editor.rendering.parallel) }.collect { render() }
        }
    }

    var dragging by remember { mutableStateOf(false) }
    Viewport(
        image,
        Modifier.fillMaxSize().pointerHoverIcon(PointerIcon.Crosshair).testTag("scene")
            .tooltip("Software-rendered on every core. Drag to move the selected object.")
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.button != PointerButton.Primary || pixels.width == 0) return@onPointerEvent
                // Positions are in dp, the viewport's size in pixels.
                val width = pixels.width / density
                val height = pixels.height / density
                val hit = editor.objectAt(event.position.x / width, event.position.y / height, width / height)
                editor.selectedId = hit?.id
                dragging = hit != null
                event.consume()
            }
            .onPointerEvent(PointerEventType.Move) { event ->
                val selected = editor.selected
                if (!dragging || selected == null || pixels.width == 0) return@onPointerEvent
                selected.x = (event.position.x * density / pixels.width).coerceIn(0f, 1f)
                selected.y = (event.position.y * density / pixels.height).coerceIn(0f, 1f)
                event.consume()
            }
            .onPointerEvent(PointerEventType.Release) { dragging = false },
        onSizeChanged = { pixels = it },
    )
}

private fun Double.ms(): String = ((this * 100).toLong() / 100.0).toString()

@Composable
private fun Profiler(editor: Editor) {
    val text = RavenTheme.colors.text
    Column(Modifier.padding(12.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Time between frames (ms)")
        Canvas(Modifier.weight(1f).fillMaxWidth().testTag("graph")) {
            // Read so a new sample draws it again.
            val cursor = editor.frameTimes.cursor
            val samples = editor.frameTimes.samples
            val width = size.width
            val height = size.height
            drawRoundRect(Color(0.5f, 0.5f, 0.5f, 0.10f), cornerRadius = 6f)
            val peak = maxOf(20f, samples.max())
            val scale = (height - 24f) / peak
            // The 16.7 ms line: 60 frames per second.
            val line = height - 16.7f * scale
            drawLine(Color(0.3f, 0.8f, 0.4f, 0.8f), Offset(0f, line), Offset(width, line))
            drawText("16.7 ms", Offset(6f, line - 16f), Color(text.red, text.green, text.blue, 0.6f), fontSize = 11.sp)
            for (i in 1 until FrameTimes.SAMPLES) {
                // Oldest sample on the left.
                val previous = samples[(cursor + i - 1) % FrameTimes.SAMPLES]
                val current = samples[(cursor + i) % FrameTimes.SAMPLES]
                val x0 = width * (i - 1) / (FrameTimes.SAMPLES - 1)
                val x1 = width * i / (FrameTimes.SAMPLES - 1)
                drawLine(Color(0.25f, 0.6f, 1f), Offset(x0, height - previous * scale), Offset(x1, height - current * scale), 1.5f)
            }
            drawText("peak ${(peak * 10).toInt() / 10.0} ms", Offset(8f, 6f), text, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Properties(editor: Editor) {
    val selected = editor.selected
    val enabled = selected != null
    Column(Modifier.padding(10.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Properties", fontSize = 15.sp)
        Grid(listOf(GridTrack.Auto, GridTrack.Fill()), emptyList(), Modifier.fillMaxWidth(), spacing = 8.dp) {
            Text("Name", Modifier.cell(0, 0).align(Alignment.CenterVertically))
            // Every keystroke renames the selected object.
            TextField(selected?.name ?: "", { selected?.name = it }, Modifier.cell(1, 0).testTag("prop.name"), enabled = enabled)

            Text("Radius", Modifier.cell(0, 1).align(Alignment.CenterVertically))
            Slider(selected?.radius ?: 0f, { selected?.radius = it }, Modifier.cell(1, 1).align(Alignment.CenterVertically).testTag("prop.radius"), enabled = enabled, valueRange = 0.03f..0.4f)

            Text("Speed", Modifier.cell(0, 2).align(Alignment.CenterVertically))
            SpinBox(
                (selected?.speed ?: 0f).toDouble(), { selected?.speed = it.toFloat() }, Modifier.cell(1, 2).testTag("prop.speed"),
                enabled = enabled, valueRange = -5.0..5.0, step = 0.25, decimals = 2,
            )

            Text("Color", Modifier.cell(0, 3).align(Alignment.CenterVertically))
            ComboBox(ColorNames, selected?.color ?: 0, { selected?.color = it }, Modifier.cell(1, 3).testTag("prop.color"), enabled = enabled)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(selected?.visible ?: false, { selected?.visible = it }, Modifier.testTag("prop.visible"), enabled = enabled)
            Text("Visible")
        }
        var expanded by remember { mutableStateOf(true) }
        Expander("Rendering", expanded, { expanded = it }, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(top = 6.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Switch(editor.rendering.parallel, { editor.rendering.parallel = it })
                    Text("Render on all cores")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Resolution")
                    ComboBox(
                        listOf("Full", "Half", "Quarter"), listOf(1, 2, 4).indexOf(editor.rendering.divisor),
                        { editor.rendering.divisor = listOf(1, 2, 4)[it] }, Modifier.weight(1f),
                    )
                }
                Text(editor.rendering.info, Modifier.fillMaxWidth().alpha(0.7f), softWrap = true)
            }
        }
    }
}
