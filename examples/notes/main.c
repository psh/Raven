/**
 * Raven example - notes
 * A list of notes: type one, Add (or Enter), select it, Delete.
 *
 *   notes.xml       the interface, shared by every language's version and test
 *   notes_app.c     what its actions do (notes.add, notes.delete)
 *   test_notes.c    its headless UI test, through raven/raven_test.h (ctest: example_notes)
 */

#include "../common/example.h"
#include "notes_app.h"

#ifndef RAVEN_EXAMPLE_DIR
#define RAVEN_EXAMPLE_DIR "."
#endif

#ifdef NOTES_WITH_MCP
/* Built against the shared libraven and libraven_mcp: RAVEN_MCP=1 (RAVEN_MCP_UI=1 for ui.*) lets an agent drive it. */
#include <raven/raven_mcp.h>
static int g_mcp;
static void mcp_stop(void) {
	if (g_mcp) Rmcp_stop(), Rmcp_tool_remove_all();
}
#endif

int main(void) {
	Rid window;
	if (!example_init("notes")) return 1;
	window = notes_open(RAVEN_EXAMPLE_DIR "/notes.xml");
	if (window == R_INVALID_ID) {
		Rshutdown();
		return 1;
	}
#ifdef NOTES_WITH_MCP
	{
		RMcpConfig config = { 0 };
		config.name = "notes";
		g_mcp = Rmcp_config_from_environment(&config) && Rmcp_start(&config);
	}
	example_run(window, NULL, mcp_stop);
#else
	example_run(window, NULL, NULL);
#endif
	return 0;
}
