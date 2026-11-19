/**
 * Raven - core/driver.c
 * The UI driver (raven_driver.h): accessibles found with selectors, read, and
 * used with real input. Built on the tree of core/accessibility.c. UI thread.
 */

#include "context.h"
#include "accessibility.h"
#include "../widget/widget.h"
#include "../widgets/common.h"
#include "raven/raven_driver.h"

#include <ctype.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>

/* ---------------------------------------------------------------------------
 *  The last error, and what Raccessible_describe gives out
 * ------------------------------------------------------------------------- */

static char g_driver_error[640];
static char* g_driver_texts[3];

static int rvn_driver_fail(const char* format, ...) {
	va_list args;
	va_start(args, format);
	vsnprintf(g_driver_error, sizeof(g_driver_error), format, args);
	va_end(args);
	return R_FALSE;
}

static void rvn_driver_ok(void) {
	g_driver_error[0] = '\0';
}

void rvn_driver_shutdown(void) {
	int i;
	for (i = 0; i < 3; i++) {
		rvn_free(g_driver_texts[i]);
		g_driver_texts[i] = NULL;
	}
	rvn_driver_ok();
}

/* A text of a node, kept until the next describe (the tree's own texts are reused by its next call). */
static const char* rvn_driver_keep(int slot, const char* text) {
	rvn_free(g_driver_texts[slot]);
	g_driver_texts[slot] = rvn_strdup(text ? text : "");
	return g_driver_texts[slot];
}

/* ---------------------------------------------------------------------------
 *  Roles
 * ------------------------------------------------------------------------- */

static const struct {
	RAccessibleRole role;
	const char* name;
} g_roles[] = {
	{ R_ROLE_WINDOW, "window" }, { R_ROLE_GROUP, "group" }, { R_ROLE_TEXT, "text" }, { R_ROLE_HEADING, "heading" },
	{ R_ROLE_LINK, "link" }, { R_ROLE_BUTTON, "button" }, { R_ROLE_TOGGLE_BUTTON, "toggle-button" },
	{ R_ROLE_CHECKBOX, "checkbox" }, { R_ROLE_RADIO_BUTTON, "radio-button" }, { R_ROLE_SLIDER, "slider" },
	{ R_ROLE_PROGRESS_BAR, "progress-bar" }, { R_ROLE_SPIN_BUTTON, "spin-button" }, { R_ROLE_TEXT_FIELD, "text-field" },
	{ R_ROLE_DOCUMENT, "document" }, { R_ROLE_COMBO_BOX, "combo-box" }, { R_ROLE_LIST, "list" },
	{ R_ROLE_LIST_ITEM, "list-item" }, { R_ROLE_TREE, "tree" }, { R_ROLE_TREE_ITEM, "tree-item" },
	{ R_ROLE_TABLE, "table" }, { R_ROLE_MENU_BAR, "menu-bar" }, { R_ROLE_MENU, "menu" },
	{ R_ROLE_MENU_ITEM, "menu-item" }, { R_ROLE_TAB_LIST, "tab-list" }, { R_ROLE_TAB, "tab" },
	{ R_ROLE_TAB_PANEL, "tab-panel" }, { R_ROLE_TOOLBAR, "toolbar" }, { R_ROLE_STATUS_BAR, "status-bar" },
	{ R_ROLE_DIALOG, "dialog" }, { R_ROLE_ALERT, "alert" }, { R_ROLE_IMAGE, "image" }, { R_ROLE_CANVAS, "canvas" },
	{ R_ROLE_SEPARATOR, "separator" }, { R_ROLE_SCROLL_BAR, "scroll-bar" }, { R_ROLE_PANE, "pane" },
};

const char* Raccessible_get_role_name(RAccessibleRole role) {
	size_t i;
	for (i = 0; i < RVN_COUNT_OF(g_roles); i++) {
		if (g_roles[i].role == role) return g_roles[i].name;
	}
	return "";
}

static int rvn_driver_is_item_role(RAccessibleRole role) {
	return role == R_ROLE_LIST_ITEM || role == R_ROLE_TREE_ITEM || role == R_ROLE_TAB;
}

/* ---------------------------------------------------------------------------
 *  Selectors
 * ------------------------------------------------------------------------- */

typedef struct rvn_selector_t {
	int has_role;
	RAccessibleRole role;
	/** 0: no name; 1: name= (exactly); 2: name~= (contains, whatever the case). */
	int name_mode;
	char name[256];
	int has_text;
	char text[256];
	int has_widget;
	char widget[128];
	uint32_t states_on, states_off;
	/** [n], or -1. */
	int index;
} rvn_selector_t;

static int rvn_selector_error(const char* selector, const char* why) {
	rvn_error(R_ERROR_INVALID_VALUE, R_SEVERITY_ERROR, "selector \"%s\": %s", selector, why);
	return rvn_driver_fail("the selector \"%s\" does not parse: %s", selector, why);
}

/* Reads a value after '=': quoted ("buy milk", with \" and \\), or up to the next space. */
static const char* rvn_selector_value(const char* p, char* out, size_t cap, int* ok) {
	size_t length = 0;
	*ok = R_TRUE;
	if (*p == '"') {
		p++;
		while (*p && *p != '"') {
			if (*p == '\\' && (p[1] == '"' || p[1] == '\\')) p++;
			if (length + 1 >= cap) {
				*ok = R_FALSE;
				return p;
			}
			out[length++] = *p++;
		}
		if (*p != '"') {
			*ok = R_FALSE;
			return p;
		}
		p++;
	} else {
		while (*p && !isspace((unsigned char)*p)) {
			if (length + 1 >= cap) {
				*ok = R_FALSE;
				return p;
			}
			out[length++] = *p++;
		}
	}
	out[length] = '\0';
	return p;
}

static const struct {
	const char* word;
	uint32_t on, off;
} g_state_words[] = {
	{ "checked", RVN_A11Y_CHECKED, 0 }, { "selected", RVN_A11Y_SELECTED, 0 }, { "expanded", RVN_A11Y_EXPANDED, 0 },
	{ "focused", RVN_A11Y_FOCUSED, 0 }, { "disabled", RVN_A11Y_DISABLED, 0 }, { "enabled", 0, RVN_A11Y_DISABLED },
};

static int rvn_selector_parse(const char* selector, rvn_selector_t* out) {
	const char* p = selector;
	memset(out, 0, sizeof(*out));
	out->index = -1;
	if (!selector) return rvn_selector_error("(null)", "no selector");
	while (*p) {
		char key[16];
		size_t length = 0;
		int ok;
		while (isspace((unsigned char)*p)) p++;
		if (!*p) break;
		if (*p == '[') {
			char* end;
			long index = strtol(p + 1, &end, 10);
			if (end == p + 1 || *end != ']' || index < 0) return rvn_selector_error(selector, "[n] takes a number from 0");
			out->index = (int)index;
			p = end + 1;
			continue;
		}
		while (*p && (isalnum((unsigned char)*p) || *p == '-' || *p == '_') && length + 1 < sizeof(key)) key[length++] = *p++;
		key[length] = '\0';
		if (length == 0) return rvn_selector_error(selector, "expected a key (role=, name=, text=, widget=), a state or [n]");
		if (*p == '=' || (p[0] == '~' && p[1] == '=')) {
			int contains = *p == '~';
			p += contains ? 2 : 1;
			if (strcmp(key, "role") == 0 && !contains) {
				char role[32];
				size_t i;
				p = rvn_selector_value(p, role, sizeof(role), &ok);
				if (!ok) return rvn_selector_error(selector, "the role is not closed or too long");
				for (i = 0; i < RVN_COUNT_OF(g_roles) && strcmp(g_roles[i].name, role) != 0; i++) {}
				if (i == RVN_COUNT_OF(g_roles)) return rvn_selector_error(selector, "unknown role (the roles are in raven_driver.h)");
				out->has_role = R_TRUE;
				out->role = g_roles[i].role;
			} else if (strcmp(key, "name") == 0) {
				p = rvn_selector_value(p, out->name, sizeof(out->name), &ok);
				if (!ok) return rvn_selector_error(selector, "the name is not closed or too long");
				out->name_mode = contains ? 2 : 1;
			} else if (strcmp(key, "text") == 0 && !contains) {
				p = rvn_selector_value(p, out->text, sizeof(out->text), &ok);
				if (!ok) return rvn_selector_error(selector, "the text is not closed or too long");
				out->has_text = R_TRUE;
			} else if (strcmp(key, "widget") == 0 && !contains) {
				p = rvn_selector_value(p, out->widget, sizeof(out->widget), &ok);
				if (!ok) return rvn_selector_error(selector, "the widget name is not closed or too long");
				out->has_widget = R_TRUE;
			} else {
				return rvn_selector_error(selector, contains ? "only name takes ~=" : "unknown key (role=, name=, text=, widget=)");
			}
			if (*p && !isspace((unsigned char)*p)) return rvn_selector_error(selector, "expected a space after a value");
			continue;
		}
		{
			size_t i;
			for (i = 0; i < RVN_COUNT_OF(g_state_words) && strcmp(g_state_words[i].word, key) != 0; i++) {}
			if (i == RVN_COUNT_OF(g_state_words)) return rvn_selector_error(selector, "unknown state (checked, selected, expanded, focused, disabled, enabled)");
			out->states_on |= g_state_words[i].on;
			out->states_off |= g_state_words[i].off;
		}
	}
	return R_TRUE;
}

static int rvn_driver_contains(const char* text, const char* part) {
	size_t length = strlen(part), i, j;
	if (length == 0) return R_TRUE;
	for (i = 0; text[i]; i++) {
		for (j = 0; j < length && text[i + j] && tolower((unsigned char)text[i + j]) == tolower((unsigned char)part[j]); j++) {}
		if (j == length) return R_TRUE;
	}
	return R_FALSE;
}

/* The text a node shows: what is typed in a field, else its name. */
static const char* rvn_driver_shown_text(const rvn_a11y_node_t* node) {
	switch (node->role) {
		case R_ROLE_TEXT_FIELD: case R_ROLE_DOCUMENT: case R_ROLE_COMBO_BOX: case R_ROLE_SPIN_BUTTON:
			return node->value ? node->value : "";
		default:
			return node->name ? node->name : "";
	}
}

static int rvn_selector_matches(const rvn_selector_t* selector, rvn_a11y_id_t id, const rvn_a11y_node_t* node) {
	if (selector->has_role && node->role != selector->role) return R_FALSE;
	if (selector->name_mode == 1 && strcmp(node->name ? node->name : "", selector->name) != 0) return R_FALSE;
	if (selector->name_mode == 2 && !rvn_driver_contains(node->name ? node->name : "", selector->name)) return R_FALSE;
	if (selector->has_text && strcmp(rvn_driver_shown_text(node), selector->text) != 0) return R_FALSE;
	if ((node->states & selector->states_on) != selector->states_on) return R_FALSE;
	if (node->states & selector->states_off) return R_FALSE;
	if (selector->has_widget) {
		rvn_widget_t* widget;
		/* a window is no widget; an item is its widget's only when asked for by an item role */
		if (rvn_a11y_parent(id) == 0) return R_FALSE;
		if ((id >> 32) && !(selector->has_role && rvn_driver_is_item_role(selector->role))) return R_FALSE;
		widget = rvn_widget_get((Rid)(id & 0xFFFFFFFFu));
		if (!widget || !widget->name || strcmp(widget->name, selector->widget) != 0) return R_FALSE;
	}
	return R_TRUE;
}

/* ---------------------------------------------------------------------------
 *  Finding
 * ------------------------------------------------------------------------- */

typedef struct rvn_search_t {
	const rvn_selector_t* selector;
	/** The match to keep (from 0); the search stops past `stop` matches, 0: never. */
	int want, stop;
	int count;
	rvn_a11y_id_t found;
} rvn_search_t;

static void rvn_driver_walk(rvn_a11y_id_t id, rvn_search_t* search) {
	rvn_a11y_node_t node;
	int i, children;
	if (search->stop > 0 && search->count >= search->stop) return;
	if (!rvn_a11y_describe(id, &node)) return;
	/* (the node's texts are reused by the next describe: matched before the children are) */
	if (rvn_selector_matches(search->selector, id, &node)) {
		if (search->count == search->want) search->found = id;
		search->count++;
	}
	children = rvn_a11y_child_count(id);
	for (i = 0; i < children; i++) rvn_driver_walk(rvn_a11y_child(id, i), search);
}

/* Every open window that is shown, or one. R_FALSE, reported, for a window that isn't one. */
static int rvn_driver_search(Rid window, rvn_search_t* search) {
	if (window != 0 && window != R_INVALID_ID) {
		if (!rvn_window_get(window)) {
			rvn_error(R_ERROR_INVALID_ID, R_SEVERITY_ERROR, "Raccessible: %u is not a window", (unsigned)window);
			return rvn_driver_fail("%u is not a window", (unsigned)window);
		}
		rvn_driver_walk((rvn_a11y_id_t)window, search);
		return R_TRUE;
	}
	{
		int i, count = Rwindow_get_count();
		for (i = 0; i < count; i++) {
			rvn_window_t* open = rvn_window_get(Rwindow_get(i));
			if (open && open->visible) rvn_driver_walk((rvn_a11y_id_t)open->id, search);
		}
	}
	return R_TRUE;
}

int Raccessible_count(Rid window, const char* selector) {
	rvn_selector_t parsed;
	rvn_search_t search;
	RVN_API_GUARD(0);
	if (!rvn_selector_parse(selector, &parsed)) return 0;
	memset(&search, 0, sizeof(search));
	search.selector = &parsed;
	search.want = -1;
	if (!rvn_driver_search(window, &search)) return 0;
	rvn_driver_ok();
	if (parsed.index >= 0) return search.count > parsed.index ? 1 : 0;
	return search.count;
}

RAccessible Raccessible_find(Rid window, const char* selector) {
	rvn_selector_t parsed;
	rvn_search_t search;
	RVN_API_GUARD(0);
	if (!rvn_selector_parse(selector, &parsed)) return 0;
	memset(&search, 0, sizeof(search));
	search.selector = &parsed;
	search.want = parsed.index >= 0 ? parsed.index : 0;
	/* (two are enough to know it is not one) */
	search.stop = parsed.index >= 0 ? 0 : 2;
	if (!rvn_driver_search(window, &search)) return 0;
	if (parsed.index >= 0 && !search.found) {
		rvn_driver_fail("%d accessible%s with %s, so none at %d", search.count, search.count == 1 ? "" : "s", selector, parsed.index);
		return 0;
	}
	if (search.count == 0) {
		rvn_driver_fail("no accessible with %s", selector);
		return 0;
	}
	if (parsed.index < 0 && search.count > 1) {
		/* (counted to the end, for the message) */
		memset(&search, 0, sizeof(search));
		search.selector = &parsed;
		search.want = -1;
		rvn_driver_search(window, &search);
		rvn_driver_fail("%d accessibles with %s, expected one", search.count, selector);
		return 0;
	}
	rvn_driver_ok();
	return search.found;
}

RAccessible Raccessible_get(Rid window, const char* selector, int index) {
	rvn_selector_t parsed;
	rvn_search_t search;
	RVN_API_GUARD(0);
	if (!rvn_selector_parse(selector, &parsed)) return 0;
	if (index < 0 || (parsed.index >= 0 && index > 0)) return 0;
	memset(&search, 0, sizeof(search));
	search.selector = &parsed;
	search.want = parsed.index >= 0 ? parsed.index : index;
	search.stop = search.want + 1;
	if (!rvn_driver_search(window, &search)) return 0;
	rvn_driver_ok();
	return search.found;
}

RAccessible Raccessible_get_root(Rid window) {
	RVN_API_GUARD(0);
	return rvn_window_get(window) ? (RAccessible)window : 0;
}

RAccessible Raccessible_get_parent(RAccessible accessible) {
	RVN_API_GUARD(0);
	return accessible && rvn_a11y_exists(accessible) ? rvn_a11y_parent(accessible) : 0;
}

int Raccessible_get_child_count(RAccessible accessible) {
	RVN_API_GUARD(0);
	return accessible && rvn_a11y_exists(accessible) ? rvn_a11y_child_count(accessible) : 0;
}

RAccessible Raccessible_get_child(RAccessible accessible, int index) {
	RVN_API_GUARD(0);
	if (!accessible || !rvn_a11y_exists(accessible) || index < 0 || index >= rvn_a11y_child_count(accessible)) return 0;
	return rvn_a11y_child(accessible, index);
}

/* ---------------------------------------------------------------------------
 *  Reading
 * ------------------------------------------------------------------------- */

int Raccessible_describe(RAccessible accessible, RAccessibleInfo* info) {
	rvn_a11y_node_t node;
	RVN_API_GUARD(R_FALSE);
	if (!info) return R_FALSE;
	memset(info, 0, sizeof(*info));
	if (!accessible || !rvn_a11y_exists(accessible) || !rvn_a11y_describe(accessible, &node)) return R_FALSE;
	info->role = node.role;
	info->name = rvn_driver_keep(0, node.name);
	info->description = rvn_driver_keep(1, node.description);
	info->value = rvn_driver_keep(2, node.value);
	info->number = node.number;
	info->minimum = node.minimum;
	info->maximum = node.maximum;
	/* (the states of the tree are the public ones, bit for bit) */
	info->states = node.states;
	info->x = node.x;
	info->y = node.y;
	info->width = node.width;
	info->height = node.height;
	info->window = rvn_a11y_window(accessible);
	info->widget = rvn_a11y_parent(accessible) == 0 ? R_INVALID_ID : (Rid)(accessible & 0xFFFFFFFFu);
	info->item = (int)(accessible >> 32) - 1;
	return R_TRUE;
}

/* ---------------------------------------------------------------------------
 *  Acting
 * ------------------------------------------------------------------------- */

/* An accessible in words, for the messages: role=button name="Save". */
static const char* rvn_driver_words(rvn_a11y_id_t id, char* out, size_t cap) {
	rvn_a11y_node_t node;
	if (!rvn_a11y_describe(id, &node)) {
		snprintf(out, cap, "the accessible");
		return out;
	}
	if (node.name && node.name[0]) snprintf(out, cap, "role=%s name=\"%s\"", Raccessible_get_role_name(node.role), node.name);
	else snprintf(out, cap, "role=%s", Raccessible_get_role_name(node.role));
	return out;
}

/* The accessible, read, if it can be used: there, and not disabled. */
static int rvn_driver_usable(RAccessible accessible, rvn_a11y_node_t* node, char* words, size_t cap) {
	if (!accessible || !rvn_a11y_exists(accessible)) {
		snprintf(words, cap, "the accessible");
		return rvn_driver_fail("the accessible is gone");
	}
	rvn_driver_words(accessible, words, cap);
	if (!rvn_a11y_describe(accessible, node)) return rvn_driver_fail("%s is gone", words);
	if (node->states & RVN_A11Y_DISABLED) return rvn_driver_fail("%s is disabled", words);
	return R_TRUE;
}

static rvn_window_t* rvn_driver_window_of(rvn_widget_t* widget) {
	for (; widget; widget = widget->parent) {
		if (widget->window) return widget->window;
	}
	return NULL;
}

static void rvn_driver_click_at(Rid window, float x, float y) {
	Rwindow_inject_mouse_move(window, x, y);
	Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_TRUE);
	Rwindow_inject_mouse_button(window, R_MOUSE_LEFT, R_FALSE);
}

/* Opens the menus a menu entry is in, from the menu bar down, the way a user does. */
static int rvn_driver_open_menus(rvn_widget_t* entry, const char* words) {
	rvn_widget_t* chain[16];
	rvn_window_t* window = rvn_driver_window_of(entry);
	int count = 0, i;
	rvn_widget_t* menu;
	for (menu = entry->parent; menu && menu->cls->type == R_WIDGET_MENU && count < 16; menu = menu->parent) chain[count++] = menu;
	if (!window) return rvn_driver_fail("%s is not in a window", words);
	for (i = count - 1; i >= 0; i--) {
		rvn_rect_t box;
		if (rvn_menu_is_open(chain[i])) continue;
		if (!rvn_menu_entry_box(chain[i], &box)) return rvn_driver_fail("%s is in a menu that is closed, and can't be opened from here", words);
		if (chain[i]->flags & RVN_WF_DISABLED) return rvn_driver_fail("%s is in a menu that is disabled", words);
		/* a title of the menu bar opens on a press; a submenu opens under the pointer */
		Rwindow_inject_mouse_move(window->id, box.x + box.w * 0.5f, box.y + box.h * 0.5f);
		if (chain[i]->parent && chain[i]->parent->cls->type == R_WIDGET_MENU_BAR) {
			Rwindow_inject_mouse_button(window->id, R_MOUSE_LEFT, R_TRUE);
			Rwindow_inject_mouse_button(window->id, R_MOUSE_LEFT, R_FALSE);
		}
		if (!rvn_menu_is_open(chain[i])) return rvn_driver_fail("%s is in a menu that did not open", words);
		/* (a popup just opened has no size until the window is laid out: its entries are placed now) */
		if (window->needs_layout) rvn_window_layout(window);
	}
	return R_TRUE;
}

int Raccessible_click(RAccessible accessible) {
	rvn_a11y_node_t node;
	char words[320];
	rvn_widget_t* widget;
	rvn_rect_t box;
	int item;
	RVN_API_GUARD(R_FALSE);
	if (!rvn_driver_usable(accessible, &node, words, sizeof(words))) return R_FALSE;
	box = rvn_rect(node.x, node.y, node.width, node.height);
	widget = rvn_a11y_parent(accessible) == 0 ? NULL : rvn_widget_get((Rid)(accessible & 0xFFFFFFFFu));
	item = (int)(accessible >> 32) - 1;
	if (widget && item >= 0) {
		/* a row is scrolled into view first */
		int found = widget->cls->type == R_WIDGET_LIST_VIEW ? rvn_list_view_row_box(widget, item, R_TRUE, &box)
			: widget->cls->type == R_WIDGET_TREE_VIEW ? rvn_tree_view_row_box(widget, item, R_TRUE, &box)
			: widget->cls->type == R_WIDGET_TAB_VIEW ? rvn_tab_view_tab_box(widget, item, &box) : R_FALSE;
		if (!found) return rvn_driver_fail("%s is not shown", words);
	} else if (widget && (widget->cls->type == R_WIDGET_MENU_ITEM || widget->cls->type == R_WIDGET_MENU) && widget->parent &&
		(widget->parent->cls->type == R_WIDGET_MENU || widget->parent->cls->type == R_WIDGET_MENU_BAR)) {
		if (!rvn_driver_open_menus(widget, words)) return R_FALSE;
		if (!rvn_menu_entry_box(widget, &box)) return rvn_driver_fail("%s is not shown", words);
	} else if (node.states & RVN_A11Y_OFFSCREEN) {
		return rvn_driver_fail("%s is out of view", words);
	}
	if (box.w <= 0.0f || box.h <= 0.0f) return rvn_driver_fail("%s has no size", words);
	rvn_driver_click_at(rvn_a11y_window(accessible), box.x + box.w * 0.5f, box.y + box.h * 0.5f);
	rvn_driver_ok();
	return R_TRUE;
}

/* Gives an accessible the keyboard, as a prelude to typing into it. */
static int rvn_driver_focus(RAccessible accessible, const char* words) {
	rvn_a11y_node_t node;
	if (!rvn_a11y_act(accessible, RVN_A11Y_FOCUS)) return rvn_driver_fail("%s can't take the keyboard focus", words);
	if (!rvn_a11y_describe(accessible, &node) || !(node.states & RVN_A11Y_FOCUSED)) {
		/* (an item has the focus when its widget has it) */
		rvn_widget_t* widget = rvn_widget_get((Rid)(accessible & 0xFFFFFFFFu));
		if (!widget || !(widget->flags & RVN_WF_FOCUSED)) return rvn_driver_fail("%s can't take the keyboard focus", words);
	}
	return R_TRUE;
}

int Raccessible_type(RAccessible accessible, const char* text) {
	rvn_a11y_node_t node;
	char words[320];
	RVN_API_GUARD(R_FALSE);
	if (!rvn_driver_usable(accessible, &node, words, sizeof(words))) return R_FALSE;
	if (rvn_a11y_parent(accessible) == 0) return rvn_driver_fail("%s is a window: type into what it holds", words);
	if (!rvn_driver_focus(accessible, words)) return R_FALSE;
	if (text && text[0]) Rwindow_inject_text(rvn_a11y_window(accessible), text);
	rvn_driver_ok();
	return R_TRUE;
}

int Raccessible_press_key(RAccessible accessible, RKey key, Ruint modifiers) {
	rvn_a11y_node_t node;
	char words[320];
	Rid window;
	RVN_API_GUARD(R_FALSE);
	if (!rvn_driver_usable(accessible, &node, words, sizeof(words))) return R_FALSE;
	if (rvn_a11y_parent(accessible) != 0 && !rvn_driver_focus(accessible, words)) return R_FALSE;
	window = rvn_a11y_window(accessible);
	Rwindow_inject_key(window, key, R_TRUE, modifiers);
	Rwindow_inject_key(window, key, R_FALSE, modifiers);
	rvn_driver_ok();
	return R_TRUE;
}

int Raccessible_act(RAccessible accessible, RAccessibleAction action) {
	rvn_a11y_node_t node;
	char words[320];
	int done;
	RVN_API_GUARD(R_FALSE);
	if (!rvn_driver_usable(accessible, &node, words, sizeof(words))) return R_FALSE;
	switch (action) {
		case R_ACCESSIBLE_EXPAND:
		case R_ACCESSIBLE_COLLAPSE:
			if (!(node.states & RVN_A11Y_EXPANDABLE)) return rvn_driver_fail("%s can't be expanded or collapsed", words);
			done = rvn_a11y_act(accessible, action == R_ACCESSIBLE_EXPAND ? RVN_A11Y_EXPAND : RVN_A11Y_COLLAPSE);
			break;
		case R_ACCESSIBLE_SELECT:
			if (!(node.states & RVN_A11Y_SELECTABLE)) return rvn_driver_fail("%s can't be selected", words);
			done = rvn_a11y_act(accessible, RVN_A11Y_SELECT);
			break;
		case R_ACCESSIBLE_FOCUS:
			done = rvn_driver_focus(accessible, words);
			if (!done) return R_FALSE;
			break;
		default:
			rvn_error(R_ERROR_INVALID_ENUM, R_SEVERITY_ERROR, "Raccessible_act: invalid action");
			return rvn_driver_fail("invalid action");
	}
	if (!done) return rvn_driver_fail("%s did not take the action", words);
	rvn_driver_ok();
	return R_TRUE;
}

const char* Raccessible_get_last_error(void) {
	return g_driver_error;
}
