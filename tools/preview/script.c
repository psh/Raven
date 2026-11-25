/* raven_preview's scripts (script.h). */
#include "script.h"
#include <ctype.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>

#define MAX_WORDS 32
#define IDLE_FRAMES 600

/* A word of a step, as written: `role=button`, `name="buy milk"`, `"Add"`, `Ctrl+S`. */
typedef struct Word {
	const char* text;
	size_t length;
} Word;

static int fail(char* error, size_t size, const char* format, ...) {
	va_list args;
	va_start(args, format);
	vsnprintf(error, size, format, args);
	va_end(args);
	return R_FALSE;
}

/* Where a quoted text that starts at `p` ends (just after its closing quote), or NULL when it isn't closed. */
static const char* skip_quoted(const char* p, const char* end) {
	for (p++; p < end; p++) {
		if (*p == '\\' && p + 1 < end) p++;
		else if (*p == '"') return p + 1;
	}
	return NULL;
}

/* Splits a step into words at spaces outside quotes. */
static int split_words(const char* p, const char* end, Word* words, int* count, char* error, size_t size) {
	*count = 0;
	while (p < end) {
		const char* start;
		while (p < end && isspace((unsigned char)*p)) p++;
		if (p >= end) break;
		start = p;
		while (p < end && !isspace((unsigned char)*p)) {
			if (*p == '"') {
				p = skip_quoted(p, end);
				if (!p) return fail(error, size, "a quote is not closed");
			} else {
				p++;
			}
		}
		if (*count == MAX_WORDS) return fail(error, size, "more than %d words", MAX_WORDS);
		words[*count].text = start;
		words[*count].length = (size_t)(p - start);
		(*count)++;
	}
	return R_TRUE;
}

static int is_quoted(const Word* word) {
	return word->length >= 2 && word->text[0] == '"' && word->text[word->length - 1] == '"';
}

static int word_is(const Word* word, const char* text) {
	return strlen(text) == word->length && strncmp(word->text, text, word->length) == 0;
}

/* The text of a quoted word, without its quotes and escapes. */
static void unquote(const Word* word, char* out, size_t size) {
	size_t i, n = 0;
	for (i = 1; i + 1 < word->length && n + 1 < size; i++) {
		char c = word->text[i];
		if (c == '\\' && i + 2 < word->length) c = word->text[++i];
		out[n++] = c;
	}
	out[n] = '\0';
}

/* The driver's selector from words: a bare quoted word is name="...". */
static int build_selector(const Word* words, int count, char* out, size_t size) {
	int i;
	size_t n = 0;
	out[0] = '\0';
	for (i = 0; i < count; i++) {
		int written = snprintf(out + n, size - n, "%s%s%.*s", n ? " " : "", is_quoted(&words[i]) ? "name=" : "",
			(int)words[i].length, words[i].text);
		if (written < 0 || (size_t)written >= size - n) return R_FALSE;
		n += (size_t)written;
	}
	return R_TRUE;
}

static const struct { const char* name; RKey key; } g_keys[] = {
	{ "Space", R_KEY_SPACE }, { "Escape", R_KEY_ESCAPE }, { "Esc", R_KEY_ESCAPE }, { "Enter", R_KEY_ENTER },
	{ "Return", R_KEY_ENTER }, { "Tab", R_KEY_TAB }, { "Backspace", R_KEY_BACKSPACE }, { "Insert", R_KEY_INSERT },
	{ "Delete", R_KEY_DELETE }, { "Del", R_KEY_DELETE }, { "Right", R_KEY_RIGHT }, { "Left", R_KEY_LEFT },
	{ "Down", R_KEY_DOWN }, { "Up", R_KEY_UP }, { "PageUp", R_KEY_PAGE_UP }, { "PageDown", R_KEY_PAGE_DOWN },
	{ "Home", R_KEY_HOME }, { "End", R_KEY_END }, { "F1", R_KEY_F1 }, { "F2", R_KEY_F2 }, { "F3", R_KEY_F3 },
	{ "F4", R_KEY_F4 }, { "F5", R_KEY_F5 }, { "F6", R_KEY_F6 }, { "F7", R_KEY_F7 }, { "F8", R_KEY_F8 },
	{ "F9", R_KEY_F9 }, { "F10", R_KEY_F10 }, { "F11", R_KEY_F11 }, { "F12", R_KEY_F12 }
};

static int same_name(const char* a, size_t length, const char* b) {
	size_t i;
	if (strlen(b) != length) return R_FALSE;
	for (i = 0; i < length; i++) {
		if (tolower((unsigned char)a[i]) != tolower((unsigned char)b[i])) return R_FALSE;
	}
	return R_TRUE;
}

/* "Ctrl+Shift+S", "Enter", "a", as menus write shortcuts. */
static int parse_key(const Word* word, RKey* key, Ruint* modifiers) {
	const char* p = word->text;
	const char* end = word->text + word->length;
	*key = R_KEY_UNKNOWN;
	*modifiers = 0;
	while (p < end) {
		const char* plus = memchr(p, '+', (size_t)(end - p));
		size_t length = plus ? (size_t)(plus - p) : (size_t)(end - p), i;
		/* "Ctrl++": the last part is the plus key itself */
		if (length == 0) length = 1;
		if (same_name(p, length, "Ctrl") || same_name(p, length, "Control")) *modifiers |= R_MOD_CTRL;
		else if (same_name(p, length, "Shift")) *modifiers |= R_MOD_SHIFT;
		else if (same_name(p, length, "Alt")) *modifiers |= R_MOD_ALT;
		else if (same_name(p, length, "Super") || same_name(p, length, "Cmd") || same_name(p, length, "Win")) *modifiers |= R_MOD_SUPER;
		else {
			if (*key != R_KEY_UNKNOWN) return R_FALSE;
			for (i = 0; i < sizeof(g_keys) / sizeof(g_keys[0]); i++) {
				if (same_name(p, length, g_keys[i].name)) *key = g_keys[i].key;
			}
			if (*key == R_KEY_UNKNOWN && length == 1 && p[0] > 32 && p[0] < 127) *key = (RKey)toupper((unsigned char)p[0]);
			if (*key == R_KEY_UNKNOWN) return R_FALSE;
		}
		p += length;
		if (p < end && *p == '+') p++;
	}
	return *key != R_KEY_UNKNOWN;
}

/* The one accessible a selector names, or 0 with why. */
static RAccessible find(const Word* words, int count, char* error, size_t size) {
	char selector[512];
	RAccessible found;
	if (count == 0) {
		fail(error, size, "no selector");
		return 0;
	}
	if (!build_selector(words, count, selector, sizeof(selector))) {
		fail(error, size, "the selector is too long");
		return 0;
	}
	found = Raccessible_find(0, selector);
	if (!found) fail(error, size, "%s", Raccessible_get_last_error());
	return found;
}

static int driver_failed(char* error, size_t size) {
	return fail(error, size, "%s", Raccessible_get_last_error());
}

static int run_step(Rid window, const Word* words, int count, char* error, size_t size) {
	const Word* verb = &words[0];
	const Word* rest = words + 1;
	int n = count - 1;
	RAccessible target;

	if (word_is(verb, "wait-idle")) {
		if (n) return fail(error, size, "wait-idle takes nothing");
		return R_TRUE;
	}
	if (word_is(verb, "click")) {
		if (!(target = find(rest, n, error, size))) return R_FALSE;
		return Raccessible_click(target) || driver_failed(error, size);
	}
	if (word_is(verb, "type")) {
		char text[1024];
		if (n == 0 || !is_quoted(&rest[n - 1])) return fail(error, size, "type ends with the text, quoted");
		unquote(&rest[n - 1], text, sizeof(text));
		if (n == 1) {
			/* the last focused one is the deepest: the widget, not the window that holds it */
			int focused = Raccessible_count(0, "focused");
			if (!focused) return fail(error, size, "nothing has the focus: say what to type into");
			target = Raccessible_get(0, "focused", focused - 1);
		} else if (!(target = find(rest, n - 1, error, size))) {
			return R_FALSE;
		}
		return Raccessible_type(target, text) || driver_failed(error, size);
	}
	if (word_is(verb, "key")) {
		RKey key;
		Ruint modifiers;
		if (n == 0) return fail(error, size, "key needs a key (Enter, Ctrl+S)");
		if (!parse_key(&rest[n - 1], &key, &modifiers)) return fail(error, size, "unknown key %.*s", (int)rest[n - 1].length, rest[n - 1].text);
		/* the window's root: the key goes to what has the focus there */
		if (n == 1) target = Raccessible_get_root(window);
		else if (!(target = find(rest, n - 1, error, size))) return R_FALSE;
		return Raccessible_press_key(target, key, modifiers) || driver_failed(error, size);
	}
	if (word_is(verb, "act")) {
		static const struct { const char* name; RAccessibleAction action; } actions[] = {
			{ "expand", R_ACCESSIBLE_EXPAND }, { "collapse", R_ACCESSIBLE_COLLAPSE },
			{ "select", R_ACCESSIBLE_SELECT }, { "focus", R_ACCESSIBLE_FOCUS }
		};
		size_t i;
		if (n == 0) return fail(error, size, "act needs expand, collapse, select or focus");
		for (i = 0; i < sizeof(actions) / sizeof(actions[0]); i++) {
			if (!word_is(&rest[0], actions[i].name)) continue;
			if (!(target = find(rest + 1, n - 1, error, size))) return R_FALSE;
			return Raccessible_act(target, actions[i].action) || driver_failed(error, size);
		}
		return fail(error, size, "act needs expand, collapse, select or focus, not %.*s", (int)rest[0].length, rest[0].text);
	}
	return fail(error, size, "unknown verb %.*s (click, type, key, act, wait-idle)", (int)verb->length, verb->text);
}

int preview_run_script(Rid window, const char* script, char* error, size_t error_size) {
	const char* p = script;
	int step = 0;
	char why[768];
	error[0] = '\0';
	while (*p) {
		const char* end = p;
		Word words[MAX_WORDS];
		int count;
		/* the step ends at a ';' outside quotes */
		while (*end && *end != ';') {
			if (*end == '"') {
				const char* closed = skip_quoted(end, end + strlen(end));
				end = closed ? closed : end + strlen(end);
			} else {
				end++;
			}
		}
		step++;
		if (!split_words(p, end, words, &count, why, sizeof(why))) {
			snprintf(error, error_size, "step %d: %s", step, why);
			return R_FALSE;
		}
		if (count > 0) {
			if (!run_step(window, words, count, why, sizeof(why))) {
				const Word* last = &words[count - 1];
				snprintf(error, error_size, "step %d, %.*s: %s", step, (int)(last->text + last->length - words[0].text), words[0].text, why);
				return R_FALSE;
			}
			Rwait_idle(IDLE_FRAMES);
		}
		p = *end ? end + 1 : end;
	}
	return R_TRUE;
}
