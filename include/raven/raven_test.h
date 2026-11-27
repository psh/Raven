/**
 * Raven
 * raven_test.h : headless UI tests for C apps, over the UI driver.
 *
 * Header-only and not included by Raven.h: include it in the one file of a
 * test. A test starts Raven, builds its windows, acts and checks through
 * selectors (raven_driver.h), and ends:
 *
 *   #include <Raven.h>
 *   #include <raven/raven_test.h>
 *
 *   int main(void) {
 *       if (!Rtest_begin(NULL)) return RTEST_SKIPPED;
 *       Rwindow_create_from_xml_file("notes.xml");
 *       RTEST_TYPE("role=text-field", "buy milk");
 *       RTEST_CLICK("role=button name=\"Add\"");
 *       RTEST_EXPECT_TEXT("role=list-item [0]", "buy milk");
 *       return Rtest_end();
 *   }
 *
 * What Rtest_begin sets up:
 *   - headless: the null backend with no window system; with
 *     RTestOptions.capture, headless Vulkan instead, and R_FALSE without it
 *     (return RTEST_SKIPPED, 77, which ctest reports as skipped with
 *     SKIP_RETURN_CODE 77);
 *   - deterministic: animations advance by a fixed step each frame;
 *   - Raven's errors fail the test (warnings too; Rtest_expect_errors when a
 *     test means to cause them);
 *   - idle around every step: Rwait_idle before every find and after every
 *     action, and a step fails when the app is still busy;
 *   - leaks checked: after Rshutdown, Rtest_end fails when Raven still holds
 *     memory;
 *   - on a failure with capture, a PNG of each open window is saved into the
 *     folder RAVEN_TEST_CAPTURES names (nothing without it, or on the null
 *     backend).
 *
 * Every RTEST_ macro returns R_TRUE, or R_FALSE after reporting a failure, and
 * the test goes on: `if (!RTEST_CLICK(...)) return Rtest_end();` stops it. A
 * failure is counted, then given to the hook (RTestOptions.on_failure), which
 * by default prints "FAIL file:line: message" on stderr; a hook reports it to
 * another test runner instead.
 *
 * The state lives in the file that includes this header: one test file, one
 * Rtest_begin ... Rtest_end at a time. UI thread only, like the rest of Raven.
 */
#ifndef RAVEN_TEST_H
#define RAVEN_TEST_H
#include "../Raven.h"
#include <math.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#ifdef __cplusplus
extern "C" {
#endif

/** What a test returns when it can't run here (no Vulkan to capture with): ctest's skip code. */
#define RTEST_SKIPPED 77

/** Reports a failure; `message` says what was expected and what the driver found. */
typedef void (*RTestFailureHook)(const char* file, int line, const char* message, void* user_data);

/** Rtest_begin's settings. A zero-initialized structure (or NULL) selects every default. */
typedef struct RTestOptions {
	/** Shown in the summary and in the names of the captures. NULL: "test". */
	const char* name;
	/** R_TRUE: headless Vulkan, so windows can be captured (Rwindow_capture, and on failure). */
	int capture;
	/** Seconds the animations advance each frame. 0: 1/60. */
	double time_step;
	/** Frames a step waits for the app to be idle. 0: 600. */
	int idle_frames;
	/** R_TRUE: Rtest_end does not check that Raven freed everything. */
	int allow_leaks;
	/** How a failure is reported. NULL: printed on stderr. Failures are counted either way. */
	RTestFailureHook on_failure;
	void* user_data;
} RTestOptions;

/* ============================================================================
 *  Steps: find, act, check. Each returns R_TRUE, or R_FALSE after a failure.
 * ============================================================================ */

#define RTEST_CLICK(selector)                  Rtest_click_selector_at(__FILE__, __LINE__, (selector))
#define RTEST_TYPE(selector, text)             Rtest_type_at(__FILE__, __LINE__, (selector), (text))
/** A key with RModifier values: RTEST_KEY("role=text-field", R_KEY_ENTER, 0). */
#define RTEST_KEY(selector, key, modifiers)    Rtest_key_at(__FILE__, __LINE__, (selector), (key), (modifiers))
/** R_ACCESSIBLE_EXPAND, _COLLAPSE, _SELECT or _FOCUS, without input. */
#define RTEST_ACT(selector, action)            Rtest_act_at(__FILE__, __LINE__, (selector), (action))
/** Reads the one accessible the selector names into an RAccessibleInfo, for anything the checks below don't cover. */
#define RTEST_DESCRIBE(selector, info)         Rtest_describe_at(__FILE__, __LINE__, (selector), (info))

/** Exactly one accessible matches. */
#define RTEST_EXPECT_EXISTS(selector)          Rtest_expect_count_at(__FILE__, __LINE__, (selector), -1)
/** None matches. */
#define RTEST_EXPECT_ABSENT(selector)          Rtest_expect_count_at(__FILE__, __LINE__, (selector), 0)
#define RTEST_EXPECT_COUNT(selector, count)    Rtest_expect_count_at(__FILE__, __LINE__, (selector), (count))
/** The text it shows: a text field's value, else its name (the driver's text=). */
#define RTEST_EXPECT_TEXT(selector, text)      Rtest_expect_text_at(__FILE__, __LINE__, (selector), (text))
/** The number of a slider, a spin box, a progress bar. */
#define RTEST_EXPECT_VALUE(selector, value)    Rtest_expect_value_at(__FILE__, __LINE__, (selector), (value))
/** Whether it is checked (R_TRUE) or not (R_FALSE); the same for enabled and focused. */
#define RTEST_EXPECT_CHECKED(selector, yes)    Rtest_expect_state_at(__FILE__, __LINE__, "RTEST_EXPECT_CHECKED", (selector), R_ACCESSIBLE_CHECKED, 0, "checked", "not checked", (yes))
#define RTEST_EXPECT_ENABLED(selector, yes)    Rtest_expect_state_at(__FILE__, __LINE__, "RTEST_EXPECT_ENABLED", (selector), R_ACCESSIBLE_DISABLED, 1, "enabled", "disabled", (yes))
#define RTEST_EXPECT_FOCUSED(selector, yes)    Rtest_expect_state_at(__FILE__, __LINE__, "RTEST_EXPECT_FOCUSED", (selector), R_ACCESSIBLE_FOCUSED, 0, "focused", "not focused", (yes))
/** Anything else: the app's own state. */
#define RTEST_CHECK(condition)                 Rtest_check_at(__FILE__, __LINE__, (condition) != 0, #condition)

/* ============================================================================
 *  Implementation
 * ============================================================================ */

static struct {
	RTestOptions options;
	int running, failures, checks, failing;
	int expecting_errors, expected_errors;
	char capture_name[64];
} g_rtest;

/** Reports a failure: counted, given to the hook, and the windows captured when the test can. */
static inline void Rtest_fail_at(const char* file, int line, const char* format, ...) {
	char message[1024];
	va_list args;
	va_start(args, format);
	vsnprintf(message, sizeof(message), format, args);
	va_end(args);
	g_rtest.failures++;
	if (g_rtest.options.on_failure) g_rtest.options.on_failure(file, line, message, g_rtest.options.user_data);
	else fprintf(stderr, "FAIL %s:%d: %s\n", file, line, message);
	/* (not while reporting one of Raven's errors: Raven is in the middle of something) */
	if (g_rtest.options.capture && g_rtest.running && !g_rtest.failing) {
		const char* folder = getenv("RAVEN_TEST_CAPTURES");
		int i, count;
		g_rtest.failing = 1;
		count = folder && folder[0] ? Rwindow_get_count() : 0;
		for (i = 0; i < count; i++) {
			char path[1024];
			snprintf(path, sizeof(path), "%s/%s-%d-window%d.png", folder, g_rtest.capture_name, g_rtest.failures, i);
			if (Rwindow_save_preview(Rwindow_get(i), path, 1.0f)) fprintf(stderr, "  captured %s\n", path);
		}
		g_rtest.failing = 0;
	}
}

static inline void Rtest_on_raven_error(RError code, RSeverity severity, const char* detail) {
	if (severity == R_SEVERITY_WEAK_WARNING) return;
	if (g_rtest.expecting_errors) {
		g_rtest.expected_errors++;
		return;
	}
	g_rtest.failing++;
	Rtest_fail_at("Raven", 0, "%s (%s): %s", Rerror_to_string(code), Rseverity_to_string(severity), detail);
	g_rtest.failing--;
}

/**
 * @brief While R_TRUE, Raven's errors are counted instead of failing the test.
 * @return How many were counted since it was turned on (when turning it off).
 */
static inline int Rtest_expect_errors(int expecting) {
	int counted = g_rtest.expected_errors;
	g_rtest.expecting_errors = expecting;
	g_rtest.expected_errors = 0;
	return counted;
}

/** @return The failures so far. */
static inline int Rtest_failures(void) {
	return g_rtest.failures;
}

/**
 * @brief Starts Raven for a test (see the top of this file).
 * @param options NULL for the defaults.
 * @return R_FALSE only when the test can't run here: with capture, no
 * usable Vulkan driver (return RTEST_SKIPPED). Raven failing to start
 * headless is a failure instead: reported, and Rtest_end returns 1.
 */
static inline int Rtest_begin(const RTestOptions* options) {
	RInitDesc desc;
	memset(&g_rtest, 0, sizeof(g_rtest));
	if (options) g_rtest.options = *options;
	if (g_rtest.options.time_step <= 0.0) g_rtest.options.time_step = 1.0 / 60.0;
	if (g_rtest.options.idle_frames <= 0) g_rtest.options.idle_frames = 600;
	snprintf(g_rtest.capture_name, sizeof(g_rtest.capture_name), "%s", g_rtest.options.name ? g_rtest.options.name : "test");
	memset(&desc, 0, sizeof(desc));
	desc.backend = g_rtest.options.capture ? R_BACKEND_VULKAN : R_BACKEND_NULL;
	desc.headless = R_TRUE;
	desc.app_name = g_rtest.capture_name;
	/* (no Vulkan is a reason to skip, not a failure: its errors stay quiet) */
	Rtest_expect_errors(R_TRUE);
	Rerror_set_callback(Rtest_on_raven_error);
	if (!Rinit_ex(&desc)) {
		Rtest_expect_errors(R_FALSE);
		if (g_rtest.options.capture) {
			fprintf(stderr, "%s: SKIPPED, no usable Vulkan driver to capture with\n", g_rtest.capture_name);
			return R_FALSE;
		}
		/* headless needs nothing: not a reason to skip (Raven already running, most likely). The steps
		   fail as not started, and Rtest_end returns 1. */
		Rtest_fail_at(__FILE__, __LINE__, "Raven didn't start headless (Rinit_ex failed: is it running already?)");
		return R_TRUE;
	}
	if (g_rtest.options.capture) {
		Rid probe = Rwindow_create("probe", 16, 16, R_WINDOW_DEFAULT);
		int width = 0, height = 0;
		uint8_t* pixels = probe != R_INVALID_ID ? Rwindow_capture(probe, &width, &height) : NULL;
		Rpixels_free(pixels);
		if (probe != R_INVALID_ID) Rwindow_destroy(probe);
		if (!pixels) {
			Rshutdown();
			Rtest_expect_errors(R_FALSE);
			fprintf(stderr, "%s: SKIPPED, no usable Vulkan driver to capture with\n", g_rtest.capture_name);
			return R_FALSE;
		}
	}
	Rtest_expect_errors(R_FALSE);
	Rset_animation_time_step(g_rtest.options.time_step);
	g_rtest.running = 1;
	return R_TRUE;
}

/**
 * @brief Shuts Raven down, checks that it freed everything, and prints the summary.
 * @return 0 when the test passed, 1 when it failed: main's exit code.
 */
static inline int Rtest_end(void) {
	if (!g_rtest.running) return 1;
	g_rtest.running = 0;
	Rshutdown();
	Rerror_set_callback(NULL);
	if (!g_rtest.options.allow_leaks) {
		RMemoryStats memory;
		Rmemory_get_stats(&memory);
		if (memory.allocation_count != 0) Rtest_fail_at(__FILE__, __LINE__, "%u allocation(s) of Raven still alive after Rshutdown", (unsigned)memory.allocation_count);
		if (!Rmemory_check()) Rtest_fail_at(__FILE__, __LINE__, "Raven's heap is corrupted");
	}
	printf("%s: %d checks, %d failures\n", g_rtest.capture_name, g_rtest.checks, g_rtest.failures);
	return g_rtest.failures == 0 ? 0 : 1;
}

/* Waits for the app to settle; a step fails when it doesn't. */
static inline int Rtest_idle(const char* file, int line, const char* step) {
	if (Rwait_idle(g_rtest.options.idle_frames)) return R_TRUE;
	Rtest_fail_at(file, line, "%s: the app is still busy after %d frames", step, g_rtest.options.idle_frames);
	return R_FALSE;
}

/* The one accessible a selector names, after waiting for idle; 0 after reporting why. */
static inline RAccessible Rtest_find_at(const char* file, int line, const char* step, const char* selector) {
	RAccessible found;
	g_rtest.checks++;
	if (!g_rtest.running) {
		Rtest_fail_at(file, line, "%s: Rtest_begin first", step);
		return 0;
	}
	if (!Rtest_idle(file, line, step)) return 0;
	found = Raccessible_find(0, selector);
	if (!found) Rtest_fail_at(file, line, "%s(%s): %s", step, selector, Raccessible_get_last_error());
	return found;
}

static inline int Rtest_acted(const char* file, int line, const char* step, const char* selector, int done) {
	if (!done) {
		Rtest_fail_at(file, line, "%s(%s): %s", step, selector, Raccessible_get_last_error());
		return R_FALSE;
	}
	return Rtest_idle(file, line, step);
}

static inline int Rtest_click_selector_at(const char* file, int line, const char* selector) {
	RAccessible found = Rtest_find_at(file, line, "RTEST_CLICK", selector);
	return found && Rtest_acted(file, line, "RTEST_CLICK", selector, Raccessible_click(found));
}

static inline int Rtest_type_at(const char* file, int line, const char* selector, const char* text) {
	RAccessible found = Rtest_find_at(file, line, "RTEST_TYPE", selector);
	return found && Rtest_acted(file, line, "RTEST_TYPE", selector, Raccessible_type(found, text));
}

static inline int Rtest_key_at(const char* file, int line, const char* selector, RKey key, Ruint modifiers) {
	RAccessible found = Rtest_find_at(file, line, "RTEST_KEY", selector);
	return found && Rtest_acted(file, line, "RTEST_KEY", selector, Raccessible_press_key(found, key, modifiers));
}

static inline int Rtest_act_at(const char* file, int line, const char* selector, RAccessibleAction action) {
	RAccessible found = Rtest_find_at(file, line, "RTEST_ACT", selector);
	return found && Rtest_acted(file, line, "RTEST_ACT", selector, Raccessible_act(found, action));
}

static inline int Rtest_describe_at(const char* file, int line, const char* selector, RAccessibleInfo* info) {
	RAccessible found = Rtest_find_at(file, line, "RTEST_DESCRIBE", selector);
	if (!found) return R_FALSE;
	if (Raccessible_describe(found, info)) return R_TRUE;
	Rtest_fail_at(file, line, "RTEST_DESCRIBE(%s): it is gone", selector);
	return R_FALSE;
}

/* count -1: exactly one. */
static inline int Rtest_expect_count_at(const char* file, int line, const char* selector, int count) {
	const char* step = count < 0 ? "RTEST_EXPECT_EXISTS" : count == 0 ? "RTEST_EXPECT_ABSENT" : "RTEST_EXPECT_COUNT";
	int found;
	g_rtest.checks++;
	if (!g_rtest.running) {
		Rtest_fail_at(file, line, "%s: Rtest_begin first", step);
		return R_FALSE;
	}
	if (!Rtest_idle(file, line, step)) return R_FALSE;
	found = Raccessible_count(0, selector);
	if (count < 0 ? found == 1 : found == count) return R_TRUE;
	Rtest_fail_at(file, line, "%s(%s): %d match%s, expected %s%d", step, selector, found, found == 1 ? "" : "es",
		count < 0 ? "exactly " : "", count < 0 ? 1 : count);
	return R_FALSE;
}

static inline int Rtest_expect_text_at(const char* file, int line, const char* selector, const char* text) {
	RAccessibleInfo info;
	const char* shown;
	if (!Rtest_describe_at(file, line, selector, &info)) return R_FALSE;
	switch (info.role) {
		case R_ROLE_TEXT_FIELD: case R_ROLE_DOCUMENT: case R_ROLE_COMBO_BOX: case R_ROLE_SPIN_BUTTON:
			shown = info.value;
			break;
		default:
			shown = info.name;
	}
	if (!shown) shown = "";
	if (strcmp(shown, text ? text : "") == 0) return R_TRUE;
	Rtest_fail_at(file, line, "RTEST_EXPECT_TEXT(%s): \"%s\", expected \"%s\"", selector, shown, text ? text : "");
	return R_FALSE;
}

static inline int Rtest_expect_value_at(const char* file, int line, const char* selector, double value) {
	RAccessibleInfo info;
	if (!Rtest_describe_at(file, line, selector, &info)) return R_FALSE;
	if (!(info.states & R_ACCESSIBLE_HAS_RANGE)) {
		Rtest_fail_at(file, line, "RTEST_EXPECT_VALUE(%s): it has no number (a %s)", selector, Raccessible_get_role_name(info.role));
		return R_FALSE;
	}
	if (fabs(info.number - value) <= 1e-6 * (fabs(value) > 1.0 ? fabs(value) : 1.0)) return R_TRUE;
	Rtest_fail_at(file, line, "RTEST_EXPECT_VALUE(%s): %g, expected %g", selector, info.number, value);
	return R_FALSE;
}

/* Whether it is `yes_word` (a state set, or with `inverted` not set), as the test expects. */
static inline int Rtest_expect_state_at(const char* file, int line, const char* step, const char* selector, Ruint state,
	int inverted, const char* yes_word, const char* no_word, int expected) {
	RAccessibleInfo info;
	int is;
	if (!Rtest_describe_at(file, line, selector, &info)) return R_FALSE;
	is = ((info.states & state) != 0) != (inverted != 0);
	if (is == (expected != 0)) return R_TRUE;
	Rtest_fail_at(file, line, "%s(%s): %s, expected %s", step, selector, is ? yes_word : no_word, expected ? yes_word : no_word);
	return R_FALSE;
}

static inline int Rtest_check_at(const char* file, int line, int holds, const char* condition) {
	g_rtest.checks++;
	if (holds) return R_TRUE;
	Rtest_fail_at(file, line, "RTEST_CHECK(%s)", condition);
	return R_FALSE;
}

#ifdef __cplusplus
}
#endif
#endif /* RAVEN_TEST_H */
