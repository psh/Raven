# raven-gui

Rust bindings for [Raven](https://github.com/oscar-soirey/Raven), a lightweight,
multithreaded GUI library in C for editors and tools.

```toml
[dependencies]
raven-gui = "0.1"
```

The crate is used as `raven`:

```rust
use raven::*;

fn main() {
    unsafe { Rinit(R_BACKEND_DEFAULT) };
    let _window = window_from_xml(r#"<Window Title="Hi"><Button Text="Quit" OnClicked="quit"/></Window>"#);
    action_bind("quit", |_, _| unsafe { Rquit(0) });
    while unsafe { Rwait_frame(-1.0) } != 0 {}
    unsafe { Rshutdown() };
}
```

## The Raven library

The crate links the Raven C library. Download a package for your platform
from the [releases](https://github.com/oscar-soirey/Raven/releases) (or build
Raven with `-DRAVEN_BUILD_SHARED=ON`), then tell cargo where it is:

```sh
RAVEN_LIB_DIR=/path/to/raven/lib cargo run          # Linux, macOS
set RAVEN_LIB_DIR=C:\path\to\raven\lib && cargo run   # Windows (raven.lib; raven.dll beside the program)
```

`RAVEN_STATIC=1` links the static library (`libraven.a` / `raven.lib`) instead.

`sys` is the whole C API, unchanged; the crate root adds `text`, `cstr`,
`action_bind`, `window_from_xml` and `widget_find`.

## UI tests

`raven::testing` tests an interface headless, as a user would use it: the
selectors of Raven's UI driver, real clicks and typing, and failures that
panic with what was found, as `cargo test` reports them.

```rust
#[test]
fn adds_a_note() {
    raven::testing::run(NOTES_XML, |ui| {
        ui.type_text("role=text-field", "buy milk");
        ui.click(r#"role=button name="Add""#);
        ui.expect_text("role=list-item [0]", "buy milk");
    });
}
```

It runs without a window system and without a GPU, animations advance by a
fixed step, the app is waited on until idle around every step, and Raven's
errors and leaked memory fail the test. Tests take turns (Raven is one per
process), and Raven is shut down even when one panics. `run_with` takes
`Options`: `capture: true` renders on headless Vulkan (the test is skipped,
with a line printed, without it), and a failure then saves a PNG of each
window into `RAVEN_TEST_CAPTURES`. The checks are `expect_exists`,
`expect_absent`, `expect_count`, `expect_text`, `expect_value`,
`expect_checked`, `expect_enabled` and `expect_focused`; `ui.find(selector)`
describes anything else.

With `RAVEN_LIB_DIR` set to the shared library, `build.rs` also gives this
crate's own examples and tests an rpath to it (macOS, Linux).

License: Apache 2.0.
