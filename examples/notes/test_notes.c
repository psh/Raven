/**
 * Raven example - notes: its headless UI test, through raven/raven_test.h.
 *
 * Add a note, check the list, delete it, as a user would: real clicks and
 * typing on the null backend, no window system and no GPU. ctest runs it
 * (example_notes). With RAVEN_TEST_CAPTURE=1 it runs on headless Vulkan
 * instead, so a failure leaves PNGs of the window in RAVEN_TEST_CAPTURES.
 */
#include <Raven.h>
#include <raven/raven_test.h>
#include "notes_app.h"

#ifndef RAVEN_EXAMPLE_DIR
#define RAVEN_EXAMPLE_DIR "."
#endif

int main(void) {
	RTestOptions options = { 0 };
	options.name = "test_notes";
	options.capture = getenv("RAVEN_TEST_CAPTURE") != NULL;
	if (!Rtest_begin(&options)) return RTEST_SKIPPED;
	if (!RTEST_CHECK(notes_open(RAVEN_EXAMPLE_DIR "/notes.xml") != R_INVALID_ID)) return Rtest_end();

	/* nothing yet: Delete waits for a note to be selected */
	RTEST_EXPECT_ABSENT("role=list-item");
	RTEST_EXPECT_TEXT("widget=count", "No notes");
	RTEST_EXPECT_ENABLED("role=button name=\"Delete\"", R_FALSE);

	/* add two: with the button, and with Enter */
	RTEST_TYPE("role=text-field name=\"New note\"", "buy milk");
	RTEST_CLICK("role=button name=\"Add\"");
	RTEST_TYPE("role=text-field", "call Oscar");
	RTEST_KEY("role=text-field", R_KEY_ENTER, 0);

	/* the list holds them in order, and the field is empty for the next one */
	RTEST_EXPECT_COUNT("role=list-item", 2);
	RTEST_EXPECT_TEXT("role=list-item [0]", "buy milk");
	RTEST_EXPECT_TEXT("role=list-item [1]", "call Oscar");
	RTEST_EXPECT_TEXT("role=text-field", "");
	RTEST_EXPECT_TEXT("widget=count", "2 notes");

	/* delete the first: select it, then Delete */
	RTEST_CLICK("role=list-item text=\"buy milk\"");
	RTEST_EXPECT_ENABLED("role=button name=\"Delete\"", R_TRUE);
	RTEST_CLICK("role=button name=\"Delete\"");
	RTEST_EXPECT_ABSENT("role=list-item text=\"buy milk\"");
	RTEST_EXPECT_EXISTS("role=list-item text=\"call Oscar\"");
	RTEST_EXPECT_TEXT("widget=count", "1 note");
	RTEST_EXPECT_ENABLED("role=button name=\"Delete\"", R_FALSE);

	/* an empty note is not added */
	RTEST_CLICK("role=button name=\"Add\"");
	RTEST_EXPECT_COUNT("role=list-item", 1);

	/* nor is a note of only whitespace (a no-break space: typing drops a tab or a newline) */
	RTEST_TYPE("role=text-field", " \xc2\xa0 ");
	RTEST_CLICK("role=button name=\"Add\"");
	RTEST_EXPECT_COUNT("role=list-item", 1);
	RTEST_EXPECT_TEXT("widget=count", "1 note");

	/* a note is added without the whitespace around it */
	RTEST_TYPE("role=text-field", "  water plants\xc2\xa0");
	RTEST_KEY("role=text-field", R_KEY_ENTER, 0);
	RTEST_EXPECT_TEXT("role=list-item [1]", "water plants");

	/* deleting the last notes leaves the label saying so */
	RTEST_CLICK("role=list-item [0]");
	RTEST_CLICK("role=button name=\"Delete\"");
	RTEST_CLICK("role=list-item [0]");
	RTEST_CLICK("role=button name=\"Delete\"");
	RTEST_EXPECT_ABSENT("role=list-item");
	RTEST_EXPECT_TEXT("widget=count", "No notes");

	/* the Delete key deletes the selected note, as the button does */
	RTEST_TYPE("role=text-field", "buy milk");
	RTEST_KEY("role=text-field", R_KEY_ENTER, 0);
	RTEST_CLICK("role=list-item text=\"buy milk\"");
	RTEST_KEY("role=list", R_KEY_DELETE, 0);
	RTEST_EXPECT_ABSENT("role=list-item");
	RTEST_EXPECT_TEXT("widget=count", "No notes");
	RTEST_EXPECT_ENABLED("role=button name=\"Delete\"", R_FALSE);

	/* in the text field, Delete edits the text and deletes no note, even with one selected:
	   Left then Delete removes the b, and Delete at the end of the text does nothing */
	RTEST_TYPE("role=text-field", "call Oscar");
	RTEST_KEY("role=text-field", R_KEY_ENTER, 0);
	RTEST_CLICK("role=list-item text=\"call Oscar\"");
	RTEST_TYPE("role=text-field", "ab");
	RTEST_KEY("role=text-field", R_KEY_LEFT, 0);
	RTEST_KEY("role=text-field", R_KEY_DELETE, 0);
	RTEST_EXPECT_TEXT("role=text-field", "a");
	RTEST_KEY("role=text-field", R_KEY_END, 0);
	RTEST_KEY("role=text-field", R_KEY_DELETE, 0);
	RTEST_EXPECT_TEXT("role=text-field", "a");
	RTEST_EXPECT_EXISTS("role=list-item text=\"call Oscar\" selected");
	RTEST_EXPECT_TEXT("widget=count", "1 note");

	/* with no note selected, Delete changes nothing (Delete on the list first, to delete call Oscar) */
	RTEST_KEY("role=list", R_KEY_DELETE, 0);
	RTEST_EXPECT_ABSENT("role=list-item");
	RTEST_TYPE("role=text-field", "x");
	RTEST_KEY("role=text-field", R_KEY_ENTER, 0);
	RTEST_EXPECT_ABSENT("role=list-item selected");
	RTEST_KEY("role=list", R_KEY_DELETE, 0);
	RTEST_EXPECT_COUNT("role=list-item", 1);

	return Rtest_end();
}
