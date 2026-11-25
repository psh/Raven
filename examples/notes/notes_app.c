/* The notes example's logic: what notes.xml's actions do. */
#include "notes_app.h"
#include <stdio.h>

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

/* notes.add: the text typed becomes a note, and the field is emptied for the next one. */
static void add(Rid sender, const char* action, void* user_data) {
	const char* text = Rtext_input_get_text(g_notes.note);
	(void)sender, (void)action, (void)user_data;
	if (!text || !text[0]) return;
	Rlist_view_add_item(g_notes.list, text);
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
