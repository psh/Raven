package raven.sample.components

import raven.cinterop.Rtext_editor_get_text
import raven.compose.foundation.RavenTestScope
import raven.compose.foundation.runRavenTest
import raven.native.toRavenString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComponentsTest {
    private fun RavenTestScope.source(): String = Rtext_editor_get_text(onNodeWithTag("source").fetchWidget()).toRavenString()

    @Test
    fun itStartsWithTheThreeCardsAndAHint() = runRavenTest {
        setContent { Components() }
        for (title in listOf("Sales", "Team", "Servers")) onNodeWithText(title).assertExists()
        onNodeWithText("node-4").assertExists()
        onNodeWithText("TOKYO").assertExists()
        onNodeWithText("Press \"Add a card\": it adds a card from Kotlin.").assertExists()
        assertEquals("// press \"Add a card\"", source())
        onNodeWithText("Remove").assertDoesNotExist()
    }

    @Test
    fun addACardAddsTheNextCardAndShowsItsKotlinAndRemoveTakesItAway() = runRavenTest {
        setContent { Components() }
        onNodeWithText("Add a card").performClick()
        onNodeWithText("Builds").assertExists()
        onNodeWithText("Closed today").assertExists()
        onNodeWithText("14").assertExists() // 3 + 11 % 40
        onNodeWithText("1 card added from Kotlin.").assertExists()
        assertEquals(cardSource(cardData(0)), source())
        assertTrue("title = \"Builds\"" in source())

        onNodeWithText("Remove").performClick()
        onNodeWithText("Builds").assertDoesNotExist()
        onNodeWithText("\"Builds\" removed.").assertExists()

        onNodeWithText("Add a card").performClick()
        onNodeWithText("Tickets").assertExists()
        assertTrue("title = \"Tickets\"" in source())
        onNodeWithText("Several cards added from Kotlin; \"Remove\" takes one away.").assertExists()
    }

    @Test
    fun theAddedCardsFollowTheCExample() {
        assertEquals(listOf("Builds", "Tickets", "Reviews", "Deploys", "Builds"), List(5) { cardData(it).title })
        assertEquals(listOf("3", "14"), cardData(0).stats.map { it.value })
        assertEquals(listOf("10", "21"), cardData(1).stats.map { it.value })
        assertEquals(listOf(-1, 1), cardData(2).stats.map { it.trend })
    }
}
