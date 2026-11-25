//! The notes example's logic in Rust: what notes.xml's actions do, as notes_app.c does it in C.
//! bindings/rust/tests/notes.rs tests it headless (`cargo test --test notes`).

use raven::*;

/// The notes.xml every language's example builds.
pub const NOTES_XML: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../../examples/notes/notes.xml");

/// Builds the window from notes.xml and binds notes.add and notes.delete. Returns the window.
pub fn open_notes(xml_path: &str) -> Rid {
    let path = cstr(xml_path);
    let window = unsafe { Rwindow_create_from_xml_file(path.as_ptr()) };
    if window == R_INVALID_ID {
        return window;
    }
    let note = widget_find(window, "note");
    let notes = widget_find(window, "notes");
    let count = widget_find(window, "count");
    let delete = widget_find(window, "delete");

    let show_count = move || unsafe {
        let n = Rlist_view_get_item_count(notes);
        let shown = if n == 0 { "No notes".to_string() } else { format!("{} note{}", n, if n == 1 { "" } else { "s" }) };
        Rlabel_set_text(count, cstr(&shown).as_ptr());
    };

    // the text typed, without the spaces around it, becomes a note, and the field is emptied for the next one
    action_bind("notes.add", move |_, _| unsafe {
        let typed = text(Rtext_input_get_text(note));
        let typed = typed.trim_matches(' ');
        if typed.is_empty() {
            return;
        }
        Rlist_view_add_item(notes, cstr(typed).as_ptr());
        Rtext_input_set_text(note, cstr("").as_ptr());
        show_count();
    });
    // the selected note goes
    action_bind("notes.delete", move |_, _| unsafe {
        let selected = Rlist_view_get_selected(notes);
        if selected < 0 {
            return;
        }
        Rlist_view_remove_item(notes, selected);
        Rlist_view_set_selected(notes, -1);
        Rwidget_set_enabled(delete, 0);
        show_count();
    });
    // Delete is there for a selected note only
    unsafe extern "C" fn selection_changed(_list: Rid, index: std::os::raw::c_int, user_data: *mut std::os::raw::c_void) {
        Rwidget_set_enabled(user_data as usize as Rid, if index >= 0 { 1 } else { 0 });
    }
    unsafe { Rlist_view_set_selection_changed_callback(notes, Some(selection_changed), delete as usize as *mut std::os::raw::c_void) };
    window
}
