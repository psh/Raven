"""
raven_mcp's tests: the schema from a signature (no library needed), then tools published with
@mcp.tool and called by an MCP client on another thread, as an agent would, while a headless app
draws frames. Those skip without the Raven MCP library.

    RAVEN_LIBRARY=... RAVEN_MCP_LIBRARY=... PYTHONPATH=bindings/python python3 -m unittest test_raven_mcp

Run by `./gradlew :raven-mcp:pythonWrapperTest` (part of compose's `check`), which builds both libraries.
"""

import enum
import gc
import json
import threading
import time
import unittest
import urllib.request
from typing import Annotated, Optional

import raven as r
from raven_mcp import Call, Hint, Mcp, _Tool

PORT = 18767


class Colour(enum.Enum):
    RED = "red"
    BLUE = "blue"


class SchemaTest(unittest.TestCase):
    def test_types_defaults_and_descriptions(self):
        def tool(text: Annotated[str, "The note's text"], count: int, ratio: float = 1.0, pinned: bool = False,
                 tags: list = None, words: Optional[list[str]] = None, colour: Colour = Colour.RED, extra: dict = None,
                 anything=None):
            pass

        schema = _Tool("t", tool).schema
        self.assertEqual(schema["required"], ["text", "count"])
        p = schema["properties"]
        self.assertEqual(p["text"], {"type": "string", "description": "The note's text"})
        self.assertEqual(p["count"], {"type": "integer"})
        self.assertEqual(p["ratio"], {"type": "number"})
        self.assertEqual(p["pinned"], {"type": "boolean"})
        self.assertEqual(p["tags"], {"type": "array"})
        self.assertEqual(p["words"], {"type": ["array", "null"], "items": {"type": "string"}})
        self.assertEqual(p["colour"], {"enum": ["red", "blue"], "type": "string"})
        self.assertEqual(p["extra"], {"type": "object"})
        self.assertEqual(p["anything"], {})

    def test_call_is_not_an_argument(self):
        def later(path: str, call: Call):
            pass

        tool = _Tool("t", later)
        self.assertTrue(tool.takes_call)
        self.assertEqual(tool.schema, {"type": "object", "properties": {"path": {"type": "string"}}, "required": ["path"]})

    def test_no_arguments(self):
        self.assertEqual(_Tool("t", lambda: None).schema, {"type": "object", "properties": {}})

    def test_arguments_are_checked_and_converted(self):
        tool = _Tool("t", _colour_tool)
        self.assertEqual(tool.arguments('{"colour": "blue"}'), {"colour": Colour.BLUE})
        with self.assertRaisesRegex(ValueError, "missing argument: colour"):
            tool.arguments("{}")
        with self.assertRaisesRegex(ValueError, "unknown argument: shade"):
            tool.arguments('{"colour": "red", "shade": 1}')
        with self.assertRaisesRegex(ValueError, "argument colour: 'green' is not a valid Colour"):
            tool.arguments('{"colour": "green"}')

    def test_unsupported_types_are_refused_when_published(self):
        def tool(when: complex):
            pass

        with self.assertRaisesRegex(TypeError, "t, argument when: a tool's arguments are"):
            _Tool("t", tool)
        with self.assertRaisesRegex(TypeError, "named, one by one"):
            _Tool("t", lambda *rest: None)


class WithoutRavenMcpTest(unittest.TestCase):
    def test_an_app_not_asked_for_raven_mcp_never_loads_it(self):
        import os
        from unittest import mock
        with mock.patch.dict(os.environ, {"RAVEN_MCP": "0"}), mock.patch.object(r, "_mcp", side_effect=AssertionError("loaded")):
            m = Mcp()
            self.assertFalse(m.start_from_environment(name="notes", ui_tools=True))
            m.stop()


def _colour_tool(colour: Colour):
    pass


def _library_available():
    try:
        r.Rmcp_get_url()
        return True
    except OSError:
        return False


class Client:
    """A Streamable HTTP MCP client, run on its own thread while the UI thread draws frames."""

    def __init__(self, url):
        self.url, self.session, self.next_id = url, None, 1

    def post(self, message):
        headers = {"Content-Type": "application/json", "Accept": "application/json, text/event-stream"}
        if self.session:
            headers["Mcp-Session-Id"] = self.session
        request = urllib.request.Request(self.url, json.dumps(message).encode(), headers)
        with urllib.request.urlopen(request, timeout=10) as response:
            self.session = response.headers.get("Mcp-Session-Id") or self.session
            body = response.read().decode()
            return json.loads(body) if body.strip() else None

    def request(self, method, params=None):
        self.next_id += 1
        return self.post({"jsonrpc": "2.0", "id": self.next_id, "method": method, "params": params or {}})

    def connect(self):
        self.request("initialize", {"protocolVersion": "2025-06-18", "capabilities": {},
                                    "clientInfo": {"name": "test-agent", "version": "1"}})
        self.post({"jsonrpc": "2.0", "method": "notifications/initialized"})
        return self

    def call(self, tool, arguments=None):
        return self.request("tools/call", {"name": tool, "arguments": arguments or {}})["result"]


def on_client(work, seconds=10):
    """Runs work() on another thread while the UI thread draws frames; returns its result."""
    box = {}

    def run():
        try:
            box["value"] = work()
        except BaseException as e:
            box["error"] = e

    thread = threading.Thread(target=run)
    thread.start()
    deadline = time.monotonic() + seconds
    while thread.is_alive() and time.monotonic() < deadline:
        r.Rwait_frame(0.01)
    thread.join(5)
    if "error" in box:
        raise box["error"]
    return box.get("value")


def text_of(result):
    return result["content"][0]["text"]


@unittest.skipUnless(_library_available(), "the Raven MCP library isn't available (RAVEN_MCP_LIBRARY)")
class ToolsTest(unittest.TestCase):
    def setUp(self):
        desc = r.RInitDesc()
        desc.backend = r.R_BACKEND_NULL
        desc.headless = 1
        self.assertTrue(r.Rinit_ex(desc))
        self.mcp = Mcp()
        self.logs = []

    def tearDown(self):
        self.mcp.stop()
        r.Rshutdown()

    def start(self):
        self.assertTrue(self.mcp.start(name="raven-mcp-python-test", port=PORT, token="",
                                       log=lambda severity, message: self.logs.append(message)))
        return Client(self.mcp.url)

    def test_a_decorated_tool_replies_with_its_return_value(self):
        notes = []

        @self.mcp.tool("notes.add", "Add a note to the list", hints={Hint.NOT_DESTRUCTIVE, Hint.CLOSED_WORLD})
        def add_note(text: Annotated[str, "The note's text"], pinned: bool = False) -> str:
            notes.append((text, pinned, bool(r.Ris_ui_thread())))
            return "added note %d" % len(notes)

        client = self.start()
        listed, result = on_client(lambda: (lambda c: (c.request("tools/list"), c.call("notes.add", {"text": "milk"})))(client.connect()))
        tool = listed["result"]["tools"][0]
        self.assertEqual(tool["inputSchema"]["properties"]["text"]["description"], "The note's text")
        self.assertEqual(tool["inputSchema"]["required"], ["text"])
        self.assertEqual(tool["annotations"]["destructiveHint"], False)
        self.assertEqual(tool["annotations"]["openWorldHint"], False)
        self.assertEqual(text_of(result), "added note 1")
        self.assertEqual(notes, [("milk", False, True)])

    def test_json_replies_and_failures(self):
        @self.mcp.tool("notes.list")
        def list_notes() -> list:
            """The notes, in order"""
            return ["milk", "eggs"]

        @self.mcp.tool("notes.count")
        def count() -> dict:
            return {"count": 2}

        @self.mcp.tool("notes.delete")
        def delete(index: int):
            raise IndexError("no note %d" % index)

        client = self.start()
        listed, counted, failed, invalid = on_client(lambda: (lambda c: (
            c.call("notes.list"), c.call("notes.count"), c.call("notes.delete", {"index": 7}),
            c.call("notes.delete", {}),
        ))(client.connect()))
        self.assertEqual(json.loads(text_of(listed)), ["milk", "eggs"])
        self.assertNotIn("structuredContent", listed)
        self.assertEqual(counted["structuredContent"], {"count": 2})
        self.assertTrue(failed["isError"])
        self.assertEqual(text_of(failed), "no note 7")
        self.assertTrue(invalid["isError"])
        self.assertEqual(text_of(invalid), "missing argument: index")

    def test_a_call_replied_later_from_another_thread(self):
        answered = {}

        @self.mcp.tool("notes.export", "Export the notes")
        def export(path: str, call: Call):
            def work():
                time.sleep(0.05)
                call.reply("exported to " + path)
                try:
                    call.reply("again")
                except RuntimeError as e:
                    answered["second"] = str(e)
            threading.Thread(target=work).start()

        client = self.start()
        result = on_client(lambda: client.connect().call("notes.export", {"path": "/tmp/notes.txt"}))
        self.assertEqual(text_of(result), "exported to /tmp/notes.txt")
        self.assertEqual(answered["second"], "the call to notes.export was already answered")

    def test_a_call_collected_without_a_reply_fails(self):
        @self.mcp.tool("notes.forget")
        def forget(call: Call):
            pass  # the Call is dropped at once

        client = self.start()
        result = on_client(lambda: (gc.collect(), client.connect().call("notes.forget"))[1])
        self.assertTrue(result["isError"])
        self.assertEqual(text_of(result), "the tool never replied")

    def test_a_removed_tool_is_gone(self):
        self.mcp.tool("notes.add")(lambda text: "added")
        self.mcp.tool("notes.clear")(lambda: "cleared")
        self.mcp.remove_tool("notes.add")
        self.assertEqual(self.mcp.tools, ["notes.clear"])
        client = self.start()
        names = on_client(lambda: [t["name"] for t in client.connect().request("tools/list")["result"]["tools"]])
        self.assertEqual(names, ["notes.clear"])

    def test_a_refused_tool_raises(self):
        with self.assertRaisesRegex(ValueError, "refused the tool not a name"):
            self.mcp.add_tool("not a name", lambda: None)


if __name__ == "__main__":
    unittest.main()
