//! The notes example's headless UI test in Rust, through raven::testing: add a note, check the
//! list, delete it, as examples/notes/test_notes.c does in C.
//!
//!   RAVEN_LIB_DIR=/path/to/lib cargo test --test notes
//!
//! RAVEN_TEST_CAPTURE=1 runs it on headless Vulkan, so a failure leaves PNGs in RAVEN_TEST_CAPTURES.

#[path = "../../../examples/notes/notes_app.rs"]
mod notes_app;

use notes_app::{open_notes, NOTES_XML};
use raven::testing::{self, Options};

#[test]
fn adds_and_deletes_notes() {
    let options = Options {
        name: "test_notes".to_string(),
        capture: std::env::var_os("RAVEN_TEST_CAPTURE").is_some(),
        ..Options::default()
    };
    testing::run_with(options, |ui| {
        open_notes(NOTES_XML);

        // nothing yet: Delete waits for a note to be selected
        ui.expect_absent("role=list-item");
        ui.expect_text("widget=count", "No notes");
        ui.expect_enabled(r#"role=button name="Delete""#, false);

        // add two: with the button, and with Enter
        ui.type_text(r#"role=text-field name="New note""#, "buy milk");
        ui.click(r#"role=button name="Add""#);
        ui.type_text("role=text-field", "call Oscar");
        ui.key("role=text-field", "Enter");

        // the list holds them in order, and the field is empty for the next one
        ui.expect_count("role=list-item", 2);
        ui.expect_text("role=list-item [0]", "buy milk");
        ui.expect_text("role=list-item [1]", "call Oscar");
        ui.expect_text("role=text-field", "");
        ui.expect_text("widget=count", "2 notes");

        // delete the first: select it, then Delete
        ui.click(r#"role=list-item text="buy milk""#);
        ui.expect_enabled(r#"role=button name="Delete""#, true);
        ui.click(r#"role=button name="Delete""#);
        ui.expect_absent(r#"role=list-item text="buy milk""#);
        ui.expect_exists(r#"role=list-item text="call Oscar""#);
        ui.expect_text("widget=count", "1 note");
        ui.expect_enabled(r#"role=button name="Delete""#, false);

        // an empty note is not added
        ui.click(r#"role=button name="Add""#);
        ui.expect_count("role=list-item", 1);
    });
}
