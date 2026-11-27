# Raven for Python

[Raven](https://github.com/oscar-soirey/Raven) is a lightweight, multithreaded
GUI library in C for editors and tools. This package is its Python binding,
with the library itself included: nothing else to install.

```sh
pip install raven-gui
```

```python
import raven as r

r.Rinit(r.R_BACKEND_DEFAULT)
window = r.Rwindow_create_from_xml("""
    <Window Title="Hello" Width="320" Height="160">
        <VBox Spacing="10" Slot.Padding="16">
            <TextInput Name="name" Placeholder="Your name"/>
            <Button Text="Greet" Style="Primary" OnClicked="greet"/>
        </VBox>
    </Window>""")
name = r.Rwidget_find(window, "name")
r.Raction_bind("greet", lambda sender, action, user: print("Hello,", r.Rtext_input_get_text(name)))
while r.Rwait_frame(-1.0):
    pass
r.Rshutdown()
```

Every function of the C API is there under its own name. Texts are `str`,
callbacks are Python functions, and the values a C function writes through
pointers are returned.

## UI tests

`raven_testing` (in the same package) tests an interface headless, as a user
would use it: the selectors of Raven's UI driver, real clicks and typing, and
failures that say what was found.

```python
import raven_testing

with raven_testing.app(xml=NOTES_XML) as ui:            # unittest, plain scripts
    ui.type("role=text-field", "buy milk")
    ui.click('role=button name="Add"')
    ui.expect_text("role=list-item [0]", "buy milk")

def test_adds_a_note(raven_ui):                         # pytest: the fixture is registered
    raven_ui.load_xml(NOTES_XML)
    raven_ui.click('role=button name="Add"')
```

It runs without a window system and without a GPU, animations advance by a
fixed step, the app is waited on until idle around every step, and Raven's
errors and leaked memory fail the test. `capture=True` (with pytest,
`@pytest.mark.raven(capture=True)`) renders on headless Vulkan, and a failure
then saves a PNG of each window into `RAVEN_TEST_CAPTURES`. The checks are
`expect_exists`, `expect_absent`, `expect_count`, `expect_text`,
`expect_value`, `expect_checked`, `expect_enabled` and `expect_focused`;
`ui.find(selector)` describes anything else. A failure is a
`RavenTestFailure`, an `AssertionError`. Neither `raven` nor `raven_testing`
needs pytest.

Raven draws with Vulkan: the machine needs a Vulkan driver (current graphics
drivers include one on Windows and Linux; on macOS, install
[MoltenVK](https://github.com/KhronosGroup/MoltenVK)). On Linux, libX11 is
used for the windows.

Wheels: Windows x64, Linux x86_64 (glibc 2.28 or newer), macOS 11 or newer
(Apple silicon and Intel). License: Apache 2.0.
