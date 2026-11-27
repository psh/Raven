package raven.sample.resources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import raven.compose.foundation.Icon
import raven.compose.foundation.Image
import raven.compose.foundation.Text
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.Row
import raven.compose.foundation.layout.padding
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.application
import raven.compose.ui.res.Font
import raven.compose.ui.res.painterResource
import raven.compose.ui.testTag
import raven.compose.ui.text.font.FontFamily
import raven.compose.ui.unit.dp
import raven.compose.ui.unit.sp
import compose_raven.samples.resources.generated.resources.Res

// A check module, not a port: Compose Multiplatform's composeResources/ in a compose-raven app.
//
//   src/nativeMain/composeResources/drawable/   Res.drawable.<name>, for painterResource and Icon
//   src/nativeMain/composeResources/font/       Res.font.<name>, for Font
//   src/nativeMain/composeResources/files/      Res.readBytes("files/<path>")
//
// The raven.compose-resources plugin generates Res, in Compose's package (<group>.<module>.generated
// .resources: compose_raven.samples.resources.generated.resources here), and copies the folder into resources/ beside the
// executable. "ic-arrow back.svg" becomes ic_arrow_back, by Compose's rule. The font is Raven's own
// Roboto subset, under the Apache License 2.0 in files/LICENSE-Roboto.txt.

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Raven - resources") { ResourcesScreen() }
}

@Composable
fun ResourcesScreen() {
    var notes by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { notes = Res.readBytes("files/notes.txt").decodeToString().trim() }
    val roboto = FontFamily(Font(Res.font.Roboto_Regular_subset))
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(Res.drawable.ic_arrow_back), contentDescription = "Back", Modifier.testTag("icon"))
            Image(painterResource(Res.drawable.badge), contentDescription = null, Modifier.testTag("badge"))
        }
        Text("Roboto, from Res.font", Modifier.testTag("font"), fontFamily = roboto, fontSize = 20.sp)
        Text(notes, Modifier.testTag("notes"))
    }
}
