"""
Raven MCP in Python: an app's own tools for agents, over raven.py's Rmcp_ calls.

    from typing import Annotated
    from raven_mcp import mcp, Hint, Call

    @mcp.tool("notes.add", "Add a note to the list", hints={Hint.NOT_DESTRUCTIVE, Hint.CLOSED_WORLD})
    def add_note(text: Annotated[str, "The note's text"], pinned: bool = False) -> str:
        notes.append(text)
        return "added note %d" % len(notes)

    @mcp.tool("notes.list", "The notes, in order", hints={Hint.READ_ONLY, Hint.CLOSED_WORLD})
    def list_notes() -> list:
        return notes                      # a list or dict replies as JSON

    @mcp.tool("notes.export", "Export the notes to a file")
    def export(path: str, call: Call):    # takes `call`: replies later, from any thread
        threading.Thread(target=lambda: (write(path, notes), call.reply("exported"))).start()

    mcp.start()                           # RMcpConfig's fields as keyword arguments
    ...                                   # the app's loop
    mcp.stop()                            # before Rshutdown

- The schema comes from the signature: str, int, float, bool, list (list[str] too),
  dict, an Enum, and Optional. A default makes a parameter optional, and
  Annotated[..., "..."] gives a description. Arguments arrive as keyword arguments.
- The return value is the reply: a str is text, a dict or a list is JSON, None is
  an empty text. Raising fails the call with the exception's message.
- A tool that takes a `call` parameter replies through call.reply(...) or
  call.fail(...), from any thread. A second reply raises, and a Call collected
  without one fails the call itself ("the tool never replied").
- Tools run on Raven's UI thread, as its actions do; add, remove, start and stop
  are called from it too. mcp.remove_tool(name) withdraws a tool; the module
  keeps every tool's function alive until then.

Raven's own actions need nothing here: r.Rmcp_expose_actions("notes.") publishes
them. The Raven MCP library is found as raven.py finds it (RAVEN_MCP_LIBRARY,
then beside libraven); without it, the first call raises OSError.
"""

import ctypes
import enum
import inspect
import json
import os
import threading
import typing

import raven as r

__all__ = ["mcp", "Mcp", "Hint", "Call"]


class Hint(enum.IntFlag):
    """What a tool says about itself (RMcpToolHint): each one makes it look safer to an agent."""
    READ_ONLY = r.R_MCP_HINT_READ_ONLY
    NOT_DESTRUCTIVE = r.R_MCP_HINT_NOT_DESTRUCTIVE
    IDEMPOTENT = r.R_MCP_HINT_IDEMPOTENT
    CLOSED_WORLD = r.R_MCP_HINT_CLOSED_WORLD


def _reply(pointer, value):
    """Sends a return value as the call's reply: text for a str (and None), JSON for anything else."""
    if value is None:
        r.Rmcp_call_reply(pointer, "")
    elif isinstance(value, str):
        r.Rmcp_call_reply(pointer, value)
    else:
        text = json.dumps(value, default=lambda o: o.value if isinstance(o, enum.Enum) else str(o))
        result = {"content": [{"type": "text", "text": text}]}
        if isinstance(value, dict):
            result["structuredContent"] = value
        r.Rmcp_call_reply_json(pointer, json.dumps(result))


def _message(error):
    return str(error) or type(error).__name__


class Call:
    """A call to answer later, from any thread: exactly one reply() or fail()."""

    def __init__(self, pointer, tool):
        self._pointer = pointer
        self._lock = threading.Lock()
        self.tool = tool

    def _take(self):
        with self._lock:
            pointer, self._pointer = self._pointer, None
        if pointer is None:
            raise RuntimeError("the call to %s was already answered" % self.tool)
        return pointer

    def reply(self, value=None):
        """Replies, as a tool's return value does: a str is text, a dict or a list is JSON."""
        _reply(self._take(), value)

    def fail(self, message):
        """Fails the call with a message the agent reads."""
        r.Rmcp_call_fail(self._take(), str(message))

    @property
    def answered(self):
        return self._pointer is None

    @property
    def cancelled(self):
        """True once the agent cancelled the call or Raven MCP stopped: long work can stop early, but still replies."""
        pointer = self._pointer
        return pointer is None or bool(r.Rmcp_call_is_cancelled(pointer))

    def __del__(self):
        pointer = getattr(self, "_pointer", None)
        if pointer is not None:
            try:
                r.Rmcp_call_fail(pointer, "the tool never replied")
            except Exception:  # interpreter shutdown
                pass


# ---- the schema, from a function's signature

_SIMPLE = {str: "string", bool: "boolean", int: "integer", float: "number"}


def _schema_of_type(annotation, where):
    """A JSON Schema for a type hint, and how to turn a JSON value back into it (or None)."""
    description = None
    if typing.get_origin(annotation) is typing.Annotated:
        annotation, *extras = typing.get_args(annotation)
        texts = [extra for extra in extras if isinstance(extra, str)]
        description = " ".join(texts) or None
    schema, convert = _schema_of_plain(annotation, where)
    if description:
        schema = dict(schema, description=description)
    return schema, convert


def _schema_of_plain(annotation, where):
    if annotation is inspect.Parameter.empty or annotation is typing.Any:
        return {}, None
    origin, args = typing.get_origin(annotation), typing.get_args(annotation)
    if origin is typing.Union or (origin is not None and getattr(origin, "__name__", "") == "UnionType"):
        others = [arg for arg in args if arg is not type(None)]
        if len(others) != 1:
            raise TypeError("%s: only Optional[X] unions are supported" % where)
        schema, convert = _schema_of_type(others[0], where)
        nullable = dict(schema)
        if "type" in nullable:
            nullable["type"] = [nullable["type"], "null"]
        return nullable, (None if convert is None else lambda value: None if value is None else convert(value))
    if annotation in _SIMPLE:
        return {"type": _SIMPLE[annotation]}, None
    if annotation is list or origin is list:
        if not args:
            return {"type": "array"}, None
        items, convert = _schema_of_type(args[0], where)
        return {"type": "array", "items": items}, (None if convert is None else lambda values: [convert(v) for v in values])
    if annotation is dict or origin is dict:
        return {"type": "object"}, None
    if isinstance(annotation, type) and issubclass(annotation, enum.Enum):
        values = [member.value for member in annotation]
        schema = {"enum": values}
        kinds = {_SIMPLE.get(type(value)) for value in values}
        if len(kinds) == 1 and None not in kinds:
            schema["type"] = kinds.pop()
        return schema, annotation
    raise TypeError("%s: a tool's arguments are str, int, float, bool, list, dict, an Enum or Optional, not %r"
                    % (where, annotation))


class _Tool:
    """A function as a tool: its schema, and how its arguments arrive."""

    def __init__(self, name, function):
        self.name, self.function = name, function
        hints = typing.get_type_hints(function, include_extras=True)
        self.properties, self.required, self.converters = {}, [], {}
        self.takes_call = False
        for parameter in inspect.signature(function).parameters.values():
            where = "%s, argument %s" % (name, parameter.name)
            if parameter.kind in (parameter.VAR_POSITIONAL, parameter.VAR_KEYWORD, parameter.POSITIONAL_ONLY):
                raise TypeError("%s: a tool's arguments are named, one by one" % where)
            if parameter.name == "call":
                self.takes_call = True
                continue
            # as written; resolved only when it is a string (a default of None doesn't make it Optional)
            annotation = parameter.annotation
            if isinstance(annotation, str):
                annotation = hints.get(parameter.name, annotation)
            schema, convert = _schema_of_type(annotation, where)
            self.properties[parameter.name] = schema
            if convert is not None:
                self.converters[parameter.name] = convert
            if parameter.default is inspect.Parameter.empty:
                self.required.append(parameter.name)

    @property
    def schema(self):
        schema = {"type": "object", "properties": self.properties}
        if self.required:
            schema["required"] = self.required
        return schema

    def arguments(self, arguments_json):
        """The call's arguments as keyword arguments; raises ValueError with what is wrong."""
        arguments = json.loads(arguments_json or "{}")
        if not isinstance(arguments, dict):
            raise ValueError("the arguments are not an object")
        unknown = sorted(set(arguments) - set(self.properties))
        if unknown:
            raise ValueError("unknown argument%s: %s" % ("s" if len(unknown) > 1 else "", ", ".join(unknown)))
        missing = [name for name in self.required if name not in arguments]
        if missing:
            raise ValueError("missing argument%s: %s" % ("s" if len(missing) > 1 else "", ", ".join(missing)))
        for key, convert in self.converters.items():
            if key in arguments:
                try:
                    arguments[key] = convert(arguments[key])
                except (ValueError, TypeError) as e:
                    raise ValueError("argument %s: %s" % (key, _message(e)))
        return arguments

    def __call__(self, pointer, arguments_json):
        """Answers one call, on the UI thread: never raises (a ctypes callback can't)."""
        call = Call(pointer, self.name) if self.takes_call else None
        try:
            arguments = self.arguments(arguments_json)
            if call is not None:
                arguments["call"] = call
            result = self.function(**arguments)
            if call is None:
                _reply(pointer, result)
        except Exception as e:
            if call is None:
                r.Rmcp_call_fail(pointer, _message(e))
            elif not call.answered:
                call.fail(_message(e))


class Mcp:
    """Raven MCP for one app: its tools and its server. The module's `mcp` is the one to use."""

    def __init__(self):
        self._tools = {}      # name -> (the tool, its ctypes callback): alive until removed
        self._used = False    # the Raven MCP library was called: stop() has something to do
        self._config = None   # the RMcpConfig, and what it points to, while the server runs

    def tool(self, name, description=None, title=None, hints=()):
        """A decorator: publishes the function as a tool. The function is returned as it is."""
        def publish(function):
            self.add_tool(name, function, description, title, hints)
            return function
        return publish

    def add_tool(self, name, function, description=None, title=None, hints=()):
        """Publishes a function as a tool, replacing one of the same name. Raises when Raven MCP refuses it."""
        tool = _Tool(name, function)
        flags = 0
        for hint in ([hints] if isinstance(hints, int) else hints):
            flags |= int(hint)
        if description is None:
            description = inspect.getdoc(function)
        desc = r.RMcpToolDesc(name=name.encode(), title=title.encode() if title else None,
                              description=description.encode() if description else None,
                              input_schema_json=json.dumps(tool.schema).encode(), hints=flags)
        callback = r.RMcpToolCallback(lambda pointer, tool_name, arguments, user: tool(pointer, arguments and arguments.decode()))
        self._used = True
        if not r.Rmcp_tool_add(desc, callback):
            raise ValueError("Raven MCP refused the tool %s (its log says why)" % name)
        self._tools[name] = (tool, callback)

    def remove_tool(self, name):
        """Withdraws a tool; its calls not yet answered fail ("the tool was removed")."""
        r.Rmcp_tool_remove(name)
        self._tools.pop(name, None)

    def start(self, name=None, version=None, instructions=None, port=0, token=None, ui_tools=False,
              log=None, on_session=None):
        """Starts serving the tools (Rmcp_start), with RMcpConfig's fields. log(severity, message) and
        on_session(connected, client) are Python functions. Returns False, logged, when it can't."""
        self._used = True
        return bool(r.Rmcp_start(self._configure(name, version, instructions, port, token, ui_tools, log, on_session)))

    def start_from_environment(self, name=None, **fields):
        """Starts only when RAVEN_MCP=1, for an app that offers Raven MCP to developers:
        RAVEN_MCP_PORT and RAVEN_MCP_UI=1 (the ui.* tools) override the fields. Returns whether it runs.
        Without RAVEN_MCP=1 it returns False before loading the Raven MCP library, so the app runs
        where the library isn't."""
        if os.environ.get("RAVEN_MCP") != "1":
            return False
        self._used = True
        config = self._configure(name, fields.pop("version", None), fields.pop("instructions", None),
                                 fields.pop("port", 0), fields.pop("token", None), fields.pop("ui_tools", False),
                                 fields.pop("log", None), fields.pop("on_session", None))
        if fields:
            raise TypeError("unknown field%s: %s" % ("s" if len(fields) > 1 else "", ", ".join(sorted(fields))))
        return bool(r.Rmcp_config_from_environment(config)) and bool(r.Rmcp_start(config))

    def _configure(self, name, version, instructions, port, token, ui_tools, log, on_session):
        def text(value):
            return value.encode() if isinstance(value, str) else value
        config = r.RMcpConfig(name=text(name), version=text(version), instructions=text(instructions),
                              port=port, token=text(token), ui_tools=1 if ui_tools else 0)
        if log is not None:
            config.log = r.RMcpLogCallback(lambda severity, message, user: log(r.RSeverity(severity), message.decode()))
        if on_session is not None:
            config.on_session = r.RMcpSessionCallback(lambda connected, client, user: on_session(bool(connected), client.decode()))
        self._config = config  # its callbacks stay alive while the server may call them
        return config

    def stop(self):
        """Stops serving and withdraws every tool (Rmcp_stop, Rmcp_tool_remove_all): before Rshutdown.
        Does nothing when Raven MCP was never used, so an app can call it unconditionally."""
        if not self._used:
            return
        r.Rmcp_stop()
        r.Rmcp_tool_remove_all()
        self._tools.clear()

    @property
    def url(self):
        """The address being served, or None when stopped."""
        return r.Rmcp_get_url()

    @property
    def tools(self):
        """The names of the tools this module published."""
        return sorted(self._tools)


mcp = Mcp()
