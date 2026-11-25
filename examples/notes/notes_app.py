"""
The notes example's logic in Python: what notes.xml's actions do, as
notes_app.c does it in C, and the same as tools for an agent (notes.add,
notes.list, notes.delete) through raven_mcp. test_notes.py tests it headless,
and test_notes_mcp.py through an MCP client.

    RAVEN_LIBRARY=/path/to/libraven.dylib PYTHONPATH=bindings/python python3 examples/notes/notes_app.py

RAVEN_MCP=1 serves the tools to agents (RAVEN_MCP_UI=1 adds the ui.* tools),
with the Raven MCP library from RAVEN_MCP_LIBRARY or beside libraven.
"""

import os
import sys
from typing import Annotated

import raven as r
from raven_mcp import Hint

HERE = os.path.dirname(os.path.abspath(__file__))
NOTES_XML = os.path.join(HERE, "notes.xml")


def open_notes(xml_path=NOTES_XML, mcp=None):
    """Builds the window from notes.xml and binds notes.add and notes.delete; with a
    raven_mcp.Mcp, publishes notes.add, notes.list and notes.delete on it too. Returns the window."""
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

    def add_note(text):
        """Adds a note, without the spaces around it; a blank one is not added. Returns the note, or None."""
        text = (text or "").strip(" ")
        if not text:
            return None
        r.Rlist_view_add_item(notes, text)
        show_count()
        return text

    def delete_note(index):
        r.Rlist_view_remove_item(notes, index)
        r.Rlist_view_set_selected(notes, -1)
        r.Rwidget_set_enabled(delete, 0)
        show_count()

    def all_notes():
        return [r.Rlist_view_get_item_text(notes, i) for i in range(r.Rlist_view_get_item_count(notes))]

    def add(sender, action, user_data):
        # the text typed becomes a note, and the field is emptied for the next one
        if add_note(r.Rtext_input_get_text(note)):
            r.Rtext_input_set_text(note, "")

    def delete_selected(sender, action, user_data):
        selected = r.Rlist_view_get_selected(notes)
        if selected >= 0:
            delete_note(selected)

    def selection_changed(list_view, index, user_data):
        # Delete is there for a selected note only
        r.Rwidget_set_enabled(delete, 1 if index >= 0 else 0)

    r.Raction_bind("notes.add", add)
    r.Raction_bind("notes.delete", delete_selected)
    r.Rlist_view_set_selection_changed_callback(notes, selection_changed)

    if mcp is not None:
        @mcp.tool("notes.add", "Add a note at the end of the list", hints={Hint.NOT_DESTRUCTIVE, Hint.CLOSED_WORLD})
        def add_tool(text: Annotated[str, "The note's text; the spaces around it are dropped"]) -> str:
            added = add_note(text)
            if added is None:
                raise ValueError("a note can't be blank")
            return "added %r, note %d" % (added, r.Rlist_view_get_item_count(notes) - 1)

        @mcp.tool("notes.list", "The notes, in order", hints={Hint.READ_ONLY, Hint.CLOSED_WORLD})
        def list_tool() -> list:
            return all_notes()

        @mcp.tool("notes.delete", "Delete a note", hints={Hint.CLOSED_WORLD})
        def delete_tool(index: Annotated[int, "The note's position, from 0, as notes.list gives them"]) -> str:
            texts = all_notes()
            if not 0 <= index < len(texts):
                raise IndexError("there is no note %d: the list has %d" % (index, len(texts)))
            delete_note(index)
            return "deleted %r" % texts[index]

    return window


def main():
    if not r.Rinit(r.R_BACKEND_VULKAN):
        print("cannot start Raven")
        return 1
    from raven_mcp import mcp
    asked = bool(os.environ.get("RAVEN_MCP"))  # the Raven MCP library is loaded only then
    open_notes(mcp=mcp if asked else None)
    if asked and mcp.start_from_environment(name="notes"):
        print("notes: serving its tools at", mcp.url)
    while r.Rwait_frame(-1.0):
        pass
    if asked:
        mcp.stop()  # the tools go too, before Rshutdown
    r.Rshutdown()
    return 0


if __name__ == "__main__":
    sys.exit(main())
