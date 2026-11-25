"""
raven_testing, the harness for Python UI tests: its checks pass and fail as
they say, its messages say why, Raven's errors fail a test, and a failure
with capture leaves a PNG of each window. unittest only (no pytest needed):

    RAVEN_LIBRARY=/path/to/libraven.dylib python3 -m unittest test_raven_testing
"""

import os
import shutil
import tempfile
import unittest

import raven as r
import raven_testing
from raven_testing import RavenTestFailure

UI = """
<Window Title="T" Width="300" Height="240">
    <VBox>
        <TextInput Name="field" AccessibleName="Field"/>
        <Button Name="go" Text="Go"/>
        <Button Name="off" Text="Off" Enabled="false"/>
        <CheckBox Name="box" Text="Box" Checked="true"/>
        <Slider Name="slider" AccessibleName="Level" Min="0" Max="10" Value="4"/>
        <Label Text="Hello"/>
    </VBox>
</Window>
"""


class Passing(unittest.TestCase):
    def test_checks_that_hold(self):
        with raven_testing.app(xml=UI) as ui:
            ui.expect_exists('role=button name="Go"')
            ui.expect_absent('role=button name="Stop"')
            ui.expect_count("role=button", 2)
            ui.expect_text("role=text", "Hello")
            ui.type("role=text-field", "typed")
            ui.expect_text("role=text-field", "typed")
            ui.expect_focused("role=text-field")
            ui.key("role=text-field", "Backspace")
            ui.expect_text("role=text-field", "type")
            ui.key("role=text-field", r.R_KEY_A, r.R_MOD_CTRL)
            ui.expect_enabled('role=button name="Go"')
            ui.expect_enabled('role=button name="Off"', False)
            ui.expect_checked("role=checkbox")
            ui.click("role=checkbox")
            ui.expect_checked("role=checkbox", False)
            ui.expect_value("role=slider", 4)
            ui.act('role=button name="Go"', "focus")
            ui.expect_focused('role=button name="Go"')
            found = ui.find("role=slider")
            self.assertEqual((found.role, found.name, found.maximum), ("slider", "Level", 10.0))
            self.assertIn("has-range", found.states)
            self.assertEqual(ui.count("role=button"), 2)
            # the errors a test means to cause are collected, not failures
            with ui.expect_errors() as errors:
                r.Rwidget_set_enabled(0x7FFF1234, 1)
            self.assertEqual(len(errors), 1)
            self.assertIn("R_ERROR_INVALID_ID", errors[0])

    def test_keys(self):
        parse = raven_testing._parse_key
        self.assertEqual(parse("Enter"), (int(r.R_KEY_ENTER), 0))
        self.assertEqual(parse("Ctrl+Shift+s"), (int(r.R_KEY_S), int(r.R_MOD_CTRL) | int(r.R_MOD_SHIFT)))
        self.assertEqual(parse("Ctrl++"), (ord("+"), int(r.R_MOD_CTRL)))
        self.assertEqual(parse("F5"), (int(r.R_KEY_F5), 0))
        with self.assertRaises(ValueError):
            parse("Hyper+Q")


class Failing(unittest.TestCase):
    def fails(self, ui, call, *parts):
        with self.assertRaises(RavenTestFailure) as caught:
            call(ui)
        for part in parts:
            self.assertIn(part, str(caught.exception))

    def test_messages(self):
        with raven_testing.app(xml=UI) as ui:
            self.fails(ui, lambda ui: ui.expect_text("role=text", "Goodbye"), 'expect_text(role=text): "Hello", expected "Goodbye"')
            self.fails(ui, lambda ui: ui.expect_absent('role=button name="Go"'), 'expect_absent(role=button name="Go"): 1 match, expected 0')
            self.fails(ui, lambda ui: ui.expect_exists("role=button"), "2 matches, expected exactly 1")
            self.fails(ui, lambda ui: ui.expect_count("role=checkbox", 3), "1 match, expected 3")
            self.fails(ui, lambda ui: ui.expect_enabled('role=button name="Off"'), 'expect_enabled(role=button name="Off"): disabled, expected enabled')
            self.fails(ui, lambda ui: ui.expect_checked("role=checkbox", False), "expect_checked(role=checkbox): checked, expected not checked")
            self.fails(ui, lambda ui: ui.expect_focused('role=button name="Go"'), "not focused, expected focused")
            self.fails(ui, lambda ui: ui.expect_value("role=slider", 5), "expect_value(role=slider): 4, expected 5")
            self.fails(ui, lambda ui: ui.expect_value('role=button name="Go"', 1), "it has no number (a button)")
            # the driver's own reasons: missing, ambiguous, disabled
            self.fails(ui, lambda ui: ui.click('role=button name="Stop"'), 'click(role=button name="Stop"): ', "Stop")
            self.fails(ui, lambda ui: ui.click("role=button"), "2 accessibles")
            self.fails(ui, lambda ui: ui.click('role=button name="Off"'), "disabled")
            self.fails(ui, lambda ui: ui.find("role=tree"), "find(role=tree): ")

    def test_raven_errors_fail_the_next_step(self):
        with raven_testing.app(xml=UI) as ui:
            r.Rwidget_set_enabled(0x7FFF1234, 1)
            self.fails(ui, lambda ui: ui.expect_exists('role=button name="Go"'), "Raven reported: R_ERROR_INVALID_ID")
            # reported once
            ui.expect_exists('role=button name="Go"')

    def test_raven_errors_fail_at_the_end(self):
        ui = raven_testing.app(xml=UI)
        with self.assertRaises(RavenTestFailure) as caught:
            with ui:
                r.Rwidget_set_enabled(0x7FFF1234, 1)
        self.assertIn("R_ERROR_INVALID_ID", str(caught.exception))

    def test_xml_warnings_fail(self):
        with self.assertRaises(RavenTestFailure) as caught:
            with raven_testing.app(xml='<Window><Slider Minimum="0"/></Window>'):
                pass
        self.assertIn("Minimum", str(caught.exception))

    def test_a_failing_test_is_reported_once(self):
        # the block's own failure comes out, not a second one from leaving it
        with self.assertRaises(RavenTestFailure) as caught:
            with raven_testing.app(xml=UI) as ui:
                r.Rwidget_set_enabled(0x7FFF1234, 1)
                raise RavenTestFailure("the test's own")
        self.assertEqual(str(caught.exception), "the test's own")

    def test_not_started(self):
        ui = raven_testing.App()
        self.fails(ui, lambda ui: ui.click("role=button"), "not started")


class Captures(unittest.TestCase):
    def test_a_failure_leaves_a_png(self):
        folder = tempfile.mkdtemp(prefix="raven_testing_")
        os.environ["RAVEN_TEST_CAPTURES"] = folder
        try:
            with raven_testing.app(xml=UI, capture=True, name="captured") as ui:
                with self.assertRaises(RavenTestFailure) as caught:
                    ui.expect_text("role=text", "Goodbye")
                path = os.path.join(folder, "captured-1-window0.png")
                self.assertIn("captured " + path, str(caught.exception))
                with open(path, "rb") as f:
                    self.assertEqual(f.read(8), b"\x89PNG\r\n\x1a\n")
        finally:
            del os.environ["RAVEN_TEST_CAPTURES"]
            shutil.rmtree(folder, ignore_errors=True)


if __name__ == "__main__":
    unittest.main()
