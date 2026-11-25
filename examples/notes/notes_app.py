"""
The notes example's logic in Python: what notes.xml's actions do, as
notes_app.c does it in C. test_notes.py tests it headless.

    RAVEN_LIBRARY=/path/to/libraven.dylib PYTHONPATH=bindings/python python3 examples/notes/notes_app.py
"""

import os
import sys

import raven as r

HERE = os.path.dirname(os.path.abspath(__file__))
NOTES_XML = os.path.join(HERE, "notes.xml")


def open_notes(xml_path=NOTES_XML):
    """Builds the window from notes.xml and binds notes.add and notes.delete. Returns the window."""
    window = r.Rwindow_create_from_xml_file(xml_path)
    if window == r.R_INVALID_ID:
        return window
    note = r.Rwidget_find(window, "note")
    notes = r.Rwidget_find(window, "notes")
    count = r.Rwidget_find(window, "count")
    delete = r.Rwidget_find(window, "delete")

    def show_count():
        n = r.Rlist_view_get_item_count(notes)
        r.Rlabel_set_text(count, "No notes" if n == 0 else "%d note%s" % (n, "" if n == 1 else "s"))

    def add(sender, action, user_data):
        # the text typed becomes a note, and the field is emptied for the next one
        text = r.Rtext_input_get_text(note)
        if not text:
            return
        r.Rlist_view_add_item(notes, text)
        r.Rtext_input_set_text(note, "")
        show_count()

    def delete_selected(sender, action, user_data):
        selected = r.Rlist_view_get_selected(notes)
        if selected < 0:
            return
        r.Rlist_view_remove_item(notes, selected)
        r.Rlist_view_set_selected(notes, -1)
        r.Rwidget_set_enabled(delete, 0)
        show_count()

    def selection_changed(list_view, index, user_data):
        # Delete is there for a selected note only
        r.Rwidget_set_enabled(delete, 1 if index >= 0 else 0)

    r.Raction_bind("notes.add", add)
    r.Raction_bind("notes.delete", delete_selected)
    r.Rlist_view_set_selection_changed_callback(notes, selection_changed)
    return window


def main():
    if not r.Rinit(r.R_BACKEND_VULKAN):
        print("cannot start Raven")
        return 1
    open_notes()
    while r.Rwait_frame(-1.0):
        pass
    r.Rshutdown()
    return 0


if __name__ == "__main__":
    sys.exit(main())
