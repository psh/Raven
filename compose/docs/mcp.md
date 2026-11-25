# Raven MCP

Raven MCP lets an agent use a running Raven app over the
[Model Context Protocol](https://modelcontextprotocol.io). The app publishes **tools**, and an agent
lists and calls them. Every **call** gets exactly one **reply** from the app. The words are defined in
[`GLOSSARY.md`](../GLOSSARY.md).

Every language uses the same C contract, `raven_mcp.h`. Python, Rust and the other bindings wrap it,
and compose-raven reaches the same registry through its Kotlin module. This document is that contract.
It is being designed under the map [MCP middleware for Raven apps](issues/0145-mcp-middleware-for-raven-apps.md),
and the sections marked *open* wait on that map's tickets.

## The header

```c
/**
 * Raven MCP
 * An agent's view of a running Raven app, over the Model Context Protocol.
 *
 * raven_mcp.h : the server, tools, calls and replies.
 *
 * Not included by Raven.h: it ships with the Raven MCP library, beside libraven.
 *
 * Words:
 *   - a tool is what the app publishes for an agent to call: a name, a
 *     description, the arguments it takes, and the app's function that answers it
 *   - a call is one use of a tool, with its arguments
 *   - a reply is the app's answer to a call; every call gets exactly one,
 *     even one the agent has cancelled
 *
 * Threading: every function must be called from Raven's UI thread, except the
 * Rmcp_call_* functions, which are thread-safe. Tool functions are called on
 * the UI thread, one call at a time, in the order the calls arrived; they are
 * called inside the frames of a modal dialog too.
 *
 * Strings are UTF-8 and copied by the library. Strings the library passes to a
 * function live only until it returns.
 */

#ifndef RAVEN_MCP_H
#define RAVEN_MCP_H

#include "raven_core.h"

#define RAVEN_MCP_API_VERSION "0.1"

/** Raven MCP library export / import: its own library (its own DLL on Windows). */
#if defined(_WIN32)
	#if defined(RAVEN_MCP_BUILD_DLL)
		#define RAVEN_MCP_API __declspec(dllexport)
	#else
		#define RAVEN_MCP_API __declspec(dllimport)
	#endif
#else
	#define RAVEN_MCP_API
#endif

#ifdef __cplusplus
extern "C" {
#endif

	/* ============================================================================
	 *  SERVER
	 * ============================================================================ */

	/**
	 * Receives what Raven MCP and the libraries under it have to say, from any
	 * thread. Information arrives as R_SEVERITY_WEAK_WARNING. Nothing Raven MCP
	 * does is written to stdout.
	 */
	typedef void (*RMcpLogCallback)(RSeverity severity, const char* message, void* user_data);

	/** An agent connected (R_TRUE) or disconnected (R_FALSE); client is its name, as it gave it. */
	typedef void (*RMcpSessionCallback)(int connected, const char* client, void* user_data);

	/**
	 * Optional settings for Rmcp_start. A zero-initialized structure selects the
	 * default for every field, so new fields can be added without breaking code.
	 */
	typedef struct RMcpConfig {
		/** The app's name, as agents see it, and its token file's. NULL: the program's name. */
		const char* name;
		/** The app's version, as agents see it. NULL: "0". */
		const char* version;
		/** A hint to the agent about how to use the app's tools. May be NULL. */
		const char* instructions;
		/**
		 * The port to listen on, on the loopback interface only. 0 = 8765. A port
		 * already taken fails Rmcp_start: there is no fallback, because the agent was
		 * configured with this address.
		 */
		int port;
		/** Where messages go. NULL: nowhere, except errors, which go to stderr. */
		RMcpLogCallback log;
		void* log_user_data;
		/**
		 * The bearer token every request must carry. NULL: the app's stored token, made once
		 * per app and user in ~/.raven/mcp/<name>.json (mode 0600, with the URL). "": no token,
		 * which is logged as a warning.
		 */
		const char* token;
		/** Called on the UI thread as agents connect and disconnect. May be NULL. */
		RMcpSessionCallback on_session;
		void* session_user_data;
		/**
		 * R_TRUE offers the UI tools (ui.*) built on Raven's UI driver. A compose-raven app leaves
		 * it R_FALSE: it adds its own set, over Compose semantics. RAVEN_MCP_UI=1 sets it.
		 */
		int ui_tools;
	} RMcpConfig;

	/**
	 * @brief Fills `config` from the environment, for an app that offers Raven MCP to
	 * developers only: RAVEN_MCP=1 turns it on, RAVEN_MCP_PORT sets the port and
	 * RAVEN_MCP_UI=1 the UI tools. Other fields are left as they are.
	 * @return R_TRUE when RAVEN_MCP=1, so the caller starts the server; R_FALSE otherwise.
	 */
	RAVEN_MCP_API int Rmcp_config_from_environment(RMcpConfig* config);

	/**
	 * @brief Starts serving the tools at Rmcp_get_url (Streamable HTTP). Call it
	 * after Rinit. There is one server in a process. Tools may be added before it
	 * starts or while it runs.
	 * RAVEN_MCP=0 in the environment makes it refuse, whatever the code asks: a
	 * kill switch for whoever runs the app. The app's SIGINT and SIGTERM handlers
	 * are left as they were.
	 * @param config Settings, or NULL for the defaults.
	 * @return R_FALSE, with the reason logged, when the server already runs, the
	 * port is taken, or tools are left from an earlier run of Raven.
	 */
	RAVEN_MCP_API int Rmcp_start(const RMcpConfig* config);

	/**
	 * @brief Closes the port and every session, and cancels the calls not yet
	 * replied to; each still needs its reply. Call it before Rshutdown. The
	 * server can start again. A few idle threads may stay in the process.
	 */
	RAVEN_MCP_API void Rmcp_stop(void);

	/** @return The address being served ("http://127.0.0.1:8765/mcp"), or NULL when stopped. */
	RAVEN_MCP_API const char* Rmcp_get_url(void);


	/* ============================================================================
	 *  TOOLS
	 * ============================================================================ */

	/**
	 * What a tool says about itself, for an agent to weigh before calling it.
	 * Each hint makes the tool look safer, so a tool with none is taken to be
	 * one that may destroy things and may reach outside the app. Hints are
	 * claims, not checks.
	 */
	typedef enum RMcpToolHint {
		/** Changes nothing. */
		R_MCP_HINT_READ_ONLY       = 1 << 0,
		/** Changes only by adding: never deletes or overwrites. */
		R_MCP_HINT_NOT_DESTRUCTIVE = 1 << 1,
		/** Calling it again with the same arguments does nothing more. */
		R_MCP_HINT_IDEMPOTENT      = 1 << 2,
		/** Touches nothing outside the app: no network, no other programs. */
		R_MCP_HINT_CLOSED_WORLD    = 1 << 3
	} RMcpToolHint;

	/**
	 * A tool, for Rmcp_tool_add. A zero-initialized structure selects the
	 * default for every field, so new fields can be added without breaking code.
	 */
	typedef struct RMcpToolDesc {
		/**
		 * Required: 1 to 128 characters of A-Z, a-z, 0-9, '_', '.' and '-'.
		 * Dotted by area ("notes.add"). Names starting with "ui." are the UI tools'.
		 */
		const char* name;
		/** A name for people. May be NULL. */
		const char* title;
		/** What the tool does, for the agent to choose it by. */
		const char* description;
		/**
		 * The arguments, as a JSON Schema object ({"type":"object","properties":...}),
		 * given to agents whole. NULL: no arguments. The library checks that it is a
		 * JSON object, not that calls follow it: the tool checks its arguments.
		 */
		const char* input_schema_json;
		/** RMcpToolHint values, or-ed. 0: none stated. */
		unsigned hints;
	} RMcpToolDesc;

	typedef struct RMcpCall RMcpCall;

	/**
	 * Answers a call: it replies at once, or hands the call to a worker thread
	 * that replies when done. It may call any Raven MCP function, Rmcp_stop
	 * included.
	 * @param tool The tool's name, so one function can answer several tools.
	 * @param arguments_json The arguments, a JSON object ("{}" when none).
	 */
	typedef void (*RMcpToolCallback)(RMcpCall* call, const char* tool, const char* arguments_json, void* user_data);

	/**
	 * @brief Publishes a tool, and tells the connected agents their list changed.
	 * A tool already published under the name is replaced, as Raction_bind
	 * replaces a function: calls that arrive from now on reach the new one.
	 * @return R_FALSE, with the reason logged, for an invalid name or schema, or
	 * when tools are left from an earlier run of Raven.
	 */
	RAVEN_MCP_API int Rmcp_tool_add(const RMcpToolDesc* desc, RMcpToolCallback callback, void* user_data);

	/**
	 * @brief Withdraws a tool. Its calls not yet given to its function fail
	 * ("the tool was removed"), so its user_data may be freed on return. Calls
	 * already given to it stay valid until replied to.
	 */
	RAVEN_MCP_API void Rmcp_tool_remove(const char* name);

	/**
	 * @brief Withdraws every tool. Call it, after Rmcp_stop, before Rshutdown:
	 * tools left from an earlier run of Raven make Rmcp_start and Rmcp_tool_add fail.
	 */
	RAVEN_MCP_API void Rmcp_tool_remove_all(void);

	/**
	 * @brief Publishes every bound action whose name starts with `prefix` (NULL: all) as a
	 * tool, kept in step as actions are bound and unbound: it takes Raction_set_list_callback's
	 * slot, and a change reaches agents a frame later, so a description set right after
	 * Raction_bind is the one they see. Built-in actions are never included, and a name that
	 * isn't a valid tool name is skipped and logged. No hints are claimed. A call fires the
	 * action from the focused window (else the first open one) and replies "ran <name>" once the
	 * app is idle, or after 2 s. A tool of the app's own, of the same name, wins while it
	 * exists. Rmcp_tool_remove_all forgets every exposed action.
	 */
	RAVEN_MCP_API void Rmcp_expose_actions(const char* prefix);

	/**
	 * @brief Publishes one action, bound or built-in, chosen on purpose. Called while nothing
	 * handles it (not bound, not a built-in), the tool fails: "nothing handles the action <name>".
	 * @param description NULL: the action's own (Raction_get_description), or a generic one.
	 * @param hints RMcpToolHint values, or-ed.
	 * @return R_FALSE, logged, for a name that isn't a valid tool name.
	 */
	RAVEN_MCP_API int Rmcp_expose_action(const char* name, const char* description, unsigned hints);


	/* ============================================================================
	 *  CALLS
	 * ============================================================================ */

	/*
	 * Every call gets exactly one reply, from any thread: Rmcp_call_reply,
	 * Rmcp_call_reply_json or Rmcp_call_fail. The reply frees the call, so the
	 * pointer must not be used after it. A second reply is logged and ignored.
	 * There is no timeout but the agent's own.
	 */

	/** @brief Replies with a text. Thread-safe. */
	RAVEN_MCP_API void Rmcp_call_reply(RMcpCall* call, const char* text);

	/**
	 * @brief Replies with a whole MCP CallToolResult: several items, images,
	 * structured content. Thread-safe.
	 * @param call_tool_result_json {"content":[...], "structuredContent":{...}}
	 */
	RAVEN_MCP_API void Rmcp_call_reply_json(RMcpCall* call, const char* call_tool_result_json);

	/**
	 * @brief Replies that the tool failed, with a message the agent reads and can
	 * act on (an MCP tool error, isError). Thread-safe.
	 */
	RAVEN_MCP_API void Rmcp_call_fail(RMcpCall* call, const char* message);

	/**
	 * @brief R_TRUE once the agent cancelled the call or Rmcp_stop ended it. Long
	 * work can stop early, but still replies: the library discards that reply.
	 * Thread-safe.
	 */
	RAVEN_MCP_API int Rmcp_call_is_cancelled(RMcpCall* call);

#ifdef __cplusplus
}
#endif

#endif /* RAVEN_MCP_H */
```

## A run, from start to end

```c
Rinit(R_BACKEND_VULKAN);
/* ... windows ... */
RMcpToolDesc add = {0};
add.name = "notes.add";
add.description = "Add a note to the app's window";
add.input_schema_json = "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},\"required\":[\"text\"]}";
add.hints = R_MCP_HINT_NOT_DESTRUCTIVE | R_MCP_HINT_CLOSED_WORLD;
Rmcp_tool_add(&add, on_add_note, notes);
Rmcp_start(NULL);                      /* http://127.0.0.1:8765/mcp */
while (Rwait_frame(-1.0)) {}
Rmcp_stop();
Rmcp_tool_remove_all();
Rshutdown();
```

```c
static void on_add_note(RMcpCall* call, const char* tool, const char* arguments_json, void* user_data) {
    char* text = read_text_argument(arguments_json);   /* the app's own JSON */
    if (!text || !text[0]) { Rmcp_call_fail(call, "a note needs text"); return; }
    notes_add(user_data, text);
    Rmcp_call_reply(call, "added");
}
```

An agent connects with, for example, `claude mcp add --transport http notes http://127.0.0.1:8765/mcp`.

## Why it is this shape

- **Descriptors, not long signatures.** `RMcpConfig` and `RMcpToolDesc` are zeroed to get the
  defaults, like `RInitDesc`, so fields can be added (a transport, an output schema, a token)
  without breaking callers.
- **JSON as strings.** Arguments, schemas and rich replies cross the boundary as JSON text, because
  every language can handle JSON and the header doesn't have to model MCP's types. `Rmcp_call_reply`
  covers the common case with plain text.
- **One rule for calls.** Exactly one reply, cancelled or not, and no server timeout, so a handle is
  never freed under a worker still holding it. Removing a tool fails the calls still queued for it,
  so freeing `user_data` after `Rmcp_tool_remove` is safe.
- **Hints only ever make a tool look safer.** MCP assumes a tool may destroy things and reach outside
  the app unless it says otherwise. A zeroed descriptor keeps that assumption, so an app has to claim
  safety on purpose.
- **A fixed port with no fallback.** People register an app with their agent once, by address, so
  the address must not drift.
- **Its own header and library.** An app that doesn't use MCP never sees it, and carries none of it.

## Building and shipping

Raven MCP is implemented on the MCP Kotlin SDK
([ADR 0003](adr/0003-raven-mcp-on-the-kotlin-sdk.md)), which the header hides from every caller.

- **One module, two products.** Raven MCP is one Kotlin/Native module, `compose/raven-mcp`, with no
  Compose in it. It holds the registry, the server and the calls, and it gives:
  - the **Raven MCP library** (`libraven_mcp.dylib`, `libraven_mcp.so`, later `raven_mcp.dll`),
    exporting `raven_mcp.h`, for C and every binding;
  - a **klib** for `compose-raven-mcp`, the opt-in module with `McpTool` and the `ui.*` tools,
    published with compose-raven's other modules (`io.github.psh`, in the BOM).

  The code exists once, and C and Kotlin apps reach the same registry. The module's cinterop names no
  library: the Raven MCP library links the shared libraven, and a compose app links the libraven it
  already has.
- **It needs the shared libraven** (`RAVEN_BUILD_SHARED=ON`), the one the app loaded, never a copy of
  its own. Every binding but C++ uses the shared libraven already. A C or C++ app that links libraven
  statically can't use Raven MCP yet.
- **Built by compose's Gradle build only.** Raven's CMake needs no Kotlin. Each per-host release job
  of compose-raven also attaches `raven-mcp-<target>.zip` to the GitHub release: the Raven MCP
  library, `raven_mcp.h`, and the shared libraven it was built and tested with. A C developer with no
  JDK downloads that.
- **Versions.** The Raven MCP library carries compose-raven's version. `RAVEN_MCP_API_VERSION` is the
  header's own. The library records the Raven it was built against, and `Rmcp_start` fails, with the
  reason logged, under an older one.
- **Targets**: `macosArm64` and `linuxX64`. There is no Intel Mac: the MCP Kotlin SDK has no
  `macosX64`, so Raven's universal macOS packages hold an arm64-only Raven MCP library. Windows
  (`mingwX64`) comes with compose-raven's Windows target.
- **The bindings** get `Rmcp_*` from `tools/bindgen`, like the rest of Raven's API. How they reach
  the Raven MCP library follows how each binding reaches libraven
  ([Raven MCP in every binding](issues/archive/0183-raven-mcp-in-every-binding.md)):
  - A binding that **loads** libraven at run time (Python, LuaJIT, Java, C#, Bun, Node) loads the
    Raven MCP library on the first `Rmcp_` call: from `RAVEN_MCP_LIBRARY`, or else from beside
    libraven. Where it is missing, that call says Raven MCP isn't available here, and the rest of
    the binding works.
  - A binding that **links** libraven (Rust, Go, Zig, Odin, C3, C++) puts `Rmcp_*` in an opt-in
    unit that links the Raven MCP library: Rust's cargo feature `mcp` (`RAVEN_MCP_LIB_DIR`; the
    idioms are the crate `raven-gui-mcp` on top), a
    package or file of its own elsewhere, and `raven_mcp.h` itself for C++. An app that doesn't opt
    in links none of it.

  The library's install name is `@rpath/libraven_mcp.dylib` (soname `libraven_mcp.so`), so a
  program that links it finds it by its own rpath. In the registries, Raven MCP is a separate, optional package beside each binding's
  (`pip install raven-gui[mcp]`, for example), so a plain install doesn't grow by its 6 MB.
- **Nothing runs until it is started.** Loading or linking Raven MCP starts no thread and opens no
  port; `Rmcp_start` does both.

## The UI tools

`ui.*` lets an agent see and drive an app's interface, the way Playwright's MCP server does for a web
page. It is **one contract for every Raven app**: compose-raven implements it over Compose semantics
(`compose-raven-ui-test`'s finders), and every other app gets it from the Raven MCP library itself,
built on the UI driver in Raven's C and switched on with `RMcpConfig.ui_tools`
([Driving a running app in any language](issues/archive/0164-driving-a-running-app-in-any-language.md)). An
agent drives a Python app and a compose app with the same tools and words. The tools are added with
`Rmcp_tool_add` like any app's tools, and are **off by default**, switched on separately from the
app's own tools (how: [Who may connect, and what they may do](#who-may-connect)).

### Selectors, not ids

Every tool says what it acts on with a **selector** in each call, in the language of Raven's UI
driver (`agent-skills.md`, "Driving a UI"): `role=button name="Add"`, `text="buy milk"`,
`widget=save_button`, states, `[n]`. compose-raven adds **`tag=`** for `Modifier.testTag`, which
matches nothing elsewhere. No ids are handed out between calls: recomposition replaces what an id
would point at.

### The tools

| Tool | Arguments | Replies | Hints |
|---|---|---|---|
| `ui.tree` | `window?` (title), `under?` (selector), `depth?` | the outline below | `READ_ONLY`, `CLOSED_WORLD` |
| `ui.find` | `selector` | role, name, text, value, states and bounds of the one match | `READ_ONLY`, `CLOSED_WORLD` |
| `ui.click` | `selector`, `snapshot?` (true) | the outline after the click | none: a click may destroy |
| `ui.type` | `selector`, `text`, `snapshot?` | the outline after typing | none |
| `ui.key` | `key` (`"Enter"`, `"ctrl+s"`, `"shift+Tab"`), `selector?`, `snapshot?` | the outline after the key | none |
| `ui.screenshot` | `window?` (title) | the window as a PNG (MCP image content) | `READ_ONLY`, `CLOSED_WORLD` |

- **Actions are real input**: a pointer move, press and release at the target's centre, typing after
  focusing it, a key on the target or the focused accessible. A disabled button can't be pressed by
  an agent either. An agent's clicks move hover and focus as a user's would.
- **Actions wait until the app is idle**, checked once a frame for up to 2 s on Raven's clock (about
  120 frames at 60 Hz), then reply with the window's outline, so the agent sees the result without
  another call. `snapshot: false` skips the outline. If the app is still busy at the limit, the reply
  still succeeds and says so. The limit is timed, not counted: headless frames take no time, so an
  animation that settles in a dozen frames on a display takes hundreds there.
- **Failures** reply through `Rmcp_call_fail` with the finder's own message, the words tests read
  too: `no node with text "Save"`, `2 nodes with role=button, expected one`, a disabled target.
- **Keys** use Raven's key names, with `ctrl`, `shift`, `alt` and `meta` (or `cmd`) as modifiers.
  Without a `selector`, the key goes to what has the focus in the focused window, else to the window.
- **Screenshots** capture the window's last frame as rendered, in device pixels, encoded with
  `Rimage_encode_png` and returned through `Rmcp_call_reply_json`. They never touch anything outside
  the app's window. Without `window`, the focused window is captured.

### The outline

`ui.tree` and the actions' replies describe the window as an indented outline, as Playwright's
accessibility snapshot does: a role, the name in quotes, states in brackets, and `tag=` where one is
set. Every line reads as a selector.

```
- window "Notes"
  - text-field "New note" [focused]
  - button "Add"
  - list "Notes"
    - list-item "buy milk"
    - list-item "call Oscar"
  - button "Delete" [disabled]
```

It stops at 500 lines and says how many accessibles it left out, so the agent narrows it with
`under` and `depth`. Bounds come only from `ui.find`.

- **What a field shows** follows its name as `text="…"`, as the driver's `text=` reads it:
  `- text-field "New note" text="buy milk" [focused]`. Texts past 80 characters or a line are cut.
- **The states** are the ones a selector names: `focused`, `checked`, `selected`, `expanded`,
  `disabled`.
- **A closed menu**, or anything expandable and not expanded, is shown without what it holds: its
  items are there to be found and clicked, not to be read every time.

Decided in [The UI tools](issues/archive/0151-the-ui-tools.md).

## compose-raven: `McpTool`

How a compose-raven app publishes its own actions. Everything here is in the opt-in
`compose-raven-mcp` module, package `raven.compose.mcp`. An app without it never sees any of it.

### Switching it on

The app's entry point takes the server's settings, and without them nothing starts:

```kotlin
fun main() = application(mcp = McpConfig(port = 8765, uiTools = false)) { … }
fun main() = singleWindowApplication(mcp = McpConfig()) { … }
```

`McpConfig` mirrors `RMcpConfig` (name, version, instructions, port, token, log), plus `uiTools`,
the separate opt-in for [the UI tools](#the-ui-tools), and `onAgentConnected(client)`. The `mcp`
parameters are overloads from `compose-raven-mcp`, and take null (as
`McpConfig.fromEnvironment()` gives without `RAVEN_MCP=1`) for no server, so `compose-raven-ui`
never depends on MCP. In an app started without a server, `McpTool` does nothing and costs nothing.
How a developer switches it on from outside is for
[Who may connect, and what they may do](#who-may-connect).

The overloads wrap the content in **`McpServer(config) { … }`**, which a test hosts too. It starts
the server as it enters the composition, from the config it has then, and stops it, withdrawing
every tool, as it leaves, before Raven shuts down. Recomposing it with another config doesn't
restart the server; only `onAgentConnected` follows the latest.

### A tool is a composable

```kotlin
@Serializable
data class AddNote(@McpDescription("The note's text") val text: String, val pinned: Boolean = false)

@Serializable
data class DeleteNote(@McpDescription("The note's position, from 0") val index: Int)

@Composable
fun NotesScreen(notes: SnapshotStateList<Note>) {
    McpTool<AddNote>("notes.add", "Add a note to the list", hints = setOf(McpHint.NotDestructive, McpHint.ClosedWorld)) { args ->
        notes += Note(args.text, args.pinned)
        "added note ${notes.size}"
    }
    McpTool("notes.list", "The notes, in order", hints = setOf(McpHint.ReadOnly, McpHint.ClosedWorld)) {
        notes.joinToString("\n") { it.text }
    }
    McpTool<DeleteNote>("notes.delete", "Delete a note") { args ->
        require(args.index in notes.indices) { "there is no note ${args.index}" }
        notes.removeAt(args.index)
        "deleted"
    }
    // … the screen itself
}
```

- **It lives while it's composed.** `McpTool` adds its tool when it enters composition and withdraws
  it when it leaves, so tools follow navigation, and an agent sees only the current screen's. A tool
  that should always be there is an `McpTool` at the top of `application { }`.
- **Typed arguments.** With a `@Serializable` type, the input schema is derived from its descriptor:
  property types, required or defaulted, enums, lists, nested objects, and descriptions from
  `@McpDescription` (a `@SerialInfo` annotation). Arguments arrive deserialized; ones that don't
  deserialize fail the call with kotlinx.serialization's message. Nullable and defaulted properties
  aren't required. An overload takes the `KSerializer` itself, and an untyped one takes a
  `JsonObject` and an optional schema string, for what the derivation can't say.
- **The handler is a `suspend` lambda on the UI thread** (`RavenDispatcher`). It returns the reply: a
  `String` for text, or an `McpReply` (`Text`, `Json`, `Image` from PNG bytes) for more; anything
  else replies with its `toString()`. Long work can
  `withContext(Dispatchers.Default)` and come back. A thrown exception is the tool's failure, with its
  message, and the agent cancelling the call cancels the coroutine. The header's one reply per call is
  kept underneath.
- **Always the latest handler.** `McpTool` holds its lambda through `rememberUpdatedState`, so a call
  runs the newest one, with today's state, while the registration stays put.
- **The same name twice**: the most recently composed `McpTool` wins, and when it leaves, the one
  before it comes back. The registry keeps a stack per name, and logs a warning while two are
  active, so one leaving never withdraws a tool the other still offers.
- **The rest of a tool**: `McpTool(name, description, title = null, hints = emptySet())`. `McpHint`
  is `ReadOnly`, `NotDestructive`, `Idempotent` and `ClosedWorld`, one to one with `RMcpToolHint`,
  and as there they are claims an app makes on purpose.

- **compose-raven's UI tools** are the contract above, over the composition's nodes, found with
  `compose-raven-ui-test`. A node's role, name and states are what Raven tells screen readers of its
  widget, read through `McpAccessible`, the same as the library's tools read them, so the outlines
  match. `tag=` finds a node by `Modifier.testTag`, and `widget=` isn't taken. A node with neither a
  role nor a tag adds no line; one with only a tag is a line of its own, `- tag="notes"`, so a list
  of rows reads as rows. Actions wait in `RavenHost.awaitIdle(120)`. An `McpTool` named `ui.*` wins
  over the UI tool of that name, as the last one composed does.

Decided in [The McpTool composable](issues/archive/0152-the-mcptool-composable.md).

## Python and Rust

Every binding gets the `Rmcp_*` calls from `tools/bindgen`, loaded on first use. Python and Rust add
only their own idioms on top, kept thin
([ADR 0002](adr/0002-rendering-in-c-bindings-thin.md)). Both publish the notes example's `notes.add`,
`notes.list` and `notes.delete` over the same `examples/notes/notes.xml` the UI tests use
(`agent-skills.md`, "UI tests in each language"). Raven's actions as tools (`Rmcp_expose_action(s)`)
need no idiom: they reach both through `tools/bindgen` like any C call.

### Python: `raven_mcp`

A small module in the optional `raven-gui[mcp]` package, beside `raven.py`, using the standard
library only.

```python
from typing import Annotated
from raven_mcp import mcp, Hint, Call

@mcp.tool("notes.add", "Add a note to the list", hints={Hint.NOT_DESTRUCTIVE, Hint.CLOSED_WORLD})
def add_note(text: Annotated[str, "The note's text"], pinned: bool = False) -> str:
    notes.append(text)
    return f"added note {len(notes)}"

@mcp.tool("notes.list", "The notes, in order", hints={Hint.READ_ONLY, Hint.CLOSED_WORLD})
def list_notes() -> list:
    return notes                       # a list or dict replies as JSON

@mcp.tool("notes.export", "Export the notes to a file")
def export(path: str, call: Call):     # takes `call`: replies later, from any thread
    threading.Thread(target=lambda: (write(path, notes), call.reply("exported"))).start()

mcp.start()                            # RMcpConfig's fields as keyword arguments
```

- **The schema comes from the signature**: `str`, `int`, `float`, `bool`, `list`, `Enum` and
  `Optional`. A default makes a parameter optional, and `Annotated[..., "…"]` gives a description.
  Arguments arrive as keyword arguments.
- **The return value is the reply**: a `str` is text, and a `dict` or `list` is JSON. Raising fails
  the call with the exception's message.
- **Replying later**: a tool that takes a `call` parameter replies through `call.reply(...)` or
  `call.fail(...)`, from any thread. A second reply raises, and a `Call` collected without one fails
  the call itself ("the tool never replied"). Raven has no asyncio loop, so there is no `async def`.
- `mcp.remove_tool(name)` withdraws a tool. The module keeps every tool's function alive until then.

### Rust: `raven_mcp`

A crate of its own, `raven-gui-mcp` (used as `raven_mcp`, like Python's module), over `raven-gui`'s
feature `mcp`. It brings in `serde`, `serde_json` and `schemars`. The design first put it in
`raven-gui` behind the feature. It became a crate in 0180 (Paul, 2026-11-24) so that `raven-gui`
keeps no dependencies and still builds offline from nothing, as its ctest checks do.

```rust
#[derive(Deserialize, JsonSchema)]
struct AddNote {
    /// The note's text
    text: String,
    #[serde(default)]
    pinned: bool,
}

let _add = raven_mcp::tool("notes.add", "Add a note to the list")
    .hints(Hint::NOT_DESTRUCTIVE | Hint::CLOSED_WORLD)
    .add(move |args: AddNote| -> Result<String, String> {
        notes.borrow_mut().push(args.text);
        Ok("added".into())
    });

let _export = raven_mcp::tool("notes.export", "Export the notes to a file")
    .add_deferred(|args: Export, call: Call| {
        std::thread::spawn(move || call.reply("exported"));   // `reply` consumes the Call
    });
```

- **Arguments through serde**, and the schema from `schemars`' `JsonSchema` derive. Doc comments
  become descriptions.
- **`Ok` replies, `Err` fails the call.** A reply can also be `serde_json::Value`, for JSON.
- **A `ToolHandle` withdraws its tool when dropped.** `.forget()` keeps it for the program's life.
- **Exactly one reply, checked by the compiler**: `add_deferred` hands over a `Call` that is `Send`
  but not `Clone`, and `reply` and `fail` consume it. A `Call` dropped without a reply fails the call
  itself.

Decided in [Python and Rust wrappers](issues/archive/0153-python-and-rust-wrappers.md).

### As built

Built in [Python and Rust MCP wrappers](issues/0180-python-and-rust-mcp-wrappers.md). What the design
left open was settled this way:

- **Where they are.** `raven_mcp.py` sits beside `raven.py` and isn't in the wheel yet: the
  optional `raven-gui[mcp]` package waits for [Shipping Raven MCP](issues/0175-shipping-raven-mcp.md),
  which is on hold. `raven_mcp` for Rust is the crate in `bindings/rust/mcp`. `tools/bindgen` writes
  its `Cargo.toml`, so its version follows Raven's, and its `build.rs`, which gives its programs the
  run-time paths of both libraries, since a dependency's link arguments don't carry over.
- **Starting and stopping**, the same in both:
  - `start(...)` takes `RMcpConfig`'s fields: keyword arguments in Python, `mcp::Config` in Rust.
  - `start_from_environment(...)` starts only when `RAVEN_MCP=1`.
  - `stop()` is `Rmcp_stop` then `Rmcp_tool_remove_all`, before `Rshutdown`.
  - `url` gives the address being served.
- **Python's schema** reads the annotation as written, so a default of `None` doesn't make a
  parameter nullable on Python 3.9 and 3.10 while leaving it plain on 3.11 and later (string
  annotations are resolved with `get_type_hints`). It covers `dict` (an object), `list[X]` (its
  items), an `Enum` (its values, and the argument arrives as the member), and `Optional[X]`
  (nullable). Any other type is refused when the tool is published.
- **Arguments are checked before the function runs.** In Python a call fails with "missing
  argument: x", "unknown argument: x" or "argument x: ...". In Rust it fails with
  `invalid arguments: <serde's message>`. A tool without arguments takes `raven_mcp::NoArgs` in
  Rust.
- **Replies.** `None` and `()` reply with an empty text. JSON is sent as text, and also as
  `structuredContent` when it is an object. In Rust a reply is a `String`, a `&str`, `()` or a
  `serde_json::Value`, and a panic fails the call ("the tool panicked: ..."). A tool Raven MCP
  refuses (an invalid name) raises `ValueError` in Python and panics in Rust, pointing at the log.
- **A replaced or removed tool's function is freed at once.** The library fails a call queued for
  a replaced or removed tool rather than reaching its old function, so the old closure can go
  straight away. A Rust `ToolHandle` withdraws its tool only while that name is still its own: a
  handle for a replaced tool frees its closure and leaves the new tool alone. A `ToolHandle` isn't
  `Send`, since tools are added and removed on Raven's UI thread.
- **Tested through a client** in compose's `check`:
  - `:raven-mcp:pythonWrapperTest` runs `bindings/python/test_raven_mcp.py` and
    `examples/notes/test_notes_mcp.py`.
  - `:raven-mcp:rustTest` runs the crate's `tests/mcp.rs` and `tests/notes_mcp.rs` after
    `raven-gui`'s `examples/mcp.rs`.
  - Each agent's calls are checked in the window afterwards, with the UI test harness.

## Who may connect

Raven MCP opens a door into a running app. It stays shut unless the app opens it, and narrow when
open.

- **Loopback only**, on a fixed port (8765 unless configured), with no fallback
  ([the header](#the-header)).
- **Host and Origin are always checked.** The SDK's DNS-rebinding protection accepts only
  `localhost`, `127.0.0.1` and `[::1]`, so a web page can't reach the app through the browser. There
  is no way to turn it off.
- **A bearer token on every request.** Loopback still lets other local processes and users connect,
  so each request carries `Authorization: Bearer <token>`. The token is made once per app and user,
  and stored with the URL in `~/.raven/mcp/<app name>.json`, readable only by that user (mode 0600).
  It is reused across launches, so an agent is set up once:

  ```sh
  claude mcp add --transport http notes http://127.0.0.1:8765/mcp \
      --header "Authorization: Bearer $(jq -r .token ~/.raven/mcp/notes.json)"
  ```

  Deleting the file makes a new token. `RMcpConfig.token` can give a token of its own, or `""` for
  none, which the log warns about.
- **On only when the app's code says so.** Raven MCP starts only through `Rmcp_start` (or
  `mcp = McpConfig(...)` in compose-raven). For an app that wants it for developers only,
  `McpConfig.fromEnvironment()` (with its C and Python counterparts) reads `RAVEN_MCP=1`,
  `RAVEN_MCP_PORT` and `RAVEN_MCP_UI=1`, and gives nothing when they're unset. **`RAVEN_MCP=0`**
  turns Raven MCP off whatever the code says: a kill switch for whoever runs the app. The library
  doesn't try to tell debug builds from release builds; that is the app's choice.
- **Narrow when open**: the UI tools are their own opt-in (`uiTools`, `RAVEN_MCP_UI`), apart from
  the app's tools, and every tool's hints say what it claims to be safe to do.
- **Knowing an agent is there**: `RMcpConfig.on_session` is called as agents connect and
  disconnect, with the client's name, and every connection is logged. In compose-raven,
  `McpConfig(onAgentConnected = …)`, and a ready-made `McpStatus()` composable shows a small marker,
  `● claude-code connected`, wherever the app places it. Nothing is shown unless the app asks.
- **The app keeps its signals.** Ktor's server takes SIGINT and SIGTERM for a shutdown hook of its
  own as it starts, which stops only the server, so Ctrl+C and `kill` stopped ending the app.
  `Rmcp_start` gives both back the handlers they had before it.

Decided in [Who may connect, and what they may do](issues/archive/0154-who-may-connect.md).

## Raven's actions as tools

A Raven app's named actions (`OnClicked="file.save"` in XML, `Raction_bind` in code) become tools
with one call and no code per action, so an XML app can be driven by its own vocabulary.

### What Raven's C gains

A fork change, proposed upstream on its own as a feature for command palettes, menus built from the
actions and scripting. It doesn't mention MCP:

```c
RAVEN_API int         Raction_get_count(void);
RAVEN_API const char* Raction_get_name(int index);
/* On a bound action; unbinding drops it. */
RAVEN_API void        Raction_set_description(const char* name, const char* description);
RAVEN_API const char* Raction_get_description(const char* name);       /* NULL when none */
/* Called on the UI thread as actions are bound (bound = R_TRUE) and unbound. One slot. */
typedef void (*RActionListCallback)(const char* name, int bound, void* user_data);
RAVEN_API void        Raction_set_list_callback(RActionListCallback callback, void* user_data);
```

### Exposing them

- **`Rmcp_expose_actions(prefix)`** publishes every **bound** action whose name starts with
  `prefix` (NULL for all), and stays in step through the list callback: binding a new action
  publishes it, and agents hear `listChanged`. Built-ins are never included: they carry arguments in
  their names (`open:ADDRESS`, `copy:TEXT`) or end the app (`app.quit`). A name that isn't a valid
  tool name (`[A-Za-z0-9_.-]`) is skipped and logged, never renamed. These tools claim no hints, so
  agents assume the cautious defaults.
- **`Rmcp_expose_action(name, description, hints)`** publishes one action on purpose, bound or
  built-in, with hints: `Rmcp_expose_action("theme.dark", "Switch to the dark theme",
  R_MCP_HINT_IDEMPOTENT | R_MCP_HINT_CLOSED_WORLD)`.
- **The description** is `Raction_get_description` when the app set one, else "Runs the app's
  action `file.save`", logged once as a nudge to describe it.
- **The sender** is the focused window, or else the first open window. Raven's built-ins and
  window-relative handlers act on the window the user is looking at. A handler that needs a
  particular widget doesn't get one.
- **The app's own tools win**: a tool added with `Rmcp_tool_add` under the same name hides the
  exposed action while it exists.
- **The reply**: the action fires on the UI thread, Raven MCP waits until the app is idle (the UI
  tools' 120-frame limit), and replies "ran file.save". An action that nothing handles fails the
  call: "nothing handles the action file.save".
- **What it takes, and what it can't see**: `Rmcp_expose_actions` takes Raven's one
  `Raction_set_list_callback` slot, so an app that needs the slot too can't use both. "Handled"
  means bound or built-in. Raven has no way to ask whether a default handler
  (`Raction_set_default_handler`) would take a name, so an action only a default handler answers
  fails as unhandled.

Decided in [Exposing Raven's actions](issues/archive/0155-exposing-ravens-actions.md).

Decided in [The raven_mcp.h contract](issues/archive/0147-the-raven-mcp-h-contract.md),
[Building and shipping Raven MCP](issues/archive/0148-building-and-shipping-raven-mcp.md),
[The UI tools](issues/archive/0151-the-ui-tools.md),
[The McpTool composable](issues/archive/0152-the-mcptool-composable.md),
[Python and Rust wrappers](issues/archive/0153-python-and-rust-wrappers.md),
[Who may connect, and what they may do](issues/archive/0154-who-may-connect.md) and
[Exposing Raven's actions](issues/archive/0155-exposing-ravens-actions.md).
