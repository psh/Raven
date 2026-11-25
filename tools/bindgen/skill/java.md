A test in any runner (JUnit here), calling the driver through `raven.R` (Java 22 or later):

```java
import static raven.R.*;

@Test
void addsANote() {
    assertTrue(Rinit_ex(new RInitDesc().backend(R_BACKEND_NULL).headless(1)) != 0, "Raven didn't start");
    try {
        Rset_animation_time_step(1.0 / 60);
        Notes.open("notes.xml");                         // the app's code: builds the window, binds its actions
        acted(Raccessible_type(find("role=text-field"), "buy milk"));
        acted(Raccessible_click(find("role=button name=\"Add\"")));
        assertEquals(1, Raccessible_count(0, "role=list-item text=\"buy milk\""));
    } finally {
        Rshutdown();
    }
}

static long find(String selector) {
    Rwait_idle(600);
    long found = Raccessible_find(0, selector);
    assertTrue(found != 0, () -> selector + ": " + Raccessible_get_last_error());
    return found;
}

static void acted(int done) {
    assertTrue(done != 0, () -> Raccessible_get_last_error());
    Rwait_idle(600);
}
```

Run the tests with `--enable-native-access=ALL-UNNAMED` and `RAVEN_LIBRARY` set, as the program.
