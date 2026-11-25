A `zig test`, calling the driver through `raven.zig`:

```zig
const std = @import("std");
const r = @import("raven.zig");

fn find(selector: [*:0]const u8) !u64 {
    _ = r.Rwait_idle(600);
    const found = r.Raccessible_find(0, selector);
    if (found == 0) {
        std.debug.print("{s}: {s}\n", .{ selector, r.Raccessible_get_last_error().? });
        return error.NotFound;
    }
    return found;
}

fn acted(done: c_int) !void {
    if (done == 0) {
        std.debug.print("{s}\n", .{r.Raccessible_get_last_error().?});
        return error.ActionFailed;
    }
    _ = r.Rwait_idle(600);
}

test "adds a note" {
    var desc = std.mem.zeroes(r.RInitDesc);
    desc.backend = r.R_BACKEND_NULL;
    desc.headless = 1;
    try std.testing.expect(r.Rinit_ex(&desc) != 0);
    defer r.Rshutdown();
    r.Rset_animation_time_step(1.0 / 60.0);
    openNotes("notes.xml"); // the app's code: builds the window, binds its actions

    try acted(r.Raccessible_type(try find("role=text-field"), "buy milk"));
    try acted(r.Raccessible_click(try find("role=button name=\"Add\"")));
    try std.testing.expectEqual(@as(c_int, 1), r.Raccessible_count(0, "role=list-item text=\"buy milk\""));
}
```

`build.zig`: the test step links `raven` and libc, as the program does.
