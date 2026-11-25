A `node --test`, calling the driver through `raven.js`. Accessibles are `bigint`s:

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import * as r from "./raven.js";

function find(selector) {
	r.Rwait_idle(600);
	const found = r.Raccessible_find(0, selector);
	assert.notEqual(found, 0n, `${selector}: ${r.Raccessible_get_last_error()}`);
	return found;
}
function acted(done) {
	assert.ok(done, r.Raccessible_get_last_error());
	r.Rwait_idle(600);
}

test("adds a note", () => {
	const desc = new r.RInitDesc();
	desc.backend = r.R_BACKEND_NULL;
	desc.headless = 1;
	assert.ok(r.Rinit_ex(desc), "Raven didn't start");
	try {
		r.Rset_animation_time_step(1 / 60);
		openNotes("notes.xml"); // the app's code: builds the window, binds its actions
		acted(r.Raccessible_type(find("role=text-field"), "buy milk"));
		acted(r.Raccessible_click(find('role=button name="Add"')));
		assert.equal(r.Raccessible_count(0, 'role=list-item text="buy milk"'), 1);
	} finally {
		r.Rshutdown();
	}
});
```

`RAVEN_LIBRARY=/path/to/libraven.so RAVEN_ADDON=/path/to/raven_napi.node node --test`.
Rwait_idle draws frames itself: a test needs no `runLoop()`.
