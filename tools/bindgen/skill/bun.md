A `bun test`, calling the driver through `raven.ts`. Accessibles are `bigint`s:

```ts
import { test, expect } from "bun:test";
import * as r from "./raven";

function find(selector: string): bigint {
	r.Rwait_idle(600);
	const found = r.Raccessible_find(0, selector);
	if (found === 0n) throw new Error(`${selector}: ${r.Raccessible_get_last_error()}`);
	return found;
}
function acted(done: number) {
	if (!done) throw new Error(r.Raccessible_get_last_error() ?? "");
	r.Rwait_idle(600);
}

test("adds a note", () => {
	const desc = new r.RInitDesc();
	desc.backend = r.R_BACKEND_NULL;
	desc.headless = 1;
	expect(r.Rinit_ex(desc)).toBeTruthy();
	try {
		r.Rset_animation_time_step(1 / 60);
		openNotes("notes.xml"); // the app's code: builds the window, binds its actions
		acted(r.Raccessible_type(find("role=text-field"), "buy milk"));
		acted(r.Raccessible_click(find('role=button name="Add"')));
		expect(r.Raccessible_count(0, 'role=list-item text="buy milk"')).toBe(1);
	} finally {
		r.Rshutdown();
	}
});
```

`RAVEN_LIBRARY=/path/to/libraven.so bun test`. Rwait_idle draws frames itself: a test needs no
`runLoop()`.
