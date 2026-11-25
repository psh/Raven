The harness is `raven/raven_test.h`, header-only, included in the one file of a test:

```c
#include <Raven.h>
#include <raven/raven_test.h>

int main(void) {
    if (!Rtest_begin(NULL)) return RTEST_SKIPPED;       /* 77: only with capture, without Vulkan */
    notes_open("notes.xml");                            /* the app's code: builds the window, binds its actions */
    RTEST_TYPE("role=text-field", "buy milk");
    RTEST_CLICK("role=button name=\"Add\"");
    RTEST_EXPECT_TEXT("role=list-item [0]", "buy milk");
    RTEST_EXPECT_ENABLED("role=button name=\"Delete\"", R_FALSE);
    return Rtest_end();                                 /* 1 on a failure, or on memory Raven still holds */
}
```

```cmake
add_test(NAME notes_ui COMMAND test_notes)
set_tests_properties(notes_ui PROPERTIES SKIP_RETURN_CODE 77)
```

The macros and `RTestOptions` (capture, a failure hook for another test runner) are listed at the
top of `raven/raven_test.h`. Each macro returns `R_FALSE` after reporting, and the test goes on:
`if (!RTEST_CLICK(...)) return Rtest_end();` stops it. Raven's repository has the worked example,
`examples/notes/test_notes.c`.

### Serving the `ui.*` tools

For an agent to drive the running app, start Raven MCP after the window is built. It is its own
library, `libraven_mcp` (`raven/raven_mcp.h`), and it needs the **shared** libraven, so an app
linked with the static one is relinked with `-lraven -lraven_mcp`:

```c
#include <raven/raven_mcp.h>

RMcpConfig config = { 0 };
config.name = "notes";                                   /* the token file: ~/.raven/mcp/notes.json */
int mcp = Rmcp_config_from_environment(&config) && Rmcp_start(&config);   /* RAVEN_MCP=1, RAVEN_MCP_UI=1 */
/* ... the app's loop ... */
if (mcp) { Rmcp_stop(); Rmcp_tool_remove_all(); }        /* before Rshutdown */
```
