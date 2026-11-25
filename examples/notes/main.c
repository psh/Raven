/**
 * Raven example - notes
 * A list of notes: type one, Add (or Enter), select it, Delete.
 *
 *   notes.xml       the interface, shared by every language's version and test
 *   notes_app.c     what its actions do (notes.add, notes.delete)
 *   test_notes.c    its headless UI test, through raven/raven_test.h (ctest: example_notes)
 */

#include "../common/example.h"
#include "notes_app.h"

#ifndef RAVEN_EXAMPLE_DIR
#define RAVEN_EXAMPLE_DIR "."
#endif

int main(void) {
	Rid window;
	if (!example_init("notes")) return 1;
	window = notes_open(RAVEN_EXAMPLE_DIR "/notes.xml");
	if (window == R_INVALID_ID) {
		Rshutdown();
		return 1;
	}
	example_run(window, NULL, NULL);
	return 0;
}
