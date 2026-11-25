The harness is `raven_testing`, beside `raven` (in the wheel, or `bindings/python` on `PYTHONPATH`).
With pytest, its `raven_ui` fixture is registered by the wheel (elsewhere: `pytest -p raven_testing`):

```python
from notes_app import open_notes                     # the app's code: builds the window, binds its actions

def test_adds_a_note(raven_ui):
    open_notes("notes.xml")                          # or raven_ui.load_xml(text) / load_xml_file(path)
    raven_ui.type("role=text-field", "buy milk")
    raven_ui.click('role=button name="Add"')
    raven_ui.expect_text("role=list-item [0]", "buy milk")
    raven_ui.expect_enabled('role=button name="Delete"', False)
```

Without pytest, the same in a with block: `with raven_testing.app(xml=TEXT) as ui:`.
`@pytest.mark.raven(capture=True)` (or `app(capture=True)`) renders on headless Vulkan and saves
PNGs of the windows on failure into `RAVEN_TEST_CAPTURES`; without Vulkan the test is skipped. A
failure is a `RavenTestFailure` (an `AssertionError`) with the driver's message. The other checks
and `ui.find` are in `raven_testing.py`'s docstrings. Raven's repository has the worked example,
`examples/notes/test_notes.py`.

### Serving the `ui.*` tools

For an agent to drive the running app, start Raven MCP after the window is built. Its library,
`libraven_mcp`, is found through `RAVEN_MCP_LIBRARY`, then beside libraven:

```python
import os

mcp = False
if os.environ.get("RAVEN_MCP"):                      # the Rmcp_ calls load the library: only when asked
    config = r.RMcpConfig()
    config.name = b"notes"                           # else the program's name (the interpreter's), and its token file
    mcp = bool(r.Rmcp_config_from_environment(config)) and bool(r.Rmcp_start(config))
# ... the app's loop ...
if mcp:
    r.Rmcp_stop()
    r.Rmcp_tool_remove_all()                         # before Rshutdown
```
