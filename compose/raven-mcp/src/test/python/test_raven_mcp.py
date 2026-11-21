"""
Raven MCP's C API, end to end: a headless Raven app in Python publishes tools through the Raven
MCP library, and an MCP client on another thread calls them over HTTP, as an agent would. The app
reaches the library through the Python binding's Rmcp_ functions (bindings/python/raven.py), which
load it from RAVEN_MCP_LIBRARY.

Run by `./gradlew :raven-mcp:pythonTest` (part of `check`), which builds the shared libraven and the
library and sets RAVEN_LIBRARY, RAVEN_MCP_LIBRARY and RAVEN_PYTHON.
"""

import base64
import json
import os
import socket
import struct
import sys
import tempfile
import threading
import time
import unittest
import urllib.error
import urllib.request

sys.path.insert(0, os.environ["RAVEN_PYTHON"])
import raven as r  # noqa: E402

PORT = 18765
TOKEN = "test-token"
logs = []
sessions = []


# Callbacks in a structure are ctypes objects, kept alive here; the texts they receive are bytes.
@r.RMcpLogCallback
def on_log(severity, message, user):
    logs.append((severity, message.decode()))


@r.RMcpSessionCallback
def on_session(connected, client, user):
    sessions.append((bool(connected), client.decode()))


def start(port=PORT, token=TOKEN, **fields):
    config = r.RMcpConfig(name=b"raven-mcp-test", port=port, log=on_log, on_session=on_session,
                          token=token.encode() if token is not None else None, **fields)
    return r.Rmcp_start(config)


def add(name, function, schema=None, hints=0):
    desc = r.RMcpToolDesc(name=name.encode(), description=b"a test tool",
                          input_schema_json=json.dumps(schema).encode() if schema else None, hints=hints)
    return r.Rmcp_tool_add(desc, lambda call, tool, arguments, user: function(call, tool, json.loads(arguments)))


def reply(call, text):
    r.Rmcp_call_reply(call, text)


class Client:
    """A Streamable HTTP MCP client, run on its own thread while the UI thread draws frames."""

    def __init__(self, token=TOKEN, origin=None, url=None):
        self.url = url or r.Rmcp_get_url()
        self.token, self.origin, self.session, self.next_id = token, origin, None, 1

    def post(self, message):
        headers = {"Content-Type": "application/json", "Accept": "application/json, text/event-stream"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        if self.session:
            headers["Mcp-Session-Id"] = self.session
        if self.origin:
            headers["Origin"] = self.origin
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
    """Runs work(client) on another thread while the UI thread draws frames; returns its result."""
    box = {}

    def run():
        try:
            box["value"] = work()
        except BaseException as e:  # reported on the UI thread
            box["error"] = e

    thread = threading.Thread(target=run)
    thread.start()
    # (timed, not counted: a headless frame takes no time, so the UI's animations take hundreds of them)
    deadline = time.monotonic() + seconds
    while thread.is_alive() and time.monotonic() < deadline:
        r.Rwait_frame(0.01)
    thread.join(5)
    if "error" in box:
        raise box["error"]
    return box.get("value")


def text_of(result):
    return result["content"][0]["text"]


class RavenMcpTest(unittest.TestCase):
    def setUp(self):
        desc = r.RInitDesc()
        desc.backend = r.R_BACKEND_NULL
        desc.headless = 1
        self.assertTrue(r.Rinit_ex(desc))
        logs.clear()
        sessions.clear()

    def tearDown(self):
        r.Rmcp_stop()
        r.Rmcp_tool_remove_all()
        r.Rshutdown()

    def test_a_call_runs_on_the_ui_thread_and_replies(self):
        seen = {}

        def add_note(call, tool, arguments):
            seen["ui_thread"] = bool(r.Ris_ui_thread())
            seen["arguments"] = arguments
            reply(call, "added " + arguments["text"])

        self.assertTrue(add("notes.add", add_note, {"type": "object", "properties": {"text": {"type": "string"}},
                                                      "required": ["text"]}))
        self.assertTrue(start())
        self.assertEqual(r.Rmcp_get_url(), "http://127.0.0.1:%d/mcp" % PORT)
        result = on_client(lambda: Client().connect().call("notes.add", {"text": "milk"}))
        self.assertEqual(text_of(result), "added milk")
        self.assertEqual(seen, {"ui_thread": True, "arguments": {"text": "milk"}})

    def test_the_list_carries_schema_and_hints(self):
        add("notes.list", lambda c, t, a: reply(c, "[]"), {"type": "object", "properties": {"n": {"type": "integer"}},
                                                          "required": ["n"]}, hints=1 | 8)
        start()
        tools = on_client(lambda: Client().connect().request("tools/list"))["result"]["tools"]
        tool = tools[0]
        self.assertEqual(tool["inputSchema"]["required"], ["n"])
        self.assertEqual(tool["annotations"]["readOnlyHint"], True)
        self.assertEqual(tool["annotations"]["openWorldHint"], False)
        self.assertNotIn("destructiveHint", tool["annotations"])

    def test_a_failure_and_a_json_reply(self):
        add("fails", lambda c, t, a: r.Rmcp_call_fail(c, "no such note"))
        add("rich", lambda c, t, a: r.Rmcp_call_reply_json(
            c, json.dumps({"content": [{"type": "text", "text": "two"}], "structuredContent": {"n": 2}})))
        start()
        failed, rich = on_client(lambda: (lambda client: (client.call("fails"), client.call("rich")))(Client().connect()))
        self.assertTrue(failed["isError"])
        self.assertEqual(text_of(failed), "no such note")
        self.assertEqual(rich["structuredContent"], {"n": 2})

    def test_a_reply_from_another_thread_and_a_second_reply(self):
        def later(call, tool, arguments):
            def work():
                time.sleep(0.05)
                reply(call, "done later")
                reply(call, "again")  # logged and ignored
            threading.Thread(target=work).start()

        add("later", later)
        start()
        self.assertEqual(text_of(on_client(lambda: Client().connect().call("later"))), "done later")
        self.assertTrue(any("replied to twice" in message for _, message in logs))

    def test_a_tool_removed_before_its_call_runs(self):
        # The call is queued for the UI thread; removing the tool first makes the library fail it.
        add("gone", lambda c, t, a: reply(c, "should not run"))
        start()
        client = on_client(lambda: Client().connect())

        def call_while_removing():
            box = {}
            thread = threading.Thread(target=lambda: box.update(result=client.call("gone")))
            thread.start()
            time.sleep(0.2)  # the request is waiting on the UI thread, which isn't drawing
            return thread, box

        thread, box = call_while_removing()  # the UI thread doesn't draw, so the call stays queued
        r.Rmcp_tool_remove("gone")
        for _ in range(200):
            if not thread.is_alive():
                break
            r.Rwait_frame(0.01)
        thread.join(5)
        self.assertTrue(box["result"]["isError"])
        self.assertEqual(text_of(box["result"]), "the tool was removed")

    def test_cancelling_a_call(self):
        calls = []
        add("slow", lambda c, t, a: calls.append(c))  # replies later, below
        start()
        client = on_client(lambda: Client().connect())
        box = {}
        # A cancelled request gets no result back, so the raw response is kept.
        thread = threading.Thread(target=lambda: box.update(
            response=client.request("tools/call", {"name": "slow", "arguments": {}})))
        thread.start()
        for _ in range(200):
            if calls:
                break
            r.Rwait_frame(0.01)
        self.assertFalse(r.Rmcp_call_is_cancelled(calls[0]))
        on_client(lambda: client.post({"jsonrpc": "2.0", "method": "notifications/cancelled",
                                       "params": {"requestId": client.next_id, "reason": "test"}}))
        for _ in range(200):
            if r.Rmcp_call_is_cancelled(calls[0]):
                break
            r.Rwait_frame(0.01)
        self.assertTrue(r.Rmcp_call_is_cancelled(calls[0]))
        reply(calls[0], "too late")  # still required; frees the call
        thread.join(5)

    def test_the_token_and_the_origin_are_checked(self):
        add("t", lambda c, t, a: reply(c, "ok"))
        start()

        def status(**client):
            try:
                Client(**client).connect()
                return 200
            except urllib.error.HTTPError as e:
                return e.code

        self.assertEqual(on_client(lambda: status(token=None)), 401)
        self.assertEqual(on_client(lambda: status(token="wrong")), 401)
        self.assertEqual(on_client(lambda: status(origin="http://evil.example")), 403)
        self.assertEqual(on_client(lambda: status()), 200)

    def test_the_stored_token(self):
        home = tempfile.mkdtemp()
        old_home = os.environ["HOME"]
        os.environ["HOME"] = home
        try:
            self.assertTrue(start(token=None))
            path = os.path.join(home, ".raven", "mcp", "raven-mcp-test.json")
            with open(path) as file:
                stored = json.load(file)
            self.assertEqual(stored["url"], "http://127.0.0.1:%d/mcp" % PORT)
            self.assertEqual(len(stored["token"]), 64)
            self.assertEqual(os.stat(path).st_mode & 0o777, 0o600)
            r.Rmcp_stop()
            self.assertTrue(start(token=None))
            with open(path) as file:
                self.assertEqual(json.load(file)["token"], stored["token"])  # kept across launches
            add("t", lambda c, t, a: reply(c, "ok"))
            self.assertEqual(text_of(on_client(lambda: Client(token=stored["token"]).connect().call("t"))), "ok")
        finally:
            os.environ["HOME"] = old_home

    def test_starting_twice_a_taken_port_and_the_kill_switch(self):
        self.assertTrue(start())
        self.assertFalse(start())
        r.Rmcp_stop()
        self.assertIsNone(r.Rmcp_get_url())
        blocker = socket.socket()
        blocker.bind(("127.0.0.1", PORT + 1))
        blocker.listen()
        try:
            self.assertFalse(start(port=PORT + 1))
        finally:
            blocker.close()
        os.environ["RAVEN_MCP"] = "0"
        try:
            self.assertFalse(start())
            self.assertTrue(any("RAVEN_MCP=0" in message for _, message in logs))
        finally:
            del os.environ["RAVEN_MCP"]

    def test_config_from_the_environment(self):
        config = r.RMcpConfig(port=1)
        self.assertFalse(r.Rmcp_config_from_environment(config))
        os.environ.update(RAVEN_MCP="1", RAVEN_MCP_PORT="9999", RAVEN_MCP_UI="1")
        try:
            self.assertTrue(r.Rmcp_config_from_environment(config))
            self.assertEqual((config.port, config.ui_tools), (9999, 1))
        finally:
            for name in ("RAVEN_MCP", "RAVEN_MCP_PORT", "RAVEN_MCP_UI"):
                del os.environ[name]

    def test_sessions_are_reported(self):
        start()
        on_client(lambda: Client().connect())
        for _ in range(50):
            if sessions:
                break
            r.Rwait_frame(0.01)
        self.assertEqual(sessions[0], (True, "test-agent"))

    def test_bad_names_and_schemas_are_refused(self):
        self.assertFalse(add("not a name", lambda c, t, a: None))
        desc = r.RMcpToolDesc(name=b"bad.schema", input_schema_json=b"[1, 2]")
        self.assertFalse(r.Rmcp_tool_add(desc, lambda *a: None))


NOTEPAD = """
<Window Title="Notes" Width="320" Height="200">
    <VBox>
        <Label Name="status" Text="no notes"/>
        <Button Text="Add" OnClicked="notes.add"/>
        <Button Text="Clear" OnClicked="notes.clear"/>
    </VBox>
</Window>"""


class ExposedActionsTest(unittest.TestCase):
    """Raven's actions as tools: an XML app's OnClicked actions, called by an agent."""

    def setUp(self):
        desc = r.RInitDesc()
        desc.backend = r.R_BACKEND_NULL
        desc.headless = 1
        self.assertTrue(r.Rinit_ex(desc))
        logs.clear()
        self.window = r.Rwindow_create_from_xml(NOTEPAD)
        self.status = r.Rwidget_find(self.window, "status")
        self.senders = []

        def add(sender, action, user):
            self.senders.append(sender)
            r.Rlabel_set_text(self.status, "1 note")

        r.Raction_bind("notes.add", add)
        r.Raction_set_description("notes.add", "Add a note")
        r.Raction_bind("notes.clear", lambda sender, action, user: r.Rlabel_set_text(self.status, "no notes"))
        r.Raction_bind("notes.bad name", lambda *a: None)  # not a tool name
        r.Raction_bind("other.thing", lambda *a: None)     # not under the prefix
        r.Raction_bind("app.quit", lambda *a: None)        # a built-in, bound: never exposed by prefix
        r.Rwait_idle(10)

    def tearDown(self):
        r.Rmcp_stop()
        r.Rmcp_tool_remove_all()
        r.Rshutdown()

    def tools(self):
        listed = on_client(lambda: Client().connect().request("tools/list"))["result"]["tools"]
        return {tool["name"]: tool for tool in listed}

    def settle(self):
        """Lets the deferred updates of the list run: they follow binding by a frame."""
        r.Rwait_idle(10)

    def test_bound_actions_under_a_prefix_are_tools(self):
        r.Rmcp_expose_actions("notes.")
        start()
        tools = self.tools()
        self.assertEqual(sorted(tools), ["notes.add", "notes.clear"])
        self.assertEqual(tools["notes.add"]["description"], "Add a note")
        self.assertEqual(tools["notes.clear"]["description"], "Runs the app's action `notes.clear`")
        self.assertNotIn("annotations", tools["notes.add"])  # no hints claimed
        self.assertTrue(any("notes.clear has no description" in m for _, m in logs))
        self.assertTrue(any('"notes.bad name" isn\'t a valid tool name' in m for _, m in logs))

    def test_a_call_fires_the_action_from_the_focused_window(self):
        r.Rmcp_expose_actions(None)
        start()
        self.assertEqual(text_of(on_client(lambda: Client().connect().call("notes.add"))), "ran notes.add")
        self.assertEqual(r.Rlabel_get_text(self.status), "1 note")
        self.assertEqual(self.senders, [self.window])
        self.assertNotIn("app.quit", self.tools())

    def test_the_tools_follow_binding_and_unbinding(self):
        r.Rmcp_expose_actions("notes.")
        start()
        r.Raction_bind("notes.pin", lambda *a: None)
        r.Raction_set_description("notes.pin", "Pin a note")   # set after binding, still seen
        r.Raction_unbind("notes.clear")
        self.settle()
        tools = self.tools()
        self.assertEqual(sorted(tools), ["notes.add", "notes.pin"])
        self.assertEqual(tools["notes.pin"]["description"], "Pin a note")

    def test_the_apps_own_tool_wins(self):
        r.Rmcp_expose_actions("notes.")
        add("notes.add", lambda c, t, a: reply(c, "the app's own"))
        start()
        self.assertEqual(text_of(on_client(lambda: Client().connect().call("notes.add"))), "the app's own")
        r.Rmcp_tool_remove("notes.add")   # the action comes back
        self.assertEqual(text_of(on_client(lambda: Client().connect().call("notes.add"))), "ran notes.add")

    def test_one_action_on_purpose_built_ins_included(self):
        self.assertTrue(r.Rmcp_expose_action("theme.dark", "Switch to the dark theme",
                                             r.R_MCP_HINT_IDEMPOTENT | r.R_MCP_HINT_CLOSED_WORLD))
        self.assertTrue(r.Rmcp_expose_action("notes.export", None, 0))   # nothing handles it
        self.assertFalse(r.Rmcp_expose_action("not a name", None, 0))
        start()
        tools = self.tools()
        self.assertEqual(sorted(tools), ["notes.export", "theme.dark"])
        self.assertEqual(tools["theme.dark"]["annotations"]["idempotentHint"], True)
        self.assertEqual(text_of(on_client(lambda: Client().connect().call("theme.dark"))), "ran theme.dark")
        failed = on_client(lambda: Client().connect().call("notes.export"))
        self.assertTrue(failed["isError"])
        self.assertEqual(text_of(failed), "nothing handles the action notes.export")

    def test_removing_every_tool_forgets_the_actions(self):
        r.Rmcp_expose_actions(None)
        r.Rmcp_tool_remove_all()
        r.Raction_bind("notes.later", lambda *a: None)
        self.settle()
        start()
        self.assertEqual(self.tools(), {})


NOTES_UI = """
<Window Title="Notes" Width="320" Height="240">
    <VBox>
        <TextInput Name="note" AccessibleName="New note" OnCommitted="notes.add"/>
        <Button Text="Add" OnClicked="notes.add"/>
        <ListView Name="notes" AccessibleName="Notes"/>
        <Button Name="delete" Text="Delete" Enabled="false" OnClicked="notes.delete"/>
    </VBox>
</Window>"""


def notes_app():
    """The notepad every language's tests drive: add a note, select it, delete it."""
    window = r.Rwindow_create_from_xml(NOTES_UI)
    note, notes, delete = (r.Rwidget_find(window, name) for name in ("note", "notes", "delete"))

    def add(*_):
        text = r.Rtext_input_get_text(note)
        if text:
            r.Rlist_view_add_item(notes, text)
            r.Rtext_input_set_text(note, "")

    def select(*_):
        r.Rwidget_set_enabled(delete, r.Rlist_view_get_selected(notes) >= 0)

    def remove(*_):
        r.Rlist_view_remove_item(notes, r.Rlist_view_get_selected(notes))
        r.Rlist_view_set_selected(notes, -1)
        r.Rwidget_set_enabled(delete, False)

    r.Raction_bind("notes.add", add)
    r.Raction_bind("notes.delete", remove)
    # (a ListView's action is fired on activation only, so the selection is heard here)
    r.Rlist_view_set_selection_changed_callback(notes, select)
    r.Rwait_idle(20)
    return window, notes


class UiToolsTest(unittest.TestCase):
    """ui.*, on Raven's UI driver: an agent sees and drives the notepad as a user would."""

    def setUp(self):
        desc = r.RInitDesc()
        desc.backend = r.R_BACKEND_NULL
        desc.headless = 1
        self.assertTrue(r.Rinit_ex(desc))
        logs.clear()
        self.window, self.notes = notes_app()

    def tearDown(self):
        r.Rmcp_stop()
        r.Rmcp_tool_remove_all()
        r.Rshutdown()

    def call(self, tool, arguments=None):
        return on_client(lambda: Client().connect().call(tool, arguments))

    def text(self, tool, arguments=None):
        result = self.call(tool, arguments)
        self.assertFalse(result.get("isError"), result)
        return text_of(result)

    def failure(self, tool, arguments=None):
        result = self.call(tool, arguments)
        self.assertTrue(result.get("isError"), result)
        return text_of(result)

    def tools(self):
        listed = on_client(lambda: Client().connect().request("tools/list"))["result"]["tools"]
        return {tool["name"]: tool for tool in listed}

    def test_off_unless_asked_for(self):
        start()
        self.assertEqual(self.tools(), {})
        r.Rmcp_stop()
        start(ui_tools=1)
        tools = self.tools()
        self.assertEqual(sorted(tools), ["ui.click", "ui.find", "ui.key", "ui.screenshot", "ui.tree", "ui.type"])
        for name in ("ui.tree", "ui.find", "ui.screenshot"):
            self.assertEqual(tools[name]["annotations"], {"readOnlyHint": True, "openWorldHint": False})
        for name in ("ui.click", "ui.type", "ui.key"):
            self.assertNotIn("annotations", tools[name])   # a click may destroy
        self.assertEqual(tools["ui.type"]["inputSchema"]["required"], ["selector", "text"])
        self.assertIn("role=button", tools["ui.click"]["description"])

    def test_the_outline(self):
        r.Rlist_view_add_item(self.notes, "buy milk")
        r.Rlist_view_add_item(self.notes, "call Oscar")
        r.Rwait_idle(20)
        start(ui_tools=1)
        self.assertEqual(self.text("ui.tree"), "\n".join([
            '- window "Notes"',
            '  - text-field "New note"',
            '  - button "Add"',
            '  - list "Notes"',
            '    - list-item "buy milk"',
            '    - list-item "call Oscar"',
            '  - button "Delete" [disabled]',
        ]))
        self.assertEqual(self.text("ui.tree", {"window": "Notes", "under": "role=list", "depth": 0}), '- list "Notes"')
        self.assertIn('no window titled "Elsewhere"; the open ones: "Notes"', self.failure("ui.tree", {"window": "Elsewhere"}))

    def test_typing_then_enter_adds_a_note(self):
        start(ui_tools=1)
        typed = self.text("ui.type", {"selector": 'role=text-field name="New note"', "text": "buy milk"})
        self.assertIn('  - text-field "New note" text="buy milk" [focused]', typed.split("\n"))
        added = self.text("ui.key", {"key": "Enter"})   # to what has the focus
        self.assertIn('    - list-item "buy milk"', added.split("\n"))
        self.assertIn('  - text-field "New note" [focused]', added.split("\n"))
        self.assertEqual(r.Rlist_view_get_item_count(self.notes), 1)
        self.assertFalse(any("still busy" in m for _, m in logs))

    def test_click_select_and_delete(self):
        r.Rlist_view_add_item(self.notes, "buy milk")
        r.Rwait_idle(20)
        start(ui_tools=1)
        self.assertEqual(self.failure("ui.click", {"selector": "role=button name=Delete"}),
                         'role=button name="Delete" is disabled')
        selected = self.text("ui.click", {"selector": 'role=list-item text="buy milk"'})
        self.assertIn('    - list-item "buy milk" [focused selected]', selected.split("\n"))
        self.assertIn('  - button "Delete"', selected.split("\n"))
        self.assertEqual(self.text("ui.click", {"selector": "role=button name=Delete", "snapshot": False}),
                         'clicked role=button name="Delete"')
        self.assertEqual(r.Rlist_view_get_item_count(self.notes), 0)

    def test_find(self):
        start(ui_tools=1)
        self.text("ui.type", {"selector": "role=text-field", "text": "eggs", "snapshot": False})
        found = self.text("ui.find", {"selector": "role=text-field"}).split("\n")
        self.assertEqual(found[:3], ['role=text-field name="New note"', 'text: "eggs"', "states: focused"])
        self.assertRegex(found[3], r'^bounds: x=0 y=0 width=320 height=\d+, logical units in the window "Notes"$')

    def test_failures_are_the_drivers_words(self):
        start(ui_tools=1)
        self.assertEqual(self.failure("ui.click", {"selector": "role=button"}), "2 accessibles with role=button, expected one")
        self.assertEqual(self.failure("ui.find", {"selector": 'text="Save"'}), 'no accessible with text="Save"')
        self.assertEqual(self.failure("ui.find"), "selector is required")
        self.assertEqual(self.failure("ui.tree", {"depth": "two"}), "depth must be a whole number")
        self.assertIn('"ctrl+banana" is not a key', self.failure("ui.key", {"key": "ctrl+banana"}))

    def test_keys_and_modifiers(self):
        start(ui_tools=1)
        self.text("ui.type", {"selector": "role=text-field", "text": "milk", "snapshot": False})
        self.text("ui.key", {"key": "ctrl+a", "selector": "role=text-field", "snapshot": False})
        self.text("ui.key", {"key": "Backspace"})
        self.assertEqual(r.Rtext_input_get_text(r.Rwidget_find(self.window, "note")), "")

    def test_a_long_outline_is_cut(self):
        for i in range(600):
            r.Rlist_view_add_item(self.notes, "note %d" % i)
        r.Rwait_idle(20)
        start(ui_tools=1)
        lines = self.text("ui.tree").split("\n")
        self.assertEqual(len(lines), 501)
        self.assertEqual(lines[-1], "- … 105 more accessibles left out: narrow it with under (a selector) or depth")

    def test_a_screenshot(self):
        start(ui_tools=1)
        result = self.call("ui.screenshot", {"window": "Notes"})
        self.assertFalse(result.get("isError"), result)
        image = result["content"][0]
        self.assertEqual((image["type"], image["mimeType"]), ("image", "image/png"))
        png = base64.b64decode(image["data"])
        self.assertEqual(png[:8], b"\x89PNG\r\n\x1a\n")
        width, height = struct.unpack(">II", png[16:24])
        scale = r.Rwindow_get_scale(self.window)
        self.assertEqual((width, height), (round(320 * scale), round(240 * scale)))

    def test_the_apps_own_tool_wins_and_stop_withdraws(self):
        add("ui.click", lambda c, t, a: reply(c, "the app's own"))
        start(ui_tools=1)
        self.assertTrue(any("ui.click is the app's own tool" in m for _, m in logs))
        self.assertEqual(self.text("ui.click", {"selector": "role=button name=Add"}), "the app's own")
        r.Rmcp_tool_remove("ui.click")   # the UI tool comes back
        self.assertEqual(self.text("ui.click", {"selector": "role=button name=Add", "snapshot": False}),
                         'clicked role=button name="Add"')
        r.Rmcp_stop()
        start()
        self.assertEqual(self.tools(), {})


class AcrossRunsTest(unittest.TestCase):
    def test_tools_left_from_an_earlier_run_are_refused(self):
        desc = r.RInitDesc()
        desc.backend = r.R_BACKEND_NULL
        desc.headless = 1
        r.Rinit_ex(desc)
        self.assertTrue(add("left", lambda c, t, a: None))
        r.Rshutdown()  # without Rmcp_tool_remove_all
        r.Rinit_ex(desc)
        try:
            self.assertFalse(start())
            self.assertFalse(add("next", lambda c, t, a: None))
            r.Rmcp_tool_remove_all()
            self.assertTrue(add("next", lambda c, t, a: None))
        finally:
            r.Rmcp_stop()
            r.Rmcp_tool_remove_all()
            r.Rshutdown()


if __name__ == "__main__":
    unittest.main(verbosity=2)
