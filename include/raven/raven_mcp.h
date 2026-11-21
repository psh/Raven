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
	 * kill switch for whoever runs the app.
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
