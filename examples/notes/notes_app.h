/* The notes example's logic (notes_app.c), shared by the app (main.c) and its test (test_notes.c). */
#ifndef NOTES_APP_H
#define NOTES_APP_H
#include <Raven.h>

/** Builds the window from notes.xml and binds notes.add and notes.delete. @return The window, or R_INVALID_ID. */
Rid notes_open(const char* xml_path);

#endif
