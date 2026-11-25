//! The notes example's headless UI test in Rust, through raven::testing: add a note, check the
//! list, delete it, as examples/notes/test_notes.c does in C.
//!
//!   RAVEN_LIB_DIR=/path/to/lib cargo test --test notes
//!
//! RAVEN_TEST_CAPTURE=1 runs it on headless Vulkan, so a failure leaves PNGs in RAVEN_TEST_CAPTURES.

#[path = "../../../examples/notes/notes_app.rs"]
mod notes_app;

use notes_app::{notes_xml, open_notes};
use raven::testing::{self, Options};

#[test]
fn adds_and_deletes_notes() {
    let options = Options {
        name: "test_notes".to_string(),
        capture: std::env::var_os("RAVEN_TEST_CAPTURE").is_some(),
        ..Options::default()
    };
    testing::run_with(options, |ui| {
        open_notes(&notes_xml());

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

        // nor is a note of only spaces
        ui.type_text("role=text-field", "   ");
        ui.click(r#"role=button name="Add""#);
        ui.expect_count("role=list-item", 1);
        ui.expect_text("widget=count", "1 note");

        // a note is added without the spaces around it
        ui.type_text("role=text-field", "  water plants  ");
        ui.key("role=text-field", "Enter");
        ui.expect_text("role=list-item [1]", "water plants");

        // deleting the last notes leaves the label saying so
        for _ in 0..2 {
            ui.click("role=list-item [0]");
            ui.click(r#"role=button name="Delete""#);
        }
        ui.expect_absent("role=list-item");
        ui.expect_text("widget=count", "No notes");

        // the Delete key deletes the selected note, as the button does
        ui.type_text("role=text-field", "buy milk");
        ui.key("role=text-field", "Enter");
        ui.click(r#"role=list-item text="buy milk""#);
        ui.key("role=list", "Delete");
        ui.expect_absent("role=list-item");
        ui.expect_text("widget=count", "No notes");
        ui.expect_enabled(r#"role=button name="Delete""#, false);

        // in the text field, Delete edits the text and deletes no note, even with one selected:
        // Left then Delete removes the b, and Delete at the end of the text does nothing
        ui.type_text("role=text-field", "call Oscar");
        ui.key("role=text-field", "Enter");
        ui.click(r#"role=list-item text="call Oscar""#);
        ui.type_text("role=text-field", "ab");
        ui.key("role=text-field", "Left");
        ui.key("role=text-field", "Delete");
        ui.expect_text("role=text-field", "a");
        ui.key("role=text-field", "End");
        ui.key("role=text-field", "Delete");
        ui.expect_text("role=text-field", "a");
        ui.expect_exists(r#"role=list-item text="call Oscar" selected"#);
        ui.expect_text("widget=count", "1 note");

        // with no note selected, Delete changes nothing (Delete on the list first, to delete call Oscar)
        ui.key("role=list", "Delete");
        ui.expect_absent("role=list-item");
        ui.type_text("role=text-field", "x");
        ui.key("role=text-field", "Enter");
        ui.expect_absent("role=list-item selected");
        ui.key("role=list", "Delete");
        ui.expect_count("role=list-item", 1);

        // adding a note keeps the one selected
        ui.click(r#"role=list-item text="ax""#);
        ui.type_text("role=text-field", "water plants");
        ui.key("role=text-field", "Enter");
        ui.expect_exists(r#"role=list-item text="ax" selected"#);
        ui.expect_count("role=list-item", 2);
    });
}
