package raven.sample.hello

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import raven.compose.foundation.Button
import raven.compose.foundation.OutlinedButton
import raven.compose.foundation.Text
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.Row
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.ui.Alignment
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.application
import raven.compose.ui.tooltip
import raven.compose.ui.unit.dp
import raven.compose.ui.unit.sp

// A port of Raven's examples/hello: a title, a click counter and two buttons.

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Hello Raven", width = 420, height = 220) {
        Hello(onQuit = ::exitApplication)
    }
}

/** The window's content: [onQuit] runs when the Quit button is clicked. */
@Composable
fun Hello(onQuit: () -> Unit) {
    var clicks by mutableIntStateOf(0)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Hello from Raven", fontSize = 24.sp)
            Text(if (clicks == 0) "Press the button" else "Clicked $clicks time${if (clicks > 1) "s" else ""}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { clicks++ }, Modifier.tooltip("Counts the clicks")) { Text("Click me") }
                OutlinedButton(onClick = onQuit) { Text("Quit") }
            }
        }
    }
}
