An `odin test`, calling the driver through the `raven` package:

```odin
package notes

import "core:testing"
import r "raven"

@(test)
adds_a_note :: proc(t: ^testing.T) {
	desc: r.RInitDesc
	desc.backend = r.R_BACKEND_NULL
	desc.headless = 1
	if !testing.expect(t, r.Rinit_ex(&desc) != 0, "Raven didn't start") do return
	defer r.Rshutdown()
	r.Rset_animation_time_step(1.0 / 60)
	open_notes("notes.xml") // the app's code: builds the window, binds its actions

	find :: proc(t: ^testing.T, selector: cstring) -> u64 {
		r.Rwait_idle(600)
		found := r.Raccessible_find(0, selector)
		testing.expectf(t, found != 0, "%s: %s", selector, r.Raccessible_get_last_error())
		return found
	}
	acted :: proc(t: ^testing.T, done: i32) {
		testing.expectf(t, done != 0, "%s", r.Raccessible_get_last_error())
		r.Rwait_idle(600)
	}
	acted(t, r.Raccessible_type(find(t, "role=text-field"), "buy milk"))
	acted(t, r.Raccessible_click(find(t, `role=button name="Add"`)))
	testing.expect_value(t, r.Raccessible_count(0, `role=list-item text="buy milk"`), 1)
}
```

`odin test . -extra-linker-flags:"-L/path/to/lib -Wl,-rpath,/path/to/lib"`
