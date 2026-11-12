package raven.compose.foundation

import androidx.compose.runtime.remember
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.mkdtemp
import platform.posix.remove
import platform.posix.rmdir
import platform.posix.usleep
import raven.compose.ui.theme.RavenTheme
import raven.compose.ui.theme.loadTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeFilesTest {
    private val directory = memScoped {
        checkNotNull(mkdtemp("/tmp/compose-raven-XXXXXX".cstr.getPointer(this))) { "no temporary directory" }.toKString()
    }
    private val path = "$directory/ocean.xml"

    @AfterTest
    fun removeFiles() {
        remove(path)
        rmdir(directory)
    }

    @Test
    fun themeNamesListALoadedThemeAndFollowItsRenameOnReload() {
        writeTheme("Ocean")
        runRavenTest {
            var name = ""
            setContent {
                name = remember { loadTheme(path) }
                Text("Themes: " + RavenTheme.themeNames.joinToString())
            }
            assertEquals("Ocean", name)
            onNodeWithText("Ocean", substring = true).assertExists()

            // Renamed in the file, the theme is renamed on reload.
            writeTheme("Seaside")
            assertEquals(1, checkFiles())
            onNodeWithText("Seaside", substring = true).assertExists()
            onNodeWithText("Ocean", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun aTestHostNeverReloadsByItself() {
        writeTheme("Ocean")
        runRavenTest {
            setContent {
                remember { loadTheme(path) }
                Text("Themes: " + RavenTheme.themeNames.joinToString())
            }
            writeTheme("Seaside")
            // Time for Raven's watcher to notice, if it were reloading.
            repeat(5) {
                usleep(100_000u)
                frame()
            }
            waitForIdle()
            onNodeWithText("Ocean", substring = true).assertExists()
            onNodeWithText("Seaside", substring = true).assertDoesNotExist()
        }
    }

    private fun writeTheme(name: String) {
        val file = checkNotNull(fopen(path, "w")) { "can't write $path" }
        try {
            fputs("""<Theme Name="$name" Accent="#0EA5E9"><Colors Panel="#0B2030"/></Theme>""", file)
        } finally {
            fclose(file)
        }
    }
}
