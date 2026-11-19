/**
 * Raven tests - the UI driver (raven_driver.h): selectors, finding, reading and acting, over the
 * examples' XML (notepad, editor) and a few windows of its own.
 */

#include "test.h"

static int g_fired[8];
static const char* const g_actions[8] = { "file.save", "file.save_as", "view.wrap", "view.compact", "go", "notepad.add" };

static void on_action(Rid sender, const char* action, void* user_data) {
	(void)sender;
	(void)user_data;
	for (int i = 0; i < 8 && g_actions[i]; i++) {
		if (strcmp(g_actions[i], action) == 0) g_fired[i]++;
	}
}

static void bind_all(void) {
	memset(g_fired, 0, sizeof(g_fired));
	for (int i = 0; i < 8 && g_actions[i]; i++) Raction_bind(g_actions[i], on_action, NULL);
}

static Rid example(const char* name) {
	char path[512];
	Rid window;
	snprintf(path, sizeof(path), "%s/%s/%s.xml", RAVEN_EXAMPLES_DIR, name, name);
	window = Rwindow_create_from_xml_file(path);
	CHECK(Rwindow_is_valid(window));
	Rwait_idle(20);
	return window;
}

static Rid window_from(const char* xml) {
	Rid window = Rwindow_create_from_xml(xml, strlen(xml));
	CHECK(Rwindow_is_valid(window));
	Rwait_idle(20);
	return window;
}

static void test_finding_by_each_key(void) {
	Rid window = example("notepad");
	RAccessible save;
	RAccessibleInfo info;

	CHECK(Raccessible_count(window, "role=window name=\"Raven Notepad\"") == 1);
	CHECK(Raccessible_count(0, "role=menu-bar") == 1);
	CHECK(Raccessible_count(window, "role=menu name=\"File\"") == 1);
	save = Raccessible_find(window, "role=menu-item name=\"Save\"");
	CHECK(save != 0);
	CHECK(Raccessible_count(window, "name~=\"SAVE\"") == 2);   /* Save, Save as... */
	CHECK(Raccessible_count(window, "role=menu-item name~=save [1]") == 1);
	CHECK(Raccessible_count(window, "role=menu-item checked") == 1);   /* Line numbers */
	CHECK(Raccessible_count(window, "role=menu-item name=\"Word wrap\" checked") == 0);
	CHECK(Raccessible_count(window, "widget=editor") == 1);
	CHECK(Raccessible_count(window, "role=document widget=editor focused") == 0);
	CHECK(Raccessible_count(window, "role=menu-item enabled") == Raccessible_count(window, "role=menu-item"));

	/* a menu item in a closed menu is found, but off screen */
	CHECK(Raccessible_describe(save, &info));
	CHECK(info.role == R_ROLE_MENU_ITEM);
	CHECK_STR(info.name, "Save");
	CHECK((info.states & R_ACCESSIBLE_OFFSCREEN) != 0);
	CHECK(info.window == window);
	CHECK(info.item == -1);
	CHECK(info.widget != R_INVALID_ID);

	/* text: what a label shows, what is typed in a field */
	Rlabel_set_text(Rwidget_find(window, "status"), "Saved");
	CHECK(Raccessible_count(window, "text=Saved") == 1);
	CHECK(Raccessible_count(window, "role=text text=\"Saved\" widget=status") == 1);

	/* the tree, from the window down */
	{
		RAccessible root = Raccessible_get_root(window);
		CHECK(root != 0 && Raccessible_get_parent(root) == 0);
		CHECK(Raccessible_get_child_count(root) > 0);
		CHECK(Raccessible_get_parent(Raccessible_get_child(root, 0)) == root);
		CHECK(Raccessible_get_child(root, 999) == 0);
		CHECK(Raccessible_get(window, "role=menu-item", 0) != 0);
		CHECK(Raccessible_get(window, "role=menu-item", 999) == 0);
		CHECK_STR(Raccessible_get_role_name(R_ROLE_LIST_ITEM), "list-item");
		CHECK_STR(Raccessible_get_role_name(R_ROLE_TEXT_FIELD), "text-field");
	}
	Rwindow_destroy(window);
	Rwait_idle(10);
}

static void test_the_failure_messages(void) {
	Rid window = example("notepad");
	int items = Raccessible_count(window, "role=menu-item");
	char expected[128];

	CHECK(Raccessible_find(window, "role=button name=\"Nope\"") == 0);
	CHECK_STR(Raccessible_get_last_error(), "no accessible with role=button name=\"Nope\"");
	CHECK(Raccessible_find(window, "role=menu-item") == 0);
	snprintf(expected, sizeof(expected), "%d accessibles with role=menu-item, expected one", items);
	CHECK_STR(Raccessible_get_last_error(), expected);
	CHECK(Raccessible_find(window, "role=menu [9]") == 0);
	CHECK_STR(Raccessible_get_last_error(), "3 accessibles with role=menu [9], so none at 9");
	CHECK(Raccessible_find(window, "role=menu [1]") != 0);
	CHECK_STR(Raccessible_get_last_error(), "");

	/* a selector that doesn't parse is reported, and finds nothing */
	EXPECT_ERRORS_BEGIN();
	CHECK(Raccessible_count(window, "role=nonsense") == 0);
	CHECK(strstr(Raccessible_get_last_error(), "does not parse") != NULL);
	CHECK(Raccessible_count(window, "name=\"open") == 0);
	CHECK(Raccessible_count(window, "shiny") == 0);
	CHECK(Raccessible_count(window, "[x]") == 0);
	CHECK(Raccessible_count(window, "text~=a") == 0);
	EXPECT_ERRORS_END(5);
	Rwindow_destroy(window);
	Rwait_idle(10);
}

static void test_menus_are_opened_through_their_menus(void) {
	Rid window = example("notepad");
	RAccessible save = Raccessible_find(window, "role=menu-item name=\"Save\"");
	RAccessibleInfo info;
	bind_all();
	CHECK(Raccessible_click(save));
	Rwait_idle(10);
	CHECK(g_fired[0] == 1);
	/* the menu closed again once the item was chosen */
	CHECK(Raccessible_describe(save, &info) && (info.states & R_ACCESSIBLE_OFFSCREEN));
	CHECK(Raccessible_count(window, "role=menu expanded") == 0);
	Rwindow_destroy(window);

	/* a submenu: View, then Scale, then Compact */
	window = example("editor");
	CHECK(Raccessible_click(Raccessible_find(window, "role=menu-item name=\"Compact\"")));
	Rwait_idle(10);
	CHECK(g_fired[3] == 1);
	Rwindow_destroy(window);
	Rwait_idle(10);
}

static void test_rows_and_tabs(void) {
	Rid window = example("editor");
	Rid log = Rwidget_find(window, "log"), outliner = Rwidget_find(window, "outliner");
	RAccessibleInfo first, second;
	Rid scene, cube;
	char selector[64];

	for (int i = 0; i < 60; i++) {
		snprintf(selector, sizeof(selector), "line %d", i);
		Rlist_view_add_item(log, selector);
	}
	scene = Rtree_view_add_item(outliner, R_INVALID_ID, "Scene");
	cube = Rtree_view_add_item(outliner, scene, "Cube");
	Rtree_view_add_item(outliner, R_INVALID_ID, "Camera");
	Rwait_idle(20);

	/* rows carry their own rects, not their list's */
	CHECK(Raccessible_describe(Raccessible_find(window, "role=list-item name=\"line 0\""), &first));
	CHECK(Raccessible_describe(Raccessible_find(window, "role=list-item name=\"line 1\""), &second));
	CHECK(first.height > 0.0f && second.y >= first.y + first.height - 0.5f);
	CHECK(first.item == 0 && second.item == 1 && first.widget == log);
	CHECK(Raccessible_count(window, "role=list-item widget=log") == 60);
	CHECK(Raccessible_count(window, "widget=log") == 1);   /* the list, not its rows */

	/* a row far down is scrolled to, then clicked */
	{
		RAccessible far = Raccessible_find(window, "role=list-item name=\"line 55\"");
		RAccessibleInfo info;
		CHECK(Raccessible_describe(far, &info) && (info.states & R_ACCESSIBLE_OFFSCREEN));
		CHECK(Raccessible_click(far));
		Rwait_idle(10);
		CHECK(Rlist_view_get_selected(log) == 55);
		CHECK(Raccessible_describe(far, &info) && !(info.states & R_ACCESSIBLE_OFFSCREEN) && (info.states & R_ACCESSIBLE_SELECTED));
		CHECK(Raccessible_count(window, "role=list-item selected") == 1);
	}

	/* tree rows: the nested ones appear when their parent is expanded */
	CHECK(Raccessible_count(window, "role=tree-item") == 2);
	CHECK(!Raccessible_act(Raccessible_find(window, "role=tree-item name=\"Camera\""), R_ACCESSIBLE_EXPAND));
	CHECK(strstr(Raccessible_get_last_error(), "can't be expanded") != NULL);
	CHECK(Raccessible_act(Raccessible_find(window, "role=tree-item name=\"Scene\""), R_ACCESSIBLE_EXPAND));
	Rwait_idle(10);
	CHECK(Raccessible_count(window, "role=tree-item") == 3);
	CHECK(Raccessible_count(window, "role=tree-item expanded") == 1);
	CHECK(Raccessible_click(Raccessible_find(window, "role=tree-item name=\"Cube\"")));
	Rwait_idle(10);
	CHECK(Rtree_view_get_selected_item(outliner) == cube);

	/* tabs */
	CHECK(Raccessible_count(window, "role=tab") == 2);
	CHECK(Raccessible_count(window, "role=tab name=\"Scene\" selected") == 1);
	CHECK(Raccessible_click(Raccessible_find(window, "role=tab name=\"Profiler\"")));
	Rwait_idle(10);
	CHECK(Raccessible_count(window, "role=tab name=\"Profiler\" selected") == 1);
	Rwindow_destroy(window);
	Rwait_idle(10);
}

static void test_typing_keys_and_a_disabled_button(void) {
	Rid window = window_from(
		"<Window Title=\"Form\" Width=\"300\" Height=\"200\"><VBox>"
		"<TextInput Name=\"note\" Placeholder=\"Note\"/>"
		"<Button Name=\"go\" Text=\"Go\" OnClicked=\"go\"/>"
		"<Button Name=\"add\" OnClicked=\"notepad.add\" AccessibleName=\"Add a note\" AccessibleDescription=\"Adds it to the list\"><HBox><Label Text=\"+\"/><Label Text=\"Add\"/></HBox></Button>"
		"</VBox></Window>");
	RAccessible note = Raccessible_find(window, "role=text-field widget=note");
	RAccessible go = Raccessible_find(window, "role=button name=\"Go\"");
	bind_all();

	CHECK(Raccessible_type(note, "buy milk"));
	Rwait_idle(10);
	CHECK_STR(Rtext_input_get_text(Rwidget_find(window, "note")), "buy milk");
	CHECK(Raccessible_count(window, "role=text-field text=\"buy milk\" focused") == 1);
	CHECK(Raccessible_press_key(note, R_KEY_BACKSPACE, 0));
	Rwait_idle(10);
	CHECK_STR(Rtext_input_get_text(Rwidget_find(window, "note")), "buy mil");

	CHECK(Raccessible_click(go));
	Rwait_idle(10);
	CHECK(g_fired[4] == 1);
	Rwidget_set_enabled(Rwidget_find(window, "go"), R_FALSE);
	Rwait_idle(10);
	CHECK(Raccessible_count(window, "role=button name=\"Go\" disabled") == 1);
	CHECK(!Raccessible_click(go));
	CHECK_STR(Raccessible_get_last_error(), "role=button name=\"Go\" is disabled");
	Rwait_idle(10);
	CHECK(g_fired[4] == 1);

	/* the fixes that came with the driver */
	CHECK_STR(Rbutton_get_text(Rwidget_find(window, "go")), "Go");
	CHECK_STR(Rbutton_get_text(Rwidget_find(window, "add")), "+");
	CHECK_STR(Rwidget_get_accessible_description(Rwidget_find(window, "add")), "Adds it to the list");
	CHECK(Rwidget_get_accessible_description(Rwidget_find(window, "go")) == NULL);
	CHECK(Raccessible_click(Raccessible_find(window, "role=button name=\"Add a note\"")));
	Rwait_idle(10);
	CHECK(g_fired[5] == 1);

	/* gone */
	Rwindow_destroy(window);
	Rwait_idle(10);
	CHECK(!Raccessible_click(go));
	CHECK_STR(Raccessible_get_last_error(), "the accessible is gone");
}

int main(void) {
	if (!test_init(0)) {
		printf("test_driver: cannot start Raven\n");
		return 1;
	}
	RUN(test_finding_by_each_key);
	RUN(test_the_failure_messages);
	RUN(test_menus_are_opened_through_their_menus);
	RUN(test_rows_and_tabs);
	RUN(test_typing_keys_and_a_disabled_button);
	Rshutdown();
	return test_report("test_driver");
}
