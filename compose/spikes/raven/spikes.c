/*
 * compose-raven verify-first spikes (group A): facts about Raven that the spec depends on.
 * Runs headless (null backend, null platform). Each check prints PASS or FAIL.
 * Build and run: ./run.sh
 */
#include <Raven.h>
#include <stdio.h>
#include <string.h>

static int failures = 0;
static void check(int ok, const char* what) {
	printf("%s  %s\n", ok ? "PASS" : "FAIL", what);
	if (!ok) failures++;
}

static int init_headless(void) {
	RInitDesc desc;
	memset(&desc, 0, sizeof(desc));
	desc.backend = R_BACKEND_NULL;
	desc.headless = R_TRUE;
	desc.worker_threads = -1;
	return Rinit_ex(&desc);
}

static size_t blocks_held(void) {
	RMemoryStats stats;
	memset(&stats, 0, sizeof(stats));
	Rmemory_get_stats(&stats);
	return stats.allocation_count;
}

/* 1. Rinit/Rshutdown repeated in one process, with a window, widgets and frames each time. */
static void spike_repeated_init(void) {
	int round, ok = 1;
	size_t after[3];
	for (round = 0; round < 3; round++) {
		Rid window, box, label;
		if (!init_headless()) { ok = 0; break; }
		window = Rwindow_create("spike", 320, 200, R_WINDOW_DEFAULT);
		box = Rwidget_create(R_WIDGET_VBOX);
		label = Rlabel_create("hello");
		Rwidget_add_child(box, label);
		Rwindow_set_content(window, box);
		Rdraw_frame();
		Rdraw_frame();
		if (Rwidget_get_parent(label) != box) ok = 0;
		Rshutdown();
		after[round] = blocks_held();
	}
	check(ok, "Rinit/Rshutdown can be repeated three times in one process");
	if (ok) {
		printf("      blocks held after each Rshutdown: %zu, %zu, %zu\n", after[0], after[1], after[2]);
		check(after[0] == 0 && after[1] == 0 && after[2] == 0, "no Raven memory is held after each Rshutdown");
	}
}

/* 2. Parsing a document with a <Widget> declaration registers nothing and leaves it unexpanded. */
static void spike_xml_parse_registers_nothing(void) {
	static const char xml[] =
		"<Widgets>"
		"  <Widget Name=\"SpikeCard\" Title=\"x\"><Label Text=\"{Title}\"/></Widget>"
		"</Widgets>";
	Rid document, root;
	RXmlError error;
	init_headless();
	memset(&error, 0, sizeof(error));
	document = Rxml_document_parse(xml, sizeof(xml) - 1, &error);
	root = Rxml_document_get_root(document);
	check(document != R_INVALID_ID, "Rxml_document_parse parses a <Widgets> document");
	check(!Rxml_is_widget_registered("SpikeCard"), "parsing does not register the <Widget> declaration");
	check(Rxml_node_get_child_count(root) == 1 && strcmp(Rxml_node_get_tag(Rxml_node_get_child(root, 0)), "Widget") == 0,
		"the <Widget> stays an unexpanded node in the document");
	Rxml_document_destroy(document);
	Rshutdown();
}

/* 3. Focusing the root (a non-focusable panel) clears the focus. */
static void spike_focus_nothing(void) {
	Rid window, root, input, focused_before, focused_after, focused_null;
	init_headless();
	window = Rwindow_create("spike", 320, 200, R_WINDOW_DEFAULT);
	root = Rwidget_create(R_WIDGET_OVERLAY);
	input = Rwidget_create(R_WIDGET_TEXT_INPUT);
	Rwidget_add_child(root, input);
	Rwindow_set_content(window, root);
	Rdraw_frame();
	Rwidget_focus(input);
	Rdraw_frame();
	focused_before = Rwindow_get_focused_widget(window);
	Rwidget_focus(root);
	Rdraw_frame();
	focused_after = Rwindow_get_focused_widget(window);
	check(focused_before == input, "Rwidget_focus focuses a text input");
	printf("      after Rwidget_focus(root): focused = %s\n",
		focused_after == R_INVALID_ID ? "nothing" : focused_after == root ? "the root" : focused_after == input ? "still the input" : "something else");
	check(focused_after != input, "focusing the root (as the spec assumed) takes the focus away from the input");
	Rwidget_focus(input);
	Rdraw_frame();
	Rwidget_focus(R_INVALID_ID);
	Rdraw_frame();
	focused_null = Rwindow_get_focused_widget(window);
	printf("      after Rwidget_focus(R_INVALID_ID): focused = %s\n",
		focused_null == R_INVALID_ID ? "nothing" : focused_null == input ? "still the input" : "something else");
	/* workaround with public API only: drop and restore the focused widget's focusable flag */
	Rwidget_set_focusable(input, R_FALSE);
	Rwidget_set_focusable(input, R_TRUE);
	Rdraw_frame();
	check(Rwindow_get_focused_widget(window) == R_INVALID_ID, "toggling the focused widget's focusable flag clears the focus");
	Rwidget_focus(input);
	Rdraw_frame();
	check(Rwindow_get_focused_widget(window) == input, "the input can take the focus again afterwards");
	Rshutdown();
}

/* 4. A MenuItem with Separator="true" is a separator; a plain Separator widget is not an entry. */
static void spike_menu_separator(void) {
	Rid bar, menu, item, sep_widget, sep_item;
	init_headless();
	bar = Rwidget_create(R_WIDGET_MENU_BAR);
	menu = Rmenu_add_menu(bar, "File");
	item = Rmenu_add_item(menu, "Open");
	sep_widget = Rwidget_create(R_WIDGET_SEPARATOR);
	sep_item = Rwidget_create(R_WIDGET_MENU_ITEM);
	Rwidget_insert_child(menu, sep_widget, -1);
	check(Rwidget_get_parent(sep_widget) == menu, "a plain Separator widget can be inserted into a Menu");
	Rwidget_remove_child(menu, sep_widget);
	Rwidget_destroy(sep_widget);
	check(Rwidget_set_attribute(sep_item, "Separator", "true"), "a MenuItem accepts the Separator attribute");
	Rwidget_insert_child(menu, sep_item, 0);
	check(Rwidget_get_child(menu, 0) == sep_item && Rwidget_get_child(menu, 1) == item,
		"the separator MenuItem can be inserted at an index");
	Rwidget_destroy(bar);
	Rshutdown();
}

int main(void) {
	spike_repeated_init();
	spike_xml_parse_registers_nothing();
	spike_focus_nothing();
	spike_menu_separator();
	printf("%d failure(s)\n", failures);
	return failures ? 1 : 0;
}
