/* The notes example's logic: what notes.xml's actions do. */
#include "notes_app.h"
#include <stdio.h>
#include <string.h>

static struct {
	Rid note, list, count, delete_button;
} g_notes;

static void show_count(void) {
	char text[32];
	int count = Rlist_view_get_item_count(g_notes.list);
	if (count == 0) snprintf(text, sizeof(text), "No notes");
	else snprintf(text, sizeof(text), "%d note%s", count, count == 1 ? "" : "s");
	Rlabel_set_text(g_notes.count, text);
}

/* Unicode's White_Space, as Python's and Rust's versions trim: tabs, newlines, spaces, no-break spaces. */
static int is_white_space(unsigned code) {
	return (code >= 0x09 && code <= 0x0D) || code == 0x20 || code == 0x85 || code == 0xA0 || code == 0x1680
		|| (code >= 0x2000 && code <= 0x200A) || code == 0x2028 || code == 0x2029 || code == 0x202F
		|| code == 0x205F || code == 0x3000;
}

/* The code point of the UTF-8 at text[i] (before end); its length in *length. */
static unsigned decode(const char* text, size_t i, size_t end, size_t* length) {
	const unsigned char* s = (const unsigned char*)text + i;
	size_t left = end - i;
	*length = 1;
	if (s[0] < 0x80) return s[0];
	if ((s[0] & 0xE0) == 0xC0 && left >= 2) return *length = 2, ((s[0] & 0x1Fu) << 6) | (s[1] & 0x3Fu);
	if ((s[0] & 0xF0) == 0xE0 && left >= 3) return *length = 3, ((s[0] & 0x0Fu) << 12) | ((s[1] & 0x3Fu) << 6) | (s[2] & 0x3Fu);
	return 0xFFFD; /* a longer code point, never white space, or not UTF-8 */
}

/* notes.add: the text typed, without the whitespace around it, becomes a note, and the field is emptied for the next one. */
static void add(Rid sender, const char* action, void* user_data) {
	const char* text = Rtext_input_get_text(g_notes.note);
	char note[1024];
	size_t first = 0, end, length;
	(void)sender, (void)action, (void)user_data;
	if (!text) return;
	end = strlen(text);
	while (first < end && is_white_space(decode(text, first, end, &length))) first += length;
	while (end > first) {
		size_t last = end - 1;
		while (last > first && ((unsigned char)text[last] & 0xC0) == 0x80) last--; /* the start of the last code point */
		if (!is_white_space(decode(text, last, end, &length)) || last + length != end) break;
		end = last;
	}
	if (end == first) return;
	if (end - first >= sizeof(note)) end = first + sizeof(note) - 1;
	memcpy(note, text + first, end - first);
	note[end - first] = '\0';
	Rlist_view_add_item(g_notes.list, note);
	Rtext_input_set_text(g_notes.note, "");
	show_count();
}

/* notes.delete: the selected note goes. */
static void delete_selected(Rid sender, const char* action, void* user_data) {
	int selected = Rlist_view_get_selected(g_notes.list);
	(void)sender, (void)action, (void)user_data;
	if (selected < 0) return;
	Rlist_view_remove_item(g_notes.list, selected);
	Rlist_view_set_selected(g_notes.list, -1);
	Rwidget_set_enabled(g_notes.delete_button, R_FALSE);
	show_count();
}

/* Delete is there for a selected note only (a ListView's action is fired on activation, so the selection is heard here). */
static void selection_changed(Rid list, int index, void* user_data) {
	(void)list, (void)user_data;
	Rwidget_set_enabled(g_notes.delete_button, index >= 0);
}

Rid notes_open(const char* xml_path) {
	Rid window = Rwindow_create_from_xml_file(xml_path);
	if (window == R_INVALID_ID) return R_INVALID_ID;
	g_notes.note = Rwidget_find(window, "note");
	g_notes.list = Rwidget_find(window, "notes");
	g_notes.count = Rwidget_find(window, "count");
	g_notes.delete_button = Rwidget_find(window, "delete");
	Raction_bind("notes.add", add, NULL);
	Raction_bind("notes.delete", delete_selected, NULL);
	Rlist_view_set_selection_changed_callback(g_notes.list, selection_changed, NULL);
	return window;
}
