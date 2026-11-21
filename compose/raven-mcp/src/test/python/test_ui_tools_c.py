"""
The UI tools from C: src/test/c/notes.c, the notepad as a C app, runs in its own process with
RAVEN_MCP=1 and RAVEN_MCP_UI=1, and an MCP client adds a note, selects it and deletes it with ui.*,
as an agent would, then ends the app with app.quit.

Run by `./gradlew :raven-mcp:cTest` (part of `check`), which builds the app and sets NOTES_APP.
"""

import os
import subprocess
import time
import unittest
import urllib.error

from test_raven_mcp import Client, text_of

PORT = 18777   # (not the in-process tests' ports, which they bind themselves)
TOKEN = "c-test-token"


class NotesInC(unittest.TestCase):
    def setUp(self):
        env = dict(os.environ, RAVEN_MCP="1", RAVEN_MCP_UI="1", RAVEN_MCP_PORT=str(PORT))
        self.app = subprocess.Popen([os.environ["NOTES_APP"], TOKEN], env=env,
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.url = "http://127.0.0.1:%d/mcp" % PORT
        deadline = time.monotonic() + 10
        while True:
            try:
                self.client = Client(token=TOKEN, url=self.url).connect()
                return
            except (urllib.error.URLError, ConnectionError):
                if self.app.poll() is not None or time.monotonic() > deadline:
                    self.fail("the app didn't serve: " + self.app.stderr.read().decode())
                time.sleep(0.05)

    def tearDown(self):
        if self.app.poll() is None:
            self.app.kill()
        self.app.wait(5)
        self.app.stdout.close()
        self.app.stderr.close()

    def text(self, tool, arguments=None):
        result = self.client.call(tool, arguments)
        self.assertFalse(result.get("isError"), result)
        return text_of(result)

    def test_an_agent_adds_and_deletes_a_note(self):
        listed = sorted(t["name"] for t in self.client.request("tools/list")["result"]["tools"])
        self.assertEqual(listed, ["app.quit", "ui.click", "ui.find", "ui.key", "ui.screenshot", "ui.tree", "ui.type"])
        self.assertIn('  - button "Delete" [disabled]', self.text("ui.tree").split("\n"))

        self.text("ui.type", {"selector": 'role=text-field name="New note"', "text": "buy milk", "snapshot": False})
        self.assertIn('    - list-item "buy milk"', self.text("ui.key", {"key": "Enter"}).split("\n"))
        selected = self.text("ui.click", {"selector": 'role=list-item text="buy milk"'}).split("\n")
        self.assertIn('  - button "Delete"', selected)
        deleted = self.text("ui.click", {"selector": "role=button name=Delete"}).split("\n")
        self.assertEqual(deleted, [
            '- window "Notes"',
            '  - text-field "New note"',
            '  - button "Add"',
            '  - list "Notes"',
            '  - button "Delete" [disabled]',
        ])

        try:
            self.client.call("app.quit")
        except (urllib.error.URLError, ConnectionError):
            pass   # the server may close before it replies
        self.assertEqual(self.app.wait(10), 0)
        self.assertEqual(self.app.stdout.read(), b"")
        self.assertEqual(self.app.stderr.read(), b"")


if __name__ == "__main__":
    unittest.main(verbosity=2)
