The harness is `raven/raven_test.h`, header-only, included in the one file of a test:

```c
#include <Raven.h>
#include <raven/raven_test.h>

int main(void) {
    if (!Rtest_begin(NULL)) return RTEST_SKIPPED;       /* 77: only with capture, without Vulkan */
    notes_open("notes.xml");                            /* the app's code: builds the window, binds its actions */
    RTEST_TYPE("role=text-field", "buy milk");
    RTEST_CLICK("role=button name=\"Add\"");
    RTEST_EXPECT_TEXT("role=list-item [0]", "buy milk");
    RTEST_EXPECT_ENABLED("role=button name=\"Delete\"", R_FALSE);
    return Rtest_end();                                 /* 1 on a failure, or on memory Raven still holds */
}
```

```cmake
add_test(NAME notes_ui COMMAND test_notes)
set_tests_properties(notes_ui PROPERTIES SKIP_RETURN_CODE 77)
```

The macros and `RTestOptions` (capture, a failure hook for another test runner) are listed at the
top of `raven/raven_test.h`. Each macro returns `R_FALSE` after reporting, and the test goes on:
`if (!RTEST_CLICK(...)) return Rtest_end();` stops it. Raven's repository has the worked example,
`examples/notes/test_notes.c`.
