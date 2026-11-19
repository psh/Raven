/**
 * Raven tests - listing the actions: Raction_get_count, Raction_get_name, descriptions and the list callback.
 */

#include "test.h"

static int g_fired_a, g_fired_b;

static void on_a(Rid sender, const char* action, void* user_data) {
	(void)sender;
	(void)action;
	(void)user_data;
	g_fired_a++;
}

static void on_b(Rid sender, const char* action, void* user_data) {
	(void)sender;
	(void)action;
	(void)user_data;
	g_fired_b++;
}

/* What the list callback heard: "+name" on bind, "-name" on unbind, separated by spaces. */
static char g_heard[512];

static void on_list(const char* name, int bound, void* user_data) {
	size_t used = strlen(g_heard);
	(void)user_data;
	snprintf(g_heard + used, sizeof(g_heard) - used, "%s%c%s", used ? " " : "", bound ? '+' : '-', name);
}

static void test_listing(void) {
	Rid window;
	CHECK(Raction_get_count() == 0);
	CHECK(Raction_get_name(0) == NULL);
	Raction_bind("file.open", on_a, NULL);
	Raction_bind("file.save", on_a, NULL);
	Raction_bind("edit.undo", on_a, NULL);
	CHECK(Raction_get_count() == 3);
	CHECK_STR(Raction_get_name(0), "file.open");
	CHECK_STR(Raction_get_name(1), "file.save");
	CHECK_STR(Raction_get_name(2), "edit.undo");
	CHECK(Raction_get_name(3) == NULL);
	CHECK(Raction_get_name(-1) == NULL);

	/* the others keep their order */
	Raction_unbind("file.save");
	CHECK(Raction_get_count() == 2);
	CHECK_STR(Raction_get_name(0), "file.open");
	CHECK_STR(Raction_get_name(1), "edit.undo");

	/* rebinding is not a second action */
	Raction_bind("file.open", on_b, NULL);
	CHECK(Raction_get_count() == 2);

	/* names in XML and built-in actions are not bound actions */
	{
		const char* xml = "<Window><Button OnClicked=\"notes.add\"/></Window>";
		window = Rwindow_create_from_xml(xml, strlen(xml));
	}
	Raction_fire("theme.dark", R_INVALID_ID);
	CHECK(Raction_get_count() == 2);
	Rwindow_destroy(window);

	Raction_unbind("file.open");
	Raction_unbind("edit.undo");
	CHECK(Raction_get_count() == 0);
}

static void test_descriptions(void) {
	g_fired_a = g_fired_b = 0;
	Raction_bind("file.save", on_a, NULL);
	CHECK(Raction_get_description("file.save") == NULL);
	Raction_set_description("file.save", "Saves the document");
	CHECK_STR(Raction_get_description("file.save"), "Saves the document");

	/* a new function keeps the description */
	Raction_bind("file.save", on_b, NULL);
	CHECK_STR(Raction_get_description("file.save"), "Saves the document");
	Raction_fire("file.save", R_INVALID_ID);
	CHECK(g_fired_a == 0 && g_fired_b == 1);

	Raction_set_description("file.save", "Saves it");
	CHECK_STR(Raction_get_description("file.save"), "Saves it");
	Raction_set_description("file.save", "");
	CHECK(Raction_get_description("file.save") == NULL);
	Raction_set_description("file.save", "Saves the document");

	/* unbinding drops it: bound again, the action starts without one */
	Raction_unbind("file.save");
	CHECK(Raction_get_description("file.save") == NULL);
	Raction_bind("file.save", on_a, NULL);
	CHECK(Raction_get_description("file.save") == NULL);
	Raction_unbind("file.save");

	/* only a bound action can be described */
	EXPECT_ERRORS_BEGIN();
	Raction_set_description("not.bound", "Nothing");
	EXPECT_ERRORS_END(1);
	CHECK(Raction_get_description("not.bound") == NULL);
	CHECK(Raction_get_description(NULL) == NULL);
}

static void bind_from_list(const char* name, int bound, void* user_data) {
	on_list(name, bound, user_data);
	/* the callback may bind and unbind too */
	if (bound && strcmp(name, "first") == 0) Raction_bind("second", on_a, NULL);
}

static void test_the_list_callback(void) {
	g_heard[0] = '\0';
	Raction_set_list_callback(on_list, NULL);
	Raction_bind("a", on_a, NULL);
	Raction_bind("b", on_a, NULL);
	/* a new function only: nothing to hear */
	Raction_bind("a", on_b, NULL);
	Raction_unbind("a");
	/* unbinding with a NULL function, and a name that isn't bound */
	Raction_bind("b", NULL, NULL);
	Raction_unbind("never.bound");
	CHECK_STR(g_heard, "+a +b -a -b");

	/* the name read from the list is freed only after the callback has it */
	g_heard[0] = '\0';
	Raction_bind("read.from.list", on_a, NULL);
	Raction_unbind(Raction_get_name(0));
	CHECK_STR(g_heard, "+read.from.list -read.from.list");
	CHECK(Raction_get_count() == 0);

	g_heard[0] = '\0';
	Raction_set_list_callback(bind_from_list, NULL);
	Raction_bind("first", on_a, NULL);
	CHECK_STR(g_heard, "+first +second");
	CHECK(Raction_get_count() == 2);

	/* no callback, nothing heard */
	g_heard[0] = '\0';
	Raction_set_list_callback(NULL, NULL);
	Raction_unbind("first");
	Raction_unbind("second");
	CHECK_STR(g_heard, "");
	CHECK(Raction_get_count() == 0);
}

int main(void) {
	if (!test_init(0)) {
		printf("test_actions: cannot start Raven\n");
		return 1;
	}
	RUN(test_listing);
	RUN(test_descriptions);
	RUN(test_the_list_callback);
	Rshutdown();
	return test_report("test_actions");
}
