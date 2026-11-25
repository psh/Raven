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

    # nor is a note of only whitespace (a no-break space: typing drops a tab or a newline)
    ui.type("role=text-field", " \u00a0 ")
    ui.click('role=button name="Add"')
    ui.expect_count("role=list-item", 1)
    ui.expect_text("widget=count", "1 note")

    # a note is added without the whitespace around it
    ui.type("role=text-field", "  water plants\u00a0")
    ui.key("role=text-field", "Enter")
    ui.expect_text("role=list-item [1]", "water plants")

    # deleting the last notes leaves the label saying so
    for _ in range(2):
        ui.click("role=list-item [0]")
        ui.click('role=button name="Delete"')
    ui.expect_absent("role=list-item")
    ui.expect_text("widget=count", "No notes")

    # the Delete key deletes the selected note, as the button does
    ui.type("role=text-field", "buy milk")
    ui.key("role=text-field", "Enter")
    ui.click('role=list-item text="buy milk"')
    ui.key("role=list", "Delete")
    ui.expect_absent("role=list-item")
    ui.expect_text("widget=count", "No notes")
    ui.expect_enabled('role=button name="Delete"', False)

    # in the text field, Delete edits the text and deletes no note, even with one selected:
    # Left then Delete removes the b, and Delete at the end of the text does nothing
    ui.type("role=text-field", "call Oscar")
    ui.key("role=text-field", "Enter")
    ui.click('role=list-item text="call Oscar"')
    ui.type("role=text-field", "ab")
    ui.key("role=text-field", "Left")
    ui.key("role=text-field", "Delete")
    ui.expect_text("role=text-field", "a")
    ui.key("role=text-field", "End")
    ui.key("role=text-field", "Delete")
    ui.expect_text("role=text-field", "a")
    ui.expect_exists('role=list-item text="call Oscar" selected')
    ui.expect_text("widget=count", "1 note")

    # with no note selected, Delete changes nothing (Delete on the list first, to delete call Oscar)
    ui.key("role=list", "Delete")
    ui.expect_absent("role=list-item")
    ui.type("role=text-field", "x")
    ui.key("role=text-field", "Enter")
    ui.expect_absent("role=list-item selected")
    ui.key("role=list", "Delete")
    ui.expect_count("role=list-item", 1)

    # adding a note keeps the one selected
    ui.click('role=list-item text="ax"')
    ui.type("role=text-field", "water plants")
    ui.key("role=text-field", "Enter")
    ui.expect_exists('role=list-item text="ax" selected')
    ui.expect_count("role=list-item", 2)


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
