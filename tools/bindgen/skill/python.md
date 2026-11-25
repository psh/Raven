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
