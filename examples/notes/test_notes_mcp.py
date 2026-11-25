"""
The notes example's tools, called by an MCP client as an agent would: notes.add, notes.list and
notes.delete over the notes window, in a headless app through raven_testing, and the window
checked after. As examples/notes/test_notes.py, with raven_mcp.

    RAVEN_LIBRARY=... RAVEN_MCP_LIBRARY=... PYTHONPATH=bindings/python python3 test_notes_mcp.py

Exits with 77 (skipped) without the Raven MCP library. Run by `./gradlew :raven-mcp:pythonWrapperTest`.
"""

import json
import sys

import raven_testing
from notes_app import open_notes
from raven_mcp import Mcp
from test_raven_mcp import Client, _library_available, on_client, text_of

PORT = 18768


def check_notes_tools(ui):
    mcp = Mcp()
    open_notes(mcp=mcp)
    try:
        assert mcp.start(name="notes-test", port=PORT, token="")

        def agent():
            client = Client(mcp.url).connect()
            tools = sorted(tool["name"] for tool in client.request("tools/list")["result"]["tools"])
            return tools, [
                client.call("notes.list"),
                client.call("notes.add", {"text": "  buy milk "}),
                client.call("notes.add", {"text": "   "}),
                client.call("notes.add", {"text": "call Oscar"}),
                client.call("notes.delete", {"index": 0}),
                client.call("notes.delete", {"index": 5}),
                client.call("notes.list"),
            ]

        tools, (empty, added, blank, second, deleted, missing, listed) = on_client(agent)
        assert tools == ["notes.add", "notes.delete", "notes.list"], tools
        assert json.loads(text_of(empty)) == []
        assert text_of(added) == "added 'buy milk', note 0", text_of(added)
        assert blank["isError"] and text_of(blank) == "a note can't be blank", blank
        assert text_of(second) == "added 'call Oscar', note 1"
        assert text_of(deleted) == "deleted 'buy milk'"
        assert missing["isError"] and text_of(missing) == "there is no note 5: the list has 1", missing
        assert json.loads(text_of(listed)) == ["call Oscar"]

        # the window shows what the agent did
        ui.expect_count("role=list-item", 1)
        ui.expect_text("role=list-item [0]", "call Oscar")
        ui.expect_text("widget=count", "1 note")
        ui.expect_enabled('role=button name="Delete"', False)
    finally:
        mcp.stop()


def main():
    if not _library_available():
        print("test_notes_mcp.py: SKIPPED, the Raven MCP library isn't available (RAVEN_MCP_LIBRARY)")
        return 77
    with raven_testing.app(name="test_notes_mcp") as ui:
        check_notes_tools(ui)
    print("test_notes_mcp.py: passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
