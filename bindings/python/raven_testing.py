"""
Headless UI tests for Raven apps in Python, over Raven's UI driver.

    import raven_testing

    with raven_testing.app(xml=NOTES_XML) as ui:           # unittest, plain scripts
        ui.type("role=text-field", "buy milk")
        ui.click('role=button name="Add"')
        ui.expect_text("role=list-item [0]", "buy milk")

    def test_adds_a_note(raven_ui):                         # pytest: the raven_ui fixture
        raven_ui.load_xml(NOTES_XML)
        ...

Selectors are the driver's (raven_driver.h): role=button name="Save" [1].
What a test means is the same as in C (raven/raven_test.h) and Rust
(raven::testing): finding, acting and waiting are Raven's C.

The harness:
  - headless: the null backend with no window system; with capture=True,
    headless Vulkan, and unittest.SkipTest (a skip for unittest and pytest)
    without it;
  - deterministic: animations advance by a fixed step each frame;
  - Raven's errors (warnings too) fail the test, at the step that follows
    them; ui.expect_errors() collects the ones a test means to cause;
  - idle around every step: Rwait_idle before every find and after every
    action, and a step fails when the app stays busy;
  - leaks checked: leaving the block fails when Raven still holds memory;
  - with capture, a failure saves a PNG of each window into the folder
    RAVEN_TEST_CAPTURES names.

A failure raises RavenTestFailure, an AssertionError, with the driver's
message. raven.py (`import raven`) is used as it is, and neither module needs
pytest: the fixture exists when pytest imports this module (the wheel
registers it as a pytest plugin; elsewhere, `-p raven_testing`).
"""

import contextlib
import os
import unittest

import raven as r

__all__ = ["app", "App", "Accessible", "RavenTestFailure"]


class RavenTestFailure(AssertionError):
    """A step or a check failed; the message says what was expected and what the driver found."""


_STATES = [
    ("focusable", r.R_ACCESSIBLE_FOCUSABLE), ("focused", r.R_ACCESSIBLE_FOCUSED),
    ("disabled", r.R_ACCESSIBLE_DISABLED), ("checked", r.R_ACCESSIBLE_CHECKED),
    ("checkable", r.R_ACCESSIBLE_CHECKABLE), ("expanded", r.R_ACCESSIBLE_EXPANDED),
    ("expandable", r.R_ACCESSIBLE_EXPANDABLE), ("selected", r.R_ACCESSIBLE_SELECTED),
    ("selectable", r.R_ACCESSIBLE_SELECTABLE), ("multiline", r.R_ACCESSIBLE_MULTILINE),
    ("readonly", r.R_ACCESSIBLE_READONLY), ("password", r.R_ACCESSIBLE_PASSWORD),
    ("has-range", r.R_ACCESSIBLE_HAS_RANGE), ("editable", r.R_ACCESSIBLE_EDITABLE),
    ("invokable", r.R_ACCESSIBLE_INVOKABLE), ("offscreen", r.R_ACCESSIBLE_OFFSCREEN),
    ("modal", r.R_ACCESSIBLE_MODAL),
]

# The roles whose shown text is their value (what is typed), as the driver's text= reads it.
_VALUE_ROLES = ("text-field", "document", "combo-box", "spin-button")

_ACTIONS = {
    "expand": r.R_ACCESSIBLE_EXPAND, "collapse": r.R_ACCESSIBLE_COLLAPSE,
    "select": r.R_ACCESSIBLE_SELECT, "focus": r.R_ACCESSIBLE_FOCUS,
}

_KEYS = {
    "space": r.R_KEY_SPACE, "escape": r.R_KEY_ESCAPE, "esc": r.R_KEY_ESCAPE, "enter": r.R_KEY_ENTER,
    "return": r.R_KEY_ENTER, "tab": r.R_KEY_TAB, "backspace": r.R_KEY_BACKSPACE, "insert": r.R_KEY_INSERT,
    "delete": r.R_KEY_DELETE, "del": r.R_KEY_DELETE, "right": r.R_KEY_RIGHT, "left": r.R_KEY_LEFT,
    "down": r.R_KEY_DOWN, "up": r.R_KEY_UP, "pageup": r.R_KEY_PAGE_UP, "pagedown": r.R_KEY_PAGE_DOWN,
    "home": r.R_KEY_HOME, "end": r.R_KEY_END,
}
_KEYS.update(("f%d" % n, getattr(r, "R_KEY_F%d" % n)) for n in range(1, 13))

_MODIFIERS = {
    "ctrl": r.R_MOD_CTRL, "control": r.R_MOD_CTRL, "shift": r.R_MOD_SHIFT, "alt": r.R_MOD_ALT,
    "super": r.R_MOD_SUPER, "cmd": r.R_MOD_SUPER, "win": r.R_MOD_SUPER,
}


def _parse_key(key):
    """(RKey, modifiers) from "Enter", "Ctrl+S", "Shift+Tab", "a", or an RKey."""
    if not isinstance(key, str):
        return int(key), 0
    parts = key.split("+")
    if key.endswith("++"):
        parts = parts[:-2] + ["+"]
    code, modifiers = None, 0
    for part in parts:
        lower = part.lower()
        if lower in _MODIFIERS:
            modifiers |= int(_MODIFIERS[lower])
        elif code is None and lower in _KEYS:
            code = int(_KEYS[lower])
        elif code is None and len(part) == 1 and 32 < ord(part) < 127:
            code = ord(part.upper())
        else:
            raise ValueError("unknown key %r (Enter, Ctrl+S, Shift+Tab, a)" % key)
    if code is None:
        raise ValueError("no key in %r, only modifiers" % key)
    return code, modifiers


def _text(value):
    if value is None:
        return ""
    return value.decode("utf-8", "replace") if isinstance(value, bytes) else value


class Accessible(object):
    """An accessible as the driver describes it, copied: role (its selector name), name, description,
    value, number, minimum, maximum, states (a set of names: "checked", "disabled"...), x, y, width,
    height (in its window, logical units), item (its index among its widget's items, or -1)."""

    def __init__(self, info):
        self.role = r.Raccessible_get_role_name(info.role)
        self.name = _text(info.name)
        self.description = _text(info.description)
        self.value = _text(info.value)
        self.number, self.minimum, self.maximum = info.number, info.minimum, info.maximum
        self.states = frozenset(name for name, bit in _STATES if info.states & int(bit))
        self.x, self.y, self.width, self.height = info.x, info.y, info.width, info.height
        self.item = info.item

    @property
    def text(self):
        """What it shows: a text field's value, else its name (the driver's text=)."""
        return self.value if self.role in _VALUE_ROLES else self.name

    def __repr__(self):
        return "Accessible(%s %r%s)" % (self.role, self.name, (" " + " ".join(sorted(self.states))) if self.states else "")


class App(object):
    """Raven started for a test; see the top of this module. Use it in a with block (or start()/stop())."""

    def __init__(self, xml=None, xml_file=None, capture=False, name="test", time_step=1.0 / 60.0,
                 idle_frames=600, allow_leaks=False):
        self.window = None
        self._xml, self._xml_file = xml, xml_file
        self.capture = capture
        self.name = name
        self.time_step = time_step
        self.idle_frames = idle_frames
        self.allow_leaks = allow_leaks
        self._running = False
        self._errors = []
        self._expected = None
        self._failures = 0

    # -- the harness ------------------------------------------------------

    def start(self):
        desc = r.RInitDesc()
        desc.backend = r.R_BACKEND_VULKAN if self.capture else r.R_BACKEND_NULL
        desc.headless = 1
        desc.app_name = self.name.encode("utf-8")
        # (no Vulkan is a reason to skip, not a failure: its errors stay quiet)
        self._expected = []
        r.Rerror_set_callback(self._on_error)
        if not r.Rinit_ex(desc):
            reasons, self._expected = self._expected, None
            if self.capture:
                raise unittest.SkipTest("no usable Vulkan driver to capture with")
            # (headless needs nothing: Raven already running, most likely)
            raise RavenTestFailure("Raven didn't start: " + ("; ".join(reasons) or "Rinit_ex failed"))
        if self.capture:
            probe = r.Rwindow_create("probe", 16, 16, r.R_WINDOW_DEFAULT)
            pixels = r.Rwindow_capture(probe)[0] if probe != r.R_INVALID_ID else None
            r.Rpixels_free(pixels)
            if probe != r.R_INVALID_ID:
                r.Rwindow_destroy(probe)
            if not pixels:
                r.Rshutdown()
                self._expected = None
                raise unittest.SkipTest("no usable Vulkan driver to capture with")
        self._expected = None
        r.Rset_animation_time_step(self.time_step)
        self._running = True
        try:
            if self._xml is not None:
                self.load_xml(self._xml)
            elif self._xml_file is not None:
                self.load_xml_file(self._xml_file)
        except BaseException:
            # (no with block to leave: Raven is shut down here)
            self.stop(check_leaks=False)
            raise
        return self

    def stop(self, check_leaks=True):
        """Shuts Raven down and, unless the test already failed or allow_leaks, checks that it freed everything."""
        if not self._running:
            return
        self._running = False
        pending = self._errors
        self._errors = []
        r.Rshutdown()
        r.Rerror_set_callback(None)
        if pending and check_leaks:
            raise RavenTestFailure("Raven reported: " + "; ".join(pending))
        if check_leaks and not self.allow_leaks:
            stats = r.RMemoryStats()
            r.Rmemory_get_stats(stats)
            if stats.allocation_count != 0:
                raise RavenTestFailure("%d allocation(s) of Raven still alive after Rshutdown" % stats.allocation_count)
            if not r.Rmemory_check():
                raise RavenTestFailure("Raven's heap is corrupted")

    def __enter__(self):
        return self.start()

    def __exit__(self, kind, error, trace):
        # a test that already failed is not reported twice
        self.stop(check_leaks=kind is None)
        return False

    def _on_error(self, code, severity, detail):
        if severity == r.R_SEVERITY_WEAK_WARNING:
            return
        message = "%s (%s): %s" % (r.Rerror_to_string(code), r.Rseverity_to_string(severity), detail)
        if self._expected is not None:
            self._expected.append(message)
        else:
            self._errors.append(message)

    @contextlib.contextmanager
    def expect_errors(self):
        """Raven's errors inside the block are collected into the list it gives, instead of failing the test."""
        collected = []
        outer, self._expected = self._expected, collected
        try:
            yield collected
        finally:
            self._expected = outer

    def fail(self, message):
        """Fails the test: the windows are captured (with capture), then RavenTestFailure is raised."""
        self._failures += 1
        folder = os.environ.get("RAVEN_TEST_CAPTURES")
        if self.capture and self._running and folder:
            for index in range(r.Rwindow_get_count()):
                path = os.path.join(folder, "%s-%d-window%d.png" % (self.name, self._failures, index))
                if r.Rwindow_save_preview(r.Rwindow_get(index), path, 1.0):
                    message += "\n  captured " + path
        raise RavenTestFailure(message)

    def _check_errors(self):
        if self._errors:
            pending, self._errors = self._errors, []
            self.fail("Raven reported: " + "; ".join(pending))

    def wait_idle(self, step="wait_idle"):
        """Draws frames until the app has nothing pending; fails when it stays busy."""
        if not self._running:
            raise RavenTestFailure("%s: the app is not started (use it in a with block)" % step)
        busy = not r.Rwait_idle(self.idle_frames)
        self._check_errors()
        if busy:
            self.fail("%s: the app is still busy after %d frames" % (step, self.idle_frames))

    # -- windows ----------------------------------------------------------

    def load_xml(self, xml):
        """Builds a window from XML text; returns it (also as ui.window)."""
        window = r.Rwindow_create_from_xml(xml)
        self._check_errors()
        if window == r.R_INVALID_ID:
            self.fail("load_xml: no window was built from the XML")
        self.window = window
        return window

    def load_xml_file(self, path):
        """Builds a window from an XML file; returns it (also as ui.window)."""
        window = r.Rwindow_create_from_xml_file(path)
        self._check_errors()
        if window == r.R_INVALID_ID:
            self.fail("load_xml_file: no window was built from %s" % path)
        self.window = window
        return window

    # -- finding ----------------------------------------------------------

    def _find(self, step, selector):
        self.wait_idle(step)
        found = r.Raccessible_find(0, selector)
        self._check_errors()
        if not found:
            self.fail("%s(%s): %s" % (step, selector, r.Raccessible_get_last_error()))
        return found

    def _describe(self, step, selector):
        info = r.RAccessibleInfo()
        if not r.Raccessible_describe(self._find(step, selector), info):
            self.fail("%s(%s): it is gone" % (step, selector))
        return Accessible(info)

    def find(self, selector):
        """The one accessible a selector names, described (an Accessible); fails unless exactly one matches."""
        return self._describe("find", selector)

    def count(self, selector):
        """How many accessibles match."""
        self.wait_idle("count")
        found = r.Raccessible_count(0, selector)
        self._check_errors()
        return found

    # -- acting -----------------------------------------------------------

    def _acted(self, step, selector, done):
        if not done:
            self.fail("%s(%s): %s" % (step, selector, r.Raccessible_get_last_error()))
        self.wait_idle(step)

    def click(self, selector):
        """Clicks it with the mouse, as a user would."""
        self._acted("click", selector, r.Raccessible_click(self._find("click", selector)))

    def type(self, selector, text):
        """Focuses it, then types the text as the keyboard would."""
        self._acted("type", selector, r.Raccessible_type(self._find("type", selector), text))

    def key(self, selector, key, modifiers=0):
        """Focuses it, then presses a key: "Enter", "Ctrl+S", "a", or an RKey with RModifier values."""
        code, parsed = _parse_key(key)
        self._acted("key", selector, r.Raccessible_press_key(self._find("key", selector), code, parsed | int(modifiers)))

    def act(self, selector, action):
        """"expand", "collapse", "select" or "focus" (or an RAccessibleAction), without input."""
        value = _ACTIONS.get(action) if isinstance(action, str) else action
        if value is None:
            raise ValueError("unknown action %r (expand, collapse, select, focus)" % action)
        self._acted("act", selector, r.Raccessible_act(self._find("act", selector), value))

    # -- checking ---------------------------------------------------------

    def expect_exists(self, selector):
        """Exactly one accessible matches."""
        self._expect_count("expect_exists", selector, 1, "exactly ")

    def expect_absent(self, selector):
        """None matches."""
        self._expect_count("expect_absent", selector, 0, "")

    def expect_count(self, selector, count):
        self._expect_count("expect_count", selector, count, "")

    def _expect_count(self, step, selector, expected, exactly):
        self.wait_idle(step)
        found = r.Raccessible_count(0, selector)
        self._check_errors()
        if found != expected:
            self.fail("%s(%s): %d match%s, expected %s%d" % (step, selector, found, "" if found == 1 else "es", exactly, expected))

    def expect_text(self, selector, text):
        """The text it shows: a text field's value, else its name (the driver's text=)."""
        shown = self._describe("expect_text", selector).text
        if shown != text:
            self.fail('expect_text(%s): "%s", expected "%s"' % (selector, shown, text))

    def expect_value(self, selector, value):
        """The number of a slider, a spin box, a progress bar."""
        found = self._describe("expect_value", selector)
        if "has-range" not in found.states:
            self.fail("expect_value(%s): it has no number (a %s)" % (selector, found.role))
        if abs(found.number - value) > 1e-6 * max(1.0, abs(value)):
            self.fail("expect_value(%s): %g, expected %g" % (selector, found.number, value))

    def _expect_state(self, step, selector, state, yes, no, expected, inverted=False):
        found = self._describe(step, selector)
        actual = (state in found.states) != inverted
        if actual != bool(expected):
            self.fail("%s(%s): %s, expected %s" % (step, selector, yes if actual else no, yes if expected else no))

    def expect_checked(self, selector, checked=True):
        self._expect_state("expect_checked", selector, "checked", "checked", "not checked", checked)

    def expect_enabled(self, selector, enabled=True):
        self._expect_state("expect_enabled", selector, "disabled", "enabled", "disabled", enabled, inverted=True)

    def expect_focused(self, selector, focused=True):
        self._expect_state("expect_focused", selector, "focused", "focused", "not focused", focused)


def app(xml=None, xml_file=None, **options):
    """A test's Raven, for a with block: `with raven_testing.app(xml=...) as ui:`. Options: capture, name,
    time_step, idle_frames, allow_leaks."""
    return App(xml=xml, xml_file=xml_file, **options)


# -- pytest: the raven_ui fixture, only when pytest is there --------------

try:
    import pytest as _pytest
except ImportError:
    _pytest = None

if _pytest is not None:
    @_pytest.fixture
    def raven_ui(request):
        """A headless Raven for one test (raven_testing.App, named after the test); capture with
        @pytest.mark.raven(capture=True). Load the window with raven_ui.load_xml or the app's own code."""
        marker = request.node.get_closest_marker("raven")
        options = dict(marker.kwargs) if marker else {}
        options.setdefault("name", request.node.name)
        ui = App(**options)
        ui.start()
        try:
            yield ui
        finally:
            failed = getattr(request.node, "_raven_failed", False)
            ui.stop(check_leaks=not failed)

    @_pytest.hookimpl(hookwrapper=True)
    def pytest_runtest_makereport(item, call):
        outcome = yield
        report = outcome.get_result()
        if report.when == "call" and report.failed:
            item._raven_failed = True

    def pytest_configure(config):
        config.addinivalue_line("markers", "raven(**options): options of the raven_ui fixture (capture=True, ...)")
