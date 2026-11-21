/**
 * Raven MCP test - the notepad, in C, offering the UI tools to an agent.
 *
 * A headless Raven app as a developer would ship it: Raven MCP starts only when the environment
 * asks for it (RAVEN_MCP=1, RAVEN_MCP_PORT, RAVEN_MCP_UI=1), with the token given as its one
 * argument. app.quit is exposed so the agent can end it. src/test/python/test_ui_tools_c.py
 * drives it; nothing is written to stdout.
 */

#include <stdio.h>
#include <string.h>

#include "Raven.h"
#include "raven/raven_mcp.h"

static const char NOTES_UI[] =
	"<Window Title=\"Notes\" Width=\"320\" Height=\"240\">"
	"  <VBox>"
	"    <TextInput Name=\"note\" AccessibleName=\"New note\" OnCommitted=\"notes.add\"/>"
	"    <Button Text=\"Add\" OnClicked=\"notes.add\"/>"
	"    <ListView Name=\"notes\" AccessibleName=\"Notes\"/>"
	"    <Button Name=\"delete\" Text=\"Delete\" Enabled=\"false\" OnClicked=\"notes.delete\"/>"
	"  </VBox>"
	"</Window>";

static Rid g_note, g_notes, g_delete;

static void add(Rid sender, const char* action, void* user_data) {
	const char* text = Rtext_input_get_text(g_note);
	(void)sender, (void)action, (void)user_data;
	if (!text || !text[0]) return;
	Rlist_view_add_item(g_notes, text);
	Rtext_input_set_text(g_note, "");
}

static void delete_selected(Rid sender, const char* action, void* user_data) {
	(void)sender, (void)action, (void)user_data;
	Rlist_view_remove_item(g_notes, Rlist_view_get_selected(g_notes));
	Rlist_view_set_selected(g_notes, -1);
	Rwidget_set_enabled(g_delete, R_FALSE);
}

/* (a ListView's action is fired on activation only, so the selection is heard here) */
static void selected(Rid list_view, int index, void* user_data) {
	(void)list_view, (void)user_data;
	Rwidget_set_enabled(g_delete, index >= 0);
}

static void on_log(RSeverity severity, const char* message, void* user_data) {
	(void)user_data;
	if (severity != R_SEVERITY_WEAK_WARNING) fprintf(stderr, "raven-mcp: %s\n", message);
}

int main(int argc, char** argv) {
	RInitDesc desc;
	RMcpConfig config;
	Rid window;

	memset(&desc, 0, sizeof(desc));
	desc.backend = R_BACKEND_NULL;
	desc.headless = R_TRUE;
	if (!Rinit_ex(&desc)) return 1;

	window = Rwindow_create_from_xml(NOTES_UI, strlen(NOTES_UI));
	g_note = Rwidget_find(window, "note");
	g_notes = Rwidget_find(window, "notes");
	g_delete = Rwidget_find(window, "delete");
	Raction_bind("notes.add", add, NULL);
	Raction_bind("notes.delete", delete_selected, NULL);
	Rlist_view_set_selection_changed_callback(g_notes, selected, NULL);

	memset(&config, 0, sizeof(config));
	config.name = "notes";
	config.token = argc > 1 ? argv[1] : NULL;
	config.log = on_log;
	if (Rmcp_config_from_environment(&config)) {
		Rmcp_expose_action("app.quit", "Quit the notepad", R_MCP_HINT_CLOSED_WORLD);
		if (!Rmcp_start(&config)) return 2;
	}

	while (Rwait_frame(0.05)) {
	}

	Rmcp_stop();
	Rmcp_tool_remove_all();
	Rshutdown();
	return 0;
}
