package raven.sample.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import raven.compose.foundation.Button
import raven.compose.foundation.CodeEditor
import raven.compose.foundation.Expander
import raven.compose.foundation.HorizontalDivider
import raven.compose.foundation.OutlinedButton
import raven.compose.foundation.Surface
import raven.compose.foundation.Text
import raven.compose.foundation.TextButton
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.ColumnScope
import raven.compose.foundation.layout.FlowRow
import raven.compose.foundation.layout.Row
import raven.compose.foundation.layout.RowScope
import raven.compose.foundation.layout.VerticalScroll
import raven.compose.foundation.layout.fillMaxSize
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.foundation.layout.height
import raven.compose.foundation.layout.padding
import raven.compose.foundation.layout.width
import raven.compose.ui.Alignment
import raven.compose.ui.AppearTransition
import raven.compose.ui.Modifier
import raven.compose.ui.Window
import raven.compose.ui.alpha
import raven.compose.ui.appearTransition
import raven.compose.ui.application
import raven.compose.ui.graphics.CircleShape
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.RoundedCornerShape
import raven.compose.ui.rememberWindowState
import raven.compose.ui.testTag
import raven.compose.ui.unit.DpSize
import raven.compose.ui.unit.dp
import raven.compose.ui.unit.sp
import kotlin.time.Duration.Companion.milliseconds

// A port of Raven's examples/components: components of your own, and interface built from code.
//
//   Card, Stat, Badge, PersonRow   the C example's <Widget> tags, as ordinary composables; the
//                                  Card's footer slot is the tag's Into="footer"
//   repeat, forEachIndexed, "$x"   its <Repeat>, <ForEach> and {...} expressions
//   "Add a card"                   adds a CardData to the app's state; the C example builds an
//                                  XML document instead, and shows the XML it wrote, where this
//                                  port shows the Kotlin call that composes the card
//
// Compose has no tag registry, so the C example's "Declared tags" status has no counterpart.

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(size = DpSize(760.dp, 560.dp)),
        title = "Raven - components",
        minimumSize = DpSize(520.dp, 400.dp),
    ) {
        Components()
    }
}

/** A card added with "Add a card": its title and its two stats. */
data class CardData(val id: Int, val title: String, val stats: List<StatData>)

/** A [Stat]'s parameters. */
data class StatData(val label: String, val value: String, val trend: Int)

private val titles = listOf("Builds", "Tickets", "Reviews", "Deploys")

/** The [index]th card added, with the title and numbers the C example gives it. */
fun cardData(index: Int): CardData = CardData(
    id = index,
    title = titles[index % titles.size],
    stats = List(2) { i ->
        StatData(
            label = if (i == 0) "Open" else "Closed today",
            value = "${3 + (index * 7 + i * 11) % 40}",
            trend = if (i == 0) -1 else 1,
        )
    },
)

/** The Kotlin that composes [card] in [Components], as the source pane shows it. */
fun cardSource(card: CardData): String = buildString {
    appendLine("Card(")
    appendLine("    title = ${quoted(card.title)},")
    appendLine("    badge = \"new\",")
    appendLine("    modifier = Modifier.appearTransition(AppearTransition.Drop),")
    appendLine("    footer = {")
    appendLine("        TextButton(onClick = { cards.remove(card) }) { Text(\"Remove\") }")
    appendLine("    },")
    appendLine(") {")
    for (stat in card.stats) {
        appendLine("    Stat(label = ${quoted(stat.label)}, value = ${quoted(stat.value)}, trend = ${stat.trend})")
    }
    append("}")
}

private fun quoted(text: String): String =
    "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""

private val Green = Color(0xFF46A758)
private val Red = Color(0xFFE5484D)
private val Blue = Color(0xFF3B82F6)
private val Purple = Color(0xFF8E4EC6)
private val Cyan = Color(0xFF05A2C2)

/** The window's content: the dashboard, its cards, the status line and the source pane. */
@Composable
fun Components() {
    val cards = remember { mutableStateListOf<CardData>() }
    var added by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Press \"Add a card\": it adds a card from Kotlin.") }
    var source by remember { mutableStateOf("// press \"Add a card\"") }
    var sourceShown by remember { mutableStateOf(true) }
    val details = { status = "A button given to a card's footer slot was clicked." }

    VerticalScroll(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Dashboard", Modifier.weight(1f), fontSize = 24.sp)
                Button(
                    onClick = {
                        val card = cardData(added)
                        cards += card
                        added++
                        source = cardSource(card)
                        status = if (added == 1) "1 card added from Kotlin."
                        else "Several cards added from Kotlin; \"Remove\" takes one away."
                    },
                    Modifier.align(Alignment.CenterVertically),
                ) { Text("Add a card") }
            }

            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    title = "Sales",
                    badge = "hot",
                    modifier = Modifier.appearTransition(AppearTransition.Pop),
                    footer = { TextButton(onClick = details) { Text("Details") } },
                ) {
                    Stat(label = "This week", value = "1 284", trend = 1)
                    Stat(label = "Returns", value = "37", trend = -1)
                }

                Card(title = "Team", modifier = Modifier.appearTransition(AppearTransition.Pop, 60.milliseconds)) {
                    PersonRow(name = "Ada", role = "Lead", online = true)
                    PersonRow(name = "Linus", role = "Kernel")
                    PersonRow(name = "Grace", role = "Compiler", online = true)
                }

                // the same composable several times: a count, or a list of texts
                Card(
                    title = "Servers",
                    badge = "4",
                    wide = true,
                    modifier = Modifier.appearTransition(AppearTransition.Pop, 120.milliseconds),
                    footer = {
                        OutlinedButton(onClick = details) { Text("Restart all") }
                        TextButton(onClick = details) { Text("Logs") }
                    },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        repeat(4) { i ->
                            Stat(label = "node-${i + 1}", value = "${20 + i * 17} %", trend = if (i == 3) -1 else 0)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Regions:", Modifier.alpha(0.6f))
                        listOf("paris", "tokyo", "oslo").forEachIndexed { n, city ->
                            Badge(text = city, color = if (n % 2 == 0) Purple else Cyan)
                        }
                    }
                }

                for (card in cards) {
                    key(card.id) {
                        Card(
                            title = card.title,
                            badge = "new",
                            modifier = Modifier.appearTransition(AppearTransition.Drop),
                            footer = {
                                TextButton(onClick = {
                                    cards.remove(card)
                                    status = "\"${card.title}\" removed."
                                }) { Text("Remove") }
                            },
                        ) {
                            for (stat in card.stats) Stat(label = stat.label, value = stat.value, trend = stat.trend)
                        }
                    }
                }
            }

            Text(status, Modifier.alpha(0.7f))
            Expander(
                title = "The Kotlin of the cards added",
                expanded = sourceShown,
                onExpandedChange = { sourceShown = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                CodeEditor(
                    source,
                    modifier = Modifier.fillMaxWidth().height(150.dp).testTag("source"),
                    language = "Kotlin",
                    readOnly = true,
                )
            }
        }
    }
}

/**
 * A card: a [title], an optional [badge], then its [content] and, at the bottom right, its
 * [footer]. A [wide] card takes the width of two.
 */
@Composable
private fun Card(
    title: String,
    badge: String = "",
    wide: Boolean = false,
    modifier: Modifier = Modifier,
    footer: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.width(if (wide) 452.dp else 220.dp), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontSize = 16.sp)
                if (badge.isNotEmpty()) Badge(text = badge, color = if (badge == "hot") Red else Blue)
            }
            HorizontalDivider(Modifier.fillMaxWidth())
            content()
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(6.dp), content = footer)
        }
    }
}

/** A number with its name; [trend] colours the number: up green, down red, level grey. */
@Composable
private fun Stat(label: String, value: String, trend: Int = 0) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, Modifier.alpha(0.6f), fontSize = 12.sp)
        Text(value, color = if (trend > 0) Green else if (trend < 0) Red else Color.Gray, fontSize = 24.sp)
    }
}

/** A small coloured label, in capitals. */
@Composable
private fun Badge(text: String, color: Color = Blue) {
    Surface(shape = CircleShape, color = color) {
        Text(text.uppercase(), Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White, fontSize = 11.sp)
    }
}

/** A person of the team: [name], [role], and whether they are [online]. */
@Composable
private fun PersonRow(name: String, role: String, online: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f))
        Text(role, Modifier.alpha(0.6f))
        Badge(text = if (online) "online" else "away", color = if (online) Green else Color.Gray)
    }
}
