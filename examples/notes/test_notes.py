"""
The notes example's headless UI test in Python, through raven_testing: add a
note, check the list, delete it, as test_notes.c does in C.

    python3 test_notes.py                      plain, with raven_testing.app (no pytest needed)
    python3 -m pytest -p raven_testing test_notes.py      with the raven_ui fixture

Both need RAVEN_LIBRARY (a shared libraven) and bindings/python on PYTHONPATH;
installed from the wheel, neither (and the wheel registers the fixture).
"""

import os
import sys
import unittest

import raven_testing
from notes_app import open_notes


def check_notes(ui):
    """The scenario, on a started app."""
    open_notes()

    # nothing yet: Delete waits for a note to be selected
    ui.expect_absent("role=list-item")
    ui.expect_text("widget=count", "No notes")
    ui.expect_enabled('role=button name="Delete"', False)

    # add two: with the button, and with Enter
    ui.type('role=text-field name="New note"', "buy milk")
    ui.click('role=button name="Add"')
    ui.type("role=text-field", "call Oscar")
    ui.key("role=text-field", "Enter")

    # the list holds them in order, and the field is empty for the next one
    ui.expect_count("role=list-item", 2)
    ui.expect_text("role=list-item [0]", "buy milk")
    ui.expect_text("role=list-item [1]", "call Oscar")
    ui.expect_text("role=text-field", "")
    ui.expect_text("widget=count", "2 notes")

    # delete the first: select it, then Delete
    ui.click('role=list-item text="buy milk"')
    ui.expect_enabled('role=button name="Delete"')
    ui.click('role=button name="Delete"')
    ui.expect_absent('role=list-item text="buy milk"')
    ui.expect_exists('role=list-item text="call Oscar"')
    ui.expect_text("widget=count", "1 note")
    ui.expect_enabled('role=button name="Delete"', False)

    # an empty note is not added
    ui.click('role=button name="Add"')
    ui.expect_count("role=list-item", 1)


def test_adds_and_deletes_notes(raven_ui):
    """pytest, with the raven_ui fixture."""
    check_notes(raven_ui)


def main():
    capture = os.environ.get("RAVEN_TEST_CAPTURE") is not None
    try:
        with raven_testing.app(name="test_notes", capture=capture) as ui:
            check_notes(ui)
    except unittest.SkipTest as reason:
        print("test_notes.py: SKIPPED, %s" % reason)
        return 77
    print("test_notes.py: passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
