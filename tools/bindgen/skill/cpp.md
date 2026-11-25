The C harness works from C++ as it is: `raven/raven_test.h`, header-only, included in the one file
of a test, beside `raven.hpp`.

```cpp
#include <raven.hpp>
#include <raven/raven_test.h>

int main() {
    if (!Rtest_begin(nullptr)) return RTEST_SKIPPED;    // 77: only with capture, without Vulkan
    notes_open("notes.xml");                            // the app's code: builds the window, binds its actions
    RTEST_TYPE("role=text-field", "buy milk");
    RTEST_CLICK(R"(role=button name="Add")");
    RTEST_EXPECT_TEXT("role=list-item [0]", "buy milk");
    return Rtest_end();                                 // 1 on a failure, or on memory Raven still holds
}
```

```cmake
add_test(NAME notes_ui COMMAND test_notes)
set_tests_properties(notes_ui PROPERTIES SKIP_RETURN_CODE 77)
```

The macros and `RTestOptions` (capture, a failure hook for GoogleTest, Catch2 or another runner)
are listed at the top of `raven/raven_test.h`.
